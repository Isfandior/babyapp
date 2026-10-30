package app.birthbaby.core

import kotlin.math.roundToInt

/** One contraction. [endMs] is null while it is still going. */
data class Contraction(
    val id: String,
    val startMs: Long,
    val endMs: Long? = null,
) {
    val isOngoing: Boolean get() = endMs == null
    fun durationMs(nowMs: Long): Long = (endMs ?: nowMs) - startMs
}

/**
 * The editable "time to call" rule. The NHS default is regular contractions
 * every 5 minutes or more often; the midwife can change it in Settings.
 */
data class CallRule(
    val maxGapMinutes: Int = 5,
    val regularCount: Int = 4,
)

enum class Advice { NONE, CALL_FOR_ADVICE, CALL_NOW }

data class Stats(
    val countLastHour: Int,
    val avgDurationSec: Int?,
    val avgGapSec: Int?,
)

data class Assessment(
    val advice: Advice,
    val reason: String?,
    val stats: Stats,
)

/**
 * NHS labour rules (nhs.uk "Signs that labour has begun"). The CALL_NOW rules are
 * fixed and cannot be switched off; only [CallRule] is editable.
 */
object LabourRules {
    const val LONG_CONTRACTION_MS = 2 * 60_000L
    const val FREQUENT_WINDOW_MS = 10 * 60_000L
    const val FREQUENT_COUNT = 6
    private const val HOUR_MS = 60 * 60_000L

    fun stats(contractions: List<Contraction>, nowMs: Long): Stats {
        val recent = contractions.filter { nowMs - it.startMs <= HOUR_MS }.sortedBy { it.startMs }
        val done = recent.filter { !it.isOngoing }
        val avgDuration = done.takeIf { it.isNotEmpty() }
            ?.map { it.durationMs(nowMs) }?.average()?.div(1000)?.roundToInt()
        val gaps = recent.zipWithNext { a, b -> b.startMs - a.startMs }
        val avgGap = gaps.takeIf { it.isNotEmpty() }?.average()?.div(1000)?.roundToInt()
        return Stats(recent.size, avgDuration, avgGap)
    }

    fun assess(contractions: List<Contraction>, nowMs: Long, rule: CallRule = CallRule()): Assessment {
        val sorted = contractions.sortedBy { it.startMs }
        val stats = stats(sorted, nowMs)

        sorted.lastOrNull { it.durationMs(nowMs) > LONG_CONTRACTION_MS }
            ?.takeIf { nowMs - it.startMs <= HOUR_MS }
            ?.let { return Assessment(Advice.CALL_NOW, "A contraction lasted longer than 2 minutes", stats) }

        val inWindow = sorted.count { nowMs - it.startMs in 0..FREQUENT_WINDOW_MS }
        if (inWindow >= FREQUENT_COUNT) {
            return Assessment(Advice.CALL_NOW, "$inWindow contractions in the last 10 minutes", stats)
        }

        val maxGapMs = rule.maxGapMinutes * 60_000L
        val lastN = sorted.takeLast(rule.regularCount)
        val regular = lastN.size == rule.regularCount &&
            lastN.zipWithNext { a, b -> b.startMs - a.startMs }.all { it <= maxGapMs } &&
            nowMs - lastN.last().startMs <= maxGapMs * 2
        if (regular) {
            return Assessment(
                Advice.CALL_FOR_ADVICE,
                "Regular contractions every ${rule.maxGapMinutes} minutes or less",
                stats,
            )
        }
        return Assessment(Advice.NONE, null, stats)
    }

    /** Plain-English line for the partner, e.g. "4 in the last hour, about every 6 min, lasting about 50 sec." */
    fun summary(stats: Stats): String {
        if (stats.countLastHour == 0) return "No contractions in the last hour."
        val parts = mutableListOf("${stats.countLastHour} in the last hour")
        stats.avgGapSec?.let { parts += "about every ${formatSec(it)}" }
        stats.avgDurationSec?.let { parts += "lasting about ${formatSec(it)}" }
        return parts.joinToString(", ") + "."
    }

    fun formatSec(sec: Int): String = when {
        sec < 90 -> "$sec sec"
        else -> "${(sec / 60.0).roundToInt()} min"
    }
}
