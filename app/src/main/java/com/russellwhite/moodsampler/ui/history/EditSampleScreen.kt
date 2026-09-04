package com.russellwhite.moodsampler.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.russellwhite.moodsampler.ui.capture.CaptureForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSampleScreen(promptId: Long, onDone: () -> Unit) {
    val viewModel: EditSampleViewModel =
        viewModel(key = "edit-$promptId", factory = EditSampleViewModel.factory(promptId))
    val state by viewModel.state.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
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
                val activities by viewModel.activities.collectAsState()
                val locations by viewModel.locations.collectAsState()
                val socials by viewModel.socials.collectAsState()
                CaptureForm(
                    initial = s.initial,
                    activities = activities,
                    locations = locations,
                    socials = socials,
                    saveLabel = "Update",
                    onSave = viewModel::save,
                    onSkip = null,
                    modifier = Modifier.padding(padding),
                    header = {
                        Text(
                            "Editing a past answer — try to recall the moment itself rather than how you feel now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
