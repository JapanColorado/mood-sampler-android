package dev.russell.moodsampler.data

import com.google.common.truth.Truth.assertThat
import dev.russell.moodsampler.data.backup.Backup
import dev.russell.moodsampler.data.backup.BackupCodec
import dev.russell.moodsampler.data.backup.BackupFormatException
import dev.russell.moodsampler.data.backup.CategoryDto
import dev.russell.moodsampler.data.backup.PromptDto
import dev.russell.moodsampler.data.backup.SampleActivityDto
import dev.russell.moodsampler.data.backup.SampleDeviceDto
import dev.russell.moodsampler.data.backup.SampleDto
import dev.russell.moodsampler.data.backup.SamplePainDto
import dev.russell.moodsampler.data.backup.SampleTagDto
import dev.russell.moodsampler.data.backup.SettingsDto
import dev.russell.moodsampler.data.backup.missingDefaultCategories
import dev.russell.moodsampler.data.backup.toEntity
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.db.DefaultCategories
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val sample = Backup(
        schemaVersion = AppDatabase.VERSION,
        appVersionName = "1.1",
        exportedAtEpochMs = 1_700_000_000_000,
        settings = SettingsDto(6, 540, 1320, 20, autoCompany = false, deviceSalt = "salt", claudeModel = "claude-haiku-5-5"),
        categories = listOf(
            CategoryDto(1, "ACTIVITY", "Work", 1, false, "WORK"),
            CategoryDto(2, "LOCATION", "Home", 1, true),
            CategoryDto(5, "PAIN", "Headache", 1, false),
        ),
        prompts = listOf(PromptDto(7, 1_700_000_000_000, "2023-11-14", 0, "ANSWERED", 1_700_000_000_000, 1_700_000_120_000)),
        samples = listOf(
            SampleDto(
                3, 7, 7f, 4f, 4, null, null, "note, with \"quotes\"", 1_700_000_120_000, 40.0, -105.0, 12f, 1_700_000_119_000,
                summary = "Working, a bit of a headache.",
            ),
        ),
        sampleActivities = listOf(SampleActivityDto(3, 1)),
        sampleDevices = listOf(SampleDeviceDto(3, "0123456789abcdef")),
        samplePainTypes = listOf(SamplePainDto(3, 5)),
        sampleTags = listOf(SampleTagDto(3, "deadline")),
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
        assertThat(decoded.settings.autoCompany).isTrue()
        assertThat(decoded.settings.deviceSalt).isNull()
        assertThat(decoded.sampleDevices).isEmpty()
        assertThat(decoded.samplePainTypes).isEmpty()
        assertThat(decoded.sampleTags).isEmpty()
        assertThat(decoded.settings.claudeModel).isNull()
        assertThat(decoded.samples.single().summary).isNull()
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
    fun preV9BackupGetsTheDefaultPainTypes() {
        val old = sample.copy(schemaVersion = 8, categories = sample.categories.filter { it.type != "PAIN" })
        val added = old.missingDefaultCategories()
        assertThat(added.map { it.label }).containsExactlyElementsIn(DefaultCategories.PAIN_TYPES).inOrder()
        assertThat(added.all { it.type == CategoryType.PAIN && it.id == 0L }).isTrue()
        assertThat(added.map { it.sortOrder }).isEqualTo((1..DefaultCategories.PAIN_TYPES.size).toList())
        // Current backups already carry their own pain types.
        assertThat(sample.missingDefaultCategories()).isEmpty()
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
