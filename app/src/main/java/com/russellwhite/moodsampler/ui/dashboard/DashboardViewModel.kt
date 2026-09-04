package com.russellwhite.moodsampler.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.PromptWithSample
import com.russellwhite.moodsampler.data.db.ScheduledPromptEntity
import com.russellwhite.moodsampler.scheduling.DeviceClock
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class DashboardRange(val label: String, val days: Long?) {
    TWO_WEEKS("2w", 14),
    SIX_WEEKS("6w", 42),
    ALL("All", null),
}

data class DayMood(val date: LocalDate, val valence: Double, val arousal: Double, val n: Int)
data class HourMood(val hour: Int, val valence: Double, val n: Int)
data class CategoryMood(val label: String, val valence: Double, val n: Int)

data class DashboardData(
    val days: List<DayMood> = emptyList(),
    val hours: List<HourMood> = emptyList(),
    val byActivity: List<CategoryMood> = emptyList(),
    val byLocation: List<CategoryMood> = emptyList(),
    val bySocial: List<CategoryMood> = emptyList(),
    val answered: Int = 0,
    val missed: Int = 0,
) {
    val responseRate: Int? get() = (answered + missed).takeIf { it > 0 }?.let { answered * 100 / it }
    val isEmpty: Boolean get() = answered == 0 && missed == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    db: AppDatabase,
    private val clock: Clock = DeviceClock,
) : ViewModel() {

    val range = MutableStateFlow(DashboardRange.TWO_WEEKS)

    val data: StateFlow<DashboardData> = range
        .flatMapLatest { r ->
            val sinceMs = r.days?.let { clock.millis() - it * 24 * 60 * 60 * 1000 } ?: 0L
            combine(
                db.scheduledPromptDao().concludedPromptsSinceFlow(sinceMs),
                db.categoryDao().allFlow(),
            ) { prompts, categories ->
                aggregate(prompts, categories.associate { it.id to it.label })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardData())

    private fun aggregate(
        prompts: List<PromptWithSample>,
        labels: Map<Long, String>,
    ): DashboardData {
        val zone = clock.zone
        val answered = prompts.filter { it.prompt.status == PromptStatus.ANSWERED && it.sample != null }
        val samples = answered.map { it.prompt to it.sample!!.sample }
        val activityRefs = answered.flatMap { row -> row.sample!!.activityIds.map { it to row.sample.sample } }

        val days = samples
            .groupBy { samplingDate(it.first, zone) }
            .map { (date, rows) ->
                DayMood(
                    date = date,
                    valence = rows.map { it.second.valence.toDouble() }.average(),
                    arousal = rows.map { it.second.arousal.toDouble() }.average(),
                    n = rows.size,
                )
            }
            .sortedBy { it.date }

        val hours = samples
            .groupBy {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.first.scheduledAtEpochMs), zone).hour
            }
            .map { (hour, rows) ->
                HourMood(hour, rows.map { it.second.valence.toDouble() }.average(), rows.size)
            }
            .sortedBy { it.hour }

        fun byCategory(pairs: List<Pair<Long, com.russellwhite.moodsampler.data.db.SampleEntity>>) =
            pairs
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (id, rows) ->
                    labels[id]?.let { label ->
                        CategoryMood(label, rows.map { it.valence.toDouble() }.average(), rows.size)
                    }
                }
                .sortedByDescending { it.valence }

        return DashboardData(
            days = days,
            hours = hours,
            byActivity = byCategory(activityRefs),
            byLocation = byCategory(samples.mapNotNull { (_, s) -> s.locationId?.let { it to s } }),
            bySocial = byCategory(samples.mapNotNull { (_, s) -> s.socialId?.let { it to s } }),
            answered = answered.size,
            missed = prompts.count { it.prompt.status == PromptStatus.MISSED },
        )
    }

    /**
     * The sampling day a prompt belongs to. Uses the stored day rather than the
     * calendar date so that with an AM end time a 00:30 sample charts under the
     * day it started, not the next one.
     */
    private fun samplingDate(prompt: ScheduledPromptEntity, zone: ZoneId): LocalDate =
        runCatching { LocalDate.parse(prompt.localDate) }.getOrElse {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(prompt.scheduledAtEpochMs), zone)
                .toLocalDate()
        }

    companion object {
        val Factory = graphViewModelFactory { graph -> DashboardViewModel(graph.database) }
    }
}
