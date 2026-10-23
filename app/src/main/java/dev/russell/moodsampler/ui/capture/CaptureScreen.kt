package dev.russell.moodsampler.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CaptureScreen(promptId: Long, onDone: () -> Unit) {
    val viewModel: CaptureViewModel =
        viewModel(key = "capture-$promptId", factory = CaptureViewModel.factory(promptId))
    val state by viewModel.state.collectAsState()

    when (state) {
        CaptureUiState.Loading -> Box(Modifier.fillMaxSize()) {}
        CaptureUiState.Saved, CaptureUiState.Skipped -> LaunchedEffect(Unit) { onDone() }
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
        CaptureUiState.Answerable -> {
            // The form opens its Manual section only when Claude isn't set up; wait to know which.
            val assistAvailable by viewModel.assistAvailable.collectAsState()
            val hasAssist = assistAvailable ?: return
            val activities by viewModel.activities.collectAsState()
            val locations by viewModel.locations.collectAsState()
            val socials by viewModel.socials.collectAsState()
            val painTypes by viewModel.painTypes.collectAsState()
            val usage by viewModel.usage.collectAsState()
            val locationStatus by viewModel.locationStatus.collectAsState()
            val suggested by viewModel.suggestedLocationId.collectAsState()
            val companyStatus by viewModel.companyStatus.collectAsState()
            val suggestedSocial by viewModel.suggestedSocialId.collectAsState()
            CaptureForm(
                initial = CaptureFormValues(),
                activities = activities,
                locations = locations,
                socials = socials,
                painTypes = painTypes,
                usage = usage,
                locationStatus = locationStatus,
                suggestedLocationId = suggested,
                companyStatus = companyStatus,
                suggestedSocialId = suggestedSocial,
                onAssist = if (hasAssist) viewModel::assist else null,
                saveLabel = "Save",
                onSave = viewModel::save,
                onAddOption = viewModel::addOption,
                onSkip = viewModel::skip,
            )
        }
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
