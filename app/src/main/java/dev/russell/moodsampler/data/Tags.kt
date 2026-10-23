package dev.russell.moodsampler.data

/** Free-form sample tags ("deadline", "coffee"): short, lower-case, de-duplicated. */
object Tags {
    const val MAX_LENGTH = 32
    const val MAX_PER_SAMPLE = 8

    /** Trimmed, lower-cased, inner whitespace collapsed; blanks and repeats dropped; capped in length and count. */
    fun normalize(tags: List<String>): List<String> =
        tags.asSequence()
            .map { it.trim().lowercase().replace(WHITESPACE, " ").removePrefix("#").take(MAX_LENGTH).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PER_SAMPLE)
            .toList()

    private val WHITESPACE = Regex("\\s+")
}
