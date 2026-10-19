package dev.russell.moodsampler.data

/** 0–10 numeric pain rating scale (NRS) helpers. */
object PainScale {
    const val MIN = 0
    const val MAX = 10

    fun descriptor(pain: Int): String = when (pain.coerceIn(MIN, MAX)) {
        0 -> "None"
        in 1..3 -> "Mild"
        in 4..6 -> "Moderate"
        else -> "Severe"
    }
}
