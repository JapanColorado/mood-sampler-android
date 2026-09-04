package com.russellwhite.moodsampler.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun prompt(at: Long, status: PromptStatus = PromptStatus.PENDING) =
        ScheduledPromptEntity(
            scheduledAtEpochMs = at, localDate = "2026-08-15",
            windowIndex = 0, status = status,
        )

    @Test
    fun nextPending_returnsEarliestFuturePending() = runTest {
        val dao = db.scheduledPromptDao()
        dao.insertAll(listOf(prompt(1_000), prompt(3_000), prompt(2_000)))
        val next = dao.nextPending(nowMs = 1_500)
        assertThat(next?.scheduledAtEpochMs).isEqualTo(2_000)
    }

    @Test
    fun statusTransitions_answeredOnlyFromPendingOrNotified() = runTest {
        val dao = db.scheduledPromptDao()
        val id = dao.insert(prompt(1_000))
        assertThat(dao.markNotified(id, 1_001)).isEqualTo(1)
        assertThat(dao.markAnswered(id, 1_002)).isEqualTo(1)
        // Already answered — a late expiry must not flip it to MISSED.
        assertThat(dao.markMissedIfNotified(id)).isEqualTo(0)
        assertThat(dao.getById(id)?.status).isEqualTo(PromptStatus.ANSWERED)
    }

    @Test
    fun saveAnswer_isAtomicAndRefusesExpiredPrompt() = runTest {
        val dao = db.scheduledPromptDao()
        val repo = SampleRepository(db)
        val okId = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val missedId = dao.insert(prompt(2_000, PromptStatus.MISSED))

        fun sample(promptId: Long) = SampleEntity(
            promptId = promptId, valence = 0.5f, arousal = -0.2f, pain = 3,
            locationId = null, socialId = null,
            note = null, createdAtEpochMs = 0,
        )
        val work = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "Work", sortOrder = 1))
        val music = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "Music", sortOrder = 2))

        assertThat(repo.saveAnswer(sample(okId), listOf(work, music), answeredAtMs = 1_100)).isTrue()
        assertThat(repo.saveAnswer(sample(missedId), listOf(work), answeredAtMs = 2_100)).isFalse()
        assertThat(db.sampleDao().count()).isEqualTo(1)
        assertThat(dao.getById(okId)?.answeredAtEpochMs).isEqualTo(1_100)
        val saved = db.sampleDao().byPromptId(okId)
        assertThat(saved?.sample?.pain).isEqualTo(3)
        assertThat(saved?.activityIds).containsExactly(work, music)
    }

    @Test
    fun updateAnswer_replacesActivityTags() = runTest {
        val dao = db.scheduledPromptDao()
        val repo = SampleRepository(db)
        val id = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val a = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "A", sortOrder = 1))
        val b = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "B", sortOrder = 2))
        repo.saveAnswer(
            SampleEntity(promptId = id, valence = 0f, arousal = 0f, locationId = null, socialId = null, note = null, createdAtEpochMs = 0),
            listOf(a), answeredAtMs = 1_100,
        )
        val saved = db.sampleDao().byPromptId(id)!!
        repo.updateAnswer(saved.sample.copy(pain = 7, note = "edited"), listOf(b))
        val updated = db.sampleDao().byPromptId(id)!!
        assertThat(updated.sample.pain).isEqualTo(7)
        assertThat(updated.sample.note).isEqualTo("edited")
        assertThat(updated.activityIds).containsExactly(b)
    }

    @Test
    fun deleteAnswer_cancelsPromptAndClearsTags() = runTest {
        val dao = db.scheduledPromptDao()
        val repo = SampleRepository(db)
        val id = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val a = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "A", sortOrder = 1))
        repo.saveAnswer(
            SampleEntity(promptId = id, valence = 0f, arousal = 0f, locationId = null, socialId = null, note = null, createdAtEpochMs = 0),
            listOf(a), answeredAtMs = 1_100,
        )
        assertThat(repo.deleteAnswer(id)).isTrue()
        assertThat(db.sampleDao().count()).isEqualTo(0)
        assertThat(dao.getById(id)?.status).isEqualTo(PromptStatus.CANCELLED)
        assertThat(dao.getById(id)?.answeredAtEpochMs).isNull()
        assertThat(db.sampleDao().categoryUsageFlow().first()).isEmpty()
    }

    @Test
    fun markSkipped_onlyFromLivePrompts() = runTest {
        val dao = db.scheduledPromptDao()
        val live = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val done = dao.insert(prompt(2_000, PromptStatus.ANSWERED))
        assertThat(dao.markSkipped(live, "busy")).isEqualTo(1)
        assertThat(dao.markSkipped(done, "busy")).isEqualTo(0)
        assertThat(dao.getById(live)?.status).isEqualTo(PromptStatus.MISSED)
        assertThat(dao.getById(live)?.skipReason).isEqualTo("busy")
        assertThat(dao.getById(done)?.skipReason).isNull()
    }

    @Test
    fun responseRateCounts_excludeCancelled() = runTest {
        val dao = db.scheduledPromptDao()
        dao.insertAll(
            listOf(
                prompt(1_000, PromptStatus.ANSWERED),
                prompt(2_000, PromptStatus.MISSED),
                prompt(3_000, PromptStatus.CANCELLED),
            )
        )
        assertThat(dao.countByStatusSince(PromptStatus.ANSWERED, 0)).isEqualTo(1)
        assertThat(dao.countByStatusSince(PromptStatus.MISSED, 0)).isEqualTo(1)
    }

    @Test
    fun seedDefaults_arePresentInPersistentDbCallback() = runTest {
        // In-memory DB also runs the callback only via AppDatabase.build; here we
        // just verify the DEFAULT_CATEGORIES constant wiring through a built DB.
        val seeded = AppDatabase.build(ApplicationProvider.getApplicationContext())
        try {
            val all = seeded.categoryDao().getAll()
            assertThat(all.map { it.label }).containsAtLeast("Work", "Home", "Alone", "Gaming", "Gym")
            val activities = all.filter { it.type == CategoryType.ACTIVITY }.sortedBy { it.sortOrder }
            assertThat(activities.last().label).isEqualTo("Other")
        } finally {
            seeded.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase("moodsampler.db")
        }
    }

    @Test
    fun concludedPrompts_joinSamplesAndKeepMissedRows() = runTest {
        val dao = db.scheduledPromptDao()
        val answeredId = dao.insert(prompt(1_000, PromptStatus.ANSWERED))
        dao.insert(prompt(2_000, PromptStatus.MISSED))
        dao.insert(prompt(3_000, PromptStatus.PENDING))
        db.sampleDao().insert(
            SampleEntity(
                promptId = answeredId, valence = 1f, arousal = 0f,
                locationId = null, socialId = null,
                note = null, createdAtEpochMs = 1_050,
            )
        )
        val rows = dao.allConcludedPrompts()
        assertThat(rows).hasSize(2)
        assertThat(rows[0].sample?.sample?.valence).isEqualTo(1f)
        assertThat(rows[0].sample?.activityIds).isEmpty()
        assertThat(rows[1].sample).isNull()
    }
}
