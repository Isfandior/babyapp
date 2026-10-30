package app.birthbaby.data

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight tracing, three layers:
 *  - every event goes to logcat and a 300-line ring buffer on the phone (Settings > Diagnostics);
 *  - every event is a Crashlytics breadcrumb, so a crash report shows what led up to it;
 *  - critical events (the alert chain: created, SMS, received, rang, acked; listener health)
 *    are also written to Firestore, so we can see the whole chain across both phones.
 */
object Trace {
    private const val TAG = "BirthBaby"
    private const val MAX_LINES = 300
    private lateinit var app: Context
    private var prefs: Prefs? = null

    fun init(context: Context) {
        app = context.applicationContext
        prefs = Prefs(app)
        safe { FirebaseCrashlytics.getInstance().setCustomKey("role", prefs?.role?.name ?: "none") }
    }

    fun event(name: String, detail: String = "") = record("info", name, detail, critical = false)

    fun critical(name: String, detail: String = "") = record("critical", name, detail, critical = true)

    fun error(name: String, e: Throwable? = null, detail: String = "") {
        record("error", name, listOf(detail, e?.message.orEmpty()).filter { it.isNotBlank() }.joinToString(" "), critical = true)
        if (e != null) safe { FirebaseCrashlytics.getInstance().recordException(e) }
    }

    fun recent(): List<String> = synchronized(this) {
        val arr = JSONArray(buffer().getString("lines", "[]"))
        (0 until arr.length()).map { arr.getString(it) }.reversed()
    }

    private fun record(level: String, name: String, detail: String, critical: Boolean) {
        val now = System.currentTimeMillis()
        val line = "${stamp(now)} $level $name $detail".trim()
        if (level == "error") Log.e(TAG, line) else Log.i(TAG, line)
        if (!::app.isInitialized) return
        appendLocal(line)
        safe { FirebaseCrashlytics.getInstance().log(line) }
        if (critical) {
            val fid = prefs?.familyId ?: return
            safe {
                Repo.writeTrace(
                    fid,
                    mapOf("ts" to now, "role" to (prefs?.role?.name ?: "none"), "level" to level, "event" to name, "detail" to detail),
                )
            }
        }
    }

    private fun appendLocal(line: String) = synchronized(this) {
        val arr = JSONArray(buffer().getString("lines", "[]"))
        arr.put(line)
        val trimmed = JSONArray()
        for (i in maxOf(0, arr.length() - MAX_LINES) until arr.length()) trimmed.put(arr.getString(i))
        buffer().edit().putString("lines", trimmed.toString()).apply()
    }

    private fun buffer() = app.getSharedPreferences("trace", Context.MODE_PRIVATE)

    private fun stamp(ms: Long) = SimpleDateFormat("dd MMM HH:mm:ss", Locale.UK).format(Date(ms))

    /** Tracing must never take the app down, e.g. when Firebase isn't configured. */
    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "trace failed: ${e.message}") }
    }
}
