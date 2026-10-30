package app.birthbaby.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.birthbaby.alerts.RuleWatcher
import app.birthbaby.core.Advice
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
    val ongoing: Contraction? get() = LabourRules.merged(contractions).lastOrNull { it.isOngoing }
    val assessment get() = LabourRules.assess(contractions, now, settings.callRule)

    /** The mother's most recent urgent alert, while it's still worth showing (one hour). */
    val latestMotherAlert: Alert?
        get() = alerts.firstOrNull { it.byRole == Role.MOTHER && now - it.createdMs < 60 * 60_000L }
}

private val URGENT_BUTTONS = listOf(AlertType.WATERS, AlertType.BLEEDING, AlertType.MOVEMENTS, AlertType.NEED_YOU)

/**
 * The mother's screen, top to bottom: one calm status line, the contraction button,
 * then, set apart so they can't be hit mid-contraction, the "Something wrong?" buttons.
 */
@Composable
fun MotherHome(state: HomeState, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    val fid = state.family.id
    var showMore by remember { mutableStateOf(false) }

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PartnerStatus(state)
        LatestAlertCard(state)
        AdviceBanner(state.assessment, s, context)
        if (state.assessment.advice == Advice.NONE && state.latestMotherAlert == null) CalmLine(state)

        ContractionButton(state, Role.MOTHER)

        SectionHeading("Something wrong?", Modifier.padding(top = 16.dp))
        URGENT_BUTTONS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { type ->
                    val urgent = type != AlertType.NEED_YOU
                    Button(
                        onClick = { RuleWatcher.raise(context, fid, Role.MOTHER, type, s) },
                        modifier = Modifier.weight(1f).height(72.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            contentColor = if (urgent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) { Text(type.title, style = MaterialTheme.typography.labelLarge, fontSize = 18.sp, textAlign = TextAlign.Center) }
                }
            }
        }
        UrgentButton("Baby coming or urge to push?\nCall 999", { dial(context, "999", "999") }, height = 80)
        QuietButton("Call Delivery Suite", { dial(context, s.deliverySuite, "delivery_suite") }, MaterialTheme.colorScheme.error)

        HospitalCard(state)

        TextButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showMore) "Less" else "More")
        }
        if (showMore) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { RuleWatcher.raise(context, fid, Role.MOTHER, AlertType.TEST, s) }) { Text("Test alarm") }
                TextButton(onClick = onDiagnostics) { Text("Diagnostics") }
            }
        }
    }
}

/** A quiet reassurance that the partner's phone is listening; turns amber if it goes quiet. */
@Composable
private fun PartnerStatus(state: HomeState) {
    val s = state.settings
    val seen = state.family.partnerSeenMs
    when {
        seen == null -> StatusLine("${s.partnerName}'s phone hasn't connected yet", MaterialTheme.colorScheme.outline)
        state.now - seen > 30 * 60_000L -> InfoCard(
            "${s.partnerName}'s phone is quiet",
            "Last checked in at ${time(seen)}. If you need him, call or text as well.",
            tones.warning,
        )
        else -> StatusLine("Connected to ${s.partnerName}'s phone", MaterialTheme.colorScheme.primary, tick = true)
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color, tick: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(if (tick) "$text ✓" else text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** When nothing needs doing: one short line instead of numbers. Full stats stay on the partner's phone. */
@Composable
private fun CalmLine(state: HomeState) {
    val text = when {
        state.ongoing != null -> "You're doing well."
        state.assessment.stats.countLastHour == 0 -> "When a contraction starts, tap the button."
        else -> "All calm. You'll be told when it's time to call."
    }
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/** The big start/stop button, with a slow breathing circle while a contraction is going. */
@Composable
fun ContractionButton(state: HomeState, role: Role) {
    val ongoing = state.ongoing
    val fid = state.family.id
    if (ongoing == null) {
        BigButton("Contraction started", { Repo.startContraction(fid, role) }, height = 160)
        return
    }
    val secs = ((state.now - ongoing.startMs) / 1000).toInt()
    val breath = rememberInfiniteTransition(label = "breath")
    // 5 seconds in, 5 seconds out.
    val scale by breath.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val pulse by breath.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).scale(scale).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$secs", fontSize = 56.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("seconds", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (role == Role.MOTHER) {
            Text(
                if ((secs / 5) % 2 == 0) "Breathe in…" else "…and out",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    BigButton("Tap when it ends", { Repo.endContraction(fid, ongoing.id) }, height = 120, modifier = Modifier.alpha(pulse))
}

/** After an urgent alert: is the partner on his way, or does she need to ring him? */
@Composable
private fun LatestAlertCard(state: HomeState) {
    val context = LocalContext.current
    val s = state.settings
    val alert = state.latestMotherAlert ?: return
    val action = alert.type.motherAction.withNames(s.motherName, s.partnerName)
    when {
        alert.ackedMs != null -> InfoCard("${s.partnerName} is on the way", "Tapped at ${time(alert.ackedMs)}. $action", tones.ok)
        AlertTiming.needsCallPrompt(alert.createdMs, alert.ackedMs, state.now) ->
            InfoCard("${s.partnerName} hasn't answered", "Call him now.", tones.warning) {
                if (s.partnerPhone.isNotBlank()) BigButton("Call ${s.partnerName}", { dial(context, s.partnerPhone, "call_partner") })
                if (s.backupPhone.isNotBlank()) BigButton("Call ${s.backupName.ifBlank { "backup" }}", { dial(context, s.backupPhone, "call_backup") })
            }
        else -> InfoCard(
            "${s.partnerName} alerted",
            "Sent at ${time(alert.createdMs)}${if (alert.ringingMs != null) ", his phone is ringing" else ""}. $action",
            tones.warning,
        ) {
            if (alert.type.callTarget == CallTarget.DELIVERY_SUITE && alert.type != AlertType.NEED_YOU && alert.type != AlertType.TEST) {
                UrgentButton("Call Delivery Suite", { dial(context, s.deliverySuite, "delivery_suite") })
            }
            QuietButton("Sent by mistake", {
                Repo.ackAlert(state.family.id, alert.id)
                RuleWatcher.sms(context, s.partnerPhone, "Sorry, that alert was sent by mistake. All fine.")
                Trace.critical("alert_cancelled_by_mother", alert.id)
            }, tones.warning.content)
        }
    }
}

/** Tap anywhere to open. Every number inside rings straight away. */
@Composable
fun HospitalCard(state: HomeState) {
    val context = LocalContext.current
    val s = state.settings
    var open by remember { mutableStateOf(false) }
    InfoCard(
        "${s.hospital} details",
        if (open) "" else "Tap to show numbers and what staff will ask for",
        modifier = Modifier.clickable { open = !open },
    ) {
        if (open) {
            PhoneRow("Delivery Suite (24 hours)", s.deliverySuite) { dial(context, it, "delivery_suite") }
            PhoneRow("Delivery Suite backup", s.deliverySuiteBackup) { dial(context, it, "delivery_suite_backup") }
            PhoneRow("Maternity Helpline (${s.helplineHours})", s.helpline) { dial(context, it, "helpline") }
            PhoneRow("Community midwives", s.communityMidwife) { dial(context, it, "community_midwife") }
            if (s.motherDetails.isNotBlank()) Text(s.motherDetails, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PhoneRow(label: String, number: String, call: (String) -> Unit) {
    if (number.isBlank()) return
    Row(
        Modifier.fillMaxWidth().clickable { call(number) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
