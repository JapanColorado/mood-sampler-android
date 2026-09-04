package com.russellwhite.moodsampler.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.data.PainScale
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.SampleWithActivities
import com.russellwhite.moodsampler.ui.capture.CaptureFormValues
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EditUiState {
    data object Loading : EditUiState
    data class Editing(val initial: CaptureFormValues) : EditUiState
    /** The sample no longer exists (deleted, or the prompt was never answered). */
    data object Missing : EditUiState
    data object Done : EditUiState
}

/** Edits or deletes an existing answer. No expiry check: the answer already exists. */
class EditSampleViewModel(
    private val promptId: Long,
    private val db: AppDatabase,
    private val repository: SampleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val state: StateFlow<EditUiState> = _state

    private var loaded: SampleWithActivities? = null

    val activities = pickerOptions(CategoryType.ACTIVITY) { loaded?.activityIds.orEmpty() }
    val locations = pickerOptions(CategoryType.LOCATION) { listOfNotNull(loaded?.sample?.locationId) }
    val socials = pickerOptions(CategoryType.SOCIAL) { listOfNotNull(loaded?.sample?.socialId) }

    init {
        viewModelScope.launch {
            val found = db.sampleDao().byPromptId(promptId)
            loaded = found
            _state.value = if (found == null) EditUiState.Missing else EditUiState.Editing(
                CaptureFormValues(
                    valence = found.sample.valence,
                    arousal = found.sample.arousal,
                    pain = found.sample.pain ?: 0,
                    activityIds = found.activityIds,
                    locationId = found.sample.locationId,
                    socialId = found.sample.socialId,
                    note = found.sample.note.orEmpty(),
                )
            )
        }
    }

    fun save(values: CaptureFormValues) {
        val current = loaded ?: return
        viewModelScope.launch {
            repository.updateAnswer(
                current.sample.copy(
                    valence = values.valence.coerceIn(-1f, 1f),
                    arousal = values.arousal.coerceIn(-1f, 1f),
                    pain = values.pain.coerceIn(PainScale.MIN, PainScale.MAX),
                    locationId = values.locationId,
                    socialId = values.socialId,
                    note = values.note.trim().ifEmpty { null },
                ),
                activityIds = values.activityIds,
            )
            _state.value = EditUiState.Done
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deleteAnswer(promptId)
            _state.value = EditUiState.Done
        }
    }

    /**
     * Active categories plus any archived ones this sample still references, so
     * an old selection stays visible (and de-selectable) in the picker.
     */
    private fun pickerOptions(type: CategoryType, referenced: () -> List<Long>): StateFlow<List<CategoryEntity>> =
        combine(db.categoryDao().allFlow(), _state) { all, _ ->
            val keep = referenced().toSet()
            all.filter { it.type == type && (!it.isArchived || it.id in keep) }
                .sortedWith(compareBy({ it.isArchived }, { it.sortOrder }, { it.id }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(promptId: Long) = graphViewModelFactory { graph ->
            EditSampleViewModel(promptId, graph.database, graph.sampleRepository)
        }
    }
}
