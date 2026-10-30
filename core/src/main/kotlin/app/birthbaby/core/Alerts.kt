package app.birthbaby.core

/** Who the mother is told to ring. */
enum class CallTarget { DELIVERY_SUITE, EMERGENCY_999 }

/**
 * Urgent alerts. Each one rings the partner's phone like an alarm (through silent
 * and Do Not Disturb), goes by SMS as a backup, and tells the mother what to do.
 * The wording follows nhs.uk; it is fixed so it cannot be edited away.
 */
enum class AlertType(
    val title: String,
    val partnerMessage: String,
    val motherAction: String,
    val callTarget: CallTarget,
) {
    WATERS(
        "Waters broke",
        "{mother}'s waters have broken. Come home now. She needs to call the Delivery Suite.",
        "Call the Delivery Suite now, even in the middle of the night.",
        CallTarget.DELIVERY_SUITE,
    ),
    BLEEDING(
        "Bleeding",
        "{mother} has bleeding. Come home now. She needs to call the Delivery Suite.",
        "Call the Delivery Suite now.",
        CallTarget.DELIVERY_SUITE,
    ),
    MOVEMENTS(
        "Baby moving less",
        "Baby is moving less than usual. {mother} needs to call the Delivery Suite now.",
        "Call the Delivery Suite now. Don't wait until tomorrow.",
        CallTarget.DELIVERY_SUITE,
    ),
    NEED_YOU(
        "I need you",
        "{mother} needs you. Call her or come home now.",
        "{partner} has been alerted.",
        CallTarget.DELIVERY_SUITE,
    ),
    TIME_TO_CALL(
        "Time to call",
        "Contractions are regular. Time to call the Delivery Suite and get ready to go.",
        "Your contractions are regular. Call the Delivery Suite for advice.",
        CallTarget.DELIVERY_SUITE,
    ),
    CALL_NOW(
        "Call now",
        "Contractions need checking now. {mother} needs to call the Delivery Suite.",
        "Call the Delivery Suite now.",
        CallTarget.DELIVERY_SUITE,
    ),
    BABY_COMING(
        "Baby coming",
        "Baby is coming now. Call 999.",
        "Call 999 now.",
        CallTarget.EMERGENCY_999,
    ),
    TEST(
        "Test alarm",
        "This is a test from {mother}'s phone. Tap On my way to confirm alerts work.",
        "Test alarm sent to {partner}.",
        CallTarget.DELIVERY_SUITE,
    ),
}

object AlertTiming {
    /** If the partner hasn't tapped "On my way" by then, the mother is told to ring him. */
    const val ACK_TIMEOUT_MS = 2 * 60_000L

    fun needsCallPrompt(createdMs: Long, ackedMs: Long?, nowMs: Long): Boolean =
        ackedMs == null && nowMs - createdMs >= ACK_TIMEOUT_MS
}

fun String.withNames(mother: String, partner: String): String =
    replace("{mother}", mother).replace("{partner}", partner)
