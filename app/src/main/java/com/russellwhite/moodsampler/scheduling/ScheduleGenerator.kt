package com.russellwhite.moodsampler.scheduling

import com.russellwhite.moodsampler.scheduling.SamplingConfig.Companion.MINUTES_PER_DAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

data class GeneratedPrompt(
    val windowIndex: Int,
    val fireAt: Instant,
    /** Sampling day the prompt belongs to — a past-midnight prompt keeps the day that started it. */
    val localDate: LocalDate,
)

/**
 * Stratified-random prompt time generation — pure JVM code, no Android imports.
 *
 * The waking span [dayStart, dayStart + spanMinutes) is split into
 * `windowsPerDay` equal windows and one instant is drawn uniformly within each
 * window, so every minute of the day has equal inclusion probability within its
 * window (the stratification property the tests assert). The span may run past
 * midnight (an AM `dayEnd` such as 09:00–01:00); its tail then fires on the next
 * calendar date while still belonging to this sampling day. The whole day is
 * rejection-sampled until all adjacent gaps are >= minGapMinutes; after
 * [MAX_ATTEMPTS] the gap constraint is relaxed rather than looping forever on
 * degenerate configs.
 */
class ScheduleGenerator(private val random: Random = Random.Default) {

    fun generateDay(date: LocalDate, zone: ZoneId, config: SamplingConfig): List<GeneratedPrompt> {
        require(config.windowsPerDay >= 1) { "windowsPerDay must be >= 1" }
        val startMin = config.dayStart.toSecondOfDay() / 60
        val spanMinutes = config.spanMinutes
        // Never ask for more windows than there are minutes to draw from.
        val windows = config.windowsPerDay.coerceAtMost(spanMinutes)

        val minuteOffsets = generateMinuteOffsets(
            spanMinutes = spanMinutes,
            windows = windows,
            minGap = config.minGapMinutes,
        )

        return minuteOffsets.mapIndexed { windowIndex, offset ->
            // Wall-clock materialization keeps DST days correct: the local time
            // is resolved to a real instant on the date it actually falls on,
            // which for a past-midnight offset is the day after [date].
            val absoluteMinute = startMin + offset
            val local = date
                .plusDays((absoluteMinute / MINUTES_PER_DAY).toLong())
                .atTime(LocalTime.ofSecondOfDay((absoluteMinute % MINUTES_PER_DAY) * 60L))
            GeneratedPrompt(
                windowIndex = windowIndex,
                fireAt = local.atZone(zone).toInstant(),
                localDate = date,
            )
        }
    }

    /** One uniform minute offset per equal window, gaps >= minGap when feasible. */
    private fun generateMinuteOffsets(spanMinutes: Int, windows: Int, minGap: Int): List<Int> {
        repeat(MAX_ATTEMPTS) {
            val candidate = drawOffsets(spanMinutes, windows)
            if (candidate.zipWithNext().all { (a, b) -> b - a >= minGap }) return candidate
        }
        // Degenerate config (windows shorter than the gap): fall back to an
        // unconstrained draw so generation always terminates.
        return drawOffsets(spanMinutes, windows)
    }

    private fun drawOffsets(spanMinutes: Int, windows: Int): List<Int> =
        List(windows) { w ->
            val windowStart = spanMinutes.toLong() * w / windows
            val windowEnd = spanMinutes.toLong() * (w + 1) / windows
            (windowStart + random.nextLong(windowEnd - windowStart)).toInt()
        }

    companion object {
        private const val MAX_ATTEMPTS = 50

        /** Expiry instant for a prompt fired at [fireAt]. */
        fun expiryAt(fireAt: Instant, config: SamplingConfig): Instant =
            fireAt.plus(Duration.ofMinutes(config.expiryMinutes.toLong()))
    }
}
