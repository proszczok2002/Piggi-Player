package pl.proszczok.player.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.ComponentName
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.proszczok.player.PiggiPlayerApplication
import pl.proszczok.player.data.AudioTrack
import pl.proszczok.player.data.Playlist
import pl.proszczok.player.playback.PlaybackService
import pl.proszczok.player.playback.EqualizerBandState
import pl.proszczok.player.playback.EqualizerPreset

private sealed interface PendingMediaOperation {
    val tracks: List<AudioTrack>

    data class Trash(override val tracks: List<AudioTrack>) : PendingMediaOperation

    data class RenameTrack(
        override val tracks: List<AudioTrack>,
        val displayName: String,
    ) : PendingMediaOperation

    data class Move(
        override val tracks: List<AudioTrack>,
        val relativePath: String,
        val description: String,
    ) : PendingMediaOperation
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PiggiPlayerApplication
    private val repository = app.mediaRepository
    private val database = app.database
    private val equalizerManager = app.equalizerManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    var tracks by mutableStateOf<List<AudioTrack>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var favoriteIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var excludedFolderPaths by mutableStateOf(
        application.getSharedPreferences("player", 0)
            .getStringSet("excluded_folder_paths", emptySet())?.toSet().orEmpty(),
    )
        private set
    var excludedTrackIds by mutableStateOf(
        application.getSharedPreferences("player", 0)
            .getStringSet("excluded_track_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty(),
    )
        private set
    var currentTrack by mutableStateOf<AudioTrack?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var sleepTimerMinutes by mutableStateOf<Int?>(null)
        private set
    var equalizerRevision by mutableIntStateOf(0)
        private set
    var mediaOperationRequest by mutableStateOf<IntentSender?>(null)
        private set
    var mediaOperationMessage by mutableStateOf<String?>(null)
        private set
    val selectedTrackIds = mutableStateListOf<Long>()

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var uiVisible = false
    private var rebuildingQueue = false
    private val navigationHistory = java.util.ArrayDeque<Int>()
    private var pendingMediaOperation: PendingMediaOperation? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            ensureFullQueue(player)
            syncPlayer(player)
            updateProgressPolling(player)
        }
    }

    init {
        application.getSharedPreferences("player", 0).edit()
            .remove("audiobook_folder_paths")
            .remove("excluded_folders")
            .apply()
        reloadDatabaseState()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            loading = true
            tracks = runCatching { repository.loadTracks() }.getOrElse { emptyList() }
            loading = false
            controllerOrNull()?.let {
                ensureFullQueue(it)
                syncPlayer(it)
            }
        }
    }

    private fun controllerOrNull(): MediaController? = controller

    private fun connectController() {
        if (controllerFuture != null) return
        val application = getApplication<Application>()
        val future = MediaController.Builder(
            application,
            SessionToken(application, ComponentName(application, PlaybackService::class.java)),
        ).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future) return@addListener
            runCatching { future.get() }
                .onSuccess { connectedController ->
                    if (!uiVisible && sleepTimerMinutes == null) {
                        controllerFuture = null
                        MediaController.releaseFuture(future)
                        return@onSuccess
                    }
                    controller = connectedController
                    connectedController.addListener(playerListener)
                    ensureFullQueue(connectedController)
                    syncPlayer(connectedController)
                    updateProgressPolling(connectedController)
                }
                .onFailure {
                    if (controllerFuture === future) controllerFuture = null
                }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun disconnectController() {
        progressJob?.cancel()
        progressJob = null
        controller?.removeListener(playerListener)
        controller = null
        val future = controllerFuture
        controllerFuture = null
        future?.let(MediaController::releaseFuture)
    }

    fun play(track: AudioTrack, queue: List<AudioTrack> = filteredTracks()) {
        val player = controllerOrNull() ?: return
        val source = queue.ifEmpty { listOf(track) }
        val index = source.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        navigationHistory.clear()
        player.setMediaItems(source.map { it.toMediaItem() }, index, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controllerOrNull() ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        controllerOrNull()?.let { player ->
            ensureFullQueue(player)
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET || player.mediaItemCount < 2) return@let
            val media3Next = player.nextMediaItemIndex
            val nextIndex = if (media3Next != C.INDEX_UNSET && media3Next != currentIndex) {
                media3Next
            } else {
                (currentIndex + 1) % player.mediaItemCount
            }
            navigationHistory.addLast(currentIndex)
            player.seekTo(nextIndex, 0L)
        }
    }

    /**
     * Przewijanie okładek jest nawigacją po karuzeli, dlatego na końcu kolejki
     * przechodzi z powrotem do jej początku niezależnie od trybu powtarzania.
     */
    fun swipeToNext() = next()

    fun previous() = skipToPrevious()

    fun skipToPrevious() {
        controllerOrNull()?.let { player ->
            ensureFullQueue(player)
            val previousIndex = if (navigationHistory.isNotEmpty()) {
                navigationHistory.removeLast()
            } else {
                val currentIndex = player.currentMediaItemIndex
                when {
                    currentIndex > 0 -> currentIndex - 1
                    player.mediaItemCount > 1 -> player.mediaItemCount - 1
                    else -> C.INDEX_UNSET
                }
            }
            if (previousIndex != C.INDEX_UNSET) player.seekTo(previousIndex, 0L)
        }
    }

    /** Cofanie okładki również zawija karuzelę na jej drugi koniec. */
    fun swipeToPrevious() = skipToPrevious()

    /**
     * Zwraca utwór, który pojawi się po przewinięciu karuzeli, bez zmiany
     * odtwarzania. Podgląd używa dokładnie tych samych reguł kolejki i historii
     * co [swipeToNext] oraz [swipeToPrevious], dzięki czemu okładka widoczna pod
     * palcem zawsze odpowiada utworowi uruchomionemu po zakończeniu gestu.
     */
    fun swipeTarget(next: Boolean): AudioTrack? {
        val player = controllerOrNull() ?: return null
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET || player.mediaItemCount < 2) return null
        val targetIndex = if (next) {
            val media3Next = player.nextMediaItemIndex
            if (media3Next != C.INDEX_UNSET && media3Next != currentIndex) {
                media3Next
            } else {
                (currentIndex + 1) % player.mediaItemCount
            }
        } else {
            navigationHistory.peekLast() ?: when {
                currentIndex > 0 -> currentIndex - 1
                player.mediaItemCount > 1 -> player.mediaItemCount - 1
                else -> C.INDEX_UNSET
            }
        }
        if (targetIndex == C.INDEX_UNSET) return null
        val mediaId = player.getMediaItemAt(targetIndex).mediaId
        return tracks.firstOrNull { it.uri.toString() == mediaId }
    }

    private fun ensureFullQueue(player: Player): Boolean {
        if (rebuildingQueue || player.mediaItemCount != 1) return false
        val currentMediaId = player.currentMediaItem?.mediaId ?: return false
        val source = filteredTracks().ifEmpty { tracks.filterNot(::isExcluded) }
        if (source.size < 2) return false
        val currentIndex = source.indexOfFirst { it.uri.toString() == currentMediaId }
            .takeIf { it >= 0 }
            ?: currentTrack?.let { current -> source.indexOfFirst { it.id == current.id } }
                ?.takeIf { it >= 0 }
            ?: return false
        val position = player.currentPosition.coerceAtLeast(0L)
        val continuePlaying = player.playWhenReady
        navigationHistory.clear()
        rebuildingQueue = true
        try {
            player.setMediaItems(source.map { it.toMediaItem() }, currentIndex, position)
            player.prepare()
            player.playWhenReady = continuePlaying
        } finally {
            rebuildingQueue = false
        }
        return true
    }

    fun seekTo(position: Long) = controllerOrNull()?.seekTo(position.coerceAtLeast(0))

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun audioSessionId(): Int = controllerOrNull()?.audioSessionId ?: 0

    fun prepareEqualizer(): Boolean {
        val ready = equalizerManager.attach(audioSessionId())
        equalizerRevision++
        return ready
    }

    fun equalizerEnabled(): Boolean = equalizerManager.isEnabled()

    fun equalizerBands(): List<EqualizerBandState> = equalizerManager.bands()

    fun equalizerPreset(): EqualizerPreset = equalizerManager.selectedPreset()

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        equalizerRevision++
    }

    fun setEqualizerBand(index: Short, level: Short) {
        equalizerManager.setBandLevel(index, level)
        equalizerRevision++
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        equalizerManager.applyPreset(preset)
        equalizerRevision++
    }

    fun resetEqualizer() {
        equalizerManager.reset()
        equalizerRevision++
    }

    fun toggleShuffle() {
        controllerOrNull()?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            shuffle = it.shuffleModeEnabled
            getApplication<Application>().getSharedPreferences("player", 0).edit().putBoolean("shuffle", shuffle).apply()
        }
    }

    fun cycleRepeat() {
        controllerOrNull()?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            repeatMode = it.repeatMode
            getApplication<Application>().getSharedPreferences("player", 0).edit().putInt("repeat_mode", repeatMode).apply()
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerMinutes = minutes
        if (minutes != null) {
            connectController()
            sleepJob = viewModelScope.launch {
                delay(minutes * 60_000L)
                controllerOrNull()?.pause()
                sleepTimerMinutes = null
                sleepJob = null
                if (!uiVisible) disconnectController()
            }
        } else if (!uiVisible) {
            disconnectController()
        }
    }

    fun addExcludedFolder(uri: Uri) {
        persistReadPermission(uri)
        treePath(uri)?.let { addExcludedFolders(listOf(it)) }
    }

    fun addExcludedFolders(paths: Collection<String>) {
        val normalized = paths.map { it.replace('\\', '/').trim('/') }
            .filter { it.isNotBlank() }
            .toSet()
        if (normalized.isEmpty()) return
        excludedFolderPaths = excludedFolderPaths + normalized
        saveStringSet("excluded_folder_paths", excludedFolderPaths)
    }

    fun removeExcludedFolder(path: String) {
        excludedFolderPaths = excludedFolderPaths - path
        saveStringSet("excluded_folder_paths", excludedFolderPaths)
    }

    fun addExcludedFile(uri: Uri) = addExcludedFiles(listOf(uri))

    fun addExcludedFiles(uris: Collection<Uri>) {
        val ids = uris.mapNotNull { uri ->
            persistReadPermission(uri)
            mediaId(uri) ?: displayName(uri)?.let { name ->
                tracks.firstOrNull {
                    it.displayName.equals(name, true) ||
                        it.title.equals(name.substringBeforeLast('.'), true)
                }?.id
            }
        }.toSet()
        if (ids.isEmpty()) return
        excludedTrackIds = excludedTrackIds + ids
        saveStringSet("excluded_track_ids", excludedTrackIds.map(Long::toString).toSet())
    }

    fun removeExcludedFile(id: Long) {
        excludedTrackIds = excludedTrackIds - id
        saveStringSet("excluded_track_ids", excludedTrackIds.map(Long::toString).toSet())
    }

    fun requestTrash(items: List<AudioTrack>) {
        val selected = items.distinctBy { it.uri }
        if (selected.isEmpty()) return
        val operation = PendingMediaOperation.Trash(selected)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startMediaAuthorization(operation) { createTrashRequest(selected) }
        } else {
            executeMediaOperation(operation)
        }
    }

    fun requestRenameTrack(track: AudioTrack, newBaseName: String) {
        val cleanName = validFileName(newBaseName) ?: return
        val extension = track.displayName.substringAfterLast('.', "")
            .takeIf { '.' in track.displayName && it.isNotBlank() }
        val displayName = extension?.let { "$cleanName.$it" } ?: cleanName
        if (displayName.equals(track.displayName, ignoreCase = true)) {
            mediaOperationMessage = "Nazwa pliku nie została zmieniona."
            return
        }
        val operation = PendingMediaOperation.RenameTrack(listOf(track), displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startMediaAuthorization(operation) { createWriteRequest(operation.tracks) }
        } else {
            executeMediaOperation(operation)
        }
    }

    fun requestRenameFolder(items: List<AudioTrack>, folderPath: String, newName: String) {
        val cleanName = validFileName(newName) ?: return
        val parent = folderPath.substringBeforeLast('/', "")
        val target = listOf(parent, cleanName).filter { it.isNotBlank() }.joinToString("/")
        requestMove(items, target, "Zmieniono nazwę katalogu na $cleanName")
    }

    fun requestMoveTrack(track: AudioTrack, destinationTree: Uri) {
        val destination = treeDestination(destinationTree) ?: return
        if (!track.volumeName.equals(destination.first, ignoreCase = true)) {
            mediaOperationMessage = "Przenoszenie między pamięcią telefonu a innym nośnikiem nie jest obsługiwane przez MediaStore."
            return
        }
        persistTreePermission(destinationTree)
        requestMove(listOf(track), destination.second, "Przeniesiono plik do ${destinationLabel(destination.second)}")
    }

    fun requestMoveFolder(
        items: List<AudioTrack>,
        folderName: String,
        currentPath: String,
        destinationTree: Uri,
    ) {
        val selected = items.distinctBy { it.uri }
        val destination = treeDestination(destinationTree) ?: return
        if (selected.any { !it.volumeName.equals(destination.first, ignoreCase = true) }) {
            mediaOperationMessage = "Przenoszenie katalogu między różnymi nośnikami nie jest obsługiwane przez MediaStore."
            return
        }
        persistTreePermission(destinationTree)
        val target = listOf(destination.second, folderName).filter { it.isNotBlank() }.joinToString("/")
        if (target.equals(currentPath, ignoreCase = true)) {
            mediaOperationMessage = "Katalog znajduje się już w wybranym miejscu."
            return
        }
        requestMove(selected, target, "Przeniesiono katalog $folderName")
    }

    fun consumeMediaOperationRequest() {
        mediaOperationRequest = null
    }

    fun clearMediaOperationMessage() {
        mediaOperationMessage = null
    }

    fun onMediaOperationAuthorizationResult(approved: Boolean) {
        val operation = pendingMediaOperation ?: return
        pendingMediaOperation = null
        if (!approved) {
            mediaOperationMessage = "Operacja została anulowana."
            return
        }
        if (operation is PendingMediaOperation.Trash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            finishTrash(operation)
            return
        }
        executeMediaOperation(operation)
    }

    private fun executeMediaOperation(operation: PendingMediaOperation) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { applyMediaOperation(operation) }
            withContext(Dispatchers.Main) {
                val error = result.exceptionOrNull()
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
                    pendingMediaOperation = operation
                    mediaOperationRequest = error.userAction.actionIntent.intentSender
                    return@withContext
                }
                if (result.isSuccess && operation is PendingMediaOperation.Trash) {
                    finishTrash(operation)
                    return@withContext
                }
                mediaOperationMessage = result.fold(
                    onSuccess = { count ->
                        if (count == operation.tracks.size) operation.successMessage()
                        else "Zmieniono $count z ${operation.tracks.size} plików."
                    },
                    onFailure = { "Nie udało się wykonać operacji: ${it.localizedMessage ?: "błąd zapisu"}" },
                )
                refreshLibrary()
            }
        }
    }

    private fun finishTrash(operation: PendingMediaOperation.Trash) {
        val affectedIds = operation.tracks.mapTo(hashSetOf()) { it.id }
        if (currentTrack?.id in affectedIds) {
            controllerOrNull()?.apply {
                pause()
                clearMediaItems()
            }
            currentTrack = null
        }
        mediaOperationMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (operation.tracks.size == 1) "Plik przeniesiono do kosza."
            else "Pliki katalogu przeniesiono do kosza."
        } else {
            if (operation.tracks.size == 1) "Plik został trwale usunięty."
            else "Pliki katalogu zostały trwale usunięte."
        }
        refreshLibrary()
    }

    private fun requestMove(items: List<AudioTrack>, relativePath: String, description: String) {
        val selected = items.distinctBy { it.uri }
        if (selected.isEmpty()) return
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (selected.all { it.folderPath.equals(normalized, ignoreCase = true) }) {
            mediaOperationMessage = "Pliki znajdują się już w wybranym miejscu."
            return
        }
        val operation = PendingMediaOperation.Move(selected, normalized, description)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startMediaAuthorization(operation) { createWriteRequest(operation.tracks) }
        } else {
            executeMediaOperation(operation)
        }
    }

    private fun startMediaAuthorization(
        operation: PendingMediaOperation,
        sender: () -> IntentSender,
    ) {
        if (operation.tracks.size > 2_000) {
            mediaOperationMessage = "Jedna operacja może obejmować maksymalnie 2000 plików."
            return
        }
        runCatching(sender).onSuccess {
            pendingMediaOperation = operation
            mediaOperationRequest = it
        }.onFailure {
            pendingMediaOperation = null
            mediaOperationMessage = "Nie można otworzyć potwierdzenia systemowego: ${it.localizedMessage ?: "błąd"}"
        }
    }

    private fun applyMediaOperation(operation: PendingMediaOperation): Int = when (operation) {
        is PendingMediaOperation.RenameTrack -> app.contentResolver.update(
            operation.tracks.single().uri,
            ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, operation.displayName) },
            null,
            null,
        )
        is PendingMediaOperation.Move -> operation.tracks.sumOf { track ->
            app.contentResolver.update(
                track.uri,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        operation.relativePath.takeIf { it.isNotBlank() }?.plus("/") ?: "",
                    )
                },
                null,
                null,
            )
        }
        is PendingMediaOperation.Trash -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                0
            } else {
                operation.tracks.forEach { app.contentResolver.delete(it.uri, null, null) }
                operation.tracks.size
            }
        }
    }

    private fun PendingMediaOperation.successMessage(): String = when (this) {
        is PendingMediaOperation.RenameTrack -> "Zmieniono nazwę pliku na $displayName"
        is PendingMediaOperation.Move -> description
        is PendingMediaOperation.Trash -> "Pliki przeniesiono do kosza."
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createTrashRequest(tracks: List<AudioTrack>): IntentSender =
        MediaStore.createTrashRequest(app.contentResolver, tracks.map { it.uri }, true).intentSender

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createWriteRequest(tracks: List<AudioTrack>): IntentSender =
        MediaStore.createWriteRequest(app.contentResolver, tracks.map { it.uri }).intentSender

    private fun validFileName(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank() || clean == "." || clean == ".." || clean.any { it in "\\/:*?\"<>|" }) {
            mediaOperationMessage = "Podaj poprawną nazwę bez znaków \\ / : * ? \" < > |"
            return null
        }
        return clean
    }

    private fun treeDestination(uri: Uri): Pair<String, String>? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val rawVolume = documentId.substringBefore(':')
        val volume = if (rawVolume.equals("primary", ignoreCase = true)) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        } else rawVolume.lowercase()
        val path = documentId.substringAfter(':', "").replace('\\', '/').trim('/')
        volume to path
    }.getOrElse {
        mediaOperationMessage = "Nie można odczytać wybranego katalogu."
        null
    }

    private fun persistTreePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun destinationLabel(path: String): String = path.ifBlank { "pamięci głównej" }

    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            database.toggleFavorite(track.id)
            withContext(Dispatchers.Main) { favoriteIds = database.favoriteIds() }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { database.createPlaylist(name) }
            withContext(Dispatchers.Main) { playlists = database.playlists() }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deletePlaylist(id)
            withContext(Dispatchers.Main) { playlists = database.playlists() }
        }
    }

    fun addToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            database.addToPlaylist(playlistId, track.id)
            withContext(Dispatchers.Main) { playlists = database.playlists() }
        }
    }

    fun filteredTracks(): List<AudioTrack> {
        val needle = query.trim()
        val visibleTracks = tracks.filterNot(::isExcluded)
        return applyQuery(visibleTracks, needle)
    }

    fun visibleTrackCount(): Int = tracks.count { !isExcluded(it) }

    private fun applyQuery(source: List<AudioTrack>, needle: String): List<AudioTrack> =
        if (needle.isEmpty()) source else source.filter {
            it.title.contains(needle, true) || it.displayName.contains(needle, true) ||
                it.artist.contains(needle, true) ||
                it.album.contains(needle, true) || it.folder.contains(needle, true) ||
                it.folderPath.contains(needle, true)
        }

    private fun isExcluded(track: AudioTrack): Boolean =
        track.id in excludedTrackIds ||
            excludedFolderPaths.any { track.folderPath.isInside(it) }

    private fun String.isInside(parent: String): Boolean =
        equals(parent, ignoreCase = true) || startsWith("$parent/", ignoreCase = true)

    private fun treePath(uri: Uri): String? = runCatching {
        DocumentsContract.getTreeDocumentId(uri)
            .substringAfter(':', "")
            .replace('\\', '/')
            .trim('/')
            .ifBlank { "Pamięć urządzenia" }
    }.getOrNull()

    private fun mediaId(uri: Uri): Long? = runCatching {
        DocumentsContract.getDocumentId(uri).substringAfterLast(':').toLongOrNull()
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.toLongOrNull()

    private fun displayName(uri: Uri): String? =
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun saveStringSet(key: String, value: Set<String>) {
        getApplication<Application>().getSharedPreferences("player", 0).edit()
            .putStringSet(key, value)
            .apply()
    }

    private fun reloadDatabaseState() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedPlaylists = database.playlists()
            val loadedFavorites = database.favoriteIds()
            withContext(Dispatchers.Main) {
                playlists = loadedPlaylists
                favoriteIds = loadedFavorites
            }
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible || sleepTimerMinutes != null) {
            connectController()
            controllerOrNull()?.let(::updateProgressPolling)
        } else {
            disconnectController()
        }
    }

    private fun updateProgressPolling(player: Player) {
        progressJob?.cancel()
        progressJob = null
        if (!uiVisible || !player.isPlaying) return
        progressJob = viewModelScope.launch {
            while (isActive && uiVisible && player.isPlaying) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = player.duration.coerceAtLeast(0)
                delay(1_000)
            }
        }
    }

    private fun syncPlayer(player: Player) {
        isPlaying = player.isPlaying
        positionMs = player.currentPosition.coerceAtLeast(0)
        durationMs = player.duration.coerceAtLeast(0)
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId != null) currentTrack = tracks.firstOrNull { it.uri.toString() == mediaId } ?: currentTrack
    }

    override fun onCleared() {
        sleepJob?.cancel()
        disconnectController()
        super.onCleared()
    }
}
