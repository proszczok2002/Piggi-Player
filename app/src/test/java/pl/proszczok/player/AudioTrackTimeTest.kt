package pl.proszczok.player

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.proszczok.player.data.asTime

class AudioTrackTimeTest {
    @Test
    fun formatsShortAndLongDurationsForAccessibleLabels() {
        assertEquals("0:00", 0L.asTime())
        assertEquals("3:05", 185_000L.asTime())
        assertEquals("1:02:03", 3_723_000L.asTime())
    }

    @Test
    fun clampsNegativeDurations() {
        assertEquals("0:00", (-1_000L).asTime())
    }
}
