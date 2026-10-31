package app.birthbaby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import app.birthbaby.data.FamilySettings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.birthbaby.alerts.PartnerService
import app.birthbaby.data.Prefs
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirthBabyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { App() }
                }
            }
        }
    }
}

enum class Screen { HOME, SETTINGS, DIAGNOSTICS }

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var role by remember { mutableStateOf(prefs.role) }
    var fid by remember { mutableStateOf(prefs.familyId) }

    val r = role
    val f = fid
    if (r == null || f == null) {
        Onboarding { newRole, newFid ->
            prefs.role = newRole
            prefs.familyId = newFid
            Trace.init(context)
            Trace.critical("paired", newRole.name)
            role = newRole
            fid = newFid
        }
        return
    }

    LaunchedEffect(r) { if (r == Role.PARTNER) PartnerService.start(context) }

    // The screen only listens while it's visible. The partner's alarms come from PartnerService, not from here.
    val family by remember(f) { Repo.familyFlow(f) }.collectAsStateWithLifecycle(null)
    val contractions by remember(f) { Repo.contractionsFlow(f) }.collectAsStateWithLifecycle(emptyList())
    val alerts by remember(f) { Repo.alertsFlow(f, System.currentTimeMillis() - 12 * 60 * 60_000L) }
        .collectAsStateWithLifecycle(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val tickMs = Ticker.intervalMs(contractions, alerts, now)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, tickMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(tickMs) }
        }
    }
    var screen by remember { mutableStateOf(Screen.HOME) }

    val fam = family
    if (fam == null) {
        Connecting()
        return
    }
    val state = HomeState(fam, contractions, alerts, now)
    when (screen) {
        Screen.HOME -> when (r) {
            Role.MOTHER -> MotherHome(state, onDiagnostics = { screen = Screen.DIAGNOSTICS })
            Role.PARTNER -> PartnerHome(
                state,
                onSettings = { screen = Screen.SETTINGS },
                onDiagnostics = { screen = Screen.DIAGNOSTICS },
            )
        }
        Screen.SETTINGS -> SettingsScreen(fam, onDone = { screen = Screen.HOME })
        Screen.DIAGNOSTICS -> DiagnosticsScreen(fam, r, onDone = { screen = Screen.HOME })
    }
}

/** Shown until the family loads. Never a dead end: the Delivery Suite is always one tap away. */
@Composable
private fun Connecting() {
    val context = LocalContext.current
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000); slow = true }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Connecting…", style = MaterialTheme.typography.titleLarge)
        if (slow) {
            Text(
                "Still connecting. Check Wi-Fi or mobile data.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            val defaults = FamilySettings()
            QuietButton("Call Delivery Suite", { dial(context, defaults.deliverySuite, "delivery_suite") }, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun Onboarding(onPaired: (Role, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Birth & Baby", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 32.dp))
        val c = created
        when {
            c != null -> {
                val joined by remember(c.first) { Repo.familyFlow(c.first) }.collectAsStateWithLifecycle(null)
                Text("On the partner's phone, choose \"I'm the partner\" and enter this code:", style = MaterialTheme.typography.bodyLarge)
                Text(c.second.chunked(3).joinToString(" "), style = MaterialTheme.typography.displayLarge)
                if ((joined?.memberCount ?: 1) >= 2) {
                    InfoCard("Partner's phone has joined ✓", "", tones.ok)
                    BigButton("Done", { onPaired(Role.MOTHER, c.first) })
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Waiting for the partner's phone…", style = MaterialTheme.typography.bodyLarge)
                    }
                    QuietButton("Skip for now", { onPaired(Role.MOTHER, c.first) })
                }
            }
            joining -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit code from the other phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton(if (busy) "Joining…" else "Join", {
                    if (busy) return@BigButton
                    busy = true
                    scope.launch {
                        try {
                            val fid = Repo.joinFamily(code)
                            if (fid == null) error = "That code didn't match. Check it on the other phone."
                            else onPaired(Role.PARTNER, fid)
                        } catch (e: Exception) {
                            Trace.error("join_failed", e)
                            error = "Couldn't connect: ${e.message}"
                        }
                        busy = false
                    }
                })
                TextButton(onClick = { joining = false }) { Text("Back") }
            }
            else -> {
                Text("Whose phone is this?", style = MaterialTheme.typography.titleLarge)
                BigButton(if (busy) "Setting up…" else "I'm having the baby", {
                    if (busy) return@BigButton
                    busy = true
                    scope.launch {
                        try {
                            created = Repo.createFamily()
                        } catch (e: Exception) {
                            Trace.error("create_failed", e)
                            error = "Couldn't connect: ${e.message}"
                        }
                        busy = false
                    }
                }, height = 80)
                BigButton("I'm the partner", { joining = true }, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, height = 80)
                Text(
                    "Set up the phone of the person having the baby first. It will show a code for the partner's phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
