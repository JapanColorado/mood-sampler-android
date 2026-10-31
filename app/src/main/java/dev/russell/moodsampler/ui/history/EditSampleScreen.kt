package dev.russell.moodsampler.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.russell.moodsampler.ui.capture.CaptureForm
import dev.russell.moodsampler.ui.capture.CompanyStatus
import dev.russell.moodsampler.ui.capture.LocationStatus
import dev.russell.moodsampler.ui.components.Aside

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSampleScreen(promptId: Long, onDone: () -> Unit) {
    val viewModel: EditSampleViewModel =
        viewModel(key = "edit-$promptId", factory = EditSampleViewModel.factory(promptId))
    val state by viewModel.state.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is EditUiState.Editing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            EditUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding)) {}
            EditUiState.Done -> LaunchedEffect(Unit) { onDone() }
            EditUiState.Missing -> Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("This entry no longer exists.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onDone) { Text("Back") }
            }
            is EditUiState.Editing -> {
                // The form opens its Manual section only when Claude isn't set up; wait to know which.
                val assistAvailable by viewModel.assistAvailable.collectAsState()
                val hasAssist = assistAvailable ?: return@Scaffold
                val activities by viewModel.activities.collectAsState()
                val locations by viewModel.locations.collectAsState()
                val socials by viewModel.socials.collectAsState()
                val painTypes by viewModel.painTypes.collectAsState()
                val usage by viewModel.usage.collectAsState()
                CaptureForm(
                    initial = s.initial,
                    activities = activities,
                    locations = locations,
                    socials = socials,
                    painTypes = painTypes,
                    usage = usage,
                    locationStatus = LocationStatus.Off,
                    suggestedLocationId = null,
                    companyStatus = CompanyStatus.Off,
                    suggestedSocialId = null,
                    onAssist = if (hasAssist) viewModel::assist else null,
                    saveLabel = "Update",
                    onSave = viewModel::save,
                    onAddOption = viewModel::addOption,
                    onSkip = null,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    moodTint = false,
                    header = {
                        Aside("Editing a past answer — try to recall the moment itself rather than how you feel now.")
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "The answer is removed and the prompt no longer counts toward your " +
                        "response rate (it isn't marked as missed). This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
