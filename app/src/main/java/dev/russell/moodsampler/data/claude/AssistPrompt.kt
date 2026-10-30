package dev.russell.moodsampler.data.claude

import dev.russell.moodsampler.data.Tags
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Labels of the options the user can currently pick, per capture section. */
data class AssistOptions(
    val places: List<String>,
    val activities: List<String>,
    val company: List<String>,
    val painTypes: List<String>,
)

/** What Claude returns, shaped by [AssistPrompt.SCHEMA]. */
@Serializable
data class AssistReply(
    val summary: String = "",
    val place: String? = null,
    val activities: List<String> = emptyList(),
    val company: String? = null,
    @SerialName("pain_types") val painTypes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

/**
 * A cleaned-up [AssistReply]: every label is either an existing option's exact label
 * or a (capped) new one, and tags don't repeat what the other fields already say.
 */
data class AssistPicks(
    val summary: String?,
    val place: String?,
    val activities: List<String>,
    val company: String?,
    val painTypes: List<String>,
    val tags: List<String>,
)

/**
 * Pure prompt building and reply cleanup for capture assist (unit-tested). Claude fills
 * the context — place, activities, company, pain types — plus a summary and tags; the
 * mood scales and the pain number always stay the user's own ratings.
 */
object AssistPrompt {

    const val SYSTEM = """You help someone fill in a mood-journal entry. The app prompted them at a random moment, and they described what they're doing, thinking and feeling, sometimes with a photo. Turn that into the entry's fields.

- summary: one short sentence (at most 20 words) in the first person, saying what they're doing and how they feel. Plain and factual, no advice.
- place, activities, company, pain_types: choose from the listed options and copy their labels exactly. Use a new label only when nothing listed fits and the narration or photo clearly says so; keep it to 1–3 words in the same style as the listed ones. Leave a field null or empty when it isn't stated or clearly implied. Don't guess. (company has its own, looser rule below.)
- place is where they are right now. activities can be several things they're doing at once.
- company is who they're with right now. Fill it on any hint in the narration or photo, even in passing ("my wife just said", "dinner with the kids", people sharing the scene in a photo), using the closest option (a sister is family, a boss is a coworker). Use "Alone" if they suggest nobody is around. Someone they're only thinking about or texting doesn't count. If they're with several people, choose who they're mainly with. Leave it null only when there's no hint either way.
- pain_types: only when they mention physical pain or discomfort.
- tags: 0–5 short lowercase tags for notable themes the other fields don't already capture (for example "deadline", "coffee", "rain", "argument").
- Never rate mood, energy or pain intensity; the person does that themselves.
- A place marked as a phone guess came from GPS: keep it unless the narration says otherwise.
- A company marked as a phone guess came from Bluetooth and is only a weak signal: replace it on any hint from the narration or photo."""

    // Declared before SCHEMA, which reads them during object initialisation.
    private val NULLABLE_STRING = mapOf("anyOf" to listOf(mapOf("type" to "string"), mapOf("type" to "null")))
    private val STRING_ARRAY = mapOf("type" to "array", "items" to mapOf("type" to "string"))

    /** JSON schema for structured output; every field required, nullable where the reply may be empty. */
    val SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "summary" to mapOf("type" to "string"),
            "place" to NULLABLE_STRING,
            "activities" to STRING_ARRAY,
            "company" to NULLABLE_STRING,
            "pain_types" to STRING_ARRAY,
            "tags" to STRING_ARRAY,
        ),
        "required" to listOf("summary", "place", "activities", "company", "pain_types", "tags"),
    )

    /** The user turn's text: local time, the option lists, the phone's guesses and the narration. */
    fun userText(
        narration: String,
        options: AssistOptions,
        localTime: ZonedDateTime,
        guessedPlace: String?,
        guessedCompany: String?,
        hasPhoto: Boolean,
    ): String = buildString {
        appendLine("Local time: ${localTime.format(TIME)}")
        appendLine()
        appendLine("Places: ${options.places.joinToString("; ")}")
        appendLine("Activities: ${options.activities.joinToString("; ")}")
        appendLine("Company: ${options.company.joinToString("; ")}")
        appendLine("Pain types: ${options.painTypes.joinToString("; ")}")
        if (guessedPlace != null) appendLine("Phone guess for place: $guessedPlace")
        if (guessedCompany != null) appendLine("Phone guess for company: $guessedCompany")
        appendLine()
        if (hasPhoto) appendLine("They attached the photo above.")
        val text = narration.trim()
        if (text.isEmpty()) {
            append("They didn't write anything; go by the photo.")
        } else {
            appendLine("Their narration:")
            appendLine("<narration>")
            appendLine(text)
            append("</narration>")
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws kotlinx.serialization.SerializationException on a malformed reply. */
    fun parse(text: String): AssistReply = json.decodeFromString(AssistReply.serializer(), text)

    /** Snap labels onto existing options, cap how many new ones a reply may create, and tidy tags. */
    fun clean(reply: AssistReply, options: AssistOptions): AssistPicks {
        val place = reply.place?.let { snap(it, options.places) }
        val company = reply.company?.let { snap(it, options.company) }
        val activities = snapAll(reply.activities, options.activities, MAX_NEW_ACTIVITIES).take(MAX_ACTIVITIES)
        val painTypes = snapAll(reply.painTypes, options.painTypes, MAX_NEW_PAIN_TYPES)
        val taken = (listOfNotNull(place, company) + activities + painTypes).map { it.lowercase() }.toSet()
        return AssistPicks(
            summary = reply.summary.trim().take(MAX_SUMMARY).ifEmpty { null },
            place = place,
            activities = activities,
            company = company,
            painTypes = painTypes,
            tags = Tags.normalize(reply.tags).filter { it !in taken },
        )
    }

    /** [label] as an existing option's spelling, a new label, or null when unusable. */
    private fun snap(label: String, options: List<String>): String? {
        val trimmed = label.trim().replace(WHITESPACE, " ")
        if (trimmed.isEmpty() || trimmed.length > MAX_LABEL || trimmed.equals(OTHER, ignoreCase = true)) return null
        return options.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    private fun snapAll(labels: List<String>, options: List<String>, maxNew: Int): List<String> {
        var newCount = 0
        return labels.mapNotNull { snap(it, options) }
            .distinctBy { it.lowercase() }
            .filter { label ->
                val isNew = options.none { it.equals(label, ignoreCase = true) }
                if (isNew) newCount++
                !isNew || newCount <= maxNew
            }
    }

    private val TIME = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.ENGLISH)
    private val WHITESPACE = Regex("\\s+")
    private const val OTHER = "Other"
    private const val MAX_LABEL = 40
    private const val MAX_SUMMARY = 240
    private const val MAX_ACTIVITIES = 6
    private const val MAX_NEW_ACTIVITIES = 2
    private const val MAX_NEW_PAIN_TYPES = 1
}
