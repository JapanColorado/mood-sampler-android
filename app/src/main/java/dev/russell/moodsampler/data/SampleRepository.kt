package dev.russell.moodsampler.data

import androidx.room.withTransaction
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.bluetooth.CompanyMatcher
import dev.russell.moodsampler.data.db.SampleActivityCrossRef
import dev.russell.moodsampler.data.db.SampleDeviceEntity
import dev.russell.moodsampler.data.db.SampleEntity
import dev.russell.moodsampler.data.db.SamplePainCrossRef
import dev.russell.moodsampler.data.db.SampleTagEntity
import dev.russell.moodsampler.data.location.LocationFix
import dev.russell.moodsampler.data.location.PlaceMatcher

class SampleRepository(private val db: AppDatabase, private val photos: PhotoStore) {

    /**
     * Persist an answered prompt atomically: the sample row, its activities, pain types and
     * free-form tags, the hashed nearby devices and the prompt's ANSWERED status must land
     * together. Returns false if the prompt was no longer answerable (already MISSED/CANCELLED)
     * — nothing is saved.
     */
    suspend fun saveAnswer(
        sample: SampleEntity,
        activityIds: List<Long> = emptyList(),
        painTypeIds: List<Long> = emptyList(),
        tags: List<String> = emptyList(),
        deviceHashes: Collection<String> = emptyList(),
        answeredAtMs: Long,
    ): Boolean =
        db.withTransaction {
            val updated = db.scheduledPromptDao().markAnswered(sample.promptId, answeredAtMs)
            if (updated == 1) {
                val sampleId = db.sampleDao().insert(sample.copy(createdAtEpochMs = answeredAtMs))
                insertRefs(sampleId, activityIds, painTypeIds, tags)
                if (deviceHashes.isNotEmpty()) {
                    db.sampleDao().insertDevices(deviceHashes.distinct().map { SampleDeviceEntity(sampleId, it) })
                }
                true
            } else {
                false
            }
        }

    /**
     * Edit an existing answer in place; activities, pain types and tags are replaced
     * wholesale and a photo the edit dropped or replaced is deleted from disk.
     */
    suspend fun updateAnswer(
        sample: SampleEntity,
        activityIds: List<Long>,
        painTypeIds: List<Long> = emptyList(),
        tags: List<String> = emptyList(),
    ) {
        val previousPhoto = db.withTransaction {
            val previous = db.sampleDao().photoPathByPromptId(sample.promptId)
            db.sampleDao().update(sample)
            db.sampleDao().deleteActivities(sample.id)
            db.sampleDao().deletePainTypes(sample.id)
            db.sampleDao().deleteTags(sample.id)
            insertRefs(sample.id, activityIds, painTypeIds, tags)
            previous
        }
        if (previousPhoto != null && previousPhoto != sample.photoPath) photos.delete(previousPhoto)
    }

    /**
     * Remove an answer. The prompt becomes CANCELLED (excluded from response-rate
     * stats): it *was* answered, so counting it as missed would penalise the user
     * for curating their data.
     */
    suspend fun deleteAnswer(promptId: Long): Boolean {
        val (photo, ok) = db.withTransaction {
            val photo = db.sampleDao().photoPathByPromptId(promptId)
            db.sampleDao().deleteByPromptId(promptId)  // cascade clears the sample's join and tag rows
            photo to (db.scheduledPromptDao().cancelAnswered(promptId) == 1)
        }
        photos.delete(photo)
        return ok
    }

    /** Place id past answers near [fix] point to, or null when nothing is close enough. */
    suspend fun suggestPlace(fix: LocationFix): Long? =
        PlaceMatcher.suggest(fix, db.sampleDao().recentGeoTags())

    /** Company id the devices around now point to from past answers, or null when they say nothing. */
    suspend fun suggestCompany(deviceHashes: Set<String>): Long? =
        CompanyMatcher.suggest(deviceHashes, db.sampleDao().recentDeviceTags())

    private suspend fun insertRefs(sampleId: Long, activityIds: List<Long>, painTypeIds: List<Long>, tags: List<String>) {
        val refs = activityIds.distinct().map { SampleActivityCrossRef(sampleId, it) }
        if (refs.isNotEmpty()) db.sampleDao().insertActivities(refs)
        val pains = painTypeIds.distinct().map { SamplePainCrossRef(sampleId, it) }
        if (pains.isNotEmpty()) db.sampleDao().insertPainTypes(pains)
        val tagRows = Tags.normalize(tags).map { SampleTagEntity(sampleId, it) }
        if (tagRows.isNotEmpty()) db.sampleDao().insertTags(tagRows)
    }
}
