package com.russellwhite.moodsampler.data

import androidx.room.withTransaction
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.SampleActivityCrossRef
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.location.LocationFix
import com.russellwhite.moodsampler.data.location.PlaceMatcher

class SampleRepository(private val db: AppDatabase) {

    /**
     * Persist an answered prompt atomically: the sample row, its activity tags and
     * the prompt's ANSWERED status must land together. Returns false if the prompt
     * was no longer answerable (already MISSED/CANCELLED) — nothing is saved.
     */
    suspend fun saveAnswer(
        sample: SampleEntity,
        activityIds: List<Long> = emptyList(),
        answeredAtMs: Long,
    ): Boolean =
        db.withTransaction {
            val updated = db.scheduledPromptDao().markAnswered(sample.promptId, answeredAtMs)
            if (updated == 1) {
                val sampleId = db.sampleDao().insert(sample.copy(createdAtEpochMs = answeredAtMs))
                insertRefs(sampleId, activityIds)
                true
            } else {
                false
            }
        }

    /** Edit an existing answer in place; activity tags are replaced wholesale. */
    suspend fun updateAnswer(sample: SampleEntity, activityIds: List<Long>) =
        db.withTransaction {
            db.sampleDao().update(sample)
            db.sampleDao().deleteActivities(sample.id)
            insertRefs(sample.id, activityIds)
        }

    /**
     * Remove an answer. The prompt becomes CANCELLED (excluded from response-rate
     * stats): it *was* answered, so counting it as missed would penalise the user
     * for curating their data.
     */
    suspend fun deleteAnswer(promptId: Long): Boolean =
        db.withTransaction {
            db.sampleDao().deleteByPromptId(promptId)  // cascade clears sample_activity
            db.scheduledPromptDao().cancelAnswered(promptId) == 1
        }

    /** Place id past answers near [fix] point to, or null when nothing is close enough. */
    suspend fun suggestPlace(fix: LocationFix): Long? =
        PlaceMatcher.suggest(fix, db.sampleDao().recentGeoTags())

    private suspend fun insertRefs(sampleId: Long, activityIds: List<Long>) {
        val refs = activityIds.distinct().map { SampleActivityCrossRef(sampleId, it) }
        if (refs.isNotEmpty()) db.sampleDao().insertActivities(refs)
    }
}
