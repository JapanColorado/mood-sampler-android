package com.russellwhite.moodsampler.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.MoodScale
import com.russellwhite.moodsampler.data.PainScale
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.PromptWithSample
import com.russellwhite.moodsampler.data.db.SampleEntity
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

/**
 * Which per-sample number the breakdown charts show. Diverging measures are bipolar
 * (awful ↔ great), so their bars grow either way from the scale's midpoint.
 */
enum class Measure(val label: String, val min: Double, val max: Double, val diverging: Boolean) {
    VALENCE("Valence", MoodScale.MIN.toDouble(), MoodScale.MAX.toDouble(), diverging = true),
    ENERGY("Energy", MoodScale.MIN.toDouble(), MoodScale.MAX.toDouble(), diverging = true),
    PAIN("Pain", PainScale.MIN.toDouble(), PainScale.MAX.toDouble(), diverging = false);

    val midpoint: Double get() = (min + max) / 2

    /** Null when the sample has no value for this measure (pre-v2 rows have no pain). */
    fun of(sample: SampleEntity): Double? = when (this) {
        VALENCE -> sample.valence.toDouble()
        ENERGY -> sample.arousal.toDouble()
        PAIN -> sample.pain?.toDouble()
    }
}

data class DayMood(val date: LocalDate, val valence: Double, val arousal: Double, val n: Int)
data class DayPain(val date: LocalDate, val pain: Double, val n: Int)
data class HourMood(val hour: Int, val value: Double, val n: Int)
data class CategoryMood(val label: String, val value: Double, val n: Int)
/** Mean valence of the samples rated at one pain level. */
data class PainLevelMood(val level: Int, val valence: Double, val n: Int)

data class DashboardData(
    val measure: Measure = Measure.VALENCE,
    val days: List<DayMood> = emptyList(),
    val painDays: List<DayPain> = emptyList(),
    val painByLevel: List<PainLevelMood> = emptyList(),
    /** Per-hour / per-category means of [measure]. */
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
    val measure = MutableStateFlow(Measure.VALENCE)

    val data: StateFlow<DashboardData> = combine(range, measure) { r, m -> r to m }
        .flatMapLatest { (r, m) ->
            val sinceMs = r.days?.let { clock.millis() - it * 24 * 60 * 60 * 1000 } ?: 0L
            combine(
                db.scheduledPromptDao().concludedPromptsSinceFlow(sinceMs),
                db.categoryDao().allFlow(),
            ) { prompts, categories ->
                aggregate(prompts, categories.associate { it.id to it.label }, m)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardData())

    private fun aggregate(
        prompts: List<PromptWithSample>,
        labels: Map<Long, String>,
        measure: Measure,
    ): DashboardData {
        val zone = clock.zone
        val answered = prompts.filter { it.prompt.status == PromptStatus.ANSWERED && it.sample != null }
        val samples = answered.map { it.prompt to it.sample!!.sample }

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

        val withPain = samples.filter { it.second.pain != null }
        val painDays = withPain
            .groupBy { samplingDate(it.first, zone) }
            .map { (date, rows) -> DayPain(date, rows.map { it.second.pain!!.toDouble() }.average(), rows.size) }
            .sortedBy { it.date }
        val painByLevel = withPain
            .groupBy { it.second.pain!! }
            .map { (level, rows) -> PainLevelMood(level, rows.map { it.second.valence.toDouble() }.average(), rows.size) }
            .sortedBy { it.level }

        // Breakdowns of the selected measure; samples lacking it are left out.
        val measured = samples.mapNotNull { (p, s) -> measure.of(s)?.let { Triple(p, s, it) } }

        val hours = measured
            .groupBy { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.first.scheduledAtEpochMs), zone).hour }
            .map { (hour, rows) -> HourMood(hour, rows.map { it.third }.average(), rows.size) }
            .sortedBy { it.hour }

        fun byCategory(pairs: List<Pair<Long, Double>>) =
            pairs
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (id, values) ->
                    labels[id]?.let { label -> CategoryMood(label, values.average(), values.size) }
                }
                .sortedByDescending { it.value }

        val activityPairs = answered.flatMap { row ->
            val value = measure.of(row.sample!!.sample) ?: return@flatMap emptyList()
            row.sample.activityIds.map { it to value }
        }

        return DashboardData(
            measure = measure,
            days = days,
            painDays = painDays,
            painByLevel = painByLevel,
            hours = hours,
            byActivity = byCategory(activityPairs),
            byLocation = byCategory(measured.mapNotNull { (_, s, v) -> s.locationId?.let { it to v } }),
            bySocial = byCategory(measured.mapNotNull { (_, s, v) -> s.socialId?.let { it to v } }),
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
