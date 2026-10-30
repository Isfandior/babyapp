package app.birthbaby.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.birthbaby.data.Trace
import app.birthbaby.ui.MainActivity

/**
 * Three channels:
 *  - urgent: alarm sound on the alarm stream, bypasses Do Not Disturb, full screen over the lock screen;
 *  - updates: quiet contraction updates, so the partner doesn't learn to ignore the alarm;
 *  - listening: the silent ongoing notification that keeps the partner's listener alive.
 */
object Notifier {
    const val URGENT = "urgent"
    const val UPDATES = "updates"
    const val LISTENING = "listening"
    const val LISTENING_ID = 1
    private const val UPDATE_ID = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val urgent = NotificationChannel(URGENT, "Urgent labour alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Waters, bleeding, baby moving less, time to call. Rings through silent."
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val updates = NotificationChannel(UPDATES, "Contraction updates", NotificationManager.IMPORTANCE_LOW)
        val listening = NotificationChannel(LISTENING, "Listening for alerts", NotificationManager.IMPORTANCE_MIN)
        nm.createNotificationChannels(listOf(urgent, updates, listening))
    }

    fun urgent(context: Context, alertId: String, title: String, message: String) {
        val full = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            AlarmActivity.intent(context, alertId, title, message),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, URGENT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(alertId.hashCode(), n)
        Trace.critical("alert_notified", alertId)
    }

    fun cancel(context: Context, alertId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(alertId.hashCode())
    }

    fun update(context: Context, text: String) {
        val n = Notification.Builder(context, UPDATES)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Contractions")
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(UPDATE_ID, n)
    }

    fun listening(context: Context, text: String): Notification =
        Notification.Builder(context, LISTENING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Listening for labour alerts")
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
