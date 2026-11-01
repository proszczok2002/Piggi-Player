package pl.proszczok.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import pl.proszczok.player.MainActivity
import pl.proszczok.player.PiggiPlayerApplication
import pl.proszczok.player.R
import pl.proszczok.player.cast.LocalCastMediaItemConverter

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var localPlayer: ExoPlayer
    private lateinit var player: CastPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var castConverter: LocalCastMediaItemConverter
    private var lastLocalTransferState: PlayerTransferState? = null
    private val persistenceHandler = Handler(Looper.getMainLooper())
    private val delayedPersistence = Runnable {
        if (::player.isInitialized) persistPlaybackState(player, includeQueue = false)
    }
    private val castTransferCallback = CastPlayer.TransferCallback { fromPlayer, toPlayer ->
        Log.i(
            TAG,
            "Cast transfer ${fromPlayer.javaClass.simpleName} -> ${toPlayer.javaClass.simpleName}; " +
                "items=${fromPlayer.mediaItemCount}, index=${fromPlayer.currentMediaItemIndex}, " +
                "position=${fromPlayer.currentPosition}",
        )
        when {
            ::localPlayer.isInitialized && fromPlayer === localPlayer -> {
                lastLocalTransferState = PlayerTransferState.fromPlayer(fromPlayer)
                CastPlayer.TransferCallback.DEFAULT.transferState(fromPlayer, toPlayer)
            }

            ::localPlayer.isInitialized && toPlayer === localPlayer -> {
                restoreLocalPlaybackState(fromPlayer, toPlayer)
            }

            else -> CastPlayer.TransferCallback.DEFAULT.transferState(fromPlayer, toPlayer)
        }
        Log.i(TAG, "Cast transfer state applied; destinationItems=${toPlayer.mediaItemCount}")
    }

    private fun restoreLocalPlaybackState(fromPlayer: Player, toPlayer: Player) {
        val snapshot = lastLocalTransferState
        val snapshotItems = snapshot?.mediaItems.orEmpty()
        if (snapshot == null || snapshotItems.isEmpty()) {
            CastPlayer.TransferCallback.DEFAULT.transferState(fromPlayer, toPlayer)
            return
        }

        val remoteHasItems = fromPlayer.mediaItemCount > 0
        val remoteMediaId = fromPlayer.currentMediaItem?.mediaId
        val matchingIndex = remoteMediaId?.let { id ->
            snapshotItems.indexOfFirst { it.mediaId == id }
        } ?: -1
        val restoredIndex = if (matchingIndex >= 0) {
            matchingIndex
        } else {
            snapshot.currentMediaItemIndex.coerceIn(0, snapshotItems.lastIndex)
        }

        snapshot.buildUpon()
            .setCurrentMediaItemIndex(restoredIndex)
            .setCurrentPosition(if (remoteHasItems) fromPlayer.currentPosition else snapshot.currentPosition)
            .setPlayWhenReady(if (remoteHasItems) fromPlayer.playWhenReady else snapshot.playWhenReady)
            .setRepeatMode(if (remoteHasItems) fromPlayer.repeatMode else snapshot.repeatMode)
            .setShuffleModeEnabled(
                if (remoteHasItems) fromPlayer.shuffleModeEnabled else snapshot.shuffleModeEnabled,
            )
            .setPlaybackParameters(
                if (remoteHasItems) fromPlayer.playbackParameters else snapshot.playbackParameters,
            )
            .build()
            .setToPlayer(toPlayer)
        Log.i(
            TAG,
            "Restored local Cast snapshot; items=${snapshotItems.size}, " +
                "remoteItems=${fromPlayer.mediaItemCount}, index=$restoredIndex",
        )
    }

    private val persistenceListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback failed: ${error.errorCodeName}", error)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            when {
                events.contains(Player.EVENT_TIMELINE_CHANGED) -> {
                    persistenceHandler.removeCallbacks(delayedPersistence)
                    persistPlaybackState(player, includeQueue = true)
                }
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED) -> {
                    persistenceHandler.removeCallbacks(delayedPersistence)
                    persistPlaybackState(player, includeQueue = false)
                }
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) -> {
                    persistenceHandler.removeCallbacks(delayedPersistence)
                    persistenceHandler.postDelayed(delayedPersistence, POSITION_PERSISTENCE_DELAY_MS)
                }
            }
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int = when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            -> {
                seekCircular(forward = true)
                SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            -> {
                seekCircular(forward = false)
                SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            else -> SessionResult.RESULT_SUCCESS
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = restorePlaybackState()
                ?: return Futures.immediateFailedFuture(IllegalStateException("Brak zapisanego utworu"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.items,
                    saved.currentIndex,
                    saved.position,
                ),
            )
        }

        override fun onMediaButtonEvent(
            mediaSession: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
            val forward = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                -> true
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                -> false
                else -> return false
            }
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                seekCircular(forward)
            }
            return true
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        localPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val equalizerManager = (application as PiggiPlayerApplication).equalizerManager
        if (equalizerManager.isConfiguredEnabled()) {
            equalizerManager.attach(localPlayer.audioSessionId)
        }

        castConverter = LocalCastMediaItemConverter(this)
        val remotePlayer = RemoteCastPlayer.Builder(this)
            .setMediaItemConverter(castConverter)
            .build()
        player = CastPlayer.Builder(this)
            .setLocalPlayer(localPlayer)
            .setRemotePlayer(remotePlayer)
            .setTransferCallback(castTransferCallback)
            .build()
            .apply {
                repeatMode = getSharedPreferences("player", MODE_PRIVATE)
                    .getInt("repeat_mode", Player.REPEAT_MODE_OFF)
                shuffleModeEnabled = getSharedPreferences("player", MODE_PRIVATE)
                    .getBoolean("shuffle", false)
                addListener(persistenceListener)
            }

        val sessionIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionIntent)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        persistenceHandler.removeCallbacks(delayedPersistence)
        if (::player.isInitialized) {
            persistPlaybackState(player, includeQueue = true)
            player.removeListener(persistenceListener)
        }
        (application as PiggiPlayerApplication).equalizerManager.release()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        if (::castConverter.isInitialized) castConverter.close()
        super.onDestroy()
    }

    private fun seekCircular(forward: Boolean) {
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (currentIndex == C.INDEX_UNSET || count < 2) return
        val media3Index = if (forward) player.nextMediaItemIndex else player.previousMediaItemIndex
        val targetIndex = if (media3Index != C.INDEX_UNSET && media3Index != currentIndex) {
            media3Index
        } else if (forward) {
            (currentIndex + 1) % count
        } else {
            (currentIndex - 1 + count) % count
        }
        player.seekTo(targetIndex, 0L)
    }

    private fun persistPlaybackState(player: Player, includeQueue: Boolean) {
        val item = player.currentMediaItem ?: return
        val local = item.localConfiguration ?: return
        val editor = getSharedPreferences("playback_resumption", MODE_PRIVATE).edit()
            .putInt("current_index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putString("media_id", item.mediaId)
            .putString("uri", local.uri.toString())
            .putString("mime", local.mimeType)
            .putString("title", item.mediaMetadata.title?.toString())
            .putString("artist", item.mediaMetadata.artist?.toString())
            .putString("album", item.mediaMetadata.albumTitle?.toString())
            .putString("artwork", item.mediaMetadata.artworkUri?.toString())
            .putLong("position", player.currentPosition.coerceAtLeast(0L))
        if (includeQueue) {
            val queue = JSONArray()
            repeat(player.mediaItemCount) { index ->
                player.getMediaItemAt(index).toJson()?.let(queue::put)
            }
            editor.putString("queue", queue.toString())
        }
        editor.apply()
    }

    private fun restorePlaybackState(): SavedPlayback? {
        val preferences = getSharedPreferences("playback_resumption", MODE_PRIVATE)
        val savedQueue = runCatching {
            val json = JSONArray(preferences.getString("queue", null) ?: return@runCatching emptyList())
            buildList {
                repeat(json.length()) { index ->
                    json.optJSONObject(index)?.toMediaItem()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        if (savedQueue.isNotEmpty()) {
            return SavedPlayback(
                items = savedQueue,
                currentIndex = preferences.getInt("current_index", 0).coerceIn(savedQueue.indices),
                position = preferences.getLong("position", 0L).coerceAtLeast(0L),
            )
        }
        val uri = preferences.getString("uri", null)?.let(Uri::parse) ?: return null
        val metadata = MediaMetadata.Builder()
            .setTitle(preferences.getString("title", null))
            .setArtist(preferences.getString("artist", null))
            .setAlbumTitle(preferences.getString("album", null))
            .setArtworkUri(preferences.getString("artwork", null)?.let(Uri::parse))
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        val item = MediaItem.Builder()
            .setMediaId(preferences.getString("media_id", uri.toString()) ?: uri.toString())
            .setUri(uri)
            .setMimeType(preferences.getString("mime", null))
            .setMediaMetadata(metadata)
            .build()
        return SavedPlayback(listOf(item), 0, preferences.getLong("position", 0L).coerceAtLeast(0L))
    }

    private fun MediaItem.toJson(): JSONObject? {
        val local = localConfiguration ?: return null
        return JSONObject().apply {
            put("media_id", mediaId)
            put("uri", local.uri.toString())
            local.mimeType?.let { put("mime", it) }
            mediaMetadata.title?.toString()?.let { put("title", it) }
            mediaMetadata.artist?.toString()?.let { put("artist", it) }
            mediaMetadata.albumTitle?.toString()?.let { put("album", it) }
            mediaMetadata.artworkUri?.toString()?.let { put("artwork", it) }
        }
    }

    private fun JSONObject.toMediaItem(): MediaItem? {
        val uri = optString("uri").takeIf(String::isNotBlank)?.let(Uri::parse) ?: return null
        val metadata = MediaMetadata.Builder()
            .setTitle(nullableString("title"))
            .setArtist(nullableString("artist"))
            .setAlbumTitle(nullableString("album"))
            .setArtworkUri(nullableString("artwork")?.let(Uri::parse))
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(nullableString("media_id") ?: uri.toString())
            .setUri(uri)
            .setMimeType(nullableString("mime"))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private data class SavedPlayback(
        val items: List<MediaItem>,
        val currentIndex: Int,
        val position: Long,
    )

    private companion object {
        const val TAG = "PiggiPlayback"
        const val POSITION_PERSISTENCE_DELAY_MS = 750L
    }
}
