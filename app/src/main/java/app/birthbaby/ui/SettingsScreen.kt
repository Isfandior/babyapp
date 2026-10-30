package app.birthbaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthbaby.BuildConfig
import app.birthbaby.data.Family
import app.birthbaby.data.FamilySettings
import app.birthbaby.data.Repo
import app.birthbaby.data.Role
import app.birthbaby.data.Trace

/** Everything that might change. Saved to both phones straight away; no app update needed. */
@Composable
fun SettingsScreen(family: Family, onDone: () -> Unit) {
    var s by remember { mutableStateOf(family.settings) }

    @Composable
    fun field(label: String, value: String, phone: Boolean = false, update: (String) -> FamilySettings) =
        OutlinedTextField(
            value = value,
            onValueChange = { s = update(it) },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )

    @Composable
    fun number(label: String, value: Int, update: (Int) -> FamilySettings) =
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { v -> v.filter(Char::isDigit).toIntOrNull()?.let { s = update(it) } },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", fontSize = 28.sp)

        Text("People", fontSize = 18.sp)
        field("Mother's name", s.motherName) { s.copy(motherName = it) }
        field("Mother's mobile", s.motherPhone, phone = true) { s.copy(motherPhone = it) }
        field("Partner's name", s.partnerName) { s.copy(partnerName = it) }
        field("Partner's mobile (gets the text backup)", s.partnerPhone, phone = true) { s.copy(partnerPhone = it) }
        field("Backup person's name", s.backupName) { s.copy(backupName = it) }
        field("Backup person's mobile", s.backupPhone, phone = true) { s.copy(backupPhone = it) }

        Text("Hospital", fontSize = 18.sp)
        field("Due date (YYYY-MM-DD)", s.dueDate) { s.copy(dueDate = it) }
        field("Hospital", s.hospital) { s.copy(hospital = it) }
        field("Delivery Suite (24 hours)", s.deliverySuite, phone = true) { s.copy(deliverySuite = it) }
        field("Delivery Suite backup line", s.deliverySuiteBackup, phone = true) { s.copy(deliverySuiteBackup = it) }
        field("Maternity Helpline", s.helpline, phone = true) { s.copy(helpline = it) }
        field("Helpline hours", s.helplineHours) { s.copy(helplineHours = it) }
        field("Community midwives", s.communityMidwife, phone = true) { s.copy(communityMidwife = it) }
        field("Details for staff (hospital number, NHS number, allergies, birth plan)", s.motherDetails) { s.copy(motherDetails = it) }

        Text("When to call", fontSize = 18.sp)
        Text(
            "NHS default: regular contractions every 5 minutes or more often. Change this to what your midwife says. " +
                "The urgent rules (waters, bleeding, movements, contractions over 2 minutes or 6 in 10 minutes) always stay on.",
            fontSize = 14.sp,
        )
        number("Call when contractions are this many minutes apart or less", s.maxGapMinutes) { s.copy(maxGapMinutes = it) }
        number("…for this many contractions in a row", s.regularCount) { s.copy(regularCount = it) }

        Text("Getting there", fontSize = 18.sp)
        field("Home address", s.homeAddress) { s.copy(homeAddress = it) }
        field("Taxi app (Android package, e.g. com.ubercab)", s.taxiAppPackage) { s.copy(taxiAppPackage = it) }
        field("Backup minicab number", s.taxiBackupPhone, phone = true) { s.copy(taxiBackupPhone = it) }

        BigButton("Save", Green, {
            Repo.saveSettings(family.id, s)
            onDone()
        })
        TextButton(onClick = onDone) { Text("Cancel") }
    }
}

/** What the app has been doing, newest first. Useful after the dress rehearsal or if an alert seems late. */
@Composable
fun DiagnosticsScreen(family: Family, role: Role, onDone: () -> Unit) {
    val lines = remember { Trace.recent() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Diagnostics", fontSize = 28.sp)
        Text("Version ${BuildConfig.VERSION_NAME} · ${role.name.lowercase()} phone · code ${family.code}", fontSize = 14.sp)
        family.partnerSeenMs?.let { Text("Partner's phone last checked in at ${time(it)}", fontSize = 14.sp) }
        TextButton(onClick = onDone) { Text("Back") }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(lines) { Text(it, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
