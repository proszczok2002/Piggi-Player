package pl.proszczok.player.playback

import android.content.Context
import android.media.audiofx.Equalizer

data class EqualizerBandState(
    val index: Short,
    val centerHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    val level: Short,
)

enum class EqualizerPreset(val label: String, internal val curve: IntArray?) {
    MANUAL("Ręczny", null),
    ROCK("Rock", intArrayOf(500, 250, -150, 250, 500)),
    POP("Pop", intArrayOf(-100, 200, 450, 250, -50)),
    TECHNO("Techno", intArrayOf(500, 250, 0, 350, 550)),
    JAZZ("Jazz", intArrayOf(350, 150, -100, 200, 350)),
    CLASSICAL("Klasyka", intArrayOf(400, 200, -100, 150, 350)),
}

class AudioEqualizerManager(context: Context) {
    private val preferences = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0

    @Synchronized
    fun attach(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) return false
        if (equalizer != null && sessionId == audioSessionId) return true
        release()
        return runCatching {
            equalizer = Equalizer(0, audioSessionId).also { effect ->
                sessionId = audioSessionId
                repeat(effect.numberOfBands.toInt()) { index ->
                    val band = index.toShort()
                    val saved = preferences.getInt("band_$index", 0)
                    val range = effect.bandLevelRange
                    effect.setBandLevel(band, saved.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
                effect.enabled = preferences.getBoolean("enabled", false)
            }
            true
        }.getOrElse {
            equalizer = null
            sessionId = 0
            false
        }
    }

    @Synchronized
    fun isEnabled(): Boolean = equalizer?.enabled == true

    fun isConfiguredEnabled(): Boolean = preferences.getBoolean("enabled", false)

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        preferences.edit().putBoolean("enabled", enabled).apply()
    }

    @Synchronized
    fun bands(): List<EqualizerBandState> {
        val effect = equalizer ?: return emptyList()
        val range = effect.bandLevelRange
        return List(effect.numberOfBands.toInt()) { index ->
            val band = index.toShort()
            EqualizerBandState(
                index = band,
                centerHz = effect.getCenterFreq(band) / 1_000,
                minLevel = range[0],
                maxLevel = range[1],
                level = effect.getBandLevel(band),
            )
        }
    }

    @Synchronized
    fun selectedPreset(): EqualizerPreset = runCatching {
        EqualizerPreset.valueOf(preferences.getString("preset", EqualizerPreset.MANUAL.name).orEmpty())
    }.getOrDefault(EqualizerPreset.MANUAL)

    @Synchronized
    fun setBandLevel(index: Short, level: Short) {
        val effect = equalizer ?: return
        val range = effect.bandLevelRange
        val safeLevel = level.toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
        effect.setBandLevel(index, safeLevel)
        preferences.edit()
            .putInt("band_${index.toInt()}", safeLevel.toInt())
            .putString("preset", EqualizerPreset.MANUAL.name)
            .apply()
    }

    @Synchronized
    fun applyPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.MANUAL) {
            preferences.edit().putString("preset", preset.name).apply()
            return
        }
        val effect = equalizer ?: return
        val curve = requireNotNull(preset.curve)
        val bandCount = effect.numberOfBands.toInt()
        val range = effect.bandLevelRange
        val editor = preferences.edit().putString("preset", preset.name).putBoolean("enabled", true)
        repeat(bandCount) { index ->
            val position = if (bandCount <= 1) 0f else index * curve.lastIndex.toFloat() / (bandCount - 1)
            val lower = position.toInt().coerceIn(curve.indices)
            val upper = (lower + 1).coerceAtMost(curve.lastIndex)
            val fraction = position - lower
            val requested = (curve[lower] + (curve[upper] - curve[lower]) * fraction).toInt()
            val safeLevel = requested.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
            effect.setBandLevel(index.toShort(), safeLevel)
            editor.putInt("band_$index", safeLevel.toInt())
        }
        effect.enabled = true
        editor.apply()
    }

    @Synchronized
    fun reset() {
        val effect = equalizer ?: return
        repeat(effect.numberOfBands.toInt()) { index ->
            effect.setBandLevel(index.toShort(), 0)
        }
        preferences.edit()
            .clear()
            .putBoolean("enabled", effect.enabled)
            .putString("preset", EqualizerPreset.MANUAL.name)
            .apply()
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = 0
    }
}
