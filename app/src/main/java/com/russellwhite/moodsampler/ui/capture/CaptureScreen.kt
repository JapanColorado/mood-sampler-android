package com.russellwhite.moodsampler.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.russellwhite.moodsampler.data.db.CategoryEntity

@Composable
fun CaptureScreen(promptId: Long, onDone: () -> Unit) {
    val viewModel: CaptureViewModel =
        viewModel(key = "capture-$promptId", factory = CaptureViewModel.factory(promptId))
    val state by viewModel.state.collectAsState()

    when (state) {
        CaptureUiState.Loading -> Box(Modifier.fillMaxSize()) {}
        CaptureUiState.Saved -> LaunchedEffect(Unit) { onDone() }
        CaptureUiState.AlreadyAnswered -> InfoScreen(
            title = "Already logged",
            body = "You already answered this prompt — nothing more to do.",
            onDone = onDone,
        )
        CaptureUiState.Expired -> InfoScreen(
            title = "This prompt expired",
            body = "It's been counted as missed to keep your stats honest. " +
                "Catch the next one when it comes!",
            onDone = onDone,
        )
        CaptureUiState.Answerable -> CaptureForm(viewModel)
    }
}

@Composable
private fun InfoScreen(title: String, body: String, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onDone) { Text("Close") }
    }
}

@Composable
private fun CaptureForm(viewModel: CaptureViewModel) {
    val activities by viewModel.activities.collectAsState()
    val locations by viewModel.locations.collectAsState()
    val socials by viewModel.socials.collectAsState()

    var valence by rememberSaveable { mutableFloatStateOf(0f) }
    var arousal by rememberSaveable { mutableFloatStateOf(0f) }
    var activityId by rememberSaveable { mutableStateOf<Long?>(null) }
    var locationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var socialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Right now…", style = MaterialTheme.typography.headlineSmall)

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

        ChipSection("What are you doing?", activities, activityId) {
            activityId = if (activityId == it) null else it
        }
        ChipSection("Where are you?", locations, locationId) {
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
            onClick = { viewModel.save(valence, arousal, activityId, locationId, socialId, note) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Save", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
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
