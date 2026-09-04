package com.russellwhite.moodsampler.data

import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.PromptWithSample
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.db.SampleWithActivities
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import java.time.ZoneId
import org.junit.Test

class CsvFormatTest {

    private val zone = ZoneId.of("UTC")

    private fun row(
        note: String?,
        status: PromptStatus = PromptStatus.ANSWERED,
        withSample: Boolean = true,
    ) = PromptWithSample(
        prompt = ScheduledPromptEntity(
            id = 1, scheduledAtEpochMs = 1_700_000_000_000, localDate = "2023-11-14",
            windowIndex = 0, status = status,
            answeredAtEpochMs = if (withSample) 1_700_000_120_000 else null,
        ),
        sample = if (withSample) SampleWithActivities(
            sample = SampleEntity(
                id = 1, promptId = 1, valence = 0.5f, arousal = -0.25f,
                locationId = null, socialId = null,
                note = note, createdAtEpochMs = 1_700_000_120_000,
            ),
            activityIds = listOf(10L),
        ) else null,
    )

    @Test
    fun plainRowHasAllColumns() {
        val csv = CsvExporter.toCsv(listOf(row(note = null)), mapOf(10L to "Work"), zone)
        val lines = csv.trim().split("\r\n")
        assertThat(lines).hasSize(2)
        assertThat(lines[0].split(",")).hasSize(10)
        assertThat(lines[1]).contains("ANSWERED")
        assertThat(lines[1]).contains("120")  // latency seconds
        assertThat(lines[1]).contains("Work")
    }

    @Test
    fun missedRowKeepsEmptyMeasures() {
        val csv = CsvExporter.toCsv(
            listOf(row(note = null, status = PromptStatus.MISSED, withSample = false)),
            emptyMap(), zone,
        )
        val dataLine = csv.trim().split("\r\n")[1]
        assertThat(dataLine).contains("MISSED")
        assertThat(dataLine.split(",").drop(3).all { it.isEmpty() }).isTrue()
    }

    @Test
    fun notesWithCommasQuotesNewlinesAreQuoted() {
        val nasty = "felt \"great\", honestly\nnew line"
        val csv = CsvExporter.toCsv(listOf(row(note = nasty)), emptyMap(), zone)
        assertThat(csv).contains("\"felt \"\"great\"\", honestly\nnew line\"")
        // Still exactly one header + one logical record (embedded newline is inside quotes).
        assertThat(csv.trim().split("\r\n")).hasSize(2)
    }
}
