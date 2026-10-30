package app.birthbaby.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabourRulesTest {
    private val min = 60_000L
    private val sec = 1_000L
    private val now = 10_000_000_000L

    /** Contractions starting every [gapMin] minutes, each lasting [lengthSec], the last one ending just before [end]. */
    private fun series(count: Int, gapMin: Double, lengthSec: Long, end: Long = now): List<Contraction> {
        val gapMs = (gapMin * min).toLong()
        val lastStart = end - lengthSec * sec - 10 * sec
        return (0 until count).map { i ->
            val start = lastStart - (count - 1 - i) * gapMs
            Contraction("c$i", start, start + lengthSec * sec)
        }
    }

    @Test fun noContractionsMeansNoAdvice() {
        assertEquals(Advice.NONE, LabourRules.assess(emptyList(), now).advice)
    }

    @Test fun earlyIrregularLabourIsNotYetTimeToCall() {
        val list = series(4, gapMin = 12.0, lengthSec = 40)
        assertEquals(Advice.NONE, LabourRules.assess(list, now).advice)
    }

    @Test fun regularEveryFiveMinutesMeansCallForAdvice() {
        val list = series(4, gapMin = 5.0, lengthSec = 50)
        assertEquals(Advice.CALL_FOR_ADVICE, LabourRules.assess(list, now).advice)
    }

    @Test fun threeRegularIsNotEnough() {
        val list = series(3, gapMin = 4.0, lengthSec = 50)
        assertEquals(Advice.NONE, LabourRules.assess(list, now).advice)
    }

    @Test fun oneLongGapBreaksRegularity() {
        val list = series(4, gapMin = 5.0, lengthSec = 50).toMutableList()
        list[0] = list[0].copy(startMs = list[0].startMs - 10 * min, endMs = list[0].endMs!! - 10 * min)
        assertEquals(Advice.NONE, LabourRules.assess(list, now).advice)
    }

    @Test fun staleRegularPatternDoesNotTrigger() {
        val list = series(4, gapMin = 5.0, lengthSec = 50, end = now - 30 * min)
        assertEquals(Advice.NONE, LabourRules.assess(list, now).advice)
    }

    @Test fun midwifeRuleIsRespected() {
        val list = series(4, gapMin = 7.0, lengthSec = 50)
        assertEquals(Advice.NONE, LabourRules.assess(list, now, CallRule(maxGapMinutes = 5)).advice)
        assertEquals(Advice.CALL_FOR_ADVICE, LabourRules.assess(list, now, CallRule(maxGapMinutes = 8)).advice)
    }

    @Test fun contractionOverTwoMinutesMeansCallNow() {
        val list = listOf(Contraction("a", now - 3 * min, now - 3 * min + 130 * sec))
        assertEquals(Advice.CALL_NOW, LabourRules.assess(list, now).advice)
    }

    @Test fun ongoingContractionOverTwoMinutesMeansCallNow() {
        val list = listOf(Contraction("a", now - 125 * sec, null))
        assertEquals(Advice.CALL_NOW, LabourRules.assess(list, now).advice)
    }

    @Test fun sixInTenMinutesMeansCallNow() {
        val list = series(6, gapMin = 1.5, lengthSec = 40)
        assertEquals(Advice.CALL_NOW, LabourRules.assess(list, now).advice)
    }

    @Test fun callNowRulesCannotBeLoosenedByTheEditableRule() {
        val list = series(6, gapMin = 1.5, lengthSec = 40)
        val loose = CallRule(maxGapMinutes = 1, regularCount = 20)
        assertEquals(Advice.CALL_NOW, LabourRules.assess(list, now, loose).advice)
    }

    @Test fun statsAndSummary() {
        val stats = LabourRules.stats(series(4, gapMin = 6.0, lengthSec = 50), now)
        assertEquals(4, stats.countLastHour)
        assertEquals(50, stats.avgDurationSec)
        assertEquals(360, stats.avgGapSec)
        assertEquals("4 in the last hour, about every 6 min, lasting about 50 sec.", LabourRules.summary(stats))
    }

    @Test fun ackTimeout() {
        assertFalse(AlertTiming.needsCallPrompt(now, null, now + 60 * sec))
        assertTrue(AlertTiming.needsCallPrompt(now, null, now + 2 * min))
        assertFalse(AlertTiming.needsCallPrompt(now, now + 30 * sec, now + 5 * min))
    }

    @Test fun namesAreFilledIn() {
        assertEquals(
            "Kaitlin needs you. Call her or come home now.",
            AlertType.NEED_YOU.partnerMessage.withNames("Kaitlin", "Sohrab"),
        )
    }
}
