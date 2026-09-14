package com.russellwhite.moodsampler.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russellwhite.moodsampler.data.PainScale
import com.russellwhite.moodsampler.data.SkipReason
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.byActivityGroup
import kotlin.math.roundToInt

/** Everything the user enters on the capture form; also the seed for the edit screen. */
data class CaptureFormValues(
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val pain: Int = 0,
    val activityIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val socialId: Long? = null,
    val note: String = "",
)

/**
 * The shared mood-capture form. Used for live prompts (with a "Can't answer now"
 * skip path) and for editing a past answer from History (no skip path).
 */
@Composable
internal fun CaptureForm(
    initial: CaptureFormValues,
    activities: List<CategoryEntity>,
    locations: List<CategoryEntity>,
    socials: List<CategoryEntity>,
    /** Sample counts per category id; orders the short place list by "most used". */
    usage: Map<Long, Int>,
    locationStatus: LocationStatus,
    /** Place matched from the fix; preselected until the user touches the place row. */
    suggestedLocationId: Long?,
    saveLabel: String,
    onSave: (CaptureFormValues) -> Unit,
    onSkip: ((reason: String) -> Unit)?,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = { Text("Right now…", style = MaterialTheme.typography.headlineSmall) },
) {
    var valence by rememberSaveable { mutableFloatStateOf(initial.valence) }
    var arousal by rememberSaveable { mutableFloatStateOf(initial.arousal) }
    var pain by rememberSaveable { mutableIntStateOf(initial.pain) }
    val activityIds = rememberSaveable(saver = LongListSaver) { initial.activityIds.toMutableStateList() }
    var locationId by rememberSaveable { mutableStateOf(initial.locationId) }
    var socialId by rememberSaveable { mutableStateOf(initial.socialId) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var showSkip by rememberSaveable { mutableStateOf(false) }
    var placeTouched by rememberSaveable { mutableStateOf(false) }

    // The fix may land after the form is on screen; only fill an untouched, empty place.
    LaunchedEffect(suggestedLocationId) {
        if (suggestedLocationId != null && !placeTouched && locationId == null) locationId = suggestedLocationId
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        header()

        MoodSlider(
            title = "How pleasant do you feel?",
            leftAnchor = "Unpleasant",
            rightAnchor = "Pleasant",
            value = valence,
            onChange = { valence = it },
        )
        MoodSlider(
            title = "How much energy do you have?",
            leftAnchor = "Low energy",
            rightAnchor = "High energy",
            value = arousal,
            onChange = { arousal = it },
        )
        PainSlider(value = pain, onChange = { pain = it })

        GroupedActivitySection("What are you doing? (pick any)", activities, activityIds) { id ->
            if (!activityIds.remove(id)) activityIds.add(id)
        }
        PlaceSection(
            locations = locations,
            usage = usage,
            selectedId = locationId,
            suggestedId = suggestedLocationId,
            status = locationStatus,
        ) {
            placeTouched = true
            locationId = if (locationId == it) null else it
        }
        ChipSection("Who are you with?", socials, socialId) {
            socialId = if (socialId == it) null else it
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Anything worth remembering? (optional)") },
            minLines = 1,
            maxLines = 4,
        )

        Button(
            onClick = {
                onSave(
                    CaptureFormValues(
                        valence = valence,
                        arousal = arousal,
                        pain = pain,
                        activityIds = activityIds.toList(),
                        locationId = locationId,
                        socialId = socialId,
                        note = note,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(saveLabel, style = MaterialTheme.typography.titleMedium)
        }
        if (onSkip != null) {
            TextButton(onClick = { showSkip = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Can't answer now")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSkip && onSkip != null) {
        SkipDialog(onDismiss = { showSkip = false }, onConfirm = { showSkip = false; onSkip(it) })
    }
}

private val LongListSaver = listSaver<SnapshotStateList<Long>, Long>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
private fun SkipDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip this prompt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "It will count as missed — that keeps your response rate honest — " +
                        "but the reason is saved so you can see why later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ReasonChips(selected = reason, onSelect = { reason = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason ?: SkipReason.OTHER) }, enabled = reason != null) {
                Text("Skip")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonChips(selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SkipReason.CAPTURE_CHOICES.forEach { (code, label) ->
            FilterChip(selected = code == selected, onClick = { onSelect(code) }, label = { Text(label) })
        }
    }
}

@Composable
private fun MoodSlider(
    title: String,
    leftAnchor: String,
    rightAnchor: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Slider(value = value, onValueChange = onChange, valueRange = -1f..1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftAnchor, style = MaterialTheme.typography.labelMedium)
            Text(rightAnchor, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 0–10 pain rating; snaps to whole numbers and names the level. */
@Composable
private fun PainSlider(value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Any physical pain right now?", style = MaterialTheme.typography.titleMedium)
            Text(
                "$value · ${PainScale.descriptor(value)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(PainScale.MIN, PainScale.MAX)) },
            valueRange = PainScale.MIN.toFloat()..PainScale.MAX.toFloat(),
            steps = PainScale.MAX - PainScale.MIN - 1,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("No pain", style = MaterialTheme.typography.labelMedium)
            Text("Worst imaginable", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipSection(
    title: String,
    options: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/**
 * Place picker that needs zero taps most of the time: shows the suggested place,
 * the most-used places and the current selection; the full list is one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceSection(
    locations: List<CategoryEntity>,
    usage: Map<Long, Int>,
    selectedId: Long?,
    suggestedId: Long?,
    status: LocationStatus,
    onSelect: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shortlist = remember(locations, usage, suggestedId, selectedId) {
        val pinned = listOfNotNull(suggestedId, selectedId)
        val top = locations.sortedByDescending { usage[it.id] ?: 0 }.take(SHORTLIST_SIZE).map { it.id }
        val keep = (pinned + top).toSet()
        locations.filter { it.id in keep }
    }
    val hasMore = shortlist.size < locations.size
    val showAll = expanded || !hasMore
    Column {
        Text("Where are you?", style = MaterialTheme.typography.titleMedium)
        LocationCaption(status, suggestedId?.let { id -> locations.firstOrNull { it.id == id }?.label })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (showAll) locations else shortlist).forEach { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.label) },
                )
            }
            if (hasMore) {
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "Fewer" else "More…") },
                )
            }
        }
    }
}

@Composable
private fun LocationCaption(status: LocationStatus, suggestedLabel: String?) {
    val text = when (status) {
        LocationStatus.Off -> return
        LocationStatus.Acquiring -> "Finding location…"
        LocationStatus.Unavailable -> "Location unavailable"
        is LocationStatus.Got -> buildString {
            append("📍 Location captured")
            status.accuracyM?.let { append(" (±${it.roundToInt()} m)") }
            if (suggestedLabel != null) append(" · suggested: $suggestedLabel")
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val SHORTLIST_SIZE = 5

/** Section header for one [com.russellwhite.moodsampler.data.db.ActivityGroup] of chips. */
@Composable
internal fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Multi-select activities under one header per activity group; empty groups are omitted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupedActivitySection(
    title: String,
    activities: List<CategoryEntity>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
) {
    val groups = remember(activities) { activities.byActivityGroup() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        groups.forEach { (group, options) ->
            GroupHeader(group.label)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.id in selectedIds,
                        onClick = { onToggle(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}
