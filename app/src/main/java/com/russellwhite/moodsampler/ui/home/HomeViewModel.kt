package com.russellwhite.moodsampler.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.settings.AppSettings
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val answeredToday: Int = 0,
    val missedToday: Int = 0,
    val expectedToday: Int = 0,
    val weekAnswered: Int = 0,
    val weekMissed: Int = 0,
    val settings: AppSettings = AppSettings(),
) {
    val weekResponseRate: Int?
        get() = (weekAnswered + weekMissed).takeIf { it > 0 }
            ?.let { (weekAnswered * 100) / it }

    val samplingSummary: String
        get() {
            val start = LocalTime.ofSecondOfDay(settings.dayStartMinutes * 60L)
            val end = LocalTime.ofSecondOfDay(settings.dayEndMinutes * 60L)
            return "Sampling $start–$end · ~${settings.windowsPerDay} prompts/day"
        }
}

class HomeViewModel(
    db: AppDatabase,
    settingsRepository: SettingsRepository,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    // NOTE: deliberately no "next prompt at HH:MM" anywhere on this screen.
    // Knowing when a prompt will arrive lets the user (consciously or not)
    // prepare for it, which biases what random sampling is supposed to measure.
    val state: StateFlow<HomeUiState> = combine(
        db.scheduledPromptDao().promptsForDateFlow(LocalDate.now(clock).toString()),
        db.scheduledPromptDao().countByStatusSinceFlow(
            PromptStatus.ANSWERED, clock.millis() - WEEK_MS,
        ),
        db.scheduledPromptDao().countByStatusSinceFlow(
            PromptStatus.MISSED, clock.millis() - WEEK_MS,
        ),
        settingsRepository.settings,
    ) { today, weekAnswered, weekMissed, settings ->
        HomeUiState(
            answeredToday = today.count { it.status == PromptStatus.ANSWERED },
            missedToday = today.count { it.status == PromptStatus.MISSED },
            expectedToday = today.count { it.status != PromptStatus.CANCELLED },
            weekAnswered = weekAnswered,
            weekMissed = weekMissed,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        val Factory = graphViewModelFactory { graph ->
            HomeViewModel(graph.database, graph.settingsRepository)
        }
    }
}
