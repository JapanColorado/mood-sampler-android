package dev.russell.moodsampler.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class PromptStatus {
    /** Scheduled, alarm not yet fired. */
    PENDING,

    /** Notification posted, waiting for the user. */
    NOTIFIED,

    /** User completed the capture. */
    ANSWERED,

    /** Expired unanswered (or skipped on purpose). Counts against response rate — kept for honest stats. */
    MISSED,

    /** Invalidated by a settings change, or its answer was deleted by the user. Excluded from response-rate stats. */
    CANCELLED,
}

enum class CategoryType {
    ACTIVITY,
    LOCATION,
    SOCIAL,

    /** Kind of pain (headache, back pain…), multi-select under the pain scale (schema v9). */
    PAIN,
}

@Entity(tableName = "scheduled_prompt", indices = [Index("localDate"), Index("status")])
data class ScheduledPromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Instant the prompt should fire, epoch millis. */
    val scheduledAtEpochMs: Long,
    /** ISO local date ("2026-08-15") in the zone current at generation time; query key. */
    val localDate: String,
    /** 0-based index of the sampling window within the day. */
    val windowIndex: Int,
    val status: PromptStatus,
    val notifiedAtEpochMs: Long? = null,
    val answeredAtEpochMs: Long? = null,
    /**
     * Why the user declined a MISSED prompt (see [dev.russell.moodsampler.data.SkipReason]);
     * null for prompts that simply expired.
     */
    val skipReason: String? = null,
)

@Entity(
    tableName = "sample",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledPromptEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val promptId: Long,
    /** Valence, 0 (awful) .. 10 (great); see [dev.russell.moodsampler.data.MoodScale]. */
    val valence: Int,
    /** Energy, 0 (drained) .. 10 (wired). */
    val arousal: Int,
    /** Physical pain, 0 (none) .. 10 (worst imaginable). Null only for samples recorded before the slider existed. */
    val pain: Int? = null,
    val locationId: Long?,
    val socialId: Long?,
    val note: String?,
    val createdAtEpochMs: Long,
    /** One foreground location fix taken while answering (schema v3); null when off, denied, or unavailable. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val fixAtEpochMs: Long? = null,
    /** File name of an optional photo in [dev.russell.moodsampler.data.PhotoStore] (schema v4). */
    val photoPath: String? = null,
    /** Claude's one-line summary of [note] (and photo) (schema v9); null when Claude wasn't asked. */
    val summary: String? = null,
)

/** Many-to-many: a sample can tag several activities. */
@Entity(
    tableName = "sample_activity",
    primaryKeys = ["sampleId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class SampleActivityCrossRef(
    val sampleId: Long,
    val categoryId: Long,
)

/** Many-to-many: the kinds of pain a sample names (schema v9). */
@Entity(
    tableName = "sample_pain",
    primaryKeys = ["sampleId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class SamplePainCrossRef(
    val sampleId: Long,
    val categoryId: Long,
)

/** A free-form tag Claude attached to a sample, e.g. "deadline" (schema v9). Stored lower-case. */
@Entity(
    tableName = "sample_tag",
    primaryKeys = ["sampleId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SampleTagEntity(
    val sampleId: Long,
    val tag: String,
)

/**
 * A Bluetooth device advertising nearby while the sample was answered (schema v8).
 * Only a salted hash is kept (see [dev.russell.moodsampler.data.bluetooth.DeviceFingerprint]),
 * never the raw address or name. Training data for company suggestions.
 */
@Entity(
    tableName = "sample_device",
    primaryKeys = ["sampleId", "deviceHash"],
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SampleDeviceEntity(
    val sampleId: Long,
    val deviceHash: String,
)

@Entity(tableName = "category", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: CategoryType,
    val label: String,
    val sortOrder: Int,
    /** Archived categories stay referenced by old samples but disappear from pickers. */
    val isArchived: Boolean = false,
    /** [ActivityGroup] name for ACTIVITY categories (see [activityGroup]); null for other types. */
    @ColumnInfo(name = "groupKey") val group: String? = null,
)

/** A sample plus the ids of the activities and pain types it tagged, and its free-form tags. */
data class SampleWithActivities(
    @Embedded val sample: SampleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sampleId",
        entity = SampleActivityCrossRef::class,
        projection = ["categoryId"],
    )
    val activityIds: List<Long>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sampleId",
        entity = SamplePainCrossRef::class,
        projection = ["categoryId"],
    )
    val painTypeIds: List<Long> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "sampleId",
        entity = SampleTagEntity::class,
        projection = ["tag"],
    )
    val tags: List<String> = emptyList(),
)

/** A concluded prompt (ANSWERED or MISSED) with its sample when answered. */
data class PromptWithSample(
    @Embedded val prompt: ScheduledPromptEntity,
    @Relation(parentColumn = "id", entityColumn = "promptId", entity = SampleEntity::class)
    val sample: SampleWithActivities?,
)
