package dev.russell.moodsampler.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.russell.moodsampler.data.settings.AppSettings
import java.time.LocalTime

/**
 * Prompts-per-day and day start/end controls, shared by Settings and onboarding.
 * The caller decides whether a change should regenerate the schedule.
 */
@Composable
fun SamplingControls(
    settings: AppSettings,
    onWindowsPerDay: (Int) -> Unit,
    onDayStart: (Int) -> Unit,
    onDayEnd: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stepper(
            label = "Prompts per day",
            value = settings.windowsPerDay,
            onChange = onWindowsPerDay,
            range = 2..12,
        )
        TimeSettingRow(
            label = "Day starts",
            minutes = settings.dayStartMinutes,
            onChange = onDayStart,
        )
        TimeSettingRow(
            label = "Day ends",
            minutes = settings.dayEndMinutes,
            onChange = onDayEnd,
            nextDay = settings.toSamplingConfig().wrapsPastMidnight,
        )
        Text(
            spanDescription(settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value - step >= range.first,
        ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease $label") }
        Text(
            "$value",
            Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value + step <= range.last,
        ) { Icon(Icons.Filled.Add, contentDescription = "Increase $label") }
    }
}

/**
 * Human-readable span, including the past-midnight case an end time before the
 * start time implies (09:00–01:00 is a 16-hour day ending in the small hours).
 */
internal fun spanDescription(settings: AppSettings): String {
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
internal fun TimeSettingRow(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    nextDay: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = { showPicker = true }) {
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

