package dev.russell.moodsampler.data.claude

/** The Claude models offered for capture assist, picked in Settings. */
enum class ClaudeModel(val id: String, val label: String, val blurb: String) {
    OPUS_5_5("claude-opus-5-5", "Opus 5.5", "Most capable · about 1–2¢ per capture"),
    SONNET_5_5("claude-sonnet-5-5", "Sonnet 5.5", "About half the cost of Opus"),
    HAIKU_5_5("claude-haiku-5-5", "Haiku 5.5", "Fastest and cheapest · well under 1¢ per capture");

    /** Opus and Sonnet can retry a declined request on another model server-side; Haiku has no fallback. */
    val hasServerFallback: Boolean get() = this != HAIKU_5_5

    companion object {
        val DEFAULT = OPUS_5_5

        /** The model with [id], or [DEFAULT] for an unknown or missing id. */
        fun parse(id: String?): ClaudeModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
