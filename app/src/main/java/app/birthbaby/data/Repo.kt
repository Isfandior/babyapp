package app.birthbaby.data

import android.content.Context
import app.birthbaby.core.AlertType
import app.birthbaby.core.Contraction
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/** Which family this phone belongs to and in which role. Stored on the phone only. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("birthbaby", Context.MODE_PRIVATE)

    var role: Role?
        get() = sp.getString("role", null)?.let { Role.valueOf(it) }
        set(v) = sp.edit().putString("role", v?.name).apply()

    var familyId: String?
        get() = sp.getString("familyId", null)
        set(v) = sp.edit().putString("familyId", v).apply()

    /** Alerts this phone has already rung, so a restart doesn't ring them again. */
    fun markRung(id: String) = sp.edit().putStringSet("rung", rung() + id).apply()
    fun rung(): Set<String> = sp.getStringSet("rung", emptySet()) ?: emptySet()
}

/**
 * Firestore layout:
 *   codes/{code}                      -> { familyId }
 *   families/{id}                     -> { code, members[], settings{}, partnerSeenMs }
 *   families/{id}/contractions/{id}   -> { startMs, endMs, by }
 *   families/{id}/alerts/{id}         -> { type, createdMs, by, ringingMs, ackedMs, cancelledMs, smsSentMs }
 *   families/{id}/trace/{id}          -> { ts, role, event, detail }
 * Firestore keeps an offline cache, so logging works with no signal and syncs later.
 */
object Repo {
    private val db get() = Firebase.firestore
    private fun family(id: String) = db.collection("families").document(id)

    suspend fun ensureSignedIn(): String {
        val auth = Firebase.auth
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    /** Returns (family id, pairing code). */
    suspend fun createFamily(): Pair<String, String> {
        val uid = ensureSignedIn()
        val code = Random.nextInt(100_000, 1_000_000).toString()
        val ref = db.collection("families").document()
        ref.set(
            mapOf(
                "code" to code,
                "members" to listOf(uid),
                "settings" to FamilySettings().toMap(),
            ),
        ).await()
        db.collection("codes").document(code).set(mapOf("familyId" to ref.id)).await()
        return ref.id to code
    }

    /** Returns the family id, or null if the code is wrong. */
    suspend fun joinFamily(code: String): String? {
        val uid = ensureSignedIn()
        val fid = db.collection("codes").document(code.trim()).get().await().getString("familyId")
            ?: return null
        family(fid).update("members", FieldValue.arrayUnion(uid)).await()
        return fid
    }

    fun familyFlow(fid: String): Flow<Family?> = callbackFlow {
        val reg = family(fid).addSnapshotListener { snap, err ->
            if (err != null) { Trace.error("family_listen_failed", err); return@addSnapshotListener }
            trySend(
                snap?.takeIf { it.exists() }?.let {
                    Family(
                        id = it.id,
                        code = it.getString("code") ?: "",
                        settings = FamilySettings.fromMap(it.get("settings") as? Map<*, *>),
                        partnerSeenMs = it.getLong("partnerSeenMs"),
                        memberCount = (it.get("members") as? List<*>)?.size ?: 1,
                    )
                },
            )
        }
        awaitClose { reg.remove() }
    }

    fun contractionsFlow(fid: String): Flow<List<Contraction>> = callbackFlow {
        val since = System.currentTimeMillis() - 24 * 60 * 60_000L
        val reg = family(fid).collection("contractions")
            .whereGreaterThan("startMs", since)
            .orderBy("startMs", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) { Trace.error("contractions_listen_failed", err); return@addSnapshotListener }
                trySend(
                    snap?.documents.orEmpty().mapNotNull { d ->
                        val start = d.getLong("startMs") ?: return@mapNotNull null
                        Contraction(d.id, start, d.getLong("endMs"))
                    },
                )
            }
        awaitClose { reg.remove() }
    }

    /** Writes return immediately from the local cache; Firestore syncs when online. */
    fun startContraction(fid: String, role: Role): String {
        val ref = family(fid).collection("contractions").document()
        ref.set(mapOf("startMs" to System.currentTimeMillis(), "by" to role.name))
        Trace.event("contraction_start", ref.id)
        return ref.id
    }

    fun endContraction(fid: String, id: String) {
        family(fid).collection("contractions").document(id).update("endMs", System.currentTimeMillis())
        Trace.event("contraction_end", id)
    }

    fun deleteContraction(fid: String, id: String) {
        family(fid).collection("contractions").document(id).delete()
        Trace.event("contraction_deleted", id)
    }

    /** Puts back a contraction removed by mistake, under the same id. */
    fun restoreContraction(fid: String, c: Contraction) {
        val data = buildMap<String, Any> {
            put("startMs", c.startMs)
            c.endMs?.let { put("endMs", it) }
            put("by", Role.PARTNER.name)
        }
        family(fid).collection("contractions").document(c.id).set(data)
        Trace.event("contraction_restored", c.id)
    }

    fun alertsFlow(fid: String, sinceMs: Long): Flow<List<Alert>> = callbackFlow {
        val reg = family(fid).collection("alerts")
            .whereGreaterThan("createdMs", sinceMs)
            .orderBy("createdMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Trace.error("alerts_listen_failed", err); return@addSnapshotListener }
                trySend(
                    snap?.documents.orEmpty().mapNotNull { d ->
                        val type = d.getString("type")?.let { t -> AlertType.entries.find { it.name == t } }
                            ?: return@mapNotNull null
                        Alert(
                            id = d.id,
                            type = type,
                            createdMs = d.getLong("createdMs") ?: 0,
                            byRole = d.getString("by")?.let { Role.valueOf(it) } ?: Role.MOTHER,
                            ringingMs = d.getLong("ringingMs"),
                            ackedMs = d.getLong("ackedMs"),
                            cancelledMs = d.getLong("cancelledMs"),
                            smsSentMs = d.getLong("smsSentMs"),
                        )
                    },
                )
            }
        awaitClose { reg.remove() }
    }

    /** [id] makes rule alerts idempotent, so both phones raising the same one write one document. */
    fun raiseAlert(fid: String, type: AlertType, role: Role, id: String? = null): String {
        val col = family(fid).collection("alerts")
        val ref = id?.let { col.document(it) } ?: col.document()
        ref.set(
            mapOf("type" to type.name, "createdMs" to System.currentTimeMillis(), "by" to role.name),
            SetOptions.merge(),
        )
        Trace.critical("alert_created", "${type.name} ${ref.id}")
        return ref.id
    }

    fun markRinging(fid: String, alertId: String) {
        family(fid).collection("alerts").document(alertId).update("ringingMs", System.currentTimeMillis())
    }

    fun ackAlert(fid: String, alertId: String) {
        family(fid).collection("alerts").document(alertId).update("ackedMs", System.currentTimeMillis())
        Trace.critical("alert_acked", alertId)
    }

    fun cancelAlert(fid: String, alertId: String) {
        family(fid).collection("alerts").document(alertId).update("cancelledMs", System.currentTimeMillis())
        Trace.critical("alert_cancelled_by_mother", alertId)
    }

    fun markSmsSent(fid: String, alertId: String) {
        family(fid).collection("alerts").document(alertId).update("smsSentMs", System.currentTimeMillis())
    }

    /** One read of an alert: from the server if reachable, otherwise the phone's cache. */
    suspend fun getAlert(fid: String, alertId: String): Alert? {
        val d = family(fid).collection("alerts").document(alertId).get().await()
        val type = d.getString("type")?.let { t -> AlertType.entries.find { it.name == t } } ?: return null
        return Alert(
            id = d.id,
            type = type,
            createdMs = d.getLong("createdMs") ?: 0,
            byRole = d.getString("by")?.let { Role.valueOf(it) } ?: Role.MOTHER,
            ringingMs = d.getLong("ringingMs"),
            ackedMs = d.getLong("ackedMs"),
            cancelledMs = d.getLong("cancelledMs"),
            smsSentMs = d.getLong("smsSentMs"),
        )
    }

    fun saveSettings(fid: String, settings: FamilySettings) {
        family(fid).update("settings", settings.toMap())
        Trace.critical("settings_saved", "")
    }

    fun heartbeat(fid: String) {
        family(fid).update("partnerSeenMs", System.currentTimeMillis())
    }

    internal fun writeTrace(fid: String, data: Map<String, Any>) {
        family(fid).collection("trace").add(data)
    }
}
