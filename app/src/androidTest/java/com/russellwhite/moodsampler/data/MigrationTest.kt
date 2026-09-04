package com.russellwhite.moodsampler.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.DefaultCategories
import com.russellwhite.moodsampler.data.db.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_carriesActivitiesAddsDefaultsAndKeepsOtherLast() {
        helper.createDatabase(DB, 1).apply {
            val v1Activities = DefaultCategories.V1_ACTIVITIES + DefaultCategories.OTHER_ACTIVITY
            v1Activities.forEachIndexed { i, label -> insertCategory("ACTIVITY", label, i + 1) }
            DefaultCategories.V1_LOCATIONS.forEachIndexed { i, label -> insertCategory("LOCATION", label, i + 1) }
            // A user-added place that collides case-insensitively with a new default.
            insertCategory("LOCATION", "gym", 7)
            execSQL(
                """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status, notifiedAtEpochMs, answeredAtEpochMs)
                   VALUES (1, 1000, '2026-08-15', 0, 'ANSWERED', 1000, 1100)"""
            )
            execSQL(
                """INSERT INTO sample (id, promptId, valence, arousal, activityId, locationId, socialId, note, createdAtEpochMs)
                   VALUES (1, 1, 0.5, -0.2, 1, 1, NULL, 'hi', 1100)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT sampleId, categoryId FROM sample_activity").use { c ->
            assertThat(c.count).isEqualTo(1)
            c.moveToFirst()
            assertThat(c.getLong(0)).isEqualTo(1)
            assertThat(c.getLong(1)).isEqualTo(1)
        }
        db.query("SELECT pain, valence, note, locationId FROM sample WHERE id = 1").use { c ->
            c.moveToFirst()
            assertThat(c.isNull(0)).isTrue()
            assertThat(c.getFloat(1)).isEqualTo(0.5f)
            assertThat(c.getString(2)).isEqualTo("hi")
            assertThat(c.getLong(3)).isEqualTo(1)
        }
        db.query("SELECT skipReason FROM scheduled_prompt WHERE id = 1").use { c ->
            c.moveToFirst()
            assertThat(c.isNull(0)).isTrue()
        }
        db.query("SELECT COUNT(*) FROM category WHERE type = 'LOCATION' AND lower(label) = 'gym'").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
        db.query("SELECT COUNT(*) FROM category WHERE type = 'ACTIVITY'").use { c ->
            c.moveToFirst()
            val expected = DefaultCategories.V1_ACTIVITIES.size + DefaultCategories.V2_ADDED_ACTIVITIES.size + 1
            assertThat(c.getInt(0)).isEqualTo(expected)
        }
        db.query("SELECT label FROM category WHERE type = 'ACTIVITY' ORDER BY sortOrder DESC, id DESC LIMIT 1").use { c ->
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("Other")
        }
        db.query("SELECT COUNT(*) FROM category WHERE type = 'LOCATION'").use { c ->
            c.moveToFirst()
            // 6 v1 + "gym" (user) + 9 new (Gym skipped)
            assertThat(c.getInt(0)).isEqualTo(6 + 1 + DefaultCategories.V2_ADDED_LOCATIONS.size - 1)
        }
    }

    private fun SupportSQLiteDatabase.insertCategory(type: String, label: String, sortOrder: Int) {
        execSQL(
            "INSERT INTO category (type, label, sortOrder, isArchived) VALUES (?, ?, ?, 0)",
            arrayOf<Any>(type, label, sortOrder),
        )
    }

    private companion object {
        const val DB = "migration-test"
    }
}
