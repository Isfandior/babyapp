package app.birthbaby.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import app.birthbaby.core.Advice
import app.birthbaby.core.Assessment
import app.birthbaby.data.FamilySettings
import app.birthbaby.data.Trace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** A full-width filled button. Defaults to the calm accent; pass the error colour only for urgent actions. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Int = 64,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
    ) { Text(text, fontSize = 19.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge) }
}

/** Filled red. Only for things that need doing now. */
@Composable
fun UrgentButton(text: String, onClick: () -> Unit, height: Int = 64, modifier: Modifier = Modifier) =
    BigButton(text, onClick, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, height, modifier)

/** Outlined, for a secondary choice next to a main one. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.primary) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    tone: Tone = tones.neutral,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyLarge)
            content()
        }
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp),
    )
}

/** The "time to call" banner both phones show. */
@Composable
fun AdviceBanner(assessment: Assessment, settings: FamilySettings, context: Context) {
    when (assessment.advice) {
        Advice.NONE -> Unit
        Advice.CALL_FOR_ADVICE -> InfoCard(
            "Time to call ${settings.hospital}",
            "${assessment.reason}. Call the Delivery Suite for advice.",
            tones.warning,
        ) {
            UrgentButton("Call Delivery Suite", { dial(context, settings.deliverySuite, "delivery_suite") })
        }
        Advice.CALL_NOW -> InfoCard(
            "Call ${settings.hospital} now",
            "${assessment.reason}.",
            tones.urgent,
        ) {
            UrgentButton("Call Delivery Suite now", { dial(context, settings.deliverySuite, "delivery_suite") })
        }
    }
}
