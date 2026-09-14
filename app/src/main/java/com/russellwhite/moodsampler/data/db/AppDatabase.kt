package com.russellwhite.moodsampler.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScheduledPromptEntity::class,
        SampleEntity::class,
        SampleActivityCrossRef::class,
        CategoryEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduledPromptDao(): ScheduledPromptDao
    abstract fun sampleDao(): SampleDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val VERSION = 3

        const val NAME = "moodsampler.db"

        /** [name] is overridable so instrumented tests never touch the real database. */
        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedCallback)
                .build()

        /** Default category lists inserted on first run; user can edit/archive them later. */
        val DEFAULT_CATEGORIES: Map<CategoryType, List<String>>
            get() = DefaultCategories.DEFAULT_CATEGORIES

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DEFAULT_CATEGORIES.forEach { (type, labels) ->
                    labels.forEachIndexed { index, label ->
                        val group = if (type == CategoryType.ACTIVITY) DefaultCategories.groupFor(label).name else null
                        db.execSQL(
                            "INSERT INTO category (type, label, sortOrder, isArchived, groupKey) VALUES (?, ?, ?, 0, ?)",
                            arrayOf<Any?>(type.name, label, index + 1, group),
                        )
                    }
                }
            }
        }
    }
}
