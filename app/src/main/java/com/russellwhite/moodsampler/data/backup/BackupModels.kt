package com.russellwhite.moodsampler.data.backup

import com.russellwhite.moodsampler.data.MoodScale
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.DefaultCategories
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleActivityCrossRef
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import com.russellwhite.moodsampler.data.settings.AppSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * On-disk backup format. Deliberately separate DTOs from the Room entities so
 * the file format and the schema can evolve independently; ids are preserved
 * so restored history stays editable and cross-references stay intact.
 */
@Serializable
data class Backup(
    /** Format of this file; bump when the shape changes incompatibly. */
    val format: Int = FORMAT,
    /** Room schema version the data was exported from. */
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAtEpochMs: Long,
    val settings: SettingsDto,
    val categories: List<CategoryDto>,
    val prompts: List<PromptDto>,
    val samples: List<SampleDto>,
    val sampleActivities: List<SampleActivityDto>,
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class SettingsDto(
    val windowsPerDay: Int,
    val dayStartMinutes: Int,
    val dayEndMinutes: Int,
    val expiryMinutes: Int,
    val autoLocation: Boolean = true,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val type: String,
    val label: String,
    val sortOrder: Int,
    @SerialName("archived") val isArchived: Boolean,
    /** Activity group name (schema v3); absent in older backups. */
    val group: String? = null,
)

@Serializable
data class PromptDto(
    val id: Long,
    val scheduledAtEpochMs: Long,
    val localDate: String,
    val windowIndex: Int,
    val status: String,
    val notifiedAtEpochMs: Long? = null,
    val answeredAtEpochMs: Long? = null,
    val skipReason: String? = null,
)

@Serializable
data class SampleDto(
    val id: Long,
    val promptId: Long,
    /**
     * 0..10 since schema v6; 1..10 in v5 backups; -1.0..+1.0 in v4 and earlier.
     * Kept a JSON number either way so both read with one shape — see [toEntity].
     */
    val valence: Float,
    val arousal: Float,
    val pain: Int? = null,
    val locationId: Long? = null,
    val socialId: Long? = null,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val fixAtEpochMs: Long? = null,
    /**
     * Photo file name (schema v4). The backup carries only the name, not the image:
     * a restore on the same phone keeps its photos, elsewhere the answer has none.
     */
    val photoPath: String? = null,
)

@Serializable
data class SampleActivityDto(val sampleId: Long, val categoryId: Long)

class BackupFormatException(message: String) : Exception(message)

/** Pure encode/decode (unit-tested); validation lives here so the UI just reports messages. */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** @throws BackupFormatException when the file is not a usable backup. */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("This file isn't a Mood Sampler backup.")
        }
        if (backup.format > Backup.FORMAT) {
            throw BackupFormatException("This backup was made by a newer version of the app (format ${backup.format}).")
        }
        if (backup.schemaVersion > AppDatabase.VERSION) {
            throw BackupFormatException("This backup needs a newer app version (schema ${backup.schemaVersion}).")
        }
        return backup
    }
}

// ---- Entity <-> DTO mapping ----

fun AppSettings.toDto() = SettingsDto(windowsPerDay, dayStartMinutes, dayEndMinutes, expiryMinutes, autoLocation)

fun CategoryEntity.toDto() = CategoryDto(id, type.name, label, sortOrder, isArchived, group)
/** [schemaVersion] is the backup's: pre-v7 files still carry an active "Other" activity. */
fun CategoryDto.toEntity(schemaVersion: Int = AppDatabase.VERSION): CategoryEntity {
    val t = CategoryType.valueOf(type)
    // Pre-v3 backups carry no group: give default activities theirs, everything else OTHER.
    val g = if (t == CategoryType.ACTIVITY) group ?: DefaultCategories.groupFor(label).name else null
    // Mirrors MIGRATION_6_7.
    val oldOther = schemaVersion < 7 && t == CategoryType.ACTIVITY &&
        label.equals(DefaultCategories.OTHER_ACTIVITY, ignoreCase = true)
    return CategoryEntity(id, t, label, sortOrder, isArchived || oldOther, g)
}

fun ScheduledPromptEntity.toDto() = PromptDto(
    id, scheduledAtEpochMs, localDate, windowIndex, status.name, notifiedAtEpochMs, answeredAtEpochMs, skipReason,
)
fun PromptDto.toEntity() = ScheduledPromptEntity(
    id, scheduledAtEpochMs, localDate, windowIndex, PromptStatus.valueOf(status), notifiedAtEpochMs, answeredAtEpochMs, skipReason,
)

fun SampleEntity.toDto() = SampleDto(
    id, promptId, valence.toFloat(), arousal.toFloat(), pain, locationId, socialId, note, createdAtEpochMs,
    latitude, longitude, accuracyM, fixAtEpochMs, photoPath,
)
/** [schemaVersion] is the backup's: older files carry mood on earlier scales that need converting. */
fun SampleDto.toEntity(schemaVersion: Int): SampleEntity {
    fun mood(v: Float) = when {
        schemaVersion < 5 -> MoodScale.fromLegacy(v)
        schemaVersion == 5 -> MoodScale.fromV5(v.roundToInt())
        else -> v.roundToInt().coerceIn(MoodScale.MIN, MoodScale.MAX)
    }
    return SampleEntity(
        id, promptId, mood(valence), mood(arousal), pain, locationId, socialId, note, createdAtEpochMs,
        latitude, longitude, accuracyM, fixAtEpochMs, photoPath,
    )
}

fun SampleActivityCrossRef.toDto() = SampleActivityDto(sampleId, categoryId)
fun SampleActivityDto.toEntity() = SampleActivityCrossRef(sampleId, categoryId)
