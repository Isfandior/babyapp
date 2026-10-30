package app.birthbaby.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthbaby.data.Prefs
import app.birthbaby.data.Repo
import app.birthbaby.data.Trace

/** Full-screen alarm on the partner's phone, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alertId = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        Trace.critical("alarm_screen_shown", alertId)

        setContent {
            Column(
                Modifier.fillMaxSize().background(Color(0xFFB3261E)).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { acknowledge(alertId) },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E)),
                ) { Text("On my way", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    private fun acknowledge(alertId: String) {
        AlarmPlayer.stop()
        Notifier.cancel(this, alertId)
        Prefs(this).familyId?.let { Repo.ackAlert(it, alertId) }
        finish()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"

        fun intent(context: Context, id: String, title: String, message: String) =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
