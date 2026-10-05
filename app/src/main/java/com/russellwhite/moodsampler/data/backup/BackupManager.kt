package com.russellwhite.moodsampler.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.russellwhite.moodsampler.data.PhotoStore
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.DeviceClock
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a restore is about to replace / bring in, for the confirmation dialog. */
data class BackupSummary(val samples: Int, val prompts: Int, val exportedAtEpochMs: Long?)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val scheduler: PromptScheduler,
    private val photos: PhotoStore,
    private val appVersionName: String,
    private val clock: Clock = DeviceClock,
) {
    /** Serialise everything and write it to [uri] (a document the user picked). */
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = snapshot()
        val text = BackupCodec.encode(backup)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Could not open the destination for writing.")
    }

    suspend fun snapshot(): Backup = Backup(
        schemaVersion = AppDatabase.VERSION,
        appVersionName = appVersionName,
        exportedAtEpochMs = clock.millis(),
        settings = settings.current().toDto(),
        categories = db.categoryDao().getAll().map { it.toDto() },
        prompts = db.scheduledPromptDao().getAll().map { it.toDto() },
        samples = db.sampleDao().getAll().map { it.toDto() },
        sampleActivities = db.sampleDao().getAllActivities().map { it.toDto() },
    )

    /** Parse and validate without touching the database. */
    suspend fun inspect(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupFormatException("Could not read the selected file.")
        BackupCodec.decode(text)
    }

    /**
     * "Delete all data": every answer and every prompt, in one transaction, plus
     * every photo. Categories and sampling settings are kept. The schedule is redrawn so
     * sampling continues from a clean slate.
     */
    suspend fun deleteSamplesAndPrompts() {
        db.withTransaction {
            db.sampleDao().deleteAllActivities()
            db.sampleDao().deleteAll()
            db.scheduledPromptDao().deleteAll()
        }
        photos.deleteAll()
        scheduler.onDataReplaced()
    }

    suspend fun currentSummary() = BackupSummary(
        samples = db.sampleDao().count(),
        prompts = db.scheduledPromptDao().count(),
        exportedAtEpochMs = null,
    )

    /**
     * Replace all local data with [backup]. Rows are inserted with their original
     * ids inside one transaction (FK order: categories, prompts, samples, tags).
     * Afterwards the schedule is rebuilt for the current phone/zone/settings.
     */
    suspend fun apply(backup: Backup) {
        db.withTransaction {
            db.sampleDao().deleteAllActivities()
            db.sampleDao().deleteAll()
            db.scheduledPromptDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(backup.categories.map { it.toEntity() })
            db.scheduledPromptDao().insertAll(backup.prompts.map { it.toEntity() })
            db.sampleDao().insertAll(backup.samples.map { it.toEntity(backup.schemaVersion) })
            db.sampleDao().insertActivities(backup.sampleActivities.map { it.toEntity() })
        }
        photos.retainOnly(backup.samples.mapNotNull { it.photoPath })
        settings.replaceAll(backup.settings)
        settings.setOnboardingDone()
        scheduler.onDataReplaced()
    }
}
