package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import dev.russell.moodsampler.data.db.PromptStatus
import dev.russell.moodsampler.data.db.PromptWithSample
import dev.russell.moodsampler.data.db.SampleEntity
import dev.russell.moodsampler.data.db.SampleWithActivities
import dev.russell.moodsampler.data.db.ScheduledPromptEntity
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
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyM: Float? = null,
        photoPath: String? = null,
    ) = PromptWithSample(
        prompt = ScheduledPromptEntity(
            id = 1, scheduledAtEpochMs = 1_700_000_000_000, localDate = "2023-11-14",
            windowIndex = 0, status = status,
            answeredAtEpochMs = if (withSample) 1_700_000_120_000 else null,
            skipReason = skipReason,
        ),
        sample = if (withSample) SampleWithActivities(
            sample = SampleEntity(
                id = 1, promptId = 1, valence = 7, arousal = 4, pain = pain,
                locationId = null, socialId = null,
                note = note, createdAtEpochMs = 1_700_000_120_000,
                latitude = latitude, longitude = longitude, accuracyM = accuracyM,
                photoPath = photoPath,
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
        assertThat(lines[0].split(",")).hasSize(16)
        assertThat(lines[0]).isEqualTo(CsvExporter.HEADER)
        val cols = columns(csv)
        assertThat(cols).hasSize(16)
        assertThat(cols[2]).isEqualTo("ANSWERED")
        assertThat(cols[3]).isEmpty()          // skip_reason
        assertThat(cols[4]).isEqualTo("120")   // latency seconds
        assertThat(cols[5]).isEqualTo("7")     // valence
        assertThat(cols[6]).isEqualTo("4")     // arousal
        assertThat(cols[7]).isEqualTo("4")     // pain
        assertThat(cols[8]).isEqualTo("Work")
        assertThat(cols.drop(12).all { it.isEmpty() }).isTrue()  // no fix, no photo
    }

    @Test
    fun photoFileNameIsExportedInLastColumn() {
        val csv = CsvExporter.toCsv(listOf(row(note = null, photoPath = "abc.jpg")), emptyMap(), zone)
        assertThat(columns(csv)[15]).isEqualTo("abc.jpg")
    }

    @Test
    fun locationFixIsExportedInTrailingColumns() {
        val csv = CsvExporter.toCsv(
            listOf(row(note = null, latitude = 40.0125, longitude = -105.25, accuracyM = 12.5f)),
            mapOf(10L to "Work"), zone,
        )
        val cols = columns(csv)
        assertThat(cols[12]).isEqualTo("40.0125")
        assertThat(cols[13]).isEqualTo("-105.25")
        assertThat(cols[14]).isEqualTo("12.5")
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
