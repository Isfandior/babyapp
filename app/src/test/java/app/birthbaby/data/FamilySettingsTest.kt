package app.birthbaby.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FamilySettingsTest {
    private val custom = FamilySettings(
        motherName = "M", motherPhone = "07000 000001", partnerName = "P", partnerPhone = "07000 000002",
        backupName = "B", backupPhone = "07000 000003", dueDate = "2027-01-01", hospital = "H",
        deliverySuite = "1", deliverySuiteBackup = "2", helpline = "3", helplineHours = "9 to 5",
        communityMidwife = "4", maxGapMinutes = 3, regularCount = 5, motherDetails = "details",
        homeAddress = "home", taxiAppPackage = "com.example.taxi", taxiBackupPhone = "5",
    )

    @Test fun defaultsAreHomertonAndTheNhsRule() {
        val d = FamilySettings()
        assertEquals("Homerton", d.hospital)
        assertEquals("020 8510 7351", d.deliverySuite)
        assertEquals("020 8510 7352", d.deliverySuiteBackup)
        assertEquals("020 8510 5955", d.helpline)
        assertEquals("020 8510 5761", d.communityMidwife)
        assertEquals(5, d.callRule.maxGapMinutes)
        assertEquals(4, d.callRule.regularCount)
    }

    @Test fun everyFieldSurvivesTheTripThroughFirestore() {
        assertEquals(custom, FamilySettings.fromMap(custom.toMap()))
    }

    @Test fun firestoreNumbersComeBackAsLongs() {
        val m = custom.toMap() + mapOf("maxGapMinutes" to 6L, "regularCount" to 3L)
        val s = FamilySettings.fromMap(m)
        assertEquals(6, s.maxGapMinutes)
        assertEquals(3, s.regularCount)
    }

    @Test fun missingOrBrokenFieldsFallBackToDefaults() {
        assertEquals(FamilySettings(), FamilySettings.fromMap(null))
        assertEquals(FamilySettings(), FamilySettings.fromMap(emptyMap<String, Any>()))
        val s = FamilySettings.fromMap(mapOf("maxGapMinutes" to "7", "deliverySuite" to 123, "motherName" to "Kat"))
        assertEquals(5, s.maxGapMinutes)
        assertEquals("020 8510 7351", s.deliverySuite)
        assertEquals("Kat", s.motherName)
    }

    @Test fun callRuleIsClampedToSensibleLimits() {
        assertEquals(1, FamilySettings(maxGapMinutes = 0).callRule.maxGapMinutes)
        assertEquals(30, FamilySettings(maxGapMinutes = 99).callRule.maxGapMinutes)
        assertEquals(2, FamilySettings(regularCount = 0).callRule.regularCount)
        assertEquals(10, FamilySettings(regularCount = 50).callRule.regularCount)
        assertEquals(3, FamilySettings(maxGapMinutes = 3).callRule.maxGapMinutes)
    }
}
