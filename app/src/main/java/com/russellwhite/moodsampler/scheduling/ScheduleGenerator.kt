package com.russellwhite.moodsampler.scheduling

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

data class GeneratedPrompt(
    val windowIndex: Int,
    val fireAt: Instant,
    val localDate: LocalDate,
)

/**
 * Stratified-random prompt time generation — pure JVM code, no Android imports.
 *
 * The waking span [dayStart, dayEnd) is split into `windowsPerDay` equal windows
 * and one instant is drawn uniformly within each window, so every minute of the
 * day has equal inclusion probability within its window (the stratification
 * property the tests assert). The whole day is rejection-sampled until all
 * adjacent gaps are >= minGapMinutes; after [MAX_ATTEMPTS] the gap constraint is
 * relaxed rather than looping forever on degenerate configs.
 */
class ScheduleGenerator(private val random: Random = Random.Default) {

    fun generateDay(date: LocalDate, zone: ZoneId, config: SamplingConfig): List<GeneratedPrompt> {
        require(config.windowsPerDay >= 1) { "windowsPerDay must be >= 1" }
        val startMin = config.dayStart.toSecondOfDay() / 60
        val endMin = config.dayEnd.toSecondOfDay() / 60
        require(endMin > startMin) { "dayEnd must be after dayStart" }

        val minuteOffsets = generateMinuteOffsets(
            spanMinutes = endMin - startMin,
            windows = config.windowsPerDay,
            minGap = config.minGapMinutes,
        )

        return minuteOffsets.mapIndexed { windowIndex, offset ->
            // ZonedDateTime materialization keeps DST days correct: the local
            // wall-clock time is resolved to a real instant for that date.
            val localTime = LocalTime.ofSecondOfDay((startMin + offset) * 60L)
            GeneratedPrompt(
                windowIndex = windowIndex,
                fireAt = date.atTime(localTime).atZone(zone).toInstant(),
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
