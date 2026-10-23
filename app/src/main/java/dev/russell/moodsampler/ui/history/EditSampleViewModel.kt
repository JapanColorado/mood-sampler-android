package dev.russell.moodsampler.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.moodsampler.data.CategoryRepository
import dev.russell.moodsampler.data.MoodScale
import dev.russell.moodsampler.data.PainScale
import dev.russell.moodsampler.data.PhotoStore
import dev.russell.moodsampler.data.SampleRepository
import dev.russell.moodsampler.data.claude.AssistFill
import dev.russell.moodsampler.data.claude.CaptureAssistant
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.db.CategoryEntity
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.db.SampleWithActivities
import dev.russell.moodsampler.ui.capture.CaptureFormValues
import dev.russell.moodsampler.ui.graphViewModelFactory
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
    private val categoryRepository: CategoryRepository,
    private val photoStore: PhotoStore,
    private val assistant: CaptureAssistant,
) : ViewModel() {

    private val _state = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val state: StateFlow<EditUiState> = _state

    private var loaded: SampleWithActivities? = null

    val activities = pickerOptions(CategoryType.ACTIVITY) { loaded?.activityIds.orEmpty() }
    val locations = pickerOptions(CategoryType.LOCATION) { listOfNotNull(loaded?.sample?.locationId) }
    val socials = pickerOptions(CategoryType.SOCIAL) { listOfNotNull(loaded?.sample?.socialId) }
    val painTypes = pickerOptions(CategoryType.PAIN) { loaded?.painTypeIds.orEmpty() }

    /** Whether a Claude API key is set, so the form can offer to fill itself in; null until known. */
    val assistAvailable: StateFlow<Boolean?> = assistant.available
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val usage = categoryRepository.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            val found = db.sampleDao().byPromptId(promptId)
            loaded = found
            _state.value = if (found == null) EditUiState.Missing else EditUiState.Editing(
                CaptureFormValues(
                    valence = found.sample.valence,
                    arousal = found.sample.arousal,
                    pain = found.sample.pain ?: 0,
                    painTypeIds = found.painTypeIds,
                    activityIds = found.activityIds,
                    locationId = found.sample.locationId,
                    socialId = found.sample.socialId,
                    note = found.sample.note.orEmpty(),
                    summary = found.sample.summary,
                    tags = found.tags,
                    photoPath = found.sample.photoPath,
                )
            )
        }
    }

    fun save(values: CaptureFormValues) {
        val current = loaded ?: return
        val valence = values.valence ?: return
        val arousal = values.arousal ?: return
        viewModelScope.launch {
            val photo = values.newPhotoUri?.let { photoStore.commit(Uri.parse(it)) } ?: values.photoPath
            repository.updateAnswer(
                current.sample.copy(
                    valence = valence.coerceIn(MoodScale.MIN, MoodScale.MAX),
                    arousal = arousal.coerceIn(MoodScale.MIN, MoodScale.MAX),
                    pain = values.pain.coerceIn(PainScale.MIN, PainScale.MAX),
                    locationId = values.locationId,
                    socialId = values.socialId,
                    note = values.note.trim().ifEmpty { null },
                    summary = values.summary,
                    photoPath = photo,
                ),
                activityIds = values.activityIds,
                painTypeIds = values.painTypeIds,
                tags = values.tags,
            )
            _state.value = EditUiState.Done
        }
    }

    /** Claude's fill for the form; see [CaptureAssistant.fill]. */
    suspend fun assist(narration: String, photo: Uri?, guessedPlaceId: Long?, guessedSocialId: Long?): AssistFill =
        assistant.fill(narration, photo, guessedPlaceId, guessedSocialId)

    /** An option typed into an "Other…" chip, added (or matched) for this and future prompts; its id. */
    suspend fun addOption(type: CategoryType, label: String): Long? = categoryRepository.findOrAdd(type, label)

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
            EditSampleViewModel(
                promptId, graph.database, graph.sampleRepository, graph.categoryRepository, graph.photoStore,
                graph.captureAssistant,
            )
        }
    }
}
