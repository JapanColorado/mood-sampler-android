package com.russellwhite.moodsampler.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russellwhite.moodsampler.data.CategoryGroup
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.settings.AppSettings
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsState()
    val categoryGroups by viewModel.categoryGroups.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sampling", style = MaterialTheme.typography.titleMedium)
                Stepper(
                    label = "Prompts per day",
                    value = settings.windowsPerDay,
                    onChange = viewModel::setWindowsPerDay,
                    range = 2..12,
                )
                TimeSettingRow(
                    label = "Day starts",
                    minutes = settings.dayStartMinutes,
                    onChange = viewModel::setDayStart,
                )
                TimeSettingRow(
                    label = "Day ends",
                    minutes = settings.dayEndMinutes,
                    onChange = viewModel::setDayEnd,
                    nextDay = settings.toSamplingConfig().wrapsPastMidnight,
                )
                Text(
                    spanDescription(settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Stepper(
                    label = "Prompt expires after (min)",
                    value = settings.expiryMinutes,
                    onChange = viewModel::setExpiryMinutes,
                    range = 5..120,
                    step = 5,
                )
                Text(
                    "Changing these regenerates the upcoming random schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TimeZoneCard()

        CategoryEditorCard("Activities", CategoryType.ACTIVITY, categoryGroups, usage, viewModel)
        CategoryEditorCard("Places", CategoryType.LOCATION, categoryGroups, usage, viewModel)
        CategoryEditorCard("Company", CategoryType.SOCIAL, categoryGroups, usage, viewModel)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Data", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    viewModel.exportCsv { uri ->
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(
                            Intent.createChooser(send, "Export mood samples")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Export all samples as CSV") }
                Text(
                    "Includes missed prompts (empty rows) so response rates stay honest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExactAlarmCard()
    }
}

/**
 * Read-only: the schedule always follows the phone's zone (see
 * [com.russellwhite.moodsampler.scheduling.DeviceClock]). Re-read on resume
 * so a zone change made in system settings shows up without restarting.
 */
@Composable
private fun TimeZoneCard() {
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        zone = ZoneId.systemDefault()
        onPauseOrDispose { }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Time zone", style = MaterialTheme.typography.titleMedium)
            Text(
                "${zone.getDisplayName(TextStyle.FULL, Locale.getDefault())} (${zone.id})",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Follows the phone's time zone. Day start and end are local times, " +
                    "and the upcoming schedule is redrawn automatically if the zone changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExactAlarmCard() {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    var canExact by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        canExact = alarmManager.canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    if (!canExact) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Exact timing is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Prompts will still arrive, but with up to ±10 min of drift. " +
                        "Allow exact alarms for precise random timing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Allow exact alarms") }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value - step >= range.first,
        ) { Text("−") }
        Text(
            "$value",
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value + step <= range.last,
        ) { Text("+") }
    }
}

/**
 * Human-readable span, including the past-midnight case an end time before the
 * start time implies (09:00–01:00 is a 16-hour day ending in the small hours).
 */
private fun spanDescription(settings: AppSettings): String {
    val config = settings.toSamplingConfig()
    val hours = config.spanMinutes / 60
    val minutes = config.spanMinutes % 60
    val length = buildString {
        if (hours > 0) append("${hours}h")
        if (minutes > 0) append(if (hours > 0) " ${minutes}m" else "${minutes}m")
    }
    return when {
        config.spanMinutes == 24 * 60 ->
            "Start and end match, so prompts run around the clock."
        config.wrapsPastMidnight ->
            "Ends after midnight: a $length day, and prompts up to " +
                "${config.dayEnd} count toward the day that started them."
        else -> "A $length day."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSettingRow(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    nextDay: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { showPicker = true }) {
            val time = LocalTime.ofSecondOfDay(minutes * 60L).toString()
            Text(if (nextDay) "$time (next day)" else time)
        }
    }
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorCard(
    title: String,
    type: CategoryType,
    groups: Map<CategoryType, CategoryGroup>,
    usage: Map<Long, Int>,
    viewModel: SettingsViewModel,
) {
    val group = groups[type] ?: CategoryGroup()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add $title")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.active.forEach { category ->
                    AssistChip(onClick = { editingId = category.id }, label = { Text(category.label) })
                }
            }
            Text(
                "Tap a chip to rename, reorder or archive it. Archived items keep their history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.archived.isNotEmpty()) {
                TextButton(onClick = { showArchived = !showArchived }) {
                    Text(if (showArchived) "Hide archived (${group.archived.size})" else "Archived (${group.archived.size})")
                }
                if (showArchived) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.archived.forEach { category ->
                            AssistChip(
                                onClick = { editingId = category.id },
                                label = { Text(category.label) },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        TextFieldDialog(
            title = "New ${title.lowercase().dropLast(if (title.endsWith("s")) 1 else 0)}",
            initial = "",
            confirmLabel = "Add",
            onConfirm = { viewModel.addCategory(type, it); adding = false },
            onDismiss = { adding = false },
        )
    }

    // Resolve from the live list so the dialog reflects moves/renames as they land.
    val editing = editingId?.let { id -> (group.active + group.archived).firstOrNull { it.id == id } }
    if (editingId != null && editing == null) editingId = null
    editing?.let { category ->
        val index = group.active.indexOfFirst { it.id == category.id }
        CategoryEditDialog(
            category = category,
            usageCount = usage[category.id] ?: 0,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < group.active.lastIndex,
            onRename = { viewModel.renameCategory(category, it); editingId = null },
            onMove = { viewModel.moveCategory(category, it) },
            onArchive = { viewModel.archiveCategory(category); editingId = null },
            onRestore = { viewModel.restoreCategory(category); editingId = null },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    category: CategoryEntity,
    usageCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(category.id) { mutableStateOf(category.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.isArchived) "Archived: ${category.label}" else "Edit \"${category.label}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Label") },
                )
                Text(
                    when (usageCount) {
                        0 -> "Not used in any sample yet — safe to archive."
                        1 -> "Used in 1 sample. Archiving keeps it in that sample's history."
                        else -> "Used in $usageCount samples. Archiving keeps it in their history."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!category.isArchived) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Position", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { onMove(+1) }, enabled = canMoveDown) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(text) },
                enabled = text.isNotBlank() && text.trim() != category.label,
            ) { Text("Rename") }
        },
        dismissButton = {
            Row {
                if (category.isArchived) {
                    TextButton(onClick = onRestore) { Text("Restore") }
                } else {
                    TextButton(onClick = onArchive) { Text("Archive") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun TextFieldDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
