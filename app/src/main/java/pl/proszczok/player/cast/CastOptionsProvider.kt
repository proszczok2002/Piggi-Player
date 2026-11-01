package pl.proszczok.player.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        // Media3 publikuje jedyną sesję i jedyne powiadomienie również podczas Cast.
        .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).build())
        .setLaunchOptions(LaunchOptions.Builder().setRelaunchIfRunning(true).build())
        .setShowSystemOutputSwitcherOnCastIconClick(true)
        .setRemoteToLocalEnabled(true)
        .setStopReceiverApplicationWhenEndingSession(true)
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
