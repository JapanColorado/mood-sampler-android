package com.russellwhite.moodsampler.scheduling

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnoozePolicyTest {
    private val now = 1_000_000L
    private val minute = 60_000L

    @Test
    fun snoozeNeedsMoreThanFiveMinutesLeft() {
        assertThat(SnoozePolicy.canSnooze(now, now + 20 * minute)).isTrue()
        assertThat(SnoozePolicy.canSnooze(now, now + 5 * minute + 1)).isTrue()
        assertThat(SnoozePolicy.canSnooze(now, now + 5 * minute)).isFalse()
        assertThat(SnoozePolicy.canSnooze(now, now + 2 * minute)).isFalse()
    }

    @Test
    fun remainingMinutesRoundsUpAndFloorsAtOne() {
        assertThat(SnoozePolicy.remainingMinutes(now, now + 20 * minute)).isEqualTo(20)
        assertThat(SnoozePolicy.remainingMinutes(now, now + 14 * minute + 1)).isEqualTo(15)
        assertThat(SnoozePolicy.remainingMinutes(now, now + 10)).isEqualTo(1)
        assertThat(SnoozePolicy.remainingMinutes(now, now - minute)).isEqualTo(1)
    }
}
