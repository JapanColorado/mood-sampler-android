package com.russellwhite.moodsampler.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.settings.AppSettings
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val answeredToday: Int = 0,
    val missedToday: Int = 0,
    val expectedToday: Int = 0,
    val weekAnswered: Int = 0,
    val weekMissed: Int = 0,
    val settings: AppSettings = AppSettings(),
    /** True while the clock is past midnight but still inside yesterday's sampling span. */
    val inLateNightTail: Boolean = false,
) {
    val weekResponseRate: Int?
        get() = (weekAnswered + weekMissed).takeIf { it > 0 }
            ?.let { (weekAnswered * 100) / it }

    val samplingSummary: String
        get() {
            val config = settings.toSamplingConfig()
            val start = config.dayStart.format(HHMM)
            val end = config.dayEnd.format(HHMM)
            val span = when {
                config.spanMinutes == FULL_DAY_MINUTES -> "Sampling around the clock"
                config.wrapsPastMidnight -> "Sampling $start–$end (next day)"
                else -> "Sampling $start–$end"
            }
            return "$span · ~${settings.windowsPerDay} prompts/day"
        }

    /** Explains the "Today" counts while they belong to a span that started yesterday. */
    val todayNote: String?
        get() = if (inLateNightTail) {
            val start = settings.toSamplingConfig().dayStart.format(HHMM)
            "Still counting the day that started yesterday at $start."
        } else {
            null
        }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val FULL_DAY_MINUTES = 24 * 60
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    db: AppDatabase,
    settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val dao = db.scheduledPromptDao()

    // NOTE: deliberately no "next prompt at HH:MM" anywhere on this screen.
    // Knowing when a prompt will arrive lets the user (consciously or not)
    // prepare for it, which biases what random sampling is supposed to measure.
    val state: StateFlow<HomeUiState> = settingsRepository.settings
        .flatMapLatest { settings ->
            // "Today" is the sampling day, so an AM end time keeps late-night
            // prompts on the day whose morning started them.
            val now = LocalDateTime.now(clock)
            val samplingDay = settings.toSamplingConfig().samplingDayFor(now)
            combine(
                dao.promptsForDateFlow(samplingDay.toString()),
                dao.countByStatusSinceFlow(PromptStatus.ANSWERED, clock.millis() - WEEK_MS),
                dao.countByStatusSinceFlow(PromptStatus.MISSED, clock.millis() - WEEK_MS),
            ) { today, weekAnswered, weekMissed ->
                HomeUiState(
                    answeredToday = today.count { it.status == PromptStatus.ANSWERED },
                    missedToday = today.count { it.status == PromptStatus.MISSED },
                    expectedToday = today.count { it.status != PromptStatus.CANCELLED },
                    weekAnswered = weekAnswered,
                    weekMissed = weekMissed,
                    settings = settings,
                    inLateNightTail = samplingDay != now.toLocalDate(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        val Factory = graphViewModelFactory { graph ->
            HomeViewModel(graph.database, graph.settingsRepository)
        }
    }
}
