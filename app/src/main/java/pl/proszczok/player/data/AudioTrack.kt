package pl.proszczok.player.data

import android.net.Uri
import android.content.ContentUris
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val volumeName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val folderPath: String,
    val mimeType: String?,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
) {
    val artworkUri: Uri?
        get() = albumId.takeIf { it > 0 }?.let {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it)
        }

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
}

data class FolderGroup(val name: String, val tracks: List<AudioTrack>)

data class Playlist(val id: Long, val name: String, val trackIds: List<Long> = emptyList())

fun Long.asTime(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** Duration rather than a clock time: avoids TalkBack reading 1:30 as 1:30 AM. */
fun Long.asSpokenTime(): String {
    val total = coerceAtLeast(0L) / 1000L
    return buildList {
        if (total >= 3600) add(spokenQuantity(total / 3600, "godzina", "godziny", "godzin"))
        if (total >= 60) add(spokenQuantity(total % 3600 / 60, "minuta", "minuty", "minut"))
        add(spokenQuantity(total % 60, "sekunda", "sekundy", "sekund"))
    }.joinToString(" ")
}

private fun spokenQuantity(value: Long, one: String, few: String, many: String): String {
    val unit = when {
        value == 1L -> one
        value % 10 in 2L..4L && value % 100 !in 12L..14L -> few
        else -> many
    }
    return "$value $unit"
}
