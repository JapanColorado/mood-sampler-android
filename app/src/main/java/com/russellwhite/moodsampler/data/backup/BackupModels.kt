package com.russellwhite.moodsampler.data.backup

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
fun CategoryDto.toEntity(): CategoryEntity {
    val t = CategoryType.valueOf(type)
    // Pre-v3 backups carry no group: give default activities theirs, everything else OTHER.
    val g = if (t == CategoryType.ACTIVITY) group ?: DefaultCategories.groupFor(label).name else null
    return CategoryEntity(id, t, label, sortOrder, isArchived, g)
}

fun ScheduledPromptEntity.toDto() = PromptDto(
    id, scheduledAtEpochMs, localDate, windowIndex, status.name, notifiedAtEpochMs, answeredAtEpochMs, skipReason,
)
fun PromptDto.toEntity() = ScheduledPromptEntity(
    id, scheduledAtEpochMs, localDate, windowIndex, PromptStatus.valueOf(status), notifiedAtEpochMs, answeredAtEpochMs, skipReason,
)

fun SampleEntity.toDto() = SampleDto(
    id, promptId, valence, arousal, pain, locationId, socialId, note, createdAtEpochMs,
    latitude, longitude, accuracyM, fixAtEpochMs, photoPath,
)
fun SampleDto.toEntity() = SampleEntity(
    id, promptId, valence, arousal, pain, locationId, socialId, note, createdAtEpochMs,
    latitude, longitude, accuracyM, fixAtEpochMs, photoPath,
)

fun SampleActivityCrossRef.toDto() = SampleActivityDto(sampleId, categoryId)
fun SampleActivityDto.toEntity() = SampleActivityCrossRef(sampleId, categoryId)
