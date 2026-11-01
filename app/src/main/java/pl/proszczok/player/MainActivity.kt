package pl.proszczok.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pl.proszczok.player.ui.PlayerApp
import pl.proszczok.player.ui.theme.AppThemeMode
import pl.proszczok.player.ui.theme.PiggiPlayerTheme

class MainActivity : FragmentActivity() {
    private var hasAudioPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAudioPermission = audioPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasAudioPermission = audioPermissionGranted()
        if (requiredPermissions().any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestRequiredPermissions()
        }
        setContent {
            val prefs = getSharedPreferences("player", MODE_PRIVATE)
            var themeMode by remember { mutableStateOf(
                runCatching { AppThemeMode.valueOf(prefs.getString("theme", AppThemeMode.SYSTEM.name)!!) }
                    .getOrDefault(AppThemeMode.SYSTEM),
            ) }
            var animationsEnabled by remember {
                mutableStateOf(!prefs.getBoolean("animations_disabled", false))
            }
            PiggiPlayerTheme(themeMode) {
                PlayerApp(
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = ::requestRequiredPermissions,
                    themeMode = themeMode,
                    onThemeMode = {
                        themeMode = it
                        prefs.edit().putString("theme", it.name).apply()
                    },
                    animationsEnabled = animationsEnabled,
                    onAnimationsEnabled = {
                        animationsEnabled = it
                        prefs.edit().putBoolean("animations_disabled", !it).apply()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAudioPermission = audioPermissionGranted()
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(requiredPermissions().toTypedArray())
    }

    private fun audioPermissionGranted(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
