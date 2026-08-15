package com.russellwhite.moodsampler.scheduling

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Test

class ScheduleGeneratorTest {

    private val zone = ZoneId.of("America/New_York")
    private val config = SamplingConfig(
        dayStart = LocalTime.of(9, 0),
        dayEnd = LocalTime.of(22, 0),
        windowsPerDay = 6,
        minGapMinutes = 15,
    )

    private fun localMinutes(prompt: GeneratedPrompt): Int {
        val local = ZonedDateTime.ofInstant(prompt.fireAt, zone).toLocalTime()
        return local.toSecondOfDay() / 60
    }

    @Test
    fun generatesOnePromptPerWindow_insideItsOwnWindow() {
        val gen = ScheduleGenerator(Random(42))
        repeat(500) { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays(day.toLong())
            val prompts = gen.generateDay(date, zone, config)
            assertThat(prompts).hasSize(6)
            assertThat(prompts.map { it.windowIndex }).isEqualTo((0..5).toList())

            val spanStart = 9 * 60
            val windowLen = (22 - 9) * 60 / 6.0
            prompts.forEach { p ->
                val minute = localMinutes(p)
                val lo = spanStart + (windowLen * p.windowIndex).toInt()
                val hi = spanStart + (windowLen * (p.windowIndex + 1)).toInt()
                assertThat(minute).isAtLeast(lo)
                assertThat(minute).isLessThan(hi)
            }
        }
    }

    @Test
    fun nothingOutsideWakingHours() {
        val gen = ScheduleGenerator(Random(7))
        repeat(1000) { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays(day.toLong())
            gen.generateDay(date, zone, config).forEach { p ->
                val minute = localMinutes(p)
                assertThat(minute).isAtLeast(9 * 60)
                assertThat(minute).isLessThan(22 * 60)
            }
        }
    }

    @Test
    fun adjacentGapsRespectMinGap() {
        val gen = ScheduleGenerator(Random(123))
        repeat(1000) { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays(day.toLong())
            val minutes = gen.generateDay(date, zone, config).map { localMinutes(it) }
            minutes.zipWithNext().forEach { (a, b) ->
                assertThat(b - a).isAtLeast(config.minGapMinutes)
            }
        }
    }

    @Test
    fun timesAreStrictlyIncreasingInstants() {
        val gen = ScheduleGenerator(Random(5))
        repeat(200) { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays(day.toLong())
            val instants = gen.generateDay(date, zone, config).map { it.fireAt }
            instants.zipWithNext().forEach { (a, b) -> assertThat(b).isGreaterThan(a) }
        }
    }

    @Test
    fun uniformityWithinWindows_roughlyFlatHistogram() {
        // Loose smoke test: over many days, first-window draws should cover the
        // window without piling onto a few minutes.
        val gen = ScheduleGenerator(Random(99))
        val counts = IntArray(130)  // window 0 = minutes [540, 670)
        repeat(10_000) { day ->
            val date = LocalDate.of(2026, 1, 1).plusDays((day % 365).toLong())
            val first = gen.generateDay(date, zone, config).first()
            counts[localMinutes(first) - 540]++
        }
        val expected = 10_000.0 / 130
        // Every minute should appear, and no minute should deviate wildly.
        counts.forEach { c ->
            assertThat(c).isGreaterThan(0)
            assertThat(c).isLessThan((expected * 3).toInt())
        }
    }

    @Test
    fun dstTransitionDaysProduceValidMonotonicInstants() {
        val gen = ScheduleGenerator(Random(2))
        // US DST 2026: spring forward Mar 8, fall back Nov 1.
        listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1)).forEach { date ->
            repeat(100) {
                val instants = gen.generateDay(date, zone, config).map { it.fireAt }
                assertThat(instants).hasSize(6)
                instants.zipWithNext().forEach { (a, b) -> assertThat(b).isGreaterThan(a) }
            }
        }
    }

    @Test
    fun degenerateConfigTerminates() {
        // 12 windows over 1 hour with a 15-minute gap is infeasible; the
        // generator must relax the gap instead of hanging.
        val tight = SamplingConfig(
            dayStart = LocalTime.of(9, 0),
            dayEnd = LocalTime.of(10, 0),
            windowsPerDay = 12,
            minGapMinutes = 15,
        )
        val prompts = ScheduleGenerator(Random(1)).generateDay(LocalDate.of(2026, 6, 1), zone, tight)
        assertThat(prompts).hasSize(12)
    }

    @Test
    fun differentSeedsGiveDifferentSchedules() {
        val date = LocalDate.of(2026, 6, 1)
        val a = ScheduleGenerator(Random(1)).generateDay(date, zone, config)
        val b = ScheduleGenerator(Random(2)).generateDay(date, zone, config)
        assertThat(a.map { it.fireAt }).isNotEqualTo(b.map { it.fireAt })
    }
}
