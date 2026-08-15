package com.russellwhite.moodsampler.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScheduledPromptEntity::class, SampleEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduledPromptDao(): ScheduledPromptDao
    abstract fun sampleDao(): SampleDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "moodsampler.db")
                .addCallback(SeedCallback)
                .build()

        /** Default category lists inserted on first run; user can edit/archive them later. */
        val DEFAULT_CATEGORIES: Map<CategoryType, List<String>> = mapOf(
            CategoryType.ACTIVITY to listOf(
                "Work", "Chores", "Meals", "Exercise", "Socializing",
                "Screen / leisure", "Reading", "Commuting", "Resting", "Other",
            ),
            CategoryType.LOCATION to listOf(
                "Home", "Work", "Outside", "Transit", "Someone's home", "Public place",
            ),
            CategoryType.SOCIAL to listOf(
                "Alone", "Partner", "Family", "Friends", "Coworkers", "Strangers",
            ),
        )

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DEFAULT_CATEGORIES.forEach { (type, labels) ->
                    labels.forEachIndexed { index, label ->
                        db.execSQL(
                            "INSERT INTO category (type, label, sortOrder, isArchived) VALUES (?, ?, ?, 0)",
                            arrayOf<Any>(type.name, label, index + 1),
                        )
                    }
                }
            }
        }
    }
}
