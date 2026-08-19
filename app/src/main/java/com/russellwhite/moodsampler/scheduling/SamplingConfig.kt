package com.russellwhite.moodsampler.scheduling

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure sampling parameters — no Android types so the generator stays JVM-testable. */
data class SamplingConfig(
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(22, 0),
    val windowsPerDay: Int = 6,
    /** Minimum spacing between consecutive prompts, in minutes. */
    val minGapMinutes: Int = 15,
    /** How long a prompt stays answerable before it is marked MISSED. */
    val expiryMinutes: Int = 20,
) {
    /**
     * Length of the waking span in minutes.
     *
     * A [dayEnd] at or before [dayStart] means the span runs past midnight into
     * the small hours (09:00–01:00 is a 16-hour day), and an identical start and
     * end means a full 24 hours rather than a zero-length one.
     */
    val spanMinutes: Int
        get() {
            val raw = (dayEnd.toSecondOfDay() - dayStart.toSecondOfDay()) / 60
            return if (raw > 0) raw else raw + MINUTES_PER_DAY
        }

    /** True when the span's tail lands on the calendar day after it started. */
    val wrapsPastMidnight: Boolean get() = dayEnd <= dayStart

    /**
     * The sampling day a wall-clock moment belongs to.
     *
     * With an AM [dayEnd], 00:30 is the tail of the span that started the
     * previous morning, not the start of a new one — so the whole app (schedule
     * generation, "today" stats, per-day charts) keys off this rather than off
     * the raw calendar date.
     */
    fun samplingDayFor(local: LocalDateTime): LocalDate =
        if (wrapsPastMidnight && local.toLocalTime() < dayEnd) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }

    companion object {
        const val MINUTES_PER_DAY: Int = 24 * 60
    }
}
