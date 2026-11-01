package pl.proszczok.player

import android.app.Application
import pl.proszczok.player.data.LibraryDatabase
import pl.proszczok.player.data.MediaStoreRepository
import pl.proszczok.player.playback.AudioEqualizerManager

class PiggiPlayerApplication : Application() {
    val database by lazy { LibraryDatabase(this) }
    val mediaRepository by lazy { MediaStoreRepository(this) }
    val equalizerManager by lazy { AudioEqualizerManager(this) }

    override fun onTerminate() {
        equalizerManager.release()
        database.close()
        super.onTerminate()
    }
}
