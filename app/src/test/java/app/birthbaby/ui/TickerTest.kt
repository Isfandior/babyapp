package app.birthbaby.ui

import app.birthbaby.core.AlertType
import app.birthbaby.core.Contraction
import app.birthbaby.data.Alert
import app.birthbaby.data.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class TickerTest {
    private val now = 1_798_621_200_000L
    private val min = 60_000L

    private fun alert(createdMs: Long, acked: Long? = null) =
        Alert("a", AlertType.WATERS, createdMs, Role.MOTHER, ringingMs = null, ackedMs = acked)

    @Test fun slowWhenNothingIsLive() {
        val done = listOf(Contraction("c", now - 10 * min, now - 9 * min))
        assertEquals(Ticker.SLOW_MS, Ticker.intervalMs(done, emptyList(), now))
        assertEquals(Ticker.SLOW_MS, Ticker.intervalMs(emptyList(), emptyList(), now))
    }

    @Test fun fastWhileTimingAContraction() {
        val ongoing = listOf(Contraction("c", now - 20_000, null))
        assertEquals(Ticker.FAST_MS, Ticker.intervalMs(ongoing, emptyList(), now))
    }

    @Test fun fastWhileAnAlertWaitsForAnAnswer() {
        assertEquals(Ticker.FAST_MS, Ticker.intervalMs(emptyList(), listOf(alert(now - 30_000)), now))
    }

    @Test fun slowOnceTheAlertIsAnsweredOrOld() {
        assertEquals(Ticker.SLOW_MS, Ticker.intervalMs(emptyList(), listOf(alert(now - min, acked = now)), now))
        assertEquals(Ticker.SLOW_MS, Ticker.intervalMs(emptyList(), listOf(alert(now - 2 * 60 * min)), now))
    }
}
