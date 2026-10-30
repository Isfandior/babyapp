package app.birthbaby.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.birthbaby.alerts.AlarmPlayer
import app.birthbaby.alerts.Notifier
import app.birthbaby.core.Advice
import app.birthbaby.core.LabourRules
import app.birthbaby.core.withNames
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace
import kotlin.math.roundToInt

/** The partner's screen: alerts first, then what to do now, the timer and log, and the setup that makes alerts ring. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PartnerHome(state: HomeState, onSettings: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    val fid = state.family.id
    val assessment = state.assessment
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.alerts.filter { it.ackedMs == null && it.byRole == Role.MOTHER }.forEach { alert ->
                InfoCard(alert.type.title, alert.type.partnerMessage.withNames(s.motherName, s.partnerName), tones.urgent) {
                    UrgentButton("On my way", {
                        AlarmPlayer.stop()
                        Notifier.cancel(context, alert.id)
                        Repo.ackAlert(fid, alert.id)
                    })
                    if (s.motherPhone.isNotBlank()) {
                        QuietButton("Call ${s.motherName}", { dial(context, s.motherPhone, "call_mother") }, tones.urgent.content)
                    }
                }
            }

            SetupCard()

            AdviceBanner(assessment, s, context)
            if (assessment.advice != Advice.NONE) {
                InfoCard("What to do now", "1. Call ${s.hospital} Delivery Suite and tell them what's happening.\n2. Grab the hospital bag.\n3. Book the taxi.") {
                    BigButton("Book taxi", { bookTaxi(context, s) })
                }
            }

            Text(LabourRules.summary(assessment.stats), style = MaterialTheme.typography.titleLarge)
            Text("Logging for ${s.motherName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ContractionButton(state, Role.PARTNER)

            if (state.contractions.isNotEmpty()) {
                SectionHeading("Recent contractions")
                Text(
                    "Press and hold a line to remove it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.contractions.take(10).forEachIndexed { i, c ->
                val next = state.contractions.getOrNull(i + 1)
                val gap = next?.let { " · ${((c.startMs - it.startMs) / 60_000.0).roundToInt()} min after the last" }.orEmpty()
                val length = c.endMs?.let { "${(it - c.startMs) / 1000}s" } ?: "going"
                Text(
                    "${time(c.startMs)}   $length$gap",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {}, onLongClick = {
                            Repo.deleteContraction(fid, c.id)
                            scope.launch {
                                val undo = snackbar.showSnackbar("Removed ${time(c.startMs)}", actionLabel = "Undo", duration = SnackbarDuration.Long)
                                if (undo == SnackbarResult.ActionPerformed) Repo.restoreContraction(fid, c)
                            }
                        })
                        .padding(vertical = 10.dp),
                )
            }

            QuietButton("Call Delivery Suite", { dial(context, s.deliverySuite, "delivery_suite") }, MaterialTheme.colorScheme.error)
            HospitalCard(state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onSettings) { Text("Settings") }
                TextButton(onClick = onDiagnostics) { Text("Diagnostics") }
            }
            Spacer(Modifier.height(72.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** One setup step: what it is, whether it's done, and the settings screen that fixes it (null = ask for the notification permission). */
private class SetupStep(val label: String, val done: Boolean, val action: Intent?)

/**
 * The one-time setup that lets alerts ring through silent and Do Not Disturb.
 * Shown as a checklist until every step is done. Re-checked every time the screen comes back,
 * and each missing step is traced.
 */
@Composable
private fun SetupCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val steps = remember(refresh) { setupSteps(context) }
    val missing = steps.filterNot { it.done }.map { it.label }
    LaunchedEffect(missing) { if (missing.isNotEmpty()) Trace.critical("setup_missing", missing.joinToString()) }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    if (missing.isEmpty()) return
    InfoCard(
        "Finish setup so alerts ring on silent",
        "${steps.count { it.done }} of ${steps.size} done. Tap each step that isn't ticked.",
        tones.warning,
    ) {
        steps.forEach { step ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !step.done) {
                        if (step.action == null) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(step.action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (step.done) "✓" else "○", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (step.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun setupSteps(context: Context): List<SetupStep> = buildList {
    val nm = context.getSystemService(NotificationManager::class.java)
    val pkg = Uri.parse("package:${context.packageName}")
    if (Build.VERSION.SDK_INT >= 33) {
        add(SetupStep(
            "Allow notifications",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            null,
        ))
    }
    if (Build.VERSION.SDK_INT >= 34) {
        add(SetupStep("Allow full-screen alerts", nm.canUseFullScreenIntent(), Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)))
    }
    add(SetupStep("Allow Do Not Disturb access", nm.isNotificationPolicyAccessGranted, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
    add(SetupStep(
        "Let urgent alerts override Do Not Disturb (switch on \"Override\" or \"Ignore Do Not Disturb\")",
        nm.getNotificationChannel(Notifier.URGENT)?.canBypassDnd() != false,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.URGENT),
    ))
    val pm = context.getSystemService(PowerManager::class.java)
    @Suppress("BatteryLife")
    add(SetupStep(
        "Set battery to Unrestricted so Samsung never puts the app to sleep",
        pm.isIgnoringBatteryOptimizations(context.packageName),
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
    ))
    val audio = context.getSystemService(AudioManager::class.java)
    add(SetupStep("Turn the alarm volume up", audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0, Intent(Settings.ACTION_SOUND_SETTINGS)))
}
