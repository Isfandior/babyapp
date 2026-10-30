package app.birthbaby.alerts

import app.birthbaby.core.Advice
import app.birthbaby.core.AlertType
import app.birthbaby.data.Alert
import app.birthbaby.data.Role
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertDecisionsTest {
    private val london = TimeZone.getTimeZone("Europe/London")
    private val hour = 60 * 60_000L

    private fun alert(id: String, type: AlertType = AlertType.WATERS, by: Role = Role.MOTHER, acked: Long? = null) =
        Alert(id, type, createdMs = 1, byRole = by, ringingMs = null, ackedMs = acked)

    @Test fun rulesMapToAlerts() {
        assertEquals(AlertType.CALL_NOW, AlertDecisions.ruleAlertType(Advice.CALL_NOW))
        assertEquals(AlertType.TIME_TO_CALL, AlertDecisions.ruleAlertType(Advice.CALL_FOR_ADVICE))
        assertNull(AlertDecisions.ruleAlertType(Advice.NONE))
    }

    @Test fun ruleAlertsRingOncePerDay() {
        val morning = 1_798_621_200_000L // 30 Dec 2026, 09:00 London
        val id = AlertDecisions.ruleAlertId(AlertType.TIME_TO_CALL, morning, london)
        assertEquals("TIME_TO_CALL-20261230", id)
        assertEquals(id, AlertDecisions.ruleAlertId(AlertType.TIME_TO_CALL, morning + 14 * hour, london))
        assertNotEquals(id, AlertDecisions.ruleAlertId(AlertType.TIME_TO_CALL, morning + 15 * hour + 1, london))
        assertNotEquals(id, AlertDecisions.ruleAlertId(AlertType.CALL_NOW, morning, london))
    }

    @Test fun newAlertFromMotherRings() {
        assertEquals(listOf("a"), AlertDecisions.toRing(listOf(alert("a")), rung = emptySet()).map { it.id })
    }

    @Test fun acknowledgedAlertDoesNotRing() {
        assertTrue(AlertDecisions.toRing(listOf(alert("a", acked = 5)), rung = emptySet()).isEmpty())
    }

    @Test fun alreadyRungAlertDoesNotRingAgainAfterRestart() {
        assertTrue(AlertDecisions.toRing(listOf(alert("a")), rung = setOf("a")).isEmpty())
    }

    @Test fun everyUrgentButtonRings() {
        val buttons = listOf(AlertType.WATERS, AlertType.BLEEDING, AlertType.MOVEMENTS, AlertType.NEED_YOU, AlertType.TEST)
        val rung = AlertDecisions.toRing(buttons.map { alert(it.name, it) }, rung = emptySet())
        assertEquals(buttons, rung.map { it.type })
    }

    @Test fun alarmKeepsGoingUntilEveryRungAlertIsAcknowledged() {
        val rung = setOf("a", "b")
        assertTrue(AlertDecisions.stillRinging(listOf(alert("a", acked = 5), alert("b")), rung))
        assertFalse(AlertDecisions.stillRinging(listOf(alert("a", acked = 5), alert("b", acked = 6)), rung))
    }

    @Test fun motherCancellingSentByMistakeStopsTheAlarm() {
        // "Sent by mistake" acknowledges the alert from the mother's phone.
        assertFalse(AlertDecisions.stillRinging(listOf(alert("a", acked = 5)), setOf("a")))
    }

    /**
     * Known bug. Both phones run the contraction rules, and both write the same "Time to call"
     * alert. If the partner's phone writes last, the alert is marked as raised by the partner,
     * and the partner's phone never rings for it. Expected: a rule alert rings the partner
     * whichever phone raised it.
     */
    @Test fun knownBug_ruleAlertRingsThePartnerWhicheverPhoneRaisedIt() {
        val raisedByPartnersPhone = alert("TIME_TO_CALL-20261230", AlertType.TIME_TO_CALL, by = Role.PARTNER)
        assertEquals(1, AlertDecisions.toRing(listOf(raisedByPartnersPhone), rung = emptySet()).size)
    }
}
