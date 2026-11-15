package pl.proszczok.player

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.proszczok.player.data.asSpokenTime

class SpokenTimeTest {
    @Test fun speaksDurationsWithUnitsInsteadOfClockTimes() {
        assertEquals("0 sekund", (-1000L).asSpokenTime())
        assertEquals("1 sekunda", 1000L.asSpokenTime())
        assertEquals("2 sekundy", 2000L.asSpokenTime())
        assertEquals("12 sekund", 12000L.asSpokenTime())
        assertEquals("21 sekund", 21000L.asSpokenTime())
        assertEquals("22 sekundy", 22000L.asSpokenTime())
        assertEquals("1 minuta 30 sekund", 90000L.asSpokenTime())
        assertEquals("1 godzina 53 minuty 49 sekund", 6829000L.asSpokenTime())
    }
}
