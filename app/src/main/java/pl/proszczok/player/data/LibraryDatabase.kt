package pl.proszczok.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, "library.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE)")
        db.execSQL("CREATE TABLE playlist_tracks(playlist_id INTEGER NOT NULL, media_id INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, media_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE favorites(media_id INTEGER PRIMARY KEY)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS audiobooks")
        }
    }

    @Synchronized
    fun playlists(): List<Playlist> {
        val result = mutableListOf<Playlist>()
        readableDatabase.rawQuery("SELECT id, name FROM playlists ORDER BY name COLLATE NOCASE", null).use { playlists ->
            while (playlists.moveToNext()) {
                val id = playlists.getLong(0)
                val ids = mutableListOf<Long>()
                readableDatabase.rawQuery(
                    "SELECT media_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position",
                    arrayOf(id.toString()),
                ).use { tracks -> while (tracks.moveToNext()) ids += tracks.getLong(0) }
                result += Playlist(id, playlists.getString(1), ids)
            }
        }
        return result
    }

    @Synchronized
    fun createPlaylist(name: String): Long = writableDatabase.insertOrThrow(
        "playlists", null, ContentValues().apply { put("name", name.trim()) },
    )

    @Synchronized
    fun deletePlaylist(id: Long) {
        writableDatabase.delete("playlists", "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun addToPlaylist(playlistId: Long, mediaId: Long) {
        val next = readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id=?",
            arrayOf(playlistId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        writableDatabase.insertWithOnConflict(
            "playlist_tracks", null,
            ContentValues().apply {
                put("playlist_id", playlistId)
                put("media_id", mediaId)
                put("position", next)
            }, SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    @Synchronized
    fun toggleFavorite(mediaId: Long): Boolean {
        val exists = isFavorite(mediaId)
        if (exists) writableDatabase.delete("favorites", "media_id=?", arrayOf(mediaId.toString()))
        else writableDatabase.insert("favorites", null, ContentValues().apply { put("media_id", mediaId) })
        return !exists
    }

    @Synchronized
    fun isFavorite(mediaId: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM favorites WHERE media_id=? LIMIT 1", arrayOf(mediaId.toString()),
    ).use { it.moveToFirst() }

    @Synchronized
    fun favoriteIds(): Set<Long> {
        val result = mutableSetOf<Long>()
        readableDatabase.rawQuery("SELECT media_id FROM favorites", null).use {
            while (it.moveToNext()) result += it.getLong(0)
        }
        return result
    }

}
