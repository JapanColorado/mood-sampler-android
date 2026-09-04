package com.russellwhite.moodsampler.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.AppGraph
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.settings.SettingsRepository
import com.russellwhite.moodsampler.scheduling.DeviceClock
import com.russellwhite.moodsampler.scheduling.PromptScheduler
import com.russellwhite.moodsampler.ui.graphViewModelFactory
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CaptureUiState {
    data object Loading : CaptureUiState

    /** Prompt is open for answering. */
    data object Answerable : CaptureUiState

    /** Prompt expired/was cancelled — counted as missed; no late answers. */
    data object Expired : CaptureUiState

    data object AlreadyAnswered : CaptureUiState

    data object Saved : CaptureUiState
}

class CaptureViewModel(
    private val promptId: Long,
    private val db: AppDatabase,
    private val sampleRepository: SampleRepository,
    private val scheduler: PromptScheduler,
    settings: SettingsRepository,
    private val clock: Clock = DeviceClock,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Loading)
    val state: StateFlow<CaptureUiState> = _state

    val activities = categories(CategoryType.ACTIVITY)
    val locations = categories(CategoryType.LOCATION)
    val socials = categories(CategoryType.SOCIAL)

    private var expiryMinutes = 20

    init {
        viewModelScope.launch {
            expiryMinutes = settings.current().expiryMinutes
            val prompt = db.scheduledPromptDao().getById(promptId)
            _state.value = when {
                prompt == null -> CaptureUiState.Expired
                prompt.status == PromptStatus.ANSWERED -> CaptureUiState.AlreadyAnswered
                prompt.status == PromptStatus.MISSED ||
                    prompt.status == PromptStatus.CANCELLED -> CaptureUiState.Expired
                pastExpiry(prompt.notifiedAtEpochMs ?: prompt.scheduledAtEpochMs) -> {
                    // Belt-and-braces: the expiry alarm may not have run yet.
                    db.scheduledPromptDao().markMissedIfNotified(promptId)
                    CaptureUiState.Expired
                }
                else -> CaptureUiState.Answerable
            }
        }
    }

    private fun pastExpiry(sinceMs: Long): Boolean =
        clock.millis() - sinceMs > expiryMinutes * 60_000L

    fun save(
        valence: Float,
        arousal: Float,
        activityId: Long?,
        locationId: Long?,
        socialId: Long?,
        note: String,
    ) {
        if (_state.value != CaptureUiState.Answerable) return
        viewModelScope.launch {
            val ok = sampleRepository.saveAnswer(
                SampleEntity(
                    promptId = promptId,
                    valence = valence.coerceIn(-1f, 1f),
                    arousal = arousal.coerceIn(-1f, 1f),
                    activityId = activityId,
                    locationId = locationId,
                    socialId = socialId,
                    note = note.trim().ifEmpty { null },
                    createdAtEpochMs = 0,  // stamped by the repository
                ),
                answeredAtMs = clock.millis(),
            )
            if (ok) {
                scheduler.onPromptAnswered(promptId)
                _state.value = CaptureUiState.Saved
            } else {
                _state.value = CaptureUiState.Expired
            }
        }
    }

    private fun categories(type: CategoryType): StateFlow<List<CategoryEntity>> =
        db.categoryDao().activeByTypeFlow(type)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(promptId: Long) = graphViewModelFactory { graph: AppGraph ->
            CaptureViewModel(
                promptId = promptId,
                db = graph.database,
                sampleRepository = graph.sampleRepository,
                scheduler = graph.promptScheduler,
                settings = graph.settingsRepository,
            )
        }
    }
}
