package dev.russell.moodsampler.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.russell.moodsampler.ui.components.Aside
import dev.russell.moodsampler.ui.components.BigStat
import dev.russell.moodsampler.ui.components.Eyebrow
import dev.russell.moodsampler.ui.components.JournalCard
import dev.russell.moodsampler.ui.components.PillToggle
import dev.russell.moodsampler.ui.components.ScreenTitle
import dev.russell.moodsampler.ui.dashboard.charts.CategoryBars
import dev.russell.moodsampler.ui.dashboard.charts.HourOfDayChart
import dev.russell.moodsampler.ui.dashboard.charts.MoodTimeSeriesChart
import dev.russell.moodsampler.ui.dashboard.charts.PainLevelChart
import dev.russell.moodsampler.ui.dashboard.charts.PainTimeSeriesChart

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val data by viewModel.data.collectAsState()
    val range by viewModel.range.collectAsState()
    val measure by viewModel.measure.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("Insights")

        PillToggle(
            options = DashboardRange.entries,
            selected = range,
            label = { it.label },
            onSelect = { viewModel.range.value = it },
        )

        if (data.isEmpty) {
            Aside(
                "No data in this range yet. Once prompts start landing, your mood " +
                    "patterns will show up here.",
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            BigStat("${data.responseRate ?: 0}%", "response rate", Modifier.weight(1f))
            BigStat("${data.answered}", "samples", Modifier.weight(1f))
            BigStat("${data.missed}", "missed", Modifier.weight(1f))
        }

        Section("Mood over time", "Daily means; 0–10") {
            if (data.days.size >= 2) {
                MoodTimeSeriesChart(data.days)
            } else {
                SectionPlaceholder("Needs at least two days of samples.")
            }
        }

        Section("Pain over time", "Daily mean; 0 (none) to 10") {
            when {
                data.painDays.size >= 2 -> PainTimeSeriesChart(data.painDays)
                data.painDays.isEmpty() -> SectionPlaceholder("No pain ratings yet.")
                else -> SectionPlaceholder("Needs at least two days of pain ratings.")
            }
        }

        Section("Valence by pain level", "How mood tracks pain, 0–10") {
            if (data.painByLevel.size >= 2) {
                PainLevelChart(data.painByLevel)
            } else {
                SectionPlaceholder("Needs samples at two or more pain levels.")
            }
        }

        // Breakdown measure: drives the hour chart and the three category sections.
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Break down by")
            PillToggle(
                options = Measure.entries,
                selected = measure,
                label = { it.label },
                onSelect = { viewModel.measure.value = it },
            )
        }

        // `data.measure` (not `measure`) so charts and labels flip together once the recompute lands.
        val shown = data.measure
        val word = shown.label.lowercase()
        Section("${shown.label} by time of day", "Mean across all sampled days") {
            if (data.hours.isNotEmpty()) {
                HourOfDayChart(data.hours, shown)
            } else {
                SectionPlaceholder(noMeasureText(shown))
            }
        }

        Section("By activity", "Mean $word while doing… (a sample counts under each activity it tagged)") {
            CategoryBarsOrPlaceholder(data.byActivity, shown)
        }
        Section("By place", "Mean $word while at…") {
            CategoryBarsOrPlaceholder(data.byLocation, shown)
        }
        Section("By company", "Mean $word while with…") {
            CategoryBarsOrPlaceholder(data.bySocial, shown)
        }
    }
}

private fun noMeasureText(measure: Measure) =
    if (measure == Measure.PAIN) "No pain ratings yet." else "No samples yet."

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    JournalCard(spacing = 16.dp) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun CategoryBarsOrPlaceholder(rows: List<CategoryMood>, measure: Measure) {
    if (rows.isEmpty()) SectionPlaceholder("No categorized samples yet.") else CategoryBars(rows, measure)
}

@Composable
private fun SectionPlaceholder(text: String) {
    Aside(text)
}
