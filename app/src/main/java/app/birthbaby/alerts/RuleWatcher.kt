package app.birthbaby.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import app.birthbaby.core.AlertType
import app.birthbaby.core.Contraction
import app.birthbaby.core.LabourRules
import app.birthbaby.core.withNames
import app.birthbaby.data.Alert
import app.birthbaby.data.FamilySettings
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace

/** Turns the NHS rules into alerts. Whichever phone logged the contraction raises it. */
object RuleWatcher {
    fun check(
        context: Context,
        fid: String,
        role: Role,
        contractions: List<Contraction>,
        settings: FamilySettings,
        alerts: List<Alert>,
    ) {
        val assessment = LabourRules.assess(contractions, System.currentTimeMillis(), settings.callRule)
        val type = AlertDecisions.ruleAlertType(assessment.advice) ?: return
        // One alert of each kind per day, so every new contraction doesn't ring again.
        val id = AlertDecisions.ruleAlertId(type, System.currentTimeMillis())
        if (alerts.any { it.id == id }) return
        Trace.critical("rule_triggered", "${type.name}: ${assessment.reason}")
        raise(context, fid, role, type, settings, id)
    }

    /** Raises an alert and, from the mother's phone, also texts the partner as a backup. */
    fun raise(context: Context, fid: String, role: Role, type: AlertType, settings: FamilySettings, id: String? = null): String {
        val alertId = Repo.raiseAlert(fid, type, role, id)
        if (role == Role.MOTHER) {
            sms(context, settings.partnerPhone, "${type.title}: ${type.partnerMessage.withNames(settings.motherName, settings.partnerName)}")
        }
        return alertId
    }

    fun sms(context: Context, phone: String, text: String) {
        if (phone.isBlank()) { Trace.critical("sms_skipped", "no partner number in Settings"); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Trace.critical("sms_skipped", "SMS permission not granted")
            return
        }
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(text), null, null)
            Trace.critical("sms_sent")
        } catch (e: Exception) {
            Trace.error("sms_failed", e)
        }
    }
}
