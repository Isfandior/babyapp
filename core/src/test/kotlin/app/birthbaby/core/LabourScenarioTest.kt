package app.birthbaby.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whole labours played through the rules, checking advice each time a contraction ends,
 * which is when the app checks.
 */
class LabourScenarioTest {
    private val min = 60_000L
    private val sec = 1_000L

    /** Plays contractions with the given gaps (minutes) and lengths; returns the advice after each one ends. */
    private fun play(gapsMin: List<Double>, lengthSec: (Int) -> Long, rule: CallRule = CallRule()): List<Advice> {
        val logged = mutableListOf<Contraction>()
        var start = 1_000_000_000L
        return gapsMin.mapIndexed { i, gap ->
            start += (gap * min).toLong()
            val end = start + lengthSec(i) * sec
            logged += Contraction("c$i", start, end)
            LabourRules.assess(logged, end, rule).advice
        }
    }

    @Test fun typicalFirstLabourCallsAtTheRightContraction() {
        val gaps = List(6) { 20.0 } + List(4) { 10.0 } + listOf(7.0, 7.0, 5.0, 5.0, 5.0, 4.0)
        val advice = play(gaps, lengthSec = { 30L + it * 2 })
        val firstCall = advice.indexOfFirst { it == Advice.CALL_FOR_ADVICE }
        // The 15th contraction completes the 4th in a row at 5 minutes apart.
        assertEquals(14, firstCall)
        assertTrue(advice.take(firstCall).all { it == Advice.NONE })
        assertTrue(Advice.CALL_NOW !in advice)
    }

    @Test fun midwifeRuleOfThreeMinutesWaitsLonger() {
        val gaps = List(5) { 5.0 } + List(4) { 3.0 }
        val advice = play(gaps, lengthSec = { 50 }, rule = CallRule(maxGapMinutes = 3, regularCount = 4))
        assertEquals(7, advice.indexOfFirst { it == Advice.CALL_FOR_ADVICE })
    }

    @Test fun fastLabourGoesStraightToCallNow() {
        val advice = play(List(8) { 1.5 }, lengthSec = { 45 })
        assertEquals(Advice.CALL_NOW, advice.last())
        assertEquals(5, advice.indexOfFirst { it == Advice.CALL_NOW })
    }

    @Test fun oneVeryLongContractionInEarlyLabour() {
        val advice = play(listOf(20.0, 20.0, 20.0), lengthSec = { if (it == 2) 150 else 40 })
        assertEquals(listOf(Advice.NONE, Advice.NONE, Advice.CALL_NOW), advice)
    }
}
