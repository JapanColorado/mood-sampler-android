package com.russellwhite.moodsampler.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.DefaultCategories
import com.russellwhite.moodsampler.data.db.MIGRATION_1_2
import com.russellwhite.moodsampler.data.db.MIGRATION_2_3
import com.russellwhite.moodsampler.data.db.MIGRATION_3_4
import com.russellwhite.moodsampler.data.db.MIGRATION_4_6
import com.russellwhite.moodsampler.data.db.MIGRATION_5_6
import com.russellwhite.moodsampler.data.db.MIGRATION_6_7
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

    @Test
    fun migrate2To3_assignsActivityGroupsAndKeepsSamples() {
        helper.createDatabase(DB, 2).apply {
            insertCategory("ACTIVITY", "Work", 1)
            insertCategory("ACTIVITY", "watching tv / video", 2)  // case-insensitive default match
            insertCategory("ACTIVITY", "Knitting", 3)             // user-added → OTHER
            insertCategory("ACTIVITY", "Other", 4)
            insertCategory("LOCATION", "Home", 1)
            execSQL(
                """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status, notifiedAtEpochMs, answeredAtEpochMs)
                   VALUES (1, 1000, '2026-08-15', 0, 'ANSWERED', 1000, 1100)"""
            )
            execSQL(
                """INSERT INTO sample (id, promptId, valence, arousal, pain, locationId, socialId, note, createdAtEpochMs)
                   VALUES (1, 1, 0.5, -0.2, 3, 5, NULL, 'hi', 1100)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        fun groupOf(label: String): String? = db.query(
            "SELECT groupKey FROM category WHERE label = ?", arrayOf(label),
        ).use { c -> c.moveToFirst(); if (c.isNull(0)) null else c.getString(0) }
        assertThat(groupOf("Work")).isEqualTo("WORK")
        assertThat(groupOf("watching tv / video")).isEqualTo("LEISURE")
        assertThat(groupOf("Knitting")).isEqualTo("OTHER")
        assertThat(groupOf("Other")).isEqualTo("OTHER")
        assertThat(groupOf("Home")).isNull()

        db.query("SELECT pain, locationId, note, latitude, longitude, accuracyM, fixAtEpochMs FROM sample WHERE id = 1").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(3)
            assertThat(c.getLong(1)).isEqualTo(5)
            assertThat(c.getString(2)).isEqualTo("hi")
            (3..6).forEach { assertThat(c.isNull(it)).isTrue() }
        }
    }

    @Test
    fun migrate3To4_addsNullPhotoPathAndKeepsSamples() {
        helper.createDatabase(DB, 3).apply {
            execSQL(
                """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status, notifiedAtEpochMs, answeredAtEpochMs)
                   VALUES (1, 1000, '2026-08-15', 0, 'ANSWERED', 1000, 1100)"""
            )
            execSQL(
                """INSERT INTO sample (id, promptId, valence, arousal, pain, locationId, socialId, note, createdAtEpochMs, latitude, longitude, accuracyM, fixAtEpochMs)
                   VALUES (1, 1, 0.5, -0.2, 3, NULL, NULL, 'hi', 1100, 40.0, -105.0, 5.0, 1100)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        db.query("SELECT note, latitude, photoPath FROM sample WHERE id = 1").use { c ->
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("hi")
            assertThat(c.getDouble(1)).isEqualTo(40.0)
            assertThat(c.isNull(2)).isTrue()
        }
    }

    @Test
    fun migrate4To6_snapsMoodToStepsAndKeepsActivityLinks() {
        helper.createDatabase(DB, 4).apply {
            insertCategory("ACTIVITY", "Work", 1)
            execSQL(
                """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status, notifiedAtEpochMs, answeredAtEpochMs)
                   VALUES (1, 1000, '2026-08-15', 0, 'ANSWERED', 1000, 1100),
                          (2, 2000, '2026-08-15', 1, 'ANSWERED', 2000, 2100)"""
            )
            execSQL(
                """INSERT INTO sample (id, promptId, valence, arousal, pain, locationId, socialId, note, createdAtEpochMs, latitude, longitude, accuracyM, fixAtEpochMs, photoPath)
                   VALUES (1, 1, 0.3, -0.5, 3, NULL, NULL, 'hi', 1100, 40.0, -105.0, 5.0, 1100, 'p.jpg'),
                          (2, 2, -1.0, 1.0, NULL, NULL, NULL, NULL, 2100, NULL, NULL, NULL, NULL, NULL)"""
            )
            execSQL("INSERT INTO sample_activity (sampleId, categoryId) SELECT 1, id FROM category WHERE label = 'Work'")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_4_6)

        db.query("SELECT valence, arousal, pain, note, latitude, photoPath FROM sample ORDER BY id").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(MoodScale.fromLegacy(0.3f))
            assertThat(c.getInt(1)).isEqualTo(MoodScale.fromLegacy(-0.5f))
            assertThat(c.getInt(2)).isEqualTo(3)
            assertThat(c.getString(3)).isEqualTo("hi")
            assertThat(c.getDouble(4)).isEqualTo(40.0)
            assertThat(c.getString(5)).isEqualTo("p.jpg")
            c.moveToNext()
            assertThat(c.getInt(0)).isEqualTo(0)
            assertThat(c.getInt(1)).isEqualTo(10)
            assertThat(c.isNull(2)).isTrue()
        }
        db.query("SELECT COUNT(*) FROM sample_activity WHERE sampleId = 1").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun migrate5To6_stretchesOneToTenOntoZeroToTen() {
        helper.createDatabase(DB, 5).apply {
            (1..10).forEach { n ->
                execSQL(
                    """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status)
                       VALUES ($n, $n, '2026-10-05', $n, 'ANSWERED')"""
                )
                execSQL(
                    """INSERT INTO sample (id, promptId, valence, arousal, createdAtEpochMs)
                       VALUES ($n, $n, $n, ${11 - n}, $n)"""
                )
            }
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6)

        db.query("SELECT valence, arousal FROM sample ORDER BY id").use { c ->
            (1..10).forEach { n ->
                c.moveToNext()
                assertThat(c.getInt(0)).isEqualTo(MoodScale.fromV5(n))
                assertThat(c.getInt(1)).isEqualTo(MoodScale.fromV5(11 - n))
            }
        }
    }

    @Test
    fun migrate6To7_archivesOtherActivityAndKeepsItsSamples() {
        helper.createDatabase(DB, 6).apply {
            insertCategory("ACTIVITY", "Work", 1)
            insertCategory("ACTIVITY", "Other", 2)
            insertCategory("LOCATION", "Other", 1)
            execSQL(
                """INSERT INTO scheduled_prompt (id, scheduledAtEpochMs, localDate, windowIndex, status)
                   VALUES (1, 1, '2026-10-09', 0, 'ANSWERED')"""
            )
            execSQL("INSERT INTO sample (id, promptId, valence, arousal, createdAtEpochMs) VALUES (1, 1, 5, 5, 1)")
            execSQL("INSERT INTO sample_activity (sampleId, categoryId) VALUES (1, 2)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_6_7)

        db.query("SELECT type, label, isArchived FROM category ORDER BY id").use { c ->
            val rows = generateSequence { if (c.moveToNext()) Triple(c.getString(0), c.getString(1), c.getInt(2)) else null }.toList()
            assertThat(rows).containsExactly(
                Triple("ACTIVITY", "Work", 0),
                Triple("ACTIVITY", "Other", 1),
                Triple("LOCATION", "Other", 0),
            ).inOrder()
        }
        db.query("SELECT categoryId FROM sample_activity WHERE sampleId = 1").use { c ->
            c.moveToFirst()
            assertThat(c.getLong(0)).isEqualTo(2)
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
