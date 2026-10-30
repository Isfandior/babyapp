package app.birthbaby.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import app.birthbaby.data.Trace

/**
 * Plays the alarm on the alarm stream, which rings even when the phone is on silent,
 * and loops until the partner taps "On my way".
 */
object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun start(context: Context) {
        if (player != null) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            Trace.critical("alarm_volume_zero")
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                isLooping = true
                prepare()
                start()
            }
            Trace.critical("alarm_started")
        } catch (e: Exception) {
            player = null
            Trace.error("alarm_failed", e)
        }
        vibrator = app.getSystemService(Vibrator::class.java)?.also {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        }
    }

    @Synchronized
    fun stop() {
        player?.run { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    val isPlaying: Boolean get() = player != null
}
