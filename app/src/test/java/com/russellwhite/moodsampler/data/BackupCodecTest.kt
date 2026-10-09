package com.russellwhite.moodsampler.data

import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.backup.Backup
import com.russellwhite.moodsampler.data.backup.BackupCodec
import com.russellwhite.moodsampler.data.backup.BackupFormatException
import com.russellwhite.moodsampler.data.backup.CategoryDto
import com.russellwhite.moodsampler.data.backup.PromptDto
import com.russellwhite.moodsampler.data.backup.SampleActivityDto
import com.russellwhite.moodsampler.data.backup.SampleDto
import com.russellwhite.moodsampler.data.backup.SettingsDto
import com.russellwhite.moodsampler.data.backup.toEntity
import com.russellwhite.moodsampler.data.db.AppDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val sample = Backup(
        schemaVersion = AppDatabase.VERSION,
        appVersionName = "1.1",
        exportedAtEpochMs = 1_700_000_000_000,
        settings = SettingsDto(6, 540, 1320, 20),
        categories = listOf(CategoryDto(1, "ACTIVITY", "Work", 1, false, "WORK"), CategoryDto(2, "LOCATION", "Home", 1, true)),
        prompts = listOf(PromptDto(7, 1_700_000_000_000, "2023-11-14", 0, "ANSWERED", 1_700_000_000_000, 1_700_000_120_000)),
        samples = listOf(SampleDto(3, 7, 7f, 4f, 4, null, null, "note, with \"quotes\"", 1_700_000_120_000, 40.0, -105.0, 12f, 1_700_000_119_000)),
        sampleActivities = listOf(SampleActivityDto(3, 1)),
    )

    @Test
    fun roundTripPreservesEverything() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample))
        assertThat(decoded).isEqualTo(sample)
    }

    @Test
    fun unknownKeysAreTolerated() {
        val text = BackupCodec.encode(sample).replaceFirst("{", "{\"futureField\":123,")
        assertThat(BackupCodec.decode(text)).isEqualTo(sample)
    }

    @Test
    fun preV3BackupDecodesWithDefaults() {
        // Shape of a v2-era file: no groups, no coordinates, no autoLocation.
        val text = """{"format":1,"schemaVersion":2,"appVersionName":"1.1","exportedAtEpochMs":1,
            "settings":{"windowsPerDay":6,"dayStartMinutes":540,"dayEndMinutes":1320,"expiryMinutes":20},
            "categories":[{"id":1,"type":"ACTIVITY","label":"Work","sortOrder":1,"archived":false}],
            "prompts":[],
            "samples":[{"id":3,"promptId":7,"valence":0.5,"arousal":0.0,"pain":4,"createdAtEpochMs":1}],
            "sampleActivities":[]}"""
        val decoded = BackupCodec.decode(text)
        assertThat(decoded.settings.autoLocation).isTrue()
        assertThat(decoded.categories.single().group).isNull()
        assertThat(decoded.categories.single().toEntity().group).isEqualTo("WORK")
        assertThat(decoded.samples.single().latitude).isNull()
        // Slider-era valence/arousal snap onto the 0–10 scale.
        val entity = decoded.samples.single().toEntity(decoded.schemaVersion)
        assertThat(entity.valence).isEqualTo(8)
        assertThat(entity.arousal).isEqualTo(5)
    }

    @Test
    fun preV7OtherActivityIsArchivedOnImport() {
        val other = CategoryDto(9, "ACTIVITY", "Other", 30, false, "OTHER")
        assertThat(other.toEntity(schemaVersion = 6).isArchived).isTrue()
        assertThat(other.toEntity(schemaVersion = 7).isArchived).isFalse()
        assertThat(CategoryDto(4, "LOCATION", "Other", 3, false).toEntity(schemaVersion = 6).isArchived).isFalse()
    }

    @Test
    fun v5SamplesStretchOntoZeroToTen() {
        val entity = sample.samples.single().toEntity(schemaVersion = 5)
        assertThat(entity.valence).isEqualTo(MoodScale.fromV5(7))
        assertThat(entity.arousal).isEqualTo(MoodScale.fromV5(4))
    }

    @Test
    fun currentSchemaSamplesKeepTheirSteps() {
        val entity = sample.samples.single().toEntity(sample.schemaVersion)
        assertThat(entity.valence).isEqualTo(7)
        assertThat(entity.arousal).isEqualTo(4)
    }

    @Test
    fun newerSchemaIsRejected() {
        val text = BackupCodec.encode(sample.copy(schemaVersion = AppDatabase.VERSION + 1))
        val e = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }
        assertThat(e.message).contains("newer app version")
    }

    @Test
    fun newerFormatIsRejected() {
        val text = BackupCodec.encode(sample.copy(format = Backup.FORMAT + 1))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun garbageIsRejectedWithFriendlyMessage() {
        val e = assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json at all") }
        assertThat(e.message).contains("isn't a Mood Sampler backup")
    }
}
