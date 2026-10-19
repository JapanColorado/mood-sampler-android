package com.russellwhite.moodsampler.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.russellwhite.moodsampler.ui.capture.GroupHeader
import com.russellwhite.moodsampler.ui.rememberBluetoothPermission
import com.russellwhite.moodsampler.ui.rememberLocationPermission
import com.russellwhite.moodsampler.data.CategoryGroup
import com.russellwhite.moodsampler.data.db.ActivityGroup
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.activityGroup
import com.russellwhite.moodsampler.data.db.byActivityGroup
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.settings.AppSettings
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsState()
    val categoryGroups by viewModel.categoryGroups.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val pendingRestore by viewModel.pendingRestore.collectAsState()
    val pendingWipe by viewModel.pendingWipe.collectAsState()
    val dataMessage by viewModel.dataMessage.collectAsState()
    val context = LocalContext.current

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backupTo) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspectRestore)
    }

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
                SamplingControls(
                    settings = settings,
                    onWindowsPerDay = viewModel::setWindowsPerDay,
                    onDayStart = viewModel::setDayStart,
                    onDayEnd = viewModel::setDayEnd,
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

        LocationCard(enabled = settings.autoLocation, onToggle = viewModel::setAutoLocation)
        CompanyScanCard(enabled = settings.autoCompany, onToggle = viewModel::setAutoCompany)

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        backupLauncher.launch("moodsampler-backup-${java.time.LocalDate.now()}.json")
                    }) { Text("Back up to file…") }
                    OutlinedButton(onClick = {
                        // Pickers often mislabel .json files, so accept the usual suspects.
                        restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                    }) { Text("Restore from file…") }
                }
                Text(
                    "A backup holds every prompt, answer, category and sampling setting. " +
                        "Restoring replaces everything on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = viewModel::askDeleteAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) { Text("Delete all data…") }
                Text(
                    "Removes every answer and prompt. Your category lists and sampling " +
                        "settings stay. Back up first if you might want the data later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExactAlarmCard()
    }

    pendingRestore?.let { pending ->
        val exported = pending.incoming.exportedAtEpochMs?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        } ?: "unknown date"
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace all current data?") },
            text = {
                Text(
                    "This deletes the ${pending.current.samples} samples and ${pending.current.prompts} prompts " +
                        "on this phone and restores the backup from $exported " +
                        "(${pending.incoming.samples} samples, ${pending.incoming.prompts} prompts). " +
                        "The upcoming schedule is redrawn afterwards. This can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Replace") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }

    pendingWipe?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteAll,
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This permanently deletes ${summary.samples} samples and ${summary.prompts} prompts " +
                        "from this phone. Categories and settings are kept, and a fresh schedule is drawn. " +
                        "This can't be undone — export a backup first if in doubt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteAll) { Text("Cancel") } },
        )
    }

    dataMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Data") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
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

/** Opt-in one-shot location per answer, plus the runtime permission it needs. */
@Composable
private fun LocationCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val permission = rememberLocationPermission()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Record location with each answer", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                "One fix per answer, only while the capture screen is open — no background tracking. " +
                    "Used to pre-select your place from where you answered before. " +
                    "Never leaves the phone except in your own exports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled && !permission.granted) {
                Text(
                    "Location permission is off, so nothing is recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = permission.request) { Text("Allow location") }
            }
        }
    }
}

/** Opt-in nearby-device scan per answer for company suggestions, plus the permission it needs. */
@Composable
private fun CompanyScanCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val permission = rememberBluetoothPermission()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Suggest company from nearby devices", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                "A few-second Bluetooth scan per answer, only while the capture screen is open. " +
                    "Devices that keep showing up when you pick the same company (a partner's watch, " +
                    "a friend's earbuds, the family car) pre-select it next time. Only scrambled device " +
                    "IDs are stored, never names or addresses, and they're left out of the CSV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled && !permission.granted) {
                Text(
                    "Nearby-devices permission is off, so nothing is scanned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = permission.request) { Text("Allow nearby devices") }
            }
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
    val grouped = type == CategoryType.ACTIVITY
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
            // Activities are shown under their group headers; other types as one flat row.
            val sections: List<Pair<String?, List<CategoryEntity>>> = remember(group.active, grouped) {
                if (grouped) group.active.byActivityGroup().map { (g, items) -> g.label to items }
                else listOf(null to group.active)
            }
            sections.forEach { (header, items) ->
                if (header != null) GroupHeader(header)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { category ->
                        AssistChip(onClick = { editingId = category.id }, label = { Text(category.label) })
                    }
                }
            }
            Text(
                "Tap a chip to rename, ${if (grouped) "regroup, " else ""}reorder or archive it. " +
                    "Archived items keep their history.",
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
            onGroup = if (grouped) { g -> viewModel.setActivityGroup(category, g) } else null,
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
    /** Present for activities only. */
    onGroup: ((ActivityGroup) -> Unit)?,
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
                if (onGroup != null) {
                    GroupDropdown(current = category.activityGroup, onSelect = onGroup)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(current: ActivityGroup, onSelect: (ActivityGroup) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ActivityGroup.entries.forEach { g ->
                DropdownMenuItem(text = { Text(g.label) }, onClick = { onSelect(g); open = false })
            }
        }
    }
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
