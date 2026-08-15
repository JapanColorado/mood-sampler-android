package com.russellwhite.moodsampler.scheduling

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
)
