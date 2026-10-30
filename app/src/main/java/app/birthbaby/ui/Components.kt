package app.birthbaby.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthbaby.core.Advice
import app.birthbaby.core.Assessment
import app.birthbaby.data.FamilySettings
import app.birthbaby.data.Trace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Red = Color(0xFFB3261E)
val Amber = Color(0xFFF2A900)
val Green = Color(0xFF2E7D32)

fun dial(context: Context, number: String, label: String) {
    Trace.critical("call_tapped", label)
    context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun bookTaxi(context: Context, settings: FamilySettings) {
    val launch = settings.taxiAppPackage.takeIf { it.isNotBlank() }
        ?.let { context.packageManager.getLaunchIntentForPackage(it) }
    if (launch != null) {
        Trace.critical("taxi_app_opened", settings.taxiAppPackage)
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else if (settings.taxiBackupPhone.isNotBlank()) {
        dial(context, settings.taxiBackupPhone, "taxi_backup")
    } else {
        Trace.critical("taxi_not_set_up")
    }
}

fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date(ms))

@Composable
fun BigButton(text: String, color: Color, onClick: () -> Unit, height: Int = 72) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun InfoCard(title: String, body: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (body.isNotBlank()) Text(body, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            content()
        }
    }
}

/** The "time to call" banner both phones show. */
@Composable
fun AdviceBanner(assessment: Assessment, settings: FamilySettings, context: Context) {
    when (assessment.advice) {
        Advice.NONE -> Unit
        Advice.CALL_FOR_ADVICE -> InfoCard(
            "Time to call ${settings.hospital}",
            "${assessment.reason}. Call the Delivery Suite for advice.",
            Amber,
        ) {
            BigButton("Call Delivery Suite", Red, { dial(context, settings.deliverySuite, "delivery_suite") })
        }
        Advice.CALL_NOW -> InfoCard(
            "Call ${settings.hospital} now",
            "${assessment.reason}.",
            Red.copy(alpha = 0.25f),
        ) {
            BigButton("Call Delivery Suite now", Red, { dial(context, settings.deliverySuite, "delivery_suite") })
        }
    }
}
