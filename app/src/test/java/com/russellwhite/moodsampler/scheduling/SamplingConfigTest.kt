package com.russellwhite.moodsampler.scheduling

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class SamplingConfigTest {

    private fun config(start: LocalTime, end: LocalTime) =
        SamplingConfig(dayStart = start, dayEnd = end)

    @Test
    fun sameDaySpanIsSimpleDifference() {
        val c = config(LocalTime.of(9, 0), LocalTime.of(22, 0))
        assertThat(c.spanMinutes).isEqualTo(13 * 60)
        assertThat(c.wrapsPastMidnight).isFalse()
    }

    @Test
    fun amEndTimeWrapsPastMidnight() {
        val c = config(LocalTime.of(9, 0), LocalTime.of(1, 0))
        assertThat(c.spanMinutes).isEqualTo(16 * 60)
        assertThat(c.wrapsPastMidnight).isTrue()
    }

    @Test
    fun midnightEndIsAFullEveningNotAWrap() {
        val c = config(LocalTime.of(9, 0), LocalTime.MIDNIGHT)
        assertThat(c.spanMinutes).isEqualTo(15 * 60)
        // 00:00 ends the day exactly, so no wall-clock time falls in a tail.
        assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, 0, 30)))
            .isEqualTo(LocalDate.of(2026, 8, 18))
    }

    @Test
    fun identicalStartAndEndMeansFullDay() {
        val c = config(LocalTime.of(9, 0), LocalTime.of(9, 0))
        assertThat(c.spanMinutes).isEqualTo(24 * 60)
    }

    @Test
    fun tailMinutesBelongToThePreviousSamplingDay() {
        val c = config(LocalTime.of(9, 0), LocalTime.of(1, 0))
        val day = LocalDate.of(2026, 8, 18)

        assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, 0, 30)))
            .isEqualTo(day.minusDays(1))
        assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, 1, 0))).isEqualTo(day)
        assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, 9, 30))).isEqualTo(day)
        assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, 23, 59))).isEqualTo(day)
    }

    @Test
    fun nonWrappingConfigAlwaysUsesTheCalendarDate() {
        val c = config(LocalTime.of(9, 0), LocalTime.of(22, 0))
        val day = LocalDate.of(2026, 8, 18)
        listOf(0, 3, 9, 21, 23).forEach { hour ->
            assertThat(c.samplingDayFor(LocalDateTime.of(2026, 8, 18, hour, 0))).isEqualTo(day)
        }
    }
}
