package com.russellwhite.moodsampler.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.SkipReason
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.PromptWithSample
import com.russellwhite.moodsampler.scheduling.DeviceClock
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryRow(
    val promptId: Long,
    val time: String,
    val answered: Boolean,
    /** "Missed" / "Skipped · Busy" for unanswered rows. */
    val missedLabel: String,
    val valence: Float?,
    val arousal: Float?,
    val pain: Int?,
    val activities: List<String>,
    val location: String?,
    val social: String?,
    val note: String?,
) {
    val tags: List<String> get() = activities + listOfNotNull(location, social)
}

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>) {
    val answered: Int get() = rows.count { it.answered }
}

class HistoryViewModel(
    db: AppDatabase,
    private val clock: Clock = DeviceClock,
) : ViewModel() {

    val query = MutableStateFlow("")

    val days: StateFlow<List<HistoryDay>> = combine(
        db.scheduledPromptDao().historyFlow(),
        db.categoryDao().allFlow(),
        query,
    ) { prompts, categories, q ->
        val labels = categories.associate { it.id to it.label }
        val needle = q.trim().lowercase()
        prompts
            .map { toRow(it, labels) }
            .filter { needle.isEmpty() || it.matches(needle) }
            .groupBy { it.first }
            .map { (date, rows) -> HistoryDay(date, rows.map { it.second }) }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun Pair<LocalDate, HistoryRow>.matches(needle: String): Boolean {
        val row = second
        return row.note?.lowercase()?.contains(needle) == true ||
            row.tags.any { it.lowercase().contains(needle) } ||
            (!row.answered && row.missedLabel.lowercase().contains(needle))
    }

    private fun toRow(row: PromptWithSample, labels: Map<Long, String>): Pair<LocalDate, HistoryRow> {
        val p = row.prompt
        val s = row.sample
        val date = runCatching { LocalDate.parse(p.localDate) }
            .getOrElse { Instant.ofEpochMilli(p.scheduledAtEpochMs).atZone(clock.zone).toLocalDate() }
        val time = Instant.ofEpochMilli(p.scheduledAtEpochMs).atZone(clock.zone).format(HHMM)
        return date to HistoryRow(
            promptId = p.id,
            time = time,
            answered = p.status == PromptStatus.ANSWERED && s != null,
            missedLabel = SkipReason.label(p.skipReason),
            valence = s?.sample?.valence,
            arousal = s?.sample?.arousal,
            pain = s?.sample?.pain,
            activities = s?.activityIds?.mapNotNull { labels[it] }.orEmpty(),
            location = s?.sample?.locationId?.let { labels[it] },
            social = s?.sample?.socialId?.let { labels[it] },
            note = s?.sample?.note,
        )
    }

    companion object {
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val Factory = graphViewModelFactory { graph -> HistoryViewModel(graph.database) }
    }
}
