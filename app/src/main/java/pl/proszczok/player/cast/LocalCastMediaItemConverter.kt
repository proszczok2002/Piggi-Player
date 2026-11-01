package pl.proszczok.player.cast

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionTransferCallback
import com.google.android.gms.cast.MediaQueueItem
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class LocalCastMediaItemConverter(context: Context) : MediaItemConverter, AutoCloseable {
    private val appContext = context.applicationContext
    private val server = LocalMediaHttpServer(appContext)
    private val delegate = DefaultMediaItemConverter()
    private val originals = ConcurrentHashMap<String, MediaItem>()
    private val castContext = CastContext.getSharedInstance(appContext)
    private val cleanupHandler = Handler(Looper.getMainLooper())
    // Keep the lightweight listener alive for the PlaybackService lifetime. After Cast ends we
    // only revoke the temporary URLs. This makes repeated Cast sessions reuse a valid endpoint.
    private val cleanupServer = Runnable { server.clearShares() }
    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = cancelServerCleanup()
        override fun onSessionStarted(session: CastSession, sessionId: String) = cancelServerCleanup()
        override fun onSessionStartFailed(session: CastSession, error: Int) = scheduleServerCleanup()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = scheduleServerCleanup()
        override fun onSessionResuming(session: CastSession, sessionId: String) = cancelServerCleanup()
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = cancelServerCleanup()
        override fun onSessionResumeFailed(session: CastSession, error: Int) = scheduleServerCleanup()
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }
    private val transferCallback = object : SessionTransferCallback() {
        override fun onTransferring(transferType: Int) {
            if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) cancelServerCleanup()
        }

        override fun onTransferred(transferType: Int, sessionState: com.google.android.gms.cast.SessionState) {
            if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) scheduleServerCleanup()
        }

        override fun onTransferFailed(transferType: Int, transferFailedReason: Int) {
            if (transferType != TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) return
            if (castContext.sessionManager.currentCastSession == null) scheduleServerCleanup()
            else cancelServerCleanup()
        }
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.addSessionTransferCallback(transferCallback)
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        cancelServerCleanup()
        val local = requireNotNull(mediaItem.localConfiguration)
        originals[mediaItem.mediaId] = mediaItem
        val receiverAddress = runCatching {
            castContext.sessionManager.currentCastSession?.castDevice?.inetAddress
        }.getOrNull()
        val urls = server.share(
            uri = local.uri,
            artworkUri = mediaItem.mediaMetadata.artworkUri,
            mimeType = local.mimeType,
            receiverAddress = receiverAddress,
        )
        val remoteMetadata = mediaItem.mediaMetadata.buildUpon()
            .setArtworkUri(Uri.parse(urls.artwork))
            .build()
        return delegate.toMediaQueueItem(
            mediaItem.buildUpon()
                .setUri(Uri.parse(urls.audio))
                .setMediaMetadata(remoteMetadata)
                .build(),
        )
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(mediaQueueItem)
        originals[converted.mediaId]?.let { return it }

        // The process may be recreated while Cast is active. Media IDs in Piggi Player are
        // stable content:// URIs, so never hand ExoPlayer a stale temporary HTTP URL.
        val localUri = Uri.parse(converted.mediaId)
        if (localUri.scheme != ContentResolver.SCHEME_CONTENT) return converted
        val localArtwork = converted.mediaMetadata.artworkUri
            ?.takeUnless { it.scheme == "http" || it.scheme == "https" }
        return converted.buildUpon()
            .setUri(localUri)
            .setMediaMetadata(converted.mediaMetadata.buildUpon().setArtworkUri(localArtwork).build())
            .build()
    }

    override fun close() {
        cleanupHandler.removeCallbacks(cleanupServer)
        castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.removeSessionTransferCallback(transferCallback)
        originals.clear()
        server.closeServer()
    }

    private fun cancelServerCleanup() {
        cleanupHandler.removeCallbacks(cleanupServer)
    }

    private fun scheduleServerCleanup() {
        cleanupHandler.removeCallbacks(cleanupServer)
        cleanupHandler.postDelayed(cleanupServer, SERVER_CLEANUP_DELAY_MS)
    }

    private companion object {
        const val SERVER_CLEANUP_DELAY_MS = 15_000L
    }
}
