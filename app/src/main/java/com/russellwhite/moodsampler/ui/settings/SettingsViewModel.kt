package com.russellwhite.moodsampler.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.CsvExporter
import com.russellwhite.moodsampler.data.db.CategoryDao
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.settings.AppSettings
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val categoryDao: CategoryDao,
    private val scheduler: PromptScheduler,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val categories: StateFlow<Map<CategoryType, List<CategoryEntity>>> =
        categoryDao.allFlow()
            .map { all ->
                all.filter { !it.isArchived }
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    .groupBy { it.type }
            }
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
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            categoryDao.insert(
                CategoryEntity(
                    type = type,
                    label = trimmed,
                    sortOrder = categoryDao.maxSortOrder(type) + 1,
                )
            )
        }
    }

    fun renameCategory(category: CategoryEntity, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { categoryDao.update(category.copy(label = trimmed)) }
    }

    fun archiveCategory(category: CategoryEntity) {
        viewModelScope.launch { categoryDao.update(category.copy(isArchived = true)) }
    }

    fun exportCsv(onReady: (Uri) -> Unit) {
        viewModelScope.launch { onReady(csvExporter.export()) }
    }

    companion object {
        val Factory = graphViewModelFactory { graph ->
            SettingsViewModel(
                settingsRepository = graph.settingsRepository,
                categoryDao = graph.database.categoryDao(),
                scheduler = graph.promptScheduler,
                csvExporter = graph.csvExporter,
            )
        }
    }
}
