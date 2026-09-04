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
        activityIds: List<Long> = listOf(10L),
        pain: Int? = 4,
        skipReason: String? = null,
    ) = PromptWithSample(
        prompt = ScheduledPromptEntity(
            id = 1, scheduledAtEpochMs = 1_700_000_000_000, localDate = "2023-11-14",
            windowIndex = 0, status = status,
            answeredAtEpochMs = if (withSample) 1_700_000_120_000 else null,
            skipReason = skipReason,
        ),
        sample = if (withSample) SampleWithActivities(
            sample = SampleEntity(
                id = 1, promptId = 1, valence = 0.5f, arousal = -0.25f, pain = pain,
                locationId = null, socialId = null,
                note = note, createdAtEpochMs = 1_700_000_120_000,
            ),
            activityIds = activityIds,
        ) else null,
    )

    private fun columns(csv: String, line: Int = 1) = csv.trim().split("\r\n")[line].split(",")

    @Test
    fun plainRowHasAllColumns() {
        val csv = CsvExporter.toCsv(listOf(row(note = null)), mapOf(10L to "Work"), zone)
        val lines = csv.trim().split("\r\n")
        assertThat(lines).hasSize(2)
        assertThat(lines[0].split(",")).hasSize(12)
        assertThat(lines[0]).isEqualTo(CsvExporter.HEADER)
        val cols = columns(csv)
        assertThat(cols).hasSize(12)
        assertThat(cols[2]).isEqualTo("ANSWERED")
        assertThat(cols[3]).isEmpty()          // skip_reason
        assertThat(cols[4]).isEqualTo("120")   // latency seconds
        assertThat(cols[7]).isEqualTo("4")     // pain
        assertThat(cols[8]).isEqualTo("Work")
    }

    @Test
    fun severalActivitiesAreJoinedWithSemicolon() {
        val csv = CsvExporter.toCsv(
            listOf(row(note = null, activityIds = listOf(10L, 11L, 99L))),
            mapOf(10L to "Work", 11L to "Music"), zone,
        )
        assertThat(columns(csv)[8]).isEqualTo("Work; Music")  // unknown id 99 dropped
    }

    @Test
    fun preMigrationSampleHasEmptyPain() {
        val csv = CsvExporter.toCsv(listOf(row(note = null, pain = null)), emptyMap(), zone)
        assertThat(columns(csv)[7]).isEmpty()
    }

    @Test
    fun skippedRowCarriesReasonAndEmptyMeasures() {
        val csv = CsvExporter.toCsv(
            listOf(row(note = null, status = PromptStatus.MISSED, withSample = false, skipReason = "busy")),
            emptyMap(), zone,
        )
        val cols = columns(csv)
        assertThat(cols[2]).isEqualTo("MISSED")
        assertThat(cols[3]).isEqualTo("busy")
        assertThat(cols.drop(4).all { it.isEmpty() }).isTrue()
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
