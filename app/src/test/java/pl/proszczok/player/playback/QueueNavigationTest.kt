package pl.proszczok.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueNavigationTest {
    @Test fun wrapsBothEndsWithoutRepeat() {
        assertEquals(0, queueSkipIndex(4, 5, -1, true))
        assertEquals(4, queueSkipIndex(0, 5, -1, false))
    }

    @Test fun respectsShuffleOrder() {
        assertEquals(3, queueSkipIndex(0, 5, 3, true))
        assertEquals(2, queueSkipIndex(4, 5, 2, false))
    }

    @Test fun manualSkipLeavesRepeatedTrack() {
        assertEquals(3, queueSkipIndex(2, 5, 2, true))
        assertEquals(1, queueSkipIndex(2, 5, 2, false))
    }

    @Test fun ignoresUnavailableOrInvalidQueues() {
        assertNull(queueSkipIndex(0, 0, -1, true))
        assertNull(queueSkipIndex(0, 1, 0, false))
        assertNull(queueSkipIndex(-1, 5, 2, true))
        assertNull(queueSkipIndex(5, 5, 2, false))
    }
}
