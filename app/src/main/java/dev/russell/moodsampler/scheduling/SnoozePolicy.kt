package dev.russell.moodsampler.scheduling

/** Pure rules for the notification's "Remind me in 5 min" action. */
object SnoozePolicy {
    const val SNOOZE_MS = 5 * 60_000L

    /** Snoozing is offered only if the re-posted prompt would still have time left to answer. */
    fun canSnooze(nowMs: Long, expiresAtMs: Long): Boolean = expiresAtMs - nowMs > SNOOZE_MS

    /** Whole minutes left before expiry, never below 1 while the prompt is live. */
    fun remainingMinutes(nowMs: Long, expiresAtMs: Long): Int =
        ((expiresAtMs - nowMs + 59_999) / 60_000).toInt().coerceAtLeast(1)
}
