package app.birthbaby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
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
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App() }
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

    val family by remember(f) { Repo.familyFlow(f) }.collectAsState(null)
    val contractions by remember(f) { Repo.contractionsFlow(f) }.collectAsState(emptyList())
    val alerts by remember(f) { Repo.alertsFlow(f, System.currentTimeMillis() - 12 * 60 * 60_000L) }
        .collectAsState(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    var screen by remember { mutableStateOf(Screen.HOME) }

    val fam = family
    if (fam == null) {
        Column(Modifier.padding(24.dp)) { Text("Connecting…", fontSize = 20.sp) }
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

@Composable
fun Onboarding(onPaired: (Role, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Birth & Baby", fontSize = 32.sp)
        val c = created
        when {
            c != null -> {
                Text("Give this code to your partner:", fontSize = 18.sp)
                Text(c.second, fontSize = 48.sp)
                BigButton("Done", Green, { onPaired(Role.MOTHER, c.first) })
            }
            joining -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit code from the mother's phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton(if (busy) "Joining…" else "Join", Green, {
                    if (busy) return@BigButton
                    busy = true
                    scope.launch {
                        try {
                            val fid = Repo.joinFamily(code)
                            if (fid == null) error = "That code didn't match. Check it on the mother's phone."
                            else onPaired(Role.PARTNER, fid)
                        } catch (e: Exception) {
                            Trace.error("join_failed", e)
                            error = "Couldn't connect: ${e.message}"
                        }
                        busy = false
                    }
                })
            }
            else -> {
                Text("Whose phone is this?", fontSize = 20.sp)
                BigButton(if (busy) "Setting up…" else "Mother", Red, {
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
                })
                BigButton("Partner", Green, { joining = true })
            }
        }
        error?.let { Text(it, color = Red) }
    }
}
