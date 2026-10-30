package app.birthbaby.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.birthbaby.alerts.RuleWatcher
import app.birthbaby.core.AlertTiming
import app.birthbaby.core.AlertType
import app.birthbaby.core.CallTarget
import app.birthbaby.core.Contraction
import app.birthbaby.core.LabourRules
import app.birthbaby.core.withNames
import app.birthbaby.data.Alert
import app.birthbaby.data.Family
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace

data class HomeState(
    val family: Family,
    val contractions: List<Contraction>,
    val alerts: List<Alert>,
    val now: Long,
) {
    val settings get() = family.settings
    val ongoing: Contraction? get() = contractions.firstOrNull { it.isOngoing }
    val assessment get() = LabourRules.assess(contractions, now, settings.callRule)
}

private val URGENT_BUTTONS = listOf(AlertType.WATERS, AlertType.BLEEDING, AlertType.MOVEMENTS, AlertType.NEED_YOU)

/** The mother's screen: one big button, what to do now, and the urgent buttons. Nothing else. */
@Composable
fun MotherHome(state: HomeState, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    val fid = state.family.id

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        Trace.critical("permissions_answered", granted.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
    }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) smsLauncher.launch(wanted.toTypedArray())
    }

    // Check the NHS rules whenever a contraction is logged or ends.
    val latestKey = state.contractions.firstOrNull()?.let { it.id + it.endMs }
    LaunchedEffect(latestKey) {
        if (latestKey != null) RuleWatcher.check(context, fid, Role.MOTHER, state.contractions, s, state.alerts)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PartnerQuietWarning(state)
        LatestAlertCard(state)
        AdviceBanner(state.assessment, s, context)

        ContractionButton(state, Role.MOTHER)
        Text(LabourRules.summary(state.assessment.stats), fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        URGENT_BUTTONS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { type ->
                    Button(
                        onClick = { RuleWatcher.raise(context, fid, Role.MOTHER, type, s) },
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == AlertType.NEED_YOU) Green else Red),
                    ) { Text(type.title, fontSize = 18.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }

        BigButton("Call Delivery Suite", Red, { dial(context, s.deliverySuite, "delivery_suite") })
        TextButton(onClick = { dial(context, "999", "999") }, modifier = Modifier.fillMaxWidth()) {
            Text("Baby coming now or strong urge to push? Call 999", color = Red)
        }

        HospitalCard(state)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { RuleWatcher.raise(context, fid, Role.MOTHER, AlertType.TEST, s) }) { Text("Test alarm") }
            TextButton(onClick = onDiagnostics) { Text("Diagnostics") }
        }
    }
}

/** The big start/stop button, with a breathing guide while a contraction is going. */
@Composable
fun ContractionButton(state: HomeState, role: Role) {
    val ongoing = state.ongoing
    val fid = state.family.id
    if (ongoing == null) {
        BigButton("Contraction started", Red, { Repo.startContraction(fid, role) }, height = 160)
    } else {
        val secs = ((state.now - ongoing.startMs) / 1000).toInt()
        val breath = if ((secs / 5) % 2 == 0) "Breathe in…" else "…and out"
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (role == Role.MOTHER) Text(breath, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("${secs}s", fontSize = 24.sp)
        }
        BigButton("Contraction ended", Color.DarkGray, { Repo.endContraction(fid, ongoing.id) }, height = 160)
    }
}

/** After an urgent alert: is the partner on his way, or does she need to ring him? */
@Composable
private fun LatestAlertCard(state: HomeState) {
    val context = LocalContext.current
    val s = state.settings
    val alert = state.alerts.firstOrNull { it.byRole == Role.MOTHER && state.now - it.createdMs < 60 * 60_000L } ?: return
    val action = alert.type.motherAction.withNames(s.motherName, s.partnerName)
    when {
        alert.ackedMs != null -> InfoCard("${s.partnerName} is on the way", "Tapped at ${time(alert.ackedMs)}. $action", Green.copy(alpha = 0.2f))
        AlertTiming.needsCallPrompt(alert.createdMs, alert.ackedMs, state.now) ->
            InfoCard("${s.partnerName} hasn't answered", "Call him now.", Amber) {
                if (s.partnerPhone.isNotBlank()) BigButton("Call ${s.partnerName}", Green, { dial(context, s.partnerPhone, "call_partner") })
                if (s.backupPhone.isNotBlank()) BigButton("Call ${s.backupName.ifBlank { "backup" }}", Green, { dial(context, s.backupPhone, "call_backup") })
            }
        else -> InfoCard(
            "${s.partnerName} alerted",
            "Sent at ${time(alert.createdMs)}${if (alert.ringingMs != null) ", his phone is ringing" else ""}. $action",
            Amber.copy(alpha = 0.4f),
        ) {
            if (alert.type.callTarget == CallTarget.DELIVERY_SUITE && alert.type != AlertType.NEED_YOU && alert.type != AlertType.TEST) {
                BigButton("Call Delivery Suite", Red, { dial(context, s.deliverySuite, "delivery_suite") })
            }
            TextButton(onClick = {
                Repo.ackAlert(state.family.id, alert.id)
                RuleWatcher.sms(context, s.partnerPhone, "Sorry, that alert was sent by mistake. All fine.")
                Trace.critical("alert_cancelled_by_mother", alert.id)
            }) { Text("Sent by mistake") }
        }
    }
}

/** Warns if the partner's phone hasn't checked in for 30 minutes, so alerts may not reach him. */
@Composable
private fun PartnerQuietWarning(state: HomeState) {
    val seen = state.family.partnerSeenMs ?: return
    if (state.now - seen > 30 * 60_000L) {
        InfoCard(
            "${state.settings.partnerName}'s phone is quiet",
            "Last checked in at ${time(seen)}. If you need him, call or text as well.",
            Amber,
        )
    }
}

@Composable
fun HospitalCard(state: HomeState) {
    val s = state.settings
    var open by remember { mutableStateOf(false) }
    InfoCard("${s.hospital} details", if (open) "" else "Tap to show what staff will ask for") {
        if (open) {
            Text("Delivery Suite (24 hours): ${s.deliverySuite} or ${s.deliverySuiteBackup}")
            Text("Maternity Helpline (${s.helplineHours}): ${s.helpline}")
            Text("Community midwives: ${s.communityMidwife}")
            if (s.motherDetails.isNotBlank()) Text(s.motherDetails, modifier = Modifier.padding(top = 8.dp))
        }
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show") }
    }
}
