package pl.proszczok.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {
    suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.VOLUME_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()

        val result = mutableListOf<AudioTrack>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val volumeNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawFolder = cursor.getString(folderColumn).orEmpty()
                val folderPath = rawFolder.replace('\\', '/').trim().trim('/')
                    .ifBlank { "Pamięć urządzenia" }
                val folder = folderPath.substringAfterLast('/').ifBlank { "Pamięć urządzenia" }
                result += AudioTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(displayNameColumn).orEmpty().ifBlank { "audio-$id" },
                    volumeName = cursor.getString(volumeNameColumn).orEmpty(),
                    title = cursor.getString(titleColumn)?.ifBlank { "Nieznany utwór" } ?: "Nieznany utwór",
                    artist = cursor.getString(artistColumn)?.takeUnless { it == "<unknown>" } ?: "Nieznany wykonawca",
                    album = cursor.getString(albumColumn)?.takeUnless { it == "<unknown>" } ?: "Nieznany album",
                    albumId = cursor.getLong(albumIdColumn),
                    durationMs = cursor.getLong(durationColumn),
                    folder = folder,
                    folderPath = folderPath,
                    mimeType = cursor.getString(mimeColumn),
                    trackNumber = cursor.getInt(trackColumn) % 1000,
                    year = cursor.getInt(yearColumn),
                    dateAdded = cursor.getLong(dateColumn),
                )
            }
        }
        result
    }
}
