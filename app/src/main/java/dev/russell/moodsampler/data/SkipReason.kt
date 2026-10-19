package dev.russell.moodsampler.data

/**
 * Codes stored in `scheduled_prompt.skipReason` when the user declines a prompt.
 * Stored as short stable strings (exported raw in CSV); labels are for display.
 */
object SkipReason {
    const val BUSY = "busy"
    const val DRIVING = "driving"
    const val SLEEPING = "sleeping"
    const val NOT_SAFE = "not_safe"
    const val OTHER = "other"

    /** Skipped straight from the notification, without picking a reason. */
    const val NOTIFICATION = "notification"

    /** Choices offered on the capture screen's "Can't answer now" dialog. */
    val CAPTURE_CHOICES: List<Pair<String, String>> = listOf(
        BUSY to "Busy",
        DRIVING to "Driving",
        SLEEPING to "Sleeping",
        NOT_SAFE to "Not safe / private",
        OTHER to "Other",
    )

    fun label(code: String?): String = when (code) {
        null -> "Missed"
        NOTIFICATION -> "Skipped"
        else -> CAPTURE_CHOICES.firstOrNull { it.first == code }?.second?.let { "Skipped · $it" } ?: "Skipped"
    }
}
