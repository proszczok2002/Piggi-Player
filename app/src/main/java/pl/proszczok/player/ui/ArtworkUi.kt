package pl.proszczok.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.proszczok.player.data.AudioTrack

private object ArtworkCache {
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    fun get(key: String): Bitmap? = synchronized(bitmaps) { bitmaps.get(key) }
    fun getBest(trackId: Long, requestedSizePx: Int): Bitmap? = synchronized(bitmaps) {
        val bucket = if (requestedSizePx <= 256) 256 else 1200
        bitmaps.get("$trackId:$bucket")
            ?: if (bucket > 256) bitmaps.get("$trackId:256") else null
    }
    fun put(key: String, bitmap: Bitmap) = synchronized(bitmaps) { bitmaps.put(key, bitmap) }
}

@Composable
internal fun TrackArtwork(track: AudioTrack, requestedSizePx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val initialBitmap = remember(track.id, requestedSizePx) {
        ArtworkCache.getBest(track.id, requestedSizePx.coerceIn(96, 1200))
    }
    val bitmap by produceState<Bitmap?>(initialBitmap, track.id, requestedSizePx) {
        value = withContext(Dispatchers.IO) { loadArtwork(context, track, requestedSizePx) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun loadArtwork(context: Context, track: AudioTrack, requestedSizePx: Int): Bitmap? {
    val safeSize = requestedSizePx.coerceIn(96, 1200)
    val cacheKey = "${track.id}:${if (safeSize <= 256) 256 else 1200}"
    ArtworkCache.get(cacheKey)?.let { return it }
    val size = Size(safeSize, safeSize)
    val loaded = runCatching { context.contentResolver.loadThumbnail(track.uri, size, null) }.getOrNull()
        ?: track.artworkUri?.let { artUri ->
            runCatching { context.contentResolver.loadThumbnail(artUri, size, null) }.getOrNull()
                ?: runCatching {
                    context.contentResolver.openInputStream(artUri)?.use(BitmapFactory::decodeStream)
                }.getOrNull()
        }
        ?: runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, track.uri)
                retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    return loaded?.let { bitmap ->
        val scaled = if (bitmap.width > safeSize * 2 || bitmap.height > safeSize * 2) {
            Bitmap.createScaledBitmap(bitmap, safeSize, safeSize, true)
        } else bitmap
        ArtworkCache.put(cacheKey, scaled)
        scaled
    }
}
