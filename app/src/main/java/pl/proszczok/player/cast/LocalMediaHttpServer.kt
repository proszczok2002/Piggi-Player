package pl.proszczok.player.cast

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CastSharedUrls(val audio: String, val artwork: String)

class LocalMediaHttpServer(private val context: Context) : NanoHTTPD(0) {
    private data class SharedTrack(
        val uri: Uri,
        val artworkUri: Uri?,
        val mimeType: String,
        val length: Long,
    )

    private val shared = ConcurrentHashMap<String, SharedTrack>()

    fun share(uri: Uri, artworkUri: Uri?, mimeType: String?, receiverAddress: InetAddress?): CastSharedUrls {
        // wasStarted() stays true after NanoHTTPD has been stopped. isAlive is the state that
        // matters here, otherwise a later Cast session receives URLs pointing at a closed port.
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
        val token = UUID.randomUUID().toString().replace("-", "")
        shared[token] = SharedTrack(
            uri = uri,
            artworkUri = artworkUri,
            mimeType = mimeType ?: context.contentResolver.getType(uri) ?: "audio/mpeg",
            length = sizeOf(uri),
        )
        val base = "http://${localIpv4(receiverAddress)}:$listeningPort"
        return CastSharedUrls("$base/audio/$token", "$base/artwork/$token")
    }

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.size != 2) return notFound()
        val item = shared[parts[1]] ?: return notFound()
        return when (parts[0]) {
            "audio" -> serveAudio(session, item)
            "artwork" -> serveArtwork(item)
            else -> notFound()
        }
    }

    private fun serveAudio(session: IHTTPSession, item: SharedTrack): Response {
        val requestedStart = session.headers["range"]
            ?.removePrefix("bytes=")
            ?.substringBefore('-')
            ?.toLongOrNull()
            ?: 0L
        if (item.length > 0 && requestedStart >= item.length) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Nieprawidłowy zakres")
                .apply { addHeader("Content-Range", "bytes */${item.length}") }
        }
        val stream = BufferedInputStream(
            context.contentResolver.openInputStream(item.uri) ?: return notFound(),
        )
        var remainingSkip = requestedStart
        while (remainingSkip > 0) {
            val skipped = stream.skip(remainingSkip)
            if (skipped <= 0) break
            remainingSkip -= skipped
        }
        val remaining = if (item.length > 0) item.length - requestedStart else -1L
        val response = if (remaining >= 0) {
            newFixedLengthResponse(
                if (requestedStart > 0) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                item.mimeType,
                stream,
                remaining,
            )
        } else {
            newChunkedResponse(Response.Status.OK, item.mimeType, stream)
        }
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "no-store")
        if (requestedStart > 0 && item.length > 0) {
            response.addHeader("Content-Range", "bytes $requestedStart-${item.length - 1}/${item.length}")
        }
        return response
    }

    private fun serveArtwork(item: SharedTrack): Response {
        val bytes = readArtwork(item) ?: return notFound()
        val mime = when {
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "image/gif"
            else -> "image/jpeg"
        }
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "private, max-age=3600") }
    }

    private fun readArtwork(item: SharedTrack): ByteArray? {
        item.artworkUri?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, item.uri)
                retriever.embeddedPicture
            }
        }.getOrNull()
    }

    private fun sizeOf(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    private fun localIpv4(receiverAddress: InetAddress?): String {
        if (receiverAddress != null) {
            runCatching {
                DatagramSocket().use { socket ->
                    socket.connect(InetSocketAddress(receiverAddress, 9))
                    socket.localAddress.hostAddress
                }
            }.getOrNull()?.takeIf { it != "0.0.0.0" && !it.contains(':') }?.let { return it }
        }
        return NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .sortedByDescending { it.name.contains("wlan", ignoreCase = true) || it.name.contains("wifi", ignoreCase = true) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && !it.hostAddress.orEmpty().contains(':') && it.isSiteLocalAddress }
            ?.hostAddress
            ?: "127.0.0.1"
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Brak pliku")

    fun clearShares() {
        shared.clear()
    }

    fun closeServer() {
        clearShares()
        stop()
    }
}
