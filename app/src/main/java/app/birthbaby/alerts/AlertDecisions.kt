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
        alerts.filter { !it.resolved && ringsPartner(it) && it.id !in rung }

    /** False once every alert this phone rang has been answered or cancelled, so the alarm can stop. */
    fun stillRinging(alerts: List<Alert>, rung: Set<String>): Boolean =
        alerts.any { !it.resolved && it.id in rung }

    /** How long the mother's phone waits for the partner's phone to start ringing before texting. */
    const val SMS_WAIT_MS = 20_000L

    /** A second tap of the same button within this time is ignored. */
    const val REPEAT_TAP_MS = 2 * 60_000L

    /**
     * The text is only a backup: send it when, after [SMS_WAIT_MS], the partner's phone still
     * hasn't started ringing and nobody has answered or cancelled. Never for a test.
     * A null alert (couldn't be read at all) is treated as not reached, so the text goes.
     */
    fun needsSmsBackup(type: AlertType, alert: Alert?): Boolean =
        type != AlertType.TEST && (alert == null || (alert.ringingMs == null && !alert.resolved))

    /** The same button's alert from the last [REPEAT_TAP_MS], if there is one. */
    fun recentSame(alerts: List<Alert>, type: AlertType, nowMs: Long): Alert? =
        alerts.firstOrNull {
            it.type == type && it.byRole == Role.MOTHER && it.cancelledMs == null && nowMs - it.createdMs < REPEAT_TAP_MS
        }
}
