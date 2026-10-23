package dev.russell.moodsampler.data.claude

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertThrows
import org.junit.Test

class AssistPromptTest {

    private val options = AssistOptions(
        places = listOf("Home", "Work", "Café / restaurant"),
        activities = listOf("Work", "Reading", "Commuting", "Meals"),
        company = listOf("Alone", "Partner", "Friends"),
        painTypes = listOf("Headache", "Back"),
    )

    @Test
    fun labelsSnapToExistingOptionsCaseInsensitively() {
        val picks = AssistPrompt.clean(
            AssistReply(
                summary = "  Reading at home with a mild headache.  ",
                place = "home",
                activities = listOf("READING"),
                company = " alone ",
                painTypes = listOf("headache"),
            ),
            options,
        )
        assertThat(picks.summary).isEqualTo("Reading at home with a mild headache.")
        assertThat(picks.place).isEqualTo("Home")
        assertThat(picks.activities).containsExactly("Reading")
        assertThat(picks.company).isEqualTo("Alone")
        assertThat(picks.painTypes).containsExactly("Headache")
    }

    @Test
    fun newLabelsAreKeptButCapped() {
        val picks = AssistPrompt.clean(
            AssistReply(
                place = "Grandma's  house",
                activities = listOf("Reading", "Gardening", "Knitting", "Baking"),
                painTypes = listOf("Sciatica", "Shin splints"),
            ),
            options,
        )
        assertThat(picks.place).isEqualTo("Grandma's house")  // whitespace collapsed
        assertThat(picks.activities).containsExactly("Reading", "Gardening", "Knitting").inOrder()
        assertThat(picks.painTypes).containsExactly("Sciatica")
    }

    @Test
    fun blankOverlongAndOtherLabelsAreDropped() {
        val picks = AssistPrompt.clean(
            AssistReply(place = "  ", company = "Other", activities = listOf("x".repeat(41), "other", "Meals", "meals")),
            options,
        )
        assertThat(picks.place).isNull()
        assertThat(picks.company).isNull()
        assertThat(picks.activities).containsExactly("Meals")
    }

    @Test
    fun tagsAreNormalisedAndSkipWhatTheFieldsAlreadySay() {
        val picks = AssistPrompt.clean(
            AssistReply(activities = listOf("Reading"), tags = listOf("#Coffee", "reading", " Deadline ", "coffee", "")),
            options,
        )
        assertThat(picks.tags).containsExactly("coffee", "deadline").inOrder()
    }

    @Test
    fun emptySummaryBecomesNull() {
        assertThat(AssistPrompt.clean(AssistReply(summary = " "), options).summary).isNull()
    }

    @Test
    fun parseReadsTheSchemaShapeIncludingNulls() {
        val reply = AssistPrompt.parse(
            """{"summary":"Commuting, tired.","place":null,"activities":["Commuting"],"company":null,
                "pain_types":[],"tags":["rain"]}"""
        )
        assertThat(reply).isEqualTo(
            AssistReply(summary = "Commuting, tired.", activities = listOf("Commuting"), tags = listOf("rain"))
        )
    }

    @Test
    fun parseRejectsMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) { AssistPrompt.parse("not json") }
    }

    @Test
    fun schemaRequiresEveryProperty() {
        @Suppress("UNCHECKED_CAST")
        val properties = AssistPrompt.SCHEMA["properties"] as Map<String, Any>
        assertThat(AssistPrompt.SCHEMA["required"] as List<*>).containsExactlyElementsIn(properties.keys)
        assertThat(AssistPrompt.SCHEMA["additionalProperties"]).isEqualTo(false)
        assertThat(properties["place"]).isNotNull()
    }

    @Test
    fun userTextCarriesTimeOptionsGuessesAndNarration() {
        val text = AssistPrompt.userText(
            narration = "  Stuck on the train again.  ",
            options = options,
            localTime = ZonedDateTime.of(2026, 10, 20, 8, 5, 0, 0, ZoneId.of("UTC")),
            guessedPlace = "Work",
            guessedCompany = null,
            hasPhoto = false,
        )
        assertThat(text).contains("Local time: Tuesday 08:05")
        assertThat(text).contains("Places: Home; Work; Café / restaurant")
        assertThat(text).contains("Pain types: Headache; Back")
        assertThat(text).contains("Phone guess for place: Work")
        assertThat(text).doesNotContain("Phone guess for company")
        assertThat(text).contains("<narration>\nStuck on the train again.\n</narration>")
        assertThat(text).doesNotContain("photo")
    }

    @Test
    fun photoOnlyCaptureSaysSo() {
        val text = AssistPrompt.userText("", options, ZonedDateTime.now(ZoneId.of("UTC")), null, null, hasPhoto = true)
        assertThat(text).contains("They attached the photo above.")
        assertThat(text).contains("go by the photo")
        assertThat(text).doesNotContain("<narration>")
    }

    @Test
    fun unknownModelIdFallsBackToDefault() {
        assertThat(ClaudeModel.parse("claude-sonnet-5-5")).isEqualTo(ClaudeModel.SONNET_5_5)
        assertThat(ClaudeModel.parse("retired-model")).isEqualTo(ClaudeModel.DEFAULT)
        assertThat(ClaudeModel.parse(null)).isEqualTo(ClaudeModel.DEFAULT)
        assertThat(ClaudeModel.HAIKU_5_5.hasServerFallback).isFalse()
    }
}
