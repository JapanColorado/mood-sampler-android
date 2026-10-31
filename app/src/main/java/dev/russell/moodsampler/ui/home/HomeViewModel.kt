package dev.russell.moodsampler.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.db.PromptStatus
import dev.russell.moodsampler.data.db.ScheduledPromptEntity
import dev.russell.moodsampler.data.settings.AppSettings
import dev.russell.moodsampler.data.settings.SettingsRepository
import dev.russell.moodsampler.scheduling.DeviceClock
import dev.russell.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * One prompt of the sampling day, in order. Deliberately carries no time: the strip
 * shows how the day went, and a position on it says nothing about when the rest land.
 */
data class DayDot(val kind: Kind, val valence: Int? = null) {
    enum class Kind { ANSWERED, MISSED, OPEN, PENDING }
}

/** Mean valence of one sampling day; null when nothing was answered. */
data class DayMean(val date: LocalDate, val valence: Double?)

internal fun dayDots(today: List<ScheduledPromptEntity>, valenceByPrompt: Map<Long, Int>): List<DayDot> =
    today.mapNotNull { prompt ->
        when (prompt.status) {
            PromptStatus.ANSWERED -> DayDot(DayDot.Kind.ANSWERED, valenceByPrompt[prompt.id])
            PromptStatus.MISSED -> DayDot(DayDot.Kind.MISSED)
            PromptStatus.NOTIFIED -> DayDot(DayDot.Kind.OPEN)
            PromptStatus.PENDING -> DayDot(DayDot.Kind.PENDING)
            PromptStatus.CANCELLED -> null
        }
    }

/** The [days] sampling days ending at [lastDay], oldest first, grouped on the stored sampling day. */
internal fun dayMeans(answers: List<Pair<String, Int>>, lastDay: LocalDate, days: Int = 7): List<DayMean> {
    val byDay = answers.groupBy({ it.first }, { it.second })
    return (days - 1L downTo 0L).map { back ->
        val date = lastDay.minusDays(back)
        DayMean(date, byDay[date.toString()]?.average())
    }
}

data class HomeUiState(
    val answeredToday: Int = 0,
    val missedToday: Int = 0,
    val expectedToday: Int = 0,
    val weekAnswered: Int = 0,
    val weekMissed: Int = 0,
    val settings: AppSettings = AppSettings(),
    /** True while the clock is past midnight but still inside yesterday's sampling span. */
    val inLateNightTail: Boolean = false,
    /** A notified prompt still inside its expiry window; lets a dismissed notification be answered. */
    val openPromptId: Long? = null,
    /** Today's prompts as a strip of dots; see [DayDot]. */
    val dots: List<DayDot> = emptyList(),
    /** Mean valence of each of the last seven sampling days. */
    val weekMeans: List<DayMean> = emptyList(),
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
    private val clock: Clock = DeviceClock,
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
                dao.latestNotifiedFlow(),
                dao.concludedPromptsSinceFlow(clock.millis() - WEEK_MEANS_LOOKBACK_MS),
            ) { today, weekAnswered, weekMissed, notified, concluded ->
                val expiryMs = settings.expiryMinutes * 60_000L
                val open = notified?.takeIf {
                    clock.millis() - (it.notifiedAtEpochMs ?: it.scheduledAtEpochMs) < expiryMs
                }
                HomeUiState(
                    answeredToday = today.count { it.status == PromptStatus.ANSWERED },
                    missedToday = today.count { it.status == PromptStatus.MISSED },
                    expectedToday = today.count { it.status != PromptStatus.CANCELLED },
                    weekAnswered = weekAnswered,
                    weekMissed = weekMissed,
                    settings = settings,
                    inLateNightTail = samplingDay != now.toLocalDate(),
                    openPromptId = open?.id,
                    dots = dayDots(today, concluded.mapNotNull { row -> row.sample?.let { row.prompt.id to it.sample.valence } }.toMap()),
                    weekMeans = dayMeans(
                        concluded.mapNotNull { row -> row.sample?.let { row.prompt.localDate to it.sample.valence } },
                        samplingDay,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        // A day more than the seven shown, so the oldest day's early prompts still count.
        private const val WEEK_MEANS_LOOKBACK_MS = 8 * WEEK_MS / 7

        val Factory = graphViewModelFactory { graph ->
            HomeViewModel(graph.database, graph.settingsRepository)
        }
    }
}
