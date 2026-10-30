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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** The partner's screen: what's happening, what to do now, and the setup that makes alerts ring. */
@Composable
fun PartnerHome(state: HomeState, onSettings: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    val fid = state.family.id
    val assessment = state.assessment

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupCard()

        state.alerts.filter { it.ackedMs == null && it.byRole == Role.MOTHER }.forEach { alert ->
            InfoCard(alert.type.title, alert.type.partnerMessage.withNames(s.motherName, s.partnerName), Red.copy(alpha = 0.25f)) {
                BigButton("On my way", Green, {
                    AlarmPlayer.stop()
                    Notifier.cancel(context, alert.id)
                    Repo.ackAlert(fid, alert.id)
                })
                if (s.motherPhone.isNotBlank()) {
                    TextButton(onClick = { dial(context, s.motherPhone, "call_mother") }) { Text("Call ${s.motherName}") }
                }
            }
        }

        Text(LabourRules.summary(assessment.stats), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        AdviceBanner(assessment, s, context)

        if (assessment.advice != Advice.NONE) {
            InfoCard("What to do now", "1. Call ${s.hospital} Delivery Suite and tell them what's happening.\n2. Grab the hospital bag.\n3. Book the taxi.") {
                BigButton("Book taxi", Green, { bookTaxi(context, s) })
            }
        }

        Text("Logging for ${s.motherName}", fontSize = 14.sp)
        ContractionButton(state, Role.PARTNER)

        state.contractions.take(10).forEachIndexed { i, c ->
            val next = state.contractions.getOrNull(i + 1)
            val gap = next?.let { " · ${((c.startMs - it.startMs) / 60_000.0).roundToInt()} min after the last" }.orEmpty()
            val length = c.endMs?.let { "${(it - c.startMs) / 1000}s" } ?: "going"
            Row(Modifier.fillMaxWidth()) {
                Text("${time(c.startMs)}  $length$gap", modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = { Repo.deleteContraction(fid, c.id) }) { Text("Remove") }
            }
        }

        BigButton("Call Delivery Suite", Red, { dial(context, s.deliverySuite, "delivery_suite") })
        HospitalCard(state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onSettings) { Text("Settings") }
            TextButton(onClick = onDiagnostics) { Text("Diagnostics") }
        }
        Text("Pairing code: ${state.family.code}", fontSize = 12.sp)
    }
}

/**
 * The one-time setup that lets alerts ring through silent and Do Not Disturb.
 * Re-checked every time the screen comes back, and each missing step is traced.
 */
@Composable
private fun SetupCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val missing = remember(refresh) { missingSetup(context) }
    LaunchedEffect(missing) { if (missing.isNotEmpty()) Trace.critical("setup_missing", missing.joinToString { it.first }) }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    if (missing.isEmpty()) return
    InfoCard("Finish setup so alerts ring on silent", "Tap each step. Samsung: also add Birth & Baby to Settings > Battery > Background usage limits > Never sleeping apps.", Amber) {
        missing.forEach { (label, action) ->
            TextButton(onClick = {
                if (action == null) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("• $label") }
        }
    }
}

/** Each missing step with the settings screen that fixes it (null = ask for the notification permission). */
private fun missingSetup(context: Context): List<Pair<String, Intent?>> = buildList {
    val nm = context.getSystemService(NotificationManager::class.java)
    val pkg = Uri.parse("package:${context.packageName}")
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) add("Allow notifications" to null)
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
        add("Allow full-screen alerts" to Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
    }
    if (!nm.isNotificationPolicyAccessGranted) {
        add("Allow Do Not Disturb access" to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
    if (nm.getNotificationChannel(Notifier.URGENT)?.canBypassDnd() == false) {
        add("Let urgent alerts override Do Not Disturb" to
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.URGENT))
    }
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        @Suppress("BatteryLife")
        add("Stop battery saving from pausing the app" to Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
    }
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
        add("Turn the alarm volume up" to Intent(Settings.ACTION_SOUND_SETTINGS))
    }
}
