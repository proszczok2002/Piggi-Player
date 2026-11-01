@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package pl.proszczok.player.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.proszczok.player.BuildConfig
import pl.proszczok.player.data.AudioTrack
import pl.proszczok.player.data.Playlist
import pl.proszczok.player.data.asTime
import pl.proszczok.player.ui.theme.AppThemeMode

private enum class LibraryTab(val label: String) {
    SONGS("Utwory"), ALBUMS("Albumy"), ARTISTS("Wykonawcy"), FAVORITES("Ulubione")
}

private data class FolderActionTarget(
    val name: String,
    val path: String,
    val tracks: List<AudioTrack>,
)

@Composable
internal fun LibraryScreen(viewModel: PlayerViewModel) {
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryTab.entries) { item ->
                FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (viewModel.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else when (tab) {
            LibraryTab.SONGS -> TrackList(viewModel.filteredTracks(), viewModel)
            LibraryTab.FAVORITES -> TrackList(viewModel.filteredTracks().filter { it.id in viewModel.favoriteIds }, viewModel)
            LibraryTab.ALBUMS -> GroupedScreen(viewModel, { it.album }, "Albumy", embedded = true)
            LibraryTab.ARTISTS -> GroupedScreen(viewModel, { it.artist }, "Wykonawcy", embedded = true)
        }
    }
}

@Composable
internal fun GroupedScreen(
    viewModel: PlayerViewModel,
    groupBy: (AudioTrack) -> String,
    heading: String,
    embedded: Boolean = false,
) {
    val folderMode = heading == "Katalogi"
    val groups = viewModel.filteredTracks()
        .groupBy { if (folderMode) it.folderPath else groupBy(it) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    var open by remember(heading) { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FolderActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<FolderActionTarget?>(null) }
    var moveTarget by remember { mutableStateOf<FolderActionTarget?>(null) }
    val moveFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = moveTarget
        if (uri != null && target != null) {
            viewModel.requestMoveFolder(target.tracks, target.name, target.path, uri)
        }
        moveTarget = null
    }
    BackHandler(enabled = open != null) { open = null }
    if (open != null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { open = null }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Wstecz do: $heading") }
            TrackList(groups[open].orEmpty(), viewModel)
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!embedded) item {
                Text(heading, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            }
            items(groups.entries.toList(), key = { it.key }) { (groupKey, tracks) ->
                val name = if (folderMode) tracks.firstOrNull()?.folder.orEmpty().ifBlank { "Pamięć urządzenia" } else groupKey
                var menu by remember(groupKey) { mutableStateOf(false) }
                val target = FolderActionTarget(name, groupKey, tracks)
                Card(onClick = { open = groupKey }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(if (folderMode) Icons.Default.Folder else Icons.Default.Album, null, Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${tracks.size} ${if (tracks.size == 1) "utwór" else "utworów"}", style = MaterialTheme.typography.bodySmall)
                            if (folderMode) Text(groupKey, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        FilledIconButton(onClick = { viewModel.play(tracks.first(), tracks) }) {
                            Icon(Icons.Default.PlayArrow, "Odtwórz grupę $name")
                        }
                        if (folderMode) Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, "Więcej opcji dla katalogu $name")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Zmień nazwę katalogu") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; renameTarget = target },
                                )
                                DropdownMenuItem(
                                    text = { Text("Przenieś katalog") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                                    onClick = {
                                        menu = false
                                        moveTarget = target
                                        moveFolderPicker.launch(null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Usuń katalog") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menu = false; deleteTarget = target },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    renameTarget?.let { target ->
        var value by remember(target.path) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Zmień nazwę katalogu") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Nowa nazwa") },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "Nowa nazwa katalogu" },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestRenameFolder(target.tracks, target.path, value)
                    renameTarget = null
                }) { Text("Zmień nazwę") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Anuluj") } },
        )
    }

    deleteTarget?.let { target ->
        val usesSystemTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usuń katalog ${target.name}?") },
            text = {
                Text(
                    if (usesSystemTrash) {
                        "${target.tracks.size} plików zostanie przeniesionych do kosza po potwierdzeniu Androida."
                    } else {
                        "Android 10 nie ma systemowego kosza. ${target.tracks.size} plików zostanie trwale usuniętych po potwierdzeniu Androida."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestTrash(target.tracks)
                    deleteTarget = null
                }) { Text(if (usesSystemTrash) "Przenieś do kosza" else "Usuń trwale") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
internal fun TrackList(tracks: List<AudioTrack>, viewModel: PlayerViewModel) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Brak pasujących plików audio")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(tracks, key = { it.id }) { track -> TrackRow(track, tracks, viewModel) }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, queue: List<AudioTrack>, viewModel: PlayerViewModel) {
    var menu by remember { mutableStateOf(false) }
    var playlistPicker by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val moveFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.requestMoveTrack(track, uri)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(
            role = Role.Button,
            onClick = { viewModel.play(track, queue) },
            onLongClick = { menu = true },
            onLongClickLabel = "Otwórz menu utworu",
        ).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            TrackArtwork(track, 208, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                "${track.artist} • ${track.durationMs.asTime()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Więcej opcji dla ${track.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (track.id in viewModel.favoriteIds) "Usuń z ulubionych" else "Dodaj do ulubionych") },
                    leadingIcon = { Icon(if (track.id in viewModel.favoriteIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                    onClick = { menu = false; viewModel.toggleFavorite(track) },
                )
                DropdownMenuItem(
                    text = { Text("Dodaj do playlisty") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    onClick = { menu = false; playlistPicker = true },
                )
                DropdownMenuItem(
                    text = { Text("Zmień nazwę pliku") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; renameDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("Przenieś plik") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                    onClick = {
                        menu = false
                        moveFilePicker.launch(null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Usuń plik") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menu = false; deleteDialog = true },
                )
            }
        }
    }
    if (playlistPicker) PlaylistPicker(
        track,
        viewModel.playlists,
        onPick = { viewModel.addToPlaylist(it, track); playlistPicker = false },
        onDismiss = { playlistPicker = false },
    )
    if (renameDialog) {
        val currentBaseName = track.displayName.substringBeforeLast('.', track.displayName)
        var value by remember(track.id) { mutableStateOf(currentBaseName) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Zmień nazwę pliku") },
            text = {
                Column {
                    Text("Rozszerzenie pliku pozostanie bez zmian.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Nazwa bez rozszerzenia") },
                        singleLine = true,
                        modifier = Modifier.semantics { contentDescription = "Nowa nazwa pliku bez rozszerzenia" },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestRenameTrack(track, value)
                    renameDialog = false
                }) { Text("Zmień nazwę") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Anuluj") } },
        )
    }
    if (deleteDialog) {
        val usesSystemTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Usunąć plik?") },
            text = {
                Text(
                    if (usesSystemTrash) {
                        "„${track.displayName}” zostanie przeniesiony do kosza po potwierdzeniu Androida."
                    } else {
                        "Android 10 nie ma systemowego kosza. „${track.displayName}” zostanie trwale usunięty po potwierdzeniu Androida."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestTrash(listOf(track))
                    deleteDialog = false
                }) { Text(if (usesSystemTrash) "Przenieś do kosza" else "Usuń trwale") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun PlaylistPicker(track: AudioTrack, playlists: List<Playlist>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj „${track.title}”") },
        text = {
            if (playlists.isEmpty()) Text("Najpierw utwórz playlistę w zakładce Playlisty.")
            else Column {
                playlists.forEach { playlist ->
                    TextButton(onClick = { onPick(playlist.id) }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
internal fun PlaylistsScreen(viewModel: PlayerViewModel) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<Long?>(null) }
    val current = viewModel.playlists.firstOrNull { it.id == openId }
    BackHandler(enabled = current != null) { openId = null }
    Box(Modifier.fillMaxSize()) {
        if (current != null) {
            Column {
                TextButton(onClick = { openId = null }) { Text("Wstecz do playlist") }
                TrackList(current.trackIds.mapNotNull { id -> viewModel.tracks.firstOrNull { it.id == id } }, viewModel)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Playlisty", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
                items(viewModel.playlists, key = { it.id }) { playlist ->
                    Card(onClick = { openId = playlist.id }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, fontWeight = FontWeight.Medium)
                                Text("${playlist.trackIds.size} utworów", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                Icon(Icons.Default.Delete, "Usuń playlistę ${playlist.name}")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Nowa playlista") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).clearAndSetSemantics {
                contentDescription = "Utwórz nową playlistę"
                role = Role.Button
                onClick(label = "Utwórz nową playlistę") {
                    creating = true
                    true
                }
            },
        )
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nowa playlista") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nazwa") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.createPlaylist(name)
                    name = ""
                    creating = false
                }) { Text("Utwórz") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
internal fun SettingsScreen(
    viewModel: PlayerViewModel,
    themeMode: AppThemeMode,
    onThemeMode: (AppThemeMode) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsEnabled: (Boolean) -> Unit,
    updateChecking: Boolean,
    onCheckUpdates: () -> Unit,
) {
    val headingFocus = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    var folderPickerOpen by remember { mutableStateOf(false) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { headingFocus.requestFocus() }
    val excludedFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addExcludedFiles(uris)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Ustawienia",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.focusRequester(headingFocus).semantics {
                    heading()
                    contentDescription = "Ustawienia"
                }.focusable(),
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wygląd", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        "Wybierz kolory aplikacji. Opcja systemowa automatycznie naśladuje jasny lub ciemny motyw telefonu.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AppThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(
                                selected = selected,
                                onClick = { onThemeMode(mode) },
                                role = Role.RadioButton,
                            ).semantics {
                                stateDescription = if (selected) "Wybrany" else "Niewybrany"
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(when (mode) {
                                AppThemeMode.SYSTEM -> "Jak w systemie"
                                AppThemeMode.LIGHT -> "Jasny"
                                AppThemeMode.DARK -> "Ciemny"
                            })
                        }
                    }
                    Text(
                        "Ruch i animacje",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(
                            value = !animationsEnabled,
                            role = Role.Switch,
                            onValueChange = { disabled -> onAnimationsEnabled(!disabled) },
                        ).semantics {
                            stateDescription = if (animationsEnabled) "Animacje włączone" else "Animacje wyłączone"
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wyłącz animacje")
                            Text(
                                "Wyłącza ruch okładki oraz animacje maksymalizacji i minimalizacji odtwarzacza.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = !animationsEnabled, onCheckedChange = null)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wykluczenia biblioteki", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        "Dodawane są wyłącznie wybrane przez Ciebie katalogi lub pliki. Wykluczenie ukrywa je w Bibliotece i Katalogach, ale niczego nie usuwa z telefonu.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Ręczne wykluczenia: ${viewModel.excludedFolderPaths.size} katalogów i ${viewModel.excludedTrackIds.size} plików.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(
                        onClick = {
                            selectedFolderPaths = emptySet()
                            folderPickerOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dodaj wykluczone katalogi")
                    }
                    OutlinedButton(
                        onClick = { excludedFilePicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.MusicNote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dodaj wykluczone pliki audio")
                    }
                    viewModel.excludedFolderPaths.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { path ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Katalog: $path", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { viewModel.removeExcludedFolder(path) }) {
                                Icon(Icons.Default.Delete, "Usuń wykluczenie katalogu $path")
                            }
                        }
                    }
                    viewModel.excludedTrackIds.sorted().forEach { id ->
                        val name = viewModel.tracks.firstOrNull { it.id == id }?.title ?: "Plik $id"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Plik: $name", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { viewModel.removeExcludedFile(id) }) {
                                Icon(Icons.Default.Delete, "Usuń wykluczenie pliku $name")
                            }
                        }
                    if (viewModel.excludedFolderPaths.isEmpty() && viewModel.excludedTrackIds.isEmpty()) {
                        Text("Brak ręcznych wykluczeń.", style = MaterialTheme.typography.bodyMedium)
                    }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wsparcie", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        "Jeśli podoba Ci się Piggi Player, możesz rzucić monetą i wesprzeć dalszy rozwój aplikacji.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://paypal.me/proszczok") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Favorite, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wesprzyj przez PayPal")
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://revolut.me/r_rosiejka") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wesprzyj przez Revolut")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Biblioteka lokalna", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text("Widoczne pliki: ${viewModel.visibleTrackCount()} z ${viewModel.tracks.size} znalezionych przez Androida.")
                    Text(
                        "Aplikacja czyta wyłącznie lokalną bibliotekę MediaStore. Nie wymaga konta, nie wyświetla reklam i nie synchronizuje muzyki z chmurą.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = viewModel::refreshLibrary, modifier = Modifier.fillMaxWidth()) {
                        Text("Przeskanuj bibliotekę ponownie")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Piggi Player", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text("Wersja ${BuildConfig.VERSION_NAME}")
                    Text(
                        "Odtwarzanie obsługuje Android Media3. Aplikacja nie obniża bitrate ani częstotliwości próbkowania pliku.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Aktualizacje są sprawdzane przy uruchomieniu. Przycisk poniżej pozwala wykonać dodatkowe sprawdzenie ręczne.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = onCheckUpdates,
                        enabled = !updateChecking,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        if (updateChecking) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Sprawdzanie…")
                        } else {
                            Text("Sprawdź aktualizacje")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
    if (folderPickerOpen) {
        val availableFolders = viewModel.tracks
            .map { it.folderPath }
            .filter { path ->
                path.isNotBlank() && viewModel.excludedFolderPaths.none { excluded ->
                    path.equals(excluded, ignoreCase = true) ||
                        path.startsWith("$excluded/", ignoreCase = true)
                }
            }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        AlertDialog(
            onDismissRequest = { folderPickerOpen = false },
            title = { Text("Wybierz katalogi") },
            text = {
                if (availableFolders.isEmpty()) {
                    Text("Brak kolejnych katalogów z plikami audio do wykluczenia.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(availableFolders, key = { it }) { path ->
                            val selected = path in selectedFolderPaths
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(
                                    value = selected,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selectedFolderPaths = if (checked) {
                                            selectedFolderPaths + path
                                        } else {
                                            selectedFolderPaths - path
                                        }
                                    },
                                ).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedFolderPaths.isNotEmpty(),
                    onClick = {
                        viewModel.addExcludedFolders(selectedFolderPaths)
                        folderPickerOpen = false
                    },
                ) {
                    Text("Dodaj zaznaczone")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPickerOpen = false }) { Text("Anuluj") }
            },
        )
    }
}
