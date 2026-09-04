package com.russellwhite.moodsampler.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russellwhite.moodsampler.ui.dashboard.charts.CategoryBars
import com.russellwhite.moodsampler.ui.dashboard.charts.HourOfDayChart
import com.russellwhite.moodsampler.ui.dashboard.charts.MoodTimeSeriesChart
import com.russellwhite.moodsampler.ui.dashboard.charts.PainLevelChart
import com.russellwhite.moodsampler.ui.dashboard.charts.PainTimeSeriesChart

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val data by viewModel.data.collectAsState()
    val range by viewModel.range.collectAsState()
    val measure by viewModel.measure.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineMedium)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DashboardRange.entries.forEachIndexed { index, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = { viewModel.range.value = r },
                    shape = SegmentedButtonDefaults.itemShape(index, DashboardRange.entries.size),
                ) { Text(r.label) }
            }
        }

        if (data.isEmpty) {
            Text(
                "No data in this range yet. Once prompts start landing, your mood " +
                    "patterns will show up here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile("${data.responseRate ?: 0}%", "response rate")
            StatTile("${data.answered}", "samples")
            StatTile("${data.missed}", "missed")
        }

        Section("Mood over time", "Daily means; –1 to +1") {
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

        Section("Pleasantness by pain level", "How mood tracks pain, 0–10") {
            if (data.painByLevel.size >= 2) {
                PainLevelChart(data.painByLevel)
            } else {
                SectionPlaceholder("Needs samples at two or more pain levels.")
            }
        }

        // Breakdown measure: drives the hour chart and the three category sections.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Break down by", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Measure.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = measure == m,
                        onClick = { viewModel.measure.value = m },
                        shape = SegmentedButtonDefaults.itemShape(index, Measure.entries.size),
                    ) { Text(m.label) }
                }
            }
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
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun CategoryBarsOrPlaceholder(rows: List<CategoryMood>, measure: Measure) {
    if (rows.isEmpty()) SectionPlaceholder("No categorized samples yet.") else CategoryBars(rows, measure)
}

@Composable
private fun SectionPlaceholder(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatTile(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
