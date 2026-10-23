package dev.russell.moodsampler.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.db.CategoryEntity
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.db.DefaultCategories
import dev.russell.moodsampler.data.db.PromptStatus
import dev.russell.moodsampler.data.db.SampleEntity
import dev.russell.moodsampler.data.db.ScheduledPromptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    private companion object {
        /** Never the real app database — the instrumented run shares the app's data dir. */
        const val SEED_TEST_DB = "dao-seed-test.db"
    }

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
    fun recentGeoTags_returnsOnlyGeotaggedSamplesWithPlace() = runTest {
        val prompts = db.scheduledPromptDao()
        val samples = db.sampleDao()
        val ids = (1..3).map { prompts.insert(prompt(it * 1_000L, PromptStatus.ANSWERED)) }
        samples.insert(SampleEntity(promptId = ids[0], valence = 6, arousal = 6, locationId = 7, socialId = null, note = null, createdAtEpochMs = 1, latitude = 40.0, longitude = -105.0, accuracyM = 5f, fixAtEpochMs = 1))
        samples.insert(SampleEntity(promptId = ids[1], valence = 6, arousal = 6, locationId = null, socialId = null, note = null, createdAtEpochMs = 2, latitude = 40.0, longitude = -105.0))
        samples.insert(SampleEntity(promptId = ids[2], valence = 6, arousal = 6, locationId = 8, socialId = null, note = null, createdAtEpochMs = 3))

        val tags = samples.recentGeoTags()
        assertThat(tags).hasSize(1)
        assertThat(tags.single().locationId).isEqualTo(7)
        assertThat(tags.single().latitude).isEqualTo(40.0)
    }

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
        val repo = SampleRepository(db, PhotoStore(ApplicationProvider.getApplicationContext()))
        val okId = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val missedId = dao.insert(prompt(2_000, PromptStatus.MISSED))

        fun sample(promptId: Long) = SampleEntity(
            promptId = promptId, valence = 7, arousal = 4, pain = 3,
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
        val repo = SampleRepository(db, PhotoStore(ApplicationProvider.getApplicationContext()))
        val id = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val a = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "A", sortOrder = 1))
        val b = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "B", sortOrder = 2))
        repo.saveAnswer(
            SampleEntity(promptId = id, valence = 6, arousal = 6, locationId = null, socialId = null, note = null, createdAtEpochMs = 0),
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
        val repo = SampleRepository(db, PhotoStore(ApplicationProvider.getApplicationContext()))
        val id = dao.insert(prompt(1_000, PromptStatus.NOTIFIED))
        val a = db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "A", sortOrder = 1))
        repo.saveAnswer(
            SampleEntity(promptId = id, valence = 6, arousal = 6, locationId = null, socialId = null, note = null, createdAtEpochMs = 0),
            listOf(a), answeredAtMs = 1_100,
        )
        assertThat(repo.deleteAnswer(id)).isTrue()
        assertThat(db.sampleDao().count()).isEqualTo(0)
        assertThat(dao.getById(id)?.status).isEqualTo(PromptStatus.CANCELLED)
        assertThat(dao.getById(id)?.answeredAtEpochMs).isNull()
        assertThat(db.sampleDao().categoryUsageFlow().first()).isEmpty()
    }

    @Test
    fun deleteUnused_keepsReferencedCategoriesAndRenumbers() = runTest {
        val dao = db.categoryDao()
        val repo = CategoryRepository(db)
        fun cat(type: CategoryType, label: String, order: Int, archived: Boolean = false) =
            CategoryEntity(type = type, label = label, sortOrder = order, isArchived = archived)
        val work = dao.insert(cat(CategoryType.ACTIVITY, "Work", 1))
        dao.insert(cat(CategoryType.ACTIVITY, "Idle", 2))
        val music = dao.insert(cat(CategoryType.ACTIVITY, "Music", 3))
        dao.insert(cat(CategoryType.ACTIVITY, "Old", 4, archived = true))
        val home = dao.insert(cat(CategoryType.LOCATION, "Home", 1))
        dao.insert(cat(CategoryType.LOCATION, "Cafe", 2))
        val promptId = db.scheduledPromptDao().insert(prompt(1_000, PromptStatus.NOTIFIED))
        SampleRepository(db, PhotoStore(ApplicationProvider.getApplicationContext())).saveAnswer(
            SampleEntity(promptId = promptId, valence = 6, arousal = 6, locationId = home, socialId = null, note = null, createdAtEpochMs = 0),
            listOf(work, music), answeredAtMs = 1_100,
        )

        val unused = repo.unused(CategoryType.ACTIVITY)
        assertThat(unused.map { it.label }).containsExactly("Idle", "Old").inOrder()
        assertThat(repo.deleteUnused(CategoryType.ACTIVITY, unused)).isEqualTo(2)

        val left = dao.byType(CategoryType.ACTIVITY)
        assertThat(left.map { it.label to it.sortOrder }).containsExactly("Work" to 1, "Music" to 2).inOrder()
        // Other types untouched, and a stale list can't delete a category that has since been used.
        assertThat(dao.byType(CategoryType.LOCATION).map { it.label }).containsExactly("Home", "Cafe")
        assertThat(repo.deleteUnused(CategoryType.LOCATION, dao.byType(CategoryType.LOCATION))).isEqualTo(1)
        assertThat(dao.byType(CategoryType.LOCATION).map { it.label }).containsExactly("Home")
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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(SEED_TEST_DB)
        val seeded = AppDatabase.build(context, SEED_TEST_DB)
        try {
            val all = seeded.categoryDao().getAll()
            assertThat(all.map { it.label }).containsAtLeast("Work", "Home", "Alone", "Gaming", "Gym")
            // The "Other…" chip replaces a seeded catch-all.
            assertThat(all.map { it.label }).doesNotContain(DefaultCategories.OTHER_ACTIVITY)
        } finally {
            seeded.close()
            context.deleteDatabase(SEED_TEST_DB)
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
                promptId = answeredId, valence = 10, arousal = 6,
                locationId = null, socialId = null,
                note = null, createdAtEpochMs = 1_050,
            )
        )
        val rows = dao.allConcludedPrompts()
        assertThat(rows).hasSize(2)
        assertThat(rows[0].sample?.sample?.valence).isEqualTo(10)
        assertThat(rows[0].sample?.activityIds).isEmpty()
        assertThat(rows[1].sample).isNull()
    }
}
