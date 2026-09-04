package com.russellwhite.moodsampler.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.backup.Backup
import com.russellwhite.moodsampler.data.backup.CategoryDto
import com.russellwhite.moodsampler.data.backup.PromptDto
import com.russellwhite.moodsampler.data.backup.SampleActivityDto
import com.russellwhite.moodsampler.data.backup.SampleDto
import com.russellwhite.moodsampler.data.backup.SettingsDto
import com.russellwhite.moodsampler.data.backup.toEntity
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import androidx.room.withTransaction
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the table-replacement half of BackupManager.apply (the part that
 * needs a real SQLite) without AlarmManager/DataStore.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun replaceTables_preservesIdsAndDropsOldRows() = runTest {
        // Pre-existing local data that must vanish.
        db.categoryDao().insert(CategoryEntity(type = CategoryType.ACTIVITY, label = "Old", sortOrder = 1))
        db.scheduledPromptDao().insert(
            ScheduledPromptEntity(scheduledAtEpochMs = 1, localDate = "2026-01-01", windowIndex = 0, status = PromptStatus.MISSED)
        )

        val backup = Backup(
            schemaVersion = AppDatabase.VERSION, appVersionName = "t", exportedAtEpochMs = 0,
            settings = SettingsDto(6, 540, 1320, 20),
            categories = listOf(CategoryDto(41, "ACTIVITY", "Work", 1, false), CategoryDto(42, "ACTIVITY", "Music", 2, false)),
            prompts = listOf(PromptDto(900, 5_000, "2026-08-15", 0, "ANSWERED", 5_000, 5_100)),
            samples = listOf(SampleDto(77, 900, 0.5f, 0f, 2, null, null, "hi", 5_100)),
            sampleActivities = listOf(SampleActivityDto(77, 41), SampleActivityDto(77, 42)),
        )

        db.withTransaction {
            db.sampleDao().deleteAllActivities()
            db.sampleDao().deleteAll()
            db.scheduledPromptDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(backup.categories.map { it.toEntity() })
            db.scheduledPromptDao().insertAll(backup.prompts.map { it.toEntity() })
            db.sampleDao().insertAll(backup.samples.map { it.toEntity() })
            db.sampleDao().insertActivities(backup.sampleActivities.map { it.toEntity() })
        }

        assertThat(db.categoryDao().getAll().map { it.id to it.label }).containsExactly(41L to "Work", 42L to "Music")
        assertThat(db.scheduledPromptDao().getAll().map { it.id }).containsExactly(900L)
        val restored = db.sampleDao().byPromptId(900)!!
        assertThat(restored.sample.id).isEqualTo(77)
        assertThat(restored.sample.pain).isEqualTo(2)
        assertThat(restored.activityIds).containsExactly(41L, 42L)
    }
}
