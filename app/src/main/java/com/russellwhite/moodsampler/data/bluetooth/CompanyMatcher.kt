package com.russellwhite.moodsampler.data.bluetooth

import com.russellwhite.moodsampler.data.db.DeviceTag

/**
 * Suggests who you're with from the devices nearby, learned from past answers.
 * A device only speaks up once it has been seen with a past answer a few times
 * and mostly with one company option — clearly more often than that option's
 * usual share, so your own earbuds (there for every answer) say nothing. Each such
 * device votes for its option, weighted by how often it was seen with it; most
 * weight wins. No votes, no suggestion. Pure Kotlin so it's unit-testable.
 */
object CompanyMatcher {
    /** Past answers a device must appear in before it counts. */
    const val MIN_SIGHTINGS = 2

    /** Share of a device's sightings that must agree on one option. */
    const val MIN_SHARE = 0.6

    /** How much more often than usual that option must be when the device is around. */
    const val MIN_LIFT = 1.25

    fun suggest(nearby: Set<String>, tags: List<DeviceTag>): Long? {
        if (nearby.isEmpty()) return null
        val companyOf = tags.associate { it.sampleId to it.socialId }
        if (companyOf.isEmpty()) return null
        val baseShare = companyOf.values.groupingBy { it }.eachCount()
            .mapValues { (_, n) -> n.toDouble() / companyOf.size }

        val votes = mutableMapOf<Long, Int>()
        tags.filter { it.deviceHash in nearby }
            .groupBy { it.deviceHash }
            .values
            .forEach { sightings ->
                if (sightings.size < MIN_SIGHTINGS) return@forEach
                val (top, n) = sightings.groupingBy { it.socialId }.eachCount().maxBy { it.value }
                val share = n.toDouble() / sightings.size
                if (share >= MIN_SHARE && share >= MIN_LIFT * baseShare.getValue(top)) votes.merge(top, n, Int::plus)
            }
        return votes.maxByOrNull { it.value }?.key
    }
}
