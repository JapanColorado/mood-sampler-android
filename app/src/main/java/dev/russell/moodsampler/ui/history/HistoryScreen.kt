package dev.russell.moodsampler.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.russell.moodsampler.appGraph
import dev.russell.moodsampler.ui.capture.PhotoThumbnail
import dev.russell.moodsampler.ui.components.Aside
import dev.russell.moodsampler.ui.components.ScreenTitle
import dev.russell.moodsampler.ui.dashboard.charts.MoodChartColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onEdit: (promptId: Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val days by viewModel.days.collectAsState()
    val query by viewModel.query.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTitle("History")
            TextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = { Text("Search notes and tags") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
            )
        }

        if (days.isEmpty()) {
            Aside(
                if (query.isBlank()) "No concluded prompts yet. Answered and missed prompts will be listed here."
                else "Nothing matches \"$query\".",
                Modifier.padding(20.dp),
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            days.forEach { day ->
                stickyHeader(key = "h-${day.date}") { DayHeader(day.date, day.answered, day.rows.size) }
                items(day.rows, key = { it.promptId }) { row ->
                    if (row.answered) AnsweredRow(row) { onEdit(row.promptId) } else MissedRow(row)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, answered: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(date.format(dayFmt), style = MaterialTheme.typography.titleLarge)
        Text(
            "$answered/$total answered",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One stop on the day's timeline: the time, a marker on a vertical rail, and the
 * content. The rail runs the row's full height, so consecutive rows join up.
 */
@Composable
private fun TimelineRow(
    time: String,
    timeColor: Color,
    modifier: Modifier = Modifier,
    marker: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            time,
            style = MaterialTheme.typography.labelMedium,
            color = timeColor,
            modifier = Modifier.width(38.dp).padding(top = 8.dp),
        )
        Box(
            Modifier
                .width(MARKER_SIZE)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        rail,
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) { marker() }
        }
        Box(Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 16.dp)) { content() }
    }
}

private val MARKER_SIZE = 30.dp

@Composable
private fun AnsweredRow(row: HistoryRow, onClick: () -> Unit) {
    val photoStore = LocalContext.current.appGraph.photoStore
    TimelineRow(
        time = row.time,
        timeColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        marker = { ValenceMarker(row.valence) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnsweredSummary(row)
            }
            row.photoPath?.let { PhotoThumbnail(photoStore.uri(it)) }
        }
    }
}

/** The moment's valence, written in a disc of its own mood color. */
@Composable
private fun ValenceMarker(valence: Int?) {
    val color = valence?.let { MoodChartColors.valenceAt(it.toDouble()) } ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(MARKER_SIZE)
            .background(color, CircleShape)
            .semantics { contentDescription = "Valence ${valence ?: "unknown"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            valence?.toString() ?: "–",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MoodChartColors.contentOn(color),
        )
    }
}

@Composable
private fun AnsweredSummary(row: HistoryRow) {
    // Claude's summary reads better in a list than the raw narration; fall back to the note.
    (row.summary ?: row.note)?.let { text ->
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Reading("Energy", row.arousal?.toString() ?: "–")
        row.pain?.let { pain -> Reading("Pain", pain.toString(), alert = pain > 0) }
    }
    if (row.tags.isNotEmpty()) {
        Text(
            row.tags.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Reading(label: String, value: String, alert: Boolean = false) {
    Text(
        "$label $value",
        style = MaterialTheme.typography.labelMedium,
        color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MissedRow(row: HistoryRow) {
    TimelineRow(
        time = row.time,
        timeColor = MaterialTheme.colorScheme.onSurfaceVariant,
        marker = {
            Box(
                Modifier
                    .size(MARKER_SIZE)
                    .padding(9.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
    ) {
        Aside(row.missedLabel)
    }
}
