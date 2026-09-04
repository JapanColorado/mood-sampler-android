package com.russellwhite.moodsampler.scheduling

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class DeviceClockTest {

    private lateinit var original: TimeZone

    @Before
    fun saveDefault() {
        original = TimeZone.getDefault()
    }

    @After
    fun restoreDefault() {
        TimeZone.setDefault(original)
    }

    @Test
    fun zoneTracksTheProcessDefaultInsteadOfSnapshottingIt() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        val snapshot = Clock.systemDefaultZone()
        assertThat(DeviceClock.zone).isEqualTo(ZoneId.of("America/Denver"))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        // The stock clock is stuck in the zone it was built in; ours follows.
        assertThat(snapshot.zone).isEqualTo(ZoneId.of("America/Denver"))
        assertThat(DeviceClock.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun localTimeIsResolvedInTheCurrentZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val utc = LocalDateTime.now(DeviceClock)
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-9")) // fixed +09:00, no DST
        val plusNine = LocalDateTime.now(DeviceClock)
        val diffMinutes = java.time.Duration.between(utc, plusNine).toMinutes()
        assertThat(diffMinutes).isIn(539L..541L)
    }
}
