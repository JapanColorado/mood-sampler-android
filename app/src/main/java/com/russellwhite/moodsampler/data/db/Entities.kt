package com.russellwhite.moodsampler.data.db

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

enum class CategoryType { ACTIVITY, LOCATION, SOCIAL }

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
     * Why the user declined a MISSED prompt (see [com.russellwhite.moodsampler.data.SkipReason]);
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
    /** Mood valence, -1.0 (unpleasant) .. +1.0 (pleasant). */
    val valence: Float,
    /** Arousal/energy, -1.0 (low) .. +1.0 (high). */
    val arousal: Float,
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

/** A sample plus the ids of the activities it tagged. */
data class SampleWithActivities(
    @Embedded val sample: SampleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sampleId",
        entity = SampleActivityCrossRef::class,
        projection = ["categoryId"],
    )
    val activityIds: List<Long>,
)

/** A concluded prompt (ANSWERED or MISSED) with its sample when answered. */
data class PromptWithSample(
    @Embedded val prompt: ScheduledPromptEntity,
    @Relation(parentColumn = "id", entityColumn = "promptId", entity = SampleEntity::class)
    val sample: SampleWithActivities?,
)
