package app.birthbaby.ui

import app.birthbaby.core.Contraction
import app.birthbaby.core.LabourRules
import app.birthbaby.data.Alert

/**
 * How often the screen's clock ticks. Every second while it shows something live (a contraction
 * being timed, or an alert waiting for "On my way"), otherwise every 15 seconds to save battery.
 * The clock stops altogether while the app is out of sight.
 */
object Ticker {
    const val FAST_MS = 1000L
    const val SLOW_MS = 15_000L

    fun intervalMs(contractions: List<Contraction>, alerts: List<Alert>, nowMs: Long): Long {
        val timing = LabourRules.merged(contractions).any { it.isOngoing }
        val waiting = alerts.any { !it.resolved && nowMs - it.createdMs < 60 * 60_000L }
        return if (timing || waiting) FAST_MS else SLOW_MS
    }
}
