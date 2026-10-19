package dev.russell.moodsampler.data

import kotlin.math.roundToInt

/** The 0–10 scale valence and energy (arousal) are answered and stored on, matching [PainScale]. */
object MoodScale {
    const val MIN = 0
    const val MAX = 10

    /**
     * Nearest step for a value from the old -1.0..+1.0 slider (schema v4 and earlier,
     * and backups made then): -1 → 0, 0 → 5, +1 → 10.
     * [dev.russell.moodsampler.data.db.MIGRATION_4_6]'s SQL mirrors this.
     */
    fun fromLegacy(value: Float): Int = ((value.coerceIn(-1f, 1f) + 1f) / 2f * (MAX - MIN)).roundToInt() + MIN

    /**
     * Nearest step for a schema-v5 value, which was on a 1–10 scale: 1 → 0, 10 → 10.
     * [dev.russell.moodsampler.data.db.MIGRATION_5_6]'s SQL mirrors this.
     */
    fun fromV5(step: Int): Int = ((step.coerceIn(1, 10) - 1) * 10 / 9f).roundToInt()
}
