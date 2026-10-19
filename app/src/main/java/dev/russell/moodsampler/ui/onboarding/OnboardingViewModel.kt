package dev.russell.moodsampler.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.moodsampler.data.settings.AppSettings
import dev.russell.moodsampler.data.settings.SettingsRepository
import dev.russell.moodsampler.scheduling.PromptScheduler
import dev.russell.moodsampler.ui.graphViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val scheduler: PromptScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    // No schedule regeneration here: nothing is scheduled until onboarding finishes.
    fun setWindowsPerDay(value: Int) = launch { settingsRepository.setWindowsPerDay(value) }
    fun setDayStart(minutes: Int) = launch { settingsRepository.setDayStartMinutes(minutes) }
    fun setDayEnd(minutes: Int) = launch { settingsRepository.setDayEndMinutes(minutes) }

    /** Marks onboarding done and draws the first schedule; [onDone] runs afterwards. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone()
            scheduler.onConfigChanged()
            onDone()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = graphViewModelFactory { graph ->
            OnboardingViewModel(graph.settingsRepository, graph.promptScheduler)
        }
    }
}
