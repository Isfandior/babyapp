package app.birthbaby.alerts

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.birthbaby.core.LabourRules
import app.birthbaby.core.withNames
import app.birthbaby.data.Alert
import app.birthbaby.data.FamilySettings
import app.birthbaby.data.Prefs
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs on the partner's phone all the time. It listens to Firestore directly, so alerts
 * arrive within a second or two over any network without needing a server, and it
 * checks in every 5 minutes so the mother's phone can warn if the partner's phone goes quiet.
 */
class PartnerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var settings = FamilySettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        val fid = prefs.familyId
        if (prefs.role != Role.PARTNER || fid == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            Notifier.LISTENING_ID,
            Notifier.listening(this, "You'll be alerted straight away, even on silent."),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!started) {
            started = true
            Trace.critical("listener_started")
            listen(fid, prefs)
        }
        return START_STICKY
    }

    private fun listen(fid: String, prefs: Prefs) {
        val since = System.currentTimeMillis() - 30 * 60_000L
        scope.launch { Repo.familyFlow(fid).collectLatest { f -> f?.let { settings = it.settings } } }
        scope.launch { Repo.alertsFlow(fid, since).collectLatest { onAlerts(fid, prefs, it) } }
        scope.launch {
            var lastSeenId: String? = null
            Repo.contractionsFlow(fid).collectLatest { list ->
                val latest = list.maxByOrNull { it.startMs } ?: return@collectLatest
                if (latest.id != lastSeenId && lastSeenId != null) {
                    Notifier.update(this@PartnerService, LabourRules.summary(LabourRules.stats(list, System.currentTimeMillis())))
                }
                lastSeenId = latest.id
                RuleWatcher.check(this@PartnerService, fid, Role.PARTNER, list, settings, alerts = lastAlerts)
            }
        }
        scope.launch {
            while (true) {
                Repo.heartbeat(fid)
                delay(5 * 60_000L)
            }
        }
    }

    private var lastAlerts: List<Alert> = emptyList()

    private fun onAlerts(fid: String, prefs: Prefs, alerts: List<Alert>) {
        lastAlerts = alerts
        val rung = prefs.rung()
        alerts.filter { it.ackedMs == null && it.byRole == Role.MOTHER && it.id !in rung }.forEach { alert ->
            prefs.markRung(alert.id)
            Trace.critical("alert_received", "${alert.type.name} ${alert.id} after ${System.currentTimeMillis() - alert.createdMs}ms")
            Repo.markRinging(fid, alert.id)
            val message = alert.type.partnerMessage.withNames(settings.motherName, settings.partnerName)
            Notifier.urgent(this, alert.id, alert.type.title, message)
            AlarmPlayer.start(this)
        }
        val stillRinging = alerts.any { it.ackedMs == null && it.id in prefs.rung() }
        if (!stillRinging && AlarmPlayer.isPlaying) {
            AlarmPlayer.stop()
            alerts.forEach { Notifier.cancel(this, it.id) }
        }
    }

    override fun onDestroy() {
        Trace.critical("listener_stopped")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PartnerService::class.java))
        }
    }
}

/** Restarts the partner listener after the phone reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Trace.critical("boot_restart", intent.action.orEmpty())
        if (Prefs(context).role == Role.PARTNER) PartnerService.start(context)
    }
}
