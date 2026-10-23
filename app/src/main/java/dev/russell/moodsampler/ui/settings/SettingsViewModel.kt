package dev.russell.moodsampler.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.moodsampler.data.CategoryGroup
import dev.russell.moodsampler.data.CategoryRepository
import dev.russell.moodsampler.data.CsvExporter
import dev.russell.moodsampler.data.backup.Backup
import dev.russell.moodsampler.data.backup.BackupFormatException
import dev.russell.moodsampler.data.backup.BackupManager
import dev.russell.moodsampler.data.backup.BackupSummary
import dev.russell.moodsampler.data.claude.ClaudeKeyStore
import dev.russell.moodsampler.data.claude.ClaudeModel
import kotlinx.coroutines.flow.MutableStateFlow
import dev.russell.moodsampler.data.db.ActivityGroup
import dev.russell.moodsampler.data.db.CategoryEntity
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.settings.AppSettings
import dev.russell.moodsampler.data.settings.SettingsRepository
import dev.russell.moodsampler.scheduling.PromptScheduler
import dev.russell.moodsampler.ui.graphViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Unused [type] categories awaiting the user's go-ahead to delete; [title] names the list ("Activities"). */
data class PendingClearUnused(val type: CategoryType, val title: String, val unused: List<CategoryEntity>)

data class PendingRestore(val backup: Backup, val current: BackupSummary, val incoming: BackupSummary)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val categories: CategoryRepository,
    private val scheduler: PromptScheduler,
    private val csvExporter: CsvExporter,
    private val backupManager: BackupManager,
    private val claudeKeyStore: ClaudeKeyStore,
) : ViewModel() {

    /** Pending restore awaiting the user's confirmation, or null. */
    val pendingRestore = MutableStateFlow<PendingRestore?>(null)

    /** One-shot user-facing result of the last backup/restore action. */
    val dataMessage = MutableStateFlow<String?>(null)

    /** Non-null while the "Delete all data" confirmation is showing (what would be deleted). */
    val pendingWipe = MutableStateFlow<BackupSummary?>(null)

    /** Non-null while a "Clear unused" confirmation is showing. */
    val pendingClearUnused = MutableStateFlow<PendingClearUnused?>(null)

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

    fun setAutoCompany(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCompany(enabled) }
    }

    /** Last four characters of the saved Claude API key, or null when none is saved. */
    val claudeKeyHint: StateFlow<String?> = claudeKeyStore.apiKey.map { it?.takeLast(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setClaudeKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch { claudeKeyStore.set(key) }
    }

    fun clearClaudeKey() {
        viewModelScope.launch { claudeKeyStore.clear() }
    }

    fun setClaudeModel(model: ClaudeModel) {
        viewModelScope.launch { settingsRepository.setClaudeModel(model) }
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

    /** Look up the unused [type] categories (fresh from the DB) and ask before deleting them. */
    fun askClearUnused(type: CategoryType, title: String) {
        viewModelScope.launch {
            val unused = categories.unused(type)
            pendingClearUnused.value = if (unused.isEmpty()) null else PendingClearUnused(type, title, unused)
        }
    }

    fun confirmClearUnused() {
        val pending = pendingClearUnused.value ?: return
        pendingClearUnused.value = null
        viewModelScope.launch { categories.deleteUnused(pending.type, pending.unused) }
    }

    fun cancelClearUnused() {
        pendingClearUnused.value = null
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
                claudeKeyStore = graph.claudeKeyStore,
            )
        }
    }
}
