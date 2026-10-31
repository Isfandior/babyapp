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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Raises an alert. From the mother's phone, the partner is also texted, but only as a backup:
     * if his phone hasn't started ringing within [AlertDecisions.SMS_WAIT_MS].
     */
    fun raise(context: Context, fid: String, role: Role, type: AlertType, settings: FamilySettings, id: String? = null): String {
        val alertId = Repo.raiseAlert(fid, type, role, id)
        if (role == Role.MOTHER && type != AlertType.TEST) {
            val app = context.applicationContext
            scope.launch {
                delay(AlertDecisions.SMS_WAIT_MS)
                val latest = try { Repo.getAlert(fid, alertId) } catch (e: Exception) { Trace.error("sms_check_failed", e); null }
                if (AlertDecisions.needsSmsBackup(type, latest)) {
                    if (sms(app, settings.partnerPhone, "${type.title}: ${type.partnerMessage.withNames(settings.motherName, settings.partnerName)}")) {
                        Repo.markSmsSent(fid, alertId)
                    }
                } else {
                    Trace.critical("sms_not_needed", alertId)
                }
            }
        }
        return alertId
    }

    /** Returns true if the text was handed to the phone to send. */
    fun sms(context: Context, phone: String, text: String): Boolean {
        if (phone.isBlank()) { Trace.critical("sms_skipped", "no partner number in Settings"); return false }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Trace.critical("sms_skipped", "SMS permission not granted")
            return false
        }
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(text), null, null)
            Trace.critical("sms_sent")
            return true
        } catch (e: Exception) {
            Trace.error("sms_failed", e)
            return false
        }
    }
}
