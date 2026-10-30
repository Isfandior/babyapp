package app.birthbaby.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Boundaries of the NHS rules, and the messy data two phones produce in real life. */
class LabourRulesEdgeCasesTest {
    private val min = 60_000L
    private val sec = 1_000L
    private val now = 10_000_000_000L

    /** Contractions starting at the given minutes before now, each lasting [lengthSec]. */
    private fun at(vararg minutesAgo: Double, lengthSec: Long = 50): List<Contraction> =
        minutesAgo.mapIndexed { i, m ->
            val start = now - (m * min).toLong()
            Contraction("c$i", start, start + lengthSec * sec)
        }

    private fun advice(list: List<Contraction>, nowMs: Long = now, rule: CallRule = CallRule()) =
        LabourRules.assess(list, nowMs, rule).advice

    // "Every 5 minutes or more often"

    @Test fun exactlyFiveMinutesApartCounts() {
        assertEquals(Advice.CALL_FOR_ADVICE, advice(at(16.0, 11.0, 6.0, 1.0)))
    }

    @Test fun justOverFiveMinutesApartDoesNot() {
        val list = at(16.0, 11.0, 6.0, 1.0).mapIndexed { i, c -> c.copy(startMs = c.startMs - (3 - i) * sec, endMs = c.endMs!! - (3 - i) * sec) }
        assertEquals(Advice.NONE, advice(list))
    }

    @Test fun regularPatternGoesStaleAfterTwiceTheGap() {
        val list = at(25.0, 20.0, 15.0, 10.0)
        assertEquals(Advice.CALL_FOR_ADVICE, advice(list))
        assertEquals(Advice.NONE, advice(list, nowMs = now + 1))
    }

    @Test fun orderOfTheListDoesNotMatter() {
        assertEquals(Advice.CALL_FOR_ADVICE, advice(at(1.0, 11.0, 16.0, 6.0)))
    }

    @Test fun ongoingContractionStillCountsTowardsRegularity() {
        val list = at(16.0, 11.0, 6.0) + Contraction("now", now - 1 * min, null)
        assertEquals(Advice.CALL_FOR_ADVICE, advice(list))
    }

    @Test fun midwifeCanAskForTwoInARow() {
        assertEquals(Advice.CALL_FOR_ADVICE, advice(at(5.0, 1.0), rule = CallRule(maxGapMinutes = 5, regularCount = 2)))
    }

    @Test fun fiveInTenMinutesIsTimeToCallNotCallNow() {
        assertEquals(Advice.CALL_FOR_ADVICE, advice(at(9.0, 7.0, 5.0, 3.0, 1.0, lengthSec = 40)))
    }

    // "Call now" rules

    @Test fun exactlyTwoMinutesIsNotOverTwoMinutes() {
        assertEquals(Advice.NONE, advice(at(5.0, lengthSec = 120)))
    }

    @Test fun twoMinutesAndOneSecondIsCallNow() {
        assertEquals(Advice.CALL_NOW, advice(at(5.0, lengthSec = 121)))
    }

    @Test fun longContractionMoreThanAnHourAgoIsForgotten() {
        assertEquals(Advice.NONE, advice(at(61.0, lengthSec = 130)))
    }

    @Test fun sixInTenMinutesCountsTheFullWindow() {
        assertEquals(Advice.CALL_NOW, advice(at(10.0, 8.0, 6.0, 4.0, 2.0, 0.0, lengthSec = 0)))
    }

    @Test fun sixJustOverTenMinutesIsNotCallNow() {
        val list = at(10.0, 8.0, 6.0, 4.0, 2.0, 0.0, lengthSec = 0).toMutableList()
        list[0] = list[0].copy(startMs = list[0].startMs - 1, endMs = list[0].endMs!! - 1)
        assertEquals(Advice.CALL_FOR_ADVICE, advice(list))
    }

    @Test fun callNowBeatsTimeToCall() {
        val assessment = LabourRules.assess(at(16.0, 11.0, 6.0) + at(3.0, lengthSec = 130), now)
        assertEquals(Advice.CALL_NOW, assessment.advice)
        assertEquals("A contraction lasted longer than 2 minutes", assessment.reason)
    }

    // Stats and wording

    @Test fun statsIgnoreOlderThanAnHour() {
        val stats = LabourRules.stats(at(90.0, 70.0, 10.0, 4.0), now)
        assertEquals(2, stats.countLastHour)
        assertEquals(360, stats.avgGapSec)
    }

    @Test fun onlyAnOngoingContraction() {
        val stats = LabourRules.stats(listOf(Contraction("a", now - 20 * sec, null)), now)
        assertEquals(1, stats.countLastHour)
        assertNull(stats.avgDurationSec)
        assertNull(stats.avgGapSec)
        assertEquals("1 in the last hour.", LabourRules.summary(stats))
    }

    @Test fun emptySummary() {
        assertEquals("No contractions in the last hour.", LabourRules.summary(LabourRules.stats(emptyList(), now)))
    }

    @Test fun secondsAndMinutesWording() {
        assertEquals("45 sec", LabourRules.formatSec(45))
        assertEquals("89 sec", LabourRules.formatSec(89))
        assertEquals("2 min", LabourRules.formatSec(120))
        assertEquals("6 min", LabourRules.formatSec(360))
    }

    // Known bugs. These fail today and show what should happen.

    /** Both phones tap "Contraction started" for the same contraction, a couple of seconds apart. */
    @Test fun knownBug_sameContractionLoggedOnBothPhonesCountsOnce() {
        val real = at(7.0, 4.0, 1.0, lengthSec = 45)
        val partnerCopies = real.map { it.copy(id = it.id + "-p", startMs = it.startMs + 2 * sec, endMs = it.endMs!! + 1 * sec) }
        // Three real contractions three minutes apart: not yet time to call (four are needed).
        assertEquals(Advice.NONE, advice(real))
        assertEquals(Advice.NONE, advice(real + partnerCopies), "3 contractions logged on both phones looked like 6 in 10 minutes")
    }

    /** One phone ended the contraction; the other phone's copy was never ended. */
    @Test fun knownBug_unendedDuplicateDoesNotLookLikeALongContraction() {
        val ended = Contraction("m", now - 3 * min, now - 3 * min + 45 * sec)
        val dangling = Contraction("p", now - 3 * min + 2 * sec, null)
        assertEquals(Advice.NONE, advice(listOf(ended, dangling)), "a forgotten duplicate looked like a 3-minute contraction")
    }

    /** The other phone's clock is a few seconds ahead, so its newest contraction starts "in the future". */
    @Test fun knownBug_smallClockDifferenceDoesNotHideAContraction() {
        val list = at(8.0, 6.5, 5.0, 3.5, 2.0, lengthSec = 40) + Contraction("skewed", now + 3 * sec, null)
        assertEquals(Advice.CALL_NOW, advice(list), "6 in 10 minutes was missed because one phone's clock is 3 seconds ahead")
    }
}
