package com.russellwhite.moodsampler.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.CategoryGroup
import com.russellwhite.moodsampler.data.CategoryRepository
import com.russellwhite.moodsampler.data.CsvExporter
import com.russellwhite.moodsampler.data.backup.Backup
import com.russellwhite.moodsampler.data.backup.BackupFormatException
import com.russellwhite.moodsampler.data.backup.BackupManager
import com.russellwhite.moodsampler.data.backup.BackupSummary
import kotlinx.coroutines.flow.MutableStateFlow
import com.russellwhite.moodsampler.data.db.ActivityGroup
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.settings.AppSettings
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingRestore(val backup: Backup, val current: BackupSummary, val incoming: BackupSummary)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val categories: CategoryRepository,
    private val scheduler: PromptScheduler,
    private val csvExporter: CsvExporter,
    private val backupManager: BackupManager,
) : ViewModel() {

    /** Pending restore awaiting the user's confirmation, or null. */
    val pendingRestore = MutableStateFlow<PendingRestore?>(null)

    /** One-shot user-facing result of the last backup/restore action. */
    val dataMessage = MutableStateFlow<String?>(null)

    /** Non-null while the "Delete all data" confirmation is showing (what would be deleted). */
    val pendingWipe = MutableStateFlow<BackupSummary?>(null)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val categoryGroups: StateFlow<Map<CategoryType, CategoryGroup>> = categories.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Sample counts per category id, for "used in N samples" hints. */
    val usage: StateFlow<Map<Long, Int>> = categories.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setWindowsPerDay(value: Int) = updateSampling { settingsRepository.setWindowsPerDay(value) }

    fun setDayStart(minutes: Int) = updateSampling { settingsRepository.setDayStartMinutes(minutes) }

    fun setDayEnd(minutes: Int) = updateSampling { settingsRepository.setDayEndMinutes(minutes) }

    fun setAutoLocation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoLocation(enabled) }
    }

    fun setExpiryMinutes(value: Int) {
        viewModelScope.launch { settingsRepository.setExpiryMinutes(value) }
    }

    /** Sampling-shape changes invalidate the future schedule — regenerate it. */
    private fun updateSampling(update: suspend () -> Unit) {
        viewModelScope.launch {
            update()
            scheduler.onConfigChanged()
        }
    }

    fun addCategory(type: CategoryType, label: String) {
        viewModelScope.launch { categories.add(type, label) }
    }

    fun renameCategory(category: CategoryEntity, label: String) {
        viewModelScope.launch { categories.rename(category, label) }
    }

    fun archiveCategory(category: CategoryEntity) {
        viewModelScope.launch { categories.setArchived(category, true) }
    }

    fun restoreCategory(category: CategoryEntity) {
        viewModelScope.launch { categories.setArchived(category, false) }
    }

    fun setActivityGroup(category: CategoryEntity, group: ActivityGroup) {
        viewModelScope.launch { categories.setGroup(category, group) }
    }

    fun moveCategory(category: CategoryEntity, delta: Int) {
        viewModelScope.launch { categories.move(category, delta) }
    }

    fun exportCsv(onReady: (Uri) -> Unit) {
        viewModelScope.launch { onReady(csvExporter.export()) }
    }

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            dataMessage.value = try {
                backupManager.export(uri)
                "Backup saved."
            } catch (e: Exception) {
                "Backup failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Parse the picked file; on success ask for confirmation via [pendingRestore]. */
    fun inspectRestore(uri: Uri) {
        viewModelScope.launch {
            try {
                val backup = backupManager.inspect(uri)
                pendingRestore.value = PendingRestore(
                    backup = backup,
                    current = backupManager.currentSummary(),
                    incoming = BackupSummary(backup.samples.size, backup.prompts.size, backup.exportedAtEpochMs),
                )
            } catch (e: BackupFormatException) {
                dataMessage.value = e.message
            } catch (e: Exception) {
                dataMessage.value = "Couldn't read that file: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore.value ?: return
        pendingRestore.value = null
        viewModelScope.launch {
            dataMessage.value = try {
                backupManager.apply(pending.backup)
                "Restored ${pending.incoming.samples} samples. The upcoming schedule was redrawn."
            } catch (e: Exception) {
                "Restore failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun cancelRestore() {
        pendingRestore.value = null
    }

    fun askDeleteAll() {
        viewModelScope.launch { pendingWipe.value = backupManager.currentSummary() }
    }

    fun cancelDeleteAll() {
        pendingWipe.value = null
    }

    fun confirmDeleteAll() {
        val summary = pendingWipe.value ?: return
        pendingWipe.value = null
        viewModelScope.launch {
            dataMessage.value = try {
                backupManager.deleteSamplesAndPrompts()
                "Deleted ${summary.samples} samples and ${summary.prompts} prompts. Sampling continues with a fresh schedule."
            } catch (e: Exception) {
                "Delete failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun dismissMessage() {
        dataMessage.value = null
    }

    companion object {
        val Factory = graphViewModelFactory { graph ->
            SettingsViewModel(
                settingsRepository = graph.settingsRepository,
                categories = graph.categoryRepository,
                scheduler = graph.promptScheduler,
                csvExporter = graph.csvExporter,
                backupManager = graph.backupManager,
            )
        }
    }
}
