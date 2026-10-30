package app.birthbaby.alerts

import app.birthbaby.core.Advice
import app.birthbaby.core.AlertType
import app.birthbaby.data.Alert
import app.birthbaby.data.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The alert decisions, kept free of Android so they can be unit tested. */
object AlertDecisions {
    fun ruleAlertType(advice: Advice): AlertType? = when (advice) {
        Advice.CALL_NOW -> AlertType.CALL_NOW
        Advice.CALL_FOR_ADVICE -> AlertType.TIME_TO_CALL
        Advice.NONE -> null
    }

    /** One alert of each kind per day, so every new contraction doesn't ring again. */
    fun ruleAlertId(type: AlertType, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.UK).apply { timeZone = zone }.format(Date(nowMs))
        return "${type.name}-$day"
    }

    /**
     * Alerts that ring the partner: anything from the mother's phone, and the contraction
     * rule alerts whichever phone raised them (both phones check the rules).
     */
    fun ringsPartner(alert: Alert): Boolean =
        alert.byRole == Role.MOTHER || alert.type == AlertType.TIME_TO_CALL || alert.type == AlertType.CALL_NOW

    /** Alerts the partner's phone should start ringing for now. */
    fun toRing(alerts: List<Alert>, rung: Set<String>): List<Alert> =
        alerts.filter { it.ackedMs == null && ringsPartner(it) && it.id !in rung }

    /** False once every alert this phone rang has been acknowledged, so the alarm can stop. */
    fun stillRinging(alerts: List<Alert>, rung: Set<String>): Boolean =
        alerts.any { it.ackedMs == null && it.id in rung }
}
