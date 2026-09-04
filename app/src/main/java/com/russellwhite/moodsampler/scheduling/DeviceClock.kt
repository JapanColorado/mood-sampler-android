package com.russellwhite.moodsampler.scheduling

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Wall clock in whatever time zone the phone is set to *right now*.
 *
 * [Clock.systemDefaultZone] snapshots the zone once at construction, so a
 * process-lifetime clock built at app start keeps scheduling in the zone the
 * phone was in back then. Android resets the process default when the zone
 * changes (travel, automatic time zone), and this clock re-reads it on every
 * call so schedule generation, "today" stats, charts and the CSV export all
 * follow the phone.
 */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}
