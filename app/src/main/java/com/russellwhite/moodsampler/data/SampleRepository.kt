package com.russellwhite.moodsampler.data

import androidx.room.withTransaction
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.SampleEntity

class SampleRepository(private val db: AppDatabase) {

    /**
     * Persist an answered prompt atomically: the sample row and the prompt's
     * ANSWERED status must land together. Returns false if the prompt was no
     * longer answerable (already MISSED/CANCELLED) — the sample is not saved.
     */
    suspend fun saveAnswer(sample: SampleEntity, answeredAtMs: Long): Boolean =
        db.withTransaction {
            val updated = db.scheduledPromptDao().markAnswered(sample.promptId, answeredAtMs)
            if (updated == 1) {
                db.sampleDao().insert(sample.copy(createdAtEpochMs = answeredAtMs))
                true
            } else {
                false
            }
        }
}
