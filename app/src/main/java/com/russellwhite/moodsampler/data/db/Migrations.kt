package com.russellwhite.moodsampler.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2:
 *  - `scheduled_prompt.skipReason` (nullable) for prompts the user declined on purpose;
 *  - `sample.pain` (nullable 0..10) — null for every pre-v2 row;
 *  - `sample.activityId` replaced by the `sample_activity` join table (multi-select),
 *    with existing single activities carried over;
 *  - new default activities/places appended for existing installs, skipping any
 *    label the user already has (case-insensitive), and "Other" moved to the end.
 *
 * `sample` is rebuilt (SQLite can't drop a column cleanly) — the CREATE statements
 * mirror Room's exported 2.json so post-migration validation passes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `scheduled_prompt` ADD COLUMN `skipReason` TEXT")

        // Park the old single-activity links before rebuilding `sample`.
        db.execSQL(
            """CREATE TEMP TABLE `_act` AS
               SELECT `id` AS `sampleId`, `activityId` AS `categoryId`
               FROM `sample` WHERE `activityId` IS NOT NULL"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `sample_new` (
               `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               `promptId` INTEGER NOT NULL,
               `valence` REAL NOT NULL,
               `arousal` REAL NOT NULL,
               `pain` INTEGER,
               `locationId` INTEGER,
               `socialId` INTEGER,
               `note` TEXT,
               `createdAtEpochMs` INTEGER NOT NULL,
               FOREIGN KEY(`promptId`) REFERENCES `scheduled_prompt`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
        )
        db.execSQL(
            """INSERT INTO `sample_new`
               (`id`, `promptId`, `valence`, `arousal`, `pain`, `locationId`, `socialId`, `note`, `createdAtEpochMs`)
               SELECT `id`, `promptId`, `valence`, `arousal`, NULL, `locationId`, `socialId`, `note`, `createdAtEpochMs`
               FROM `sample`"""
        )
        db.execSQL("DROP TABLE `sample`")
        db.execSQL("ALTER TABLE `sample_new` RENAME TO `sample`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sample_promptId` ON `sample` (`promptId`)")

        // Only now: created earlier, DROP TABLE sample would have cascaded into it.
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `sample_activity` (
               `sampleId` INTEGER NOT NULL,
               `categoryId` INTEGER NOT NULL,
               PRIMARY KEY(`sampleId`, `categoryId`),
               FOREIGN KEY(`sampleId`) REFERENCES `sample`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
               FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sample_activity_categoryId` ON `sample_activity` (`categoryId`)"
        )
        db.execSQL(
            """INSERT OR IGNORE INTO `sample_activity` (`sampleId`, `categoryId`)
               SELECT `sampleId`, `categoryId` FROM `_act`
               WHERE `categoryId` IN (SELECT `id` FROM `category`)"""
        )
        db.execSQL("DROP TABLE `_act`")

        DefaultCategories.V2_ADDED.forEach { (type, labels) ->
            labels.forEach { label -> insertCategoryIfMissing(db, type, label) }
        }
        // Keep the catch-all at the end of the activity list.
        db.execSQL(
            """UPDATE `category`
               SET `sortOrder` = (SELECT COALESCE(MAX(`sortOrder`), 0) + 1 FROM `category` WHERE `type` = 'ACTIVITY')
               WHERE `type` = 'ACTIVITY' AND lower(`label`) = lower(?)""",
            arrayOf<Any>(DefaultCategories.OTHER_ACTIVITY),
        )
    }

}

/**
 * v2 → v3:
 *  - `sample.latitude/longitude/accuracyM/fixAtEpochMs` (all nullable) for the
 *    one-off location fix taken while answering;
 *  - `category.groupKey` (nullable) — the [ActivityGroup] of each activity,
 *    back-filled for the default labels; anything else becomes OTHER.
 * All additive, so plain ALTERs keep the tables' declared shape identical to 3.json.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sample` ADD COLUMN `latitude` REAL")
        db.execSQL("ALTER TABLE `sample` ADD COLUMN `longitude` REAL")
        db.execSQL("ALTER TABLE `sample` ADD COLUMN `accuracyM` REAL")
        db.execSQL("ALTER TABLE `sample` ADD COLUMN `fixAtEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `category` ADD COLUMN `groupKey` TEXT")

        DefaultCategories.ACTIVITY_GROUPS.forEach { (label, group) ->
            db.execSQL(
                "UPDATE `category` SET `groupKey` = ? WHERE `type` = 'ACTIVITY' AND lower(`label`) = ?",
                arrayOf<Any>(group.name, label),
            )
        }
        db.execSQL(
            "UPDATE `category` SET `groupKey` = ? WHERE `type` = 'ACTIVITY' AND `groupKey` IS NULL",
            arrayOf<Any>(ActivityGroup.OTHER.name),
        )
    }
}

private fun insertCategoryIfMissing(db: SupportSQLiteDatabase, type: CategoryType, label: String) {
    db.execSQL(
        """INSERT INTO `category` (`type`, `label`, `sortOrder`, `isArchived`)
           SELECT ?, ?, (SELECT COALESCE(MAX(`sortOrder`), 0) + 1 FROM `category` WHERE `type` = ?), 0
           WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `type` = ? AND lower(`label`) = lower(?))""",
        arrayOf<Any>(type.name, label, type.name, type.name, label),
    )
}
