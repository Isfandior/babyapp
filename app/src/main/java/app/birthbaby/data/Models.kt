package app.birthbaby.data

import app.birthbaby.core.AlertType
import app.birthbaby.core.CallRule

enum class Role { MOTHER, PARTNER }

/**
 * Everything that might change lives here and is edited in Settings on the partner's
 * phone. Defaults are Homerton's published numbers (homerton.nhs.uk/maternity-services)
 * and the NHS "every 5 minutes" rule.
 */
data class FamilySettings(
    val motherName: String = "Mum",
    val motherPhone: String = "",
    val partnerName: String = "Partner",
    val partnerPhone: String = "",
    val backupName: String = "",
    val backupPhone: String = "",
    val dueDate: String = "",
    val hospital: String = "Homerton",
    val deliverySuite: String = "020 8510 7351",
    val deliverySuiteBackup: String = "020 8510 7352",
    val helpline: String = "020 8510 5955",
    val helplineHours: String = "10am to 6pm",
    val communityMidwife: String = "020 8510 5761",
    val maxGapMinutes: Int = 5,
    val regularCount: Int = 4,
    val motherDetails: String = "",
    val homeAddress: String = "",
    val taxiAppPackage: String = "com.ubercab",
    val taxiBackupPhone: String = "",
) {
    val callRule: CallRule get() = CallRule(maxGapMinutes.coerceIn(1, 30), regularCount.coerceIn(2, 10))

    fun toMap(): Map<String, Any> = mapOf(
        "motherName" to motherName, "motherPhone" to motherPhone,
        "partnerName" to partnerName, "partnerPhone" to partnerPhone,
        "backupName" to backupName, "backupPhone" to backupPhone,
        "dueDate" to dueDate, "hospital" to hospital,
        "deliverySuite" to deliverySuite, "deliverySuiteBackup" to deliverySuiteBackup,
        "helpline" to helpline, "helplineHours" to helplineHours,
        "communityMidwife" to communityMidwife,
        "maxGapMinutes" to maxGapMinutes, "regularCount" to regularCount,
        "motherDetails" to motherDetails, "homeAddress" to homeAddress,
        "taxiAppPackage" to taxiAppPackage, "taxiBackupPhone" to taxiBackupPhone,
    )

    companion object {
        fun fromMap(m: Map<*, *>?): FamilySettings {
            if (m == null) return FamilySettings()
            val d = FamilySettings()
            fun s(k: String, def: String) = m[k] as? String ?: def
            fun i(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            return FamilySettings(
                motherName = s("motherName", d.motherName), motherPhone = s("motherPhone", d.motherPhone),
                partnerName = s("partnerName", d.partnerName), partnerPhone = s("partnerPhone", d.partnerPhone),
                backupName = s("backupName", d.backupName), backupPhone = s("backupPhone", d.backupPhone),
                dueDate = s("dueDate", d.dueDate), hospital = s("hospital", d.hospital),
                deliverySuite = s("deliverySuite", d.deliverySuite),
                deliverySuiteBackup = s("deliverySuiteBackup", d.deliverySuiteBackup),
                helpline = s("helpline", d.helpline), helplineHours = s("helplineHours", d.helplineHours),
                communityMidwife = s("communityMidwife", d.communityMidwife),
                maxGapMinutes = i("maxGapMinutes", d.maxGapMinutes), regularCount = i("regularCount", d.regularCount),
                motherDetails = s("motherDetails", d.motherDetails), homeAddress = s("homeAddress", d.homeAddress),
                taxiAppPackage = s("taxiAppPackage", d.taxiAppPackage),
                taxiBackupPhone = s("taxiBackupPhone", d.taxiBackupPhone),
            )
        }
    }
}

data class Family(
    val id: String,
    val code: String,
    val settings: FamilySettings,
    val partnerSeenMs: Long?,
    /** 1 until the partner's phone joins with the code. */
    val memberCount: Int = 1,
)

data class Alert(
    val id: String,
    val type: AlertType,
    val createdMs: Long,
    val byRole: Role,
    val ringingMs: Long?,
    val ackedMs: Long?,
    /** Set when the mother taps "Sent by mistake". Stops the alarm without saying he's on his way. */
    val cancelledMs: Long? = null,
    /** Set when the backup text actually went out. */
    val smsSentMs: Long? = null,
) {
    /** Answered or cancelled: nothing more should ring for it. */
    val resolved: Boolean get() = ackedMs != null || cancelledMs != null
}
