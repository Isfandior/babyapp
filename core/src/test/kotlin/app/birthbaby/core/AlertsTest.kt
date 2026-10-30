package app.birthbaby.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertsTest {
    private val sec = 1_000L
    private val created = 10_000_000_000L

    @Test fun everyAlertHasWordsForBothPhones() {
        AlertType.entries.forEach {
            assertTrue(it.title.isNotBlank(), "${it.name} title")
            assertTrue(it.partnerMessage.isNotBlank(), "${it.name} partner message")
            assertTrue(it.motherAction.isNotBlank(), "${it.name} mother action")
        }
    }

    @Test fun namesAreFilledInEverywhere() {
        AlertType.entries.forEach {
            val texts = listOf(it.partnerMessage, it.motherAction).map { t -> t.withNames("Mum", "Partner") }
            texts.forEach { t -> assertFalse('{' in t || '}' in t, "${it.name} left a placeholder: $t") }
        }
    }

    @Test fun onlyBabyComingSaysCall999() {
        assertEquals(CallTarget.EMERGENCY_999, AlertType.BABY_COMING.callTarget)
        AlertType.entries.filter { it != AlertType.BABY_COMING }.forEach {
            assertEquals(CallTarget.DELIVERY_SUITE, it.callTarget, it.name)
        }
    }

    @Test fun medicalAlertsTellTheMotherToCallNow() {
        listOf(AlertType.WATERS, AlertType.BLEEDING, AlertType.MOVEMENTS, AlertType.CALL_NOW).forEach {
            assertTrue("now" in it.motherAction.lowercase(), "${it.name}: ${it.motherAction}")
            assertTrue("Delivery Suite" in it.motherAction, "${it.name}: ${it.motherAction}")
        }
    }

    @Test fun reducedMovementsSaysDontWait() {
        assertTrue("don't wait" in AlertType.MOVEMENTS.motherAction.lowercase())
    }

    @Test fun ackTimeoutBoundary() {
        assertFalse(AlertTiming.needsCallPrompt(created, null, created + 2 * 60 * sec - 1))
        assertTrue(AlertTiming.needsCallPrompt(created, null, created + 2 * 60 * sec))
    }

    @Test fun lateAcknowledgementClearsThePrompt() {
        assertFalse(AlertTiming.needsCallPrompt(created, created + 10 * 60 * sec, created + 11 * 60 * sec))
    }
}
