package com.russellwhite.moodsampler.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.CategoryGroup
import com.russellwhite.moodsampler.data.CategoryRepository
import com.russellwhite.moodsampler.data.CsvExporter
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

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val categories: CategoryRepository,
    private val scheduler: PromptScheduler,
    private val csvExporter: CsvExporter,
) : ViewModel() {

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

    fun moveCategory(category: CategoryEntity, delta: Int) {
        viewModelScope.launch { categories.move(category, delta) }
    }

    fun exportCsv(onReady: (Uri) -> Unit) {
        viewModelScope.launch { onReady(csvExporter.export()) }
    }

    companion object {
        val Factory = graphViewModelFactory { graph ->
            SettingsViewModel(
                settingsRepository = graph.settingsRepository,
                categories = graph.categoryRepository,
                scheduler = graph.promptScheduler,
                csvExporter = graph.csvExporter,
            )
        }
    }
}
