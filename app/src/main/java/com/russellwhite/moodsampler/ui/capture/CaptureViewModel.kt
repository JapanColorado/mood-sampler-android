package com.russellwhite.moodsampler.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russellwhite.moodsampler.AppGraph
import com.russellwhite.moodsampler.data.MoodScale
import com.russellwhite.moodsampler.data.PainScale
import com.russellwhite.moodsampler.data.PhotoStore
import com.russellwhite.moodsampler.data.SampleRepository
import com.russellwhite.moodsampler.data.bluetooth.BluetoothScanner
import com.russellwhite.moodsampler.data.bluetooth.DeviceFingerprint
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import com.russellwhite.moodsampler.data.db.PromptStatus
import com.russellwhite.moodsampler.data.db.SampleEntity
import com.russellwhite.moodsampler.data.location.LocationFix
import com.russellwhite.moodsampler.data.location.LocationProvider
import com.russellwhite.moodsampler.data.CategoryRepository
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

    /** User declined via "Can't answer now" — recorded as MISSED with a reason. */
    data object Skipped : CaptureUiState
}

/** Progress of the one-shot location fix shown as a caption on the form. */
sealed interface LocationStatus {
    /** Auto-location disabled in settings or permission not granted. */
    data object Off : LocationStatus
    data object Acquiring : LocationStatus
    data class Got(val accuracyM: Float?) : LocationStatus
    data object Unavailable : LocationStatus
}

/** Progress of the one-shot nearby-device scan shown as a caption on the company row. */
sealed interface CompanyStatus {
    /** Disabled in settings or permission not granted. */
    data object Off : CompanyStatus
    data object Scanning : CompanyStatus
    data object Scanned : CompanyStatus

    /** Bluetooth is off or the scan failed. */
    data object Unavailable : CompanyStatus
}

class CaptureViewModel(
    private val promptId: Long,
    private val db: AppDatabase,
    private val sampleRepository: SampleRepository,
    private val scheduler: PromptScheduler,
    private val settings: SettingsRepository,
    private val categoryRepository: CategoryRepository,
    private val locationProvider: LocationProvider,
    private val bluetoothScanner: BluetoothScanner,
    private val photoStore: PhotoStore,
    private val clock: Clock = DeviceClock,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Loading)
    val state: StateFlow<CaptureUiState> = _state

    val activities = categories(CategoryType.ACTIVITY)
    val locations = categories(CategoryType.LOCATION)
    val socials = categories(CategoryType.SOCIAL)
    val usage = categoryRepository.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _locationStatus = MutableStateFlow<LocationStatus>(LocationStatus.Off)
    val locationStatus: StateFlow<LocationStatus> = _locationStatus

    /** The fix to stamp on the sample; null until acquired (or forever, if unavailable). */
    private var fix: LocationFix? = null

    /** Place matched from past answers near the fix; null when nothing is close enough. */
    private val _suggestedLocationId = MutableStateFlow<Long?>(null)
    val suggestedLocationId: StateFlow<Long?> = _suggestedLocationId

    private val _companyStatus = MutableStateFlow<CompanyStatus>(CompanyStatus.Off)
    val companyStatus: StateFlow<CompanyStatus> = _companyStatus

    /** Hashed devices to store with the sample; empty until the scan finishes. */
    private var deviceHashes: Set<String> = emptySet()

    /** Company matched from past answers with the same devices around; null when they say nothing. */
    private val _suggestedSocialId = MutableStateFlow<Long?>(null)
    val suggestedSocialId: StateFlow<Long?> = _suggestedSocialId

    private var expiryMinutes = 20

    init {
        viewModelScope.launch {
            val current = settings.current()
            expiryMinutes = current.expiryMinutes
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
            if (_state.value == CaptureUiState.Answerable && current.autoLocation && locationProvider.hasPermission()) {
                acquireLocation()
            }
            if (_state.value == CaptureUiState.Answerable && current.autoCompany && bluetoothScanner.hasPermission()) {
                scanNearby()
            }
        }
    }

    private fun scanNearby() {
        _companyStatus.value = CompanyStatus.Scanning
        viewModelScope.launch {
            val devices = bluetoothScanner.scan()
            if (devices == null) {
                _companyStatus.value = CompanyStatus.Unavailable
                return@launch
            }
            deviceHashes = DeviceFingerprint.hashes(devices, settings.deviceSalt())
            _companyStatus.value = CompanyStatus.Scanned
            _suggestedSocialId.value = sampleRepository.suggestCompany(deviceHashes)
        }
    }

    private fun acquireLocation() {
        _locationStatus.value = LocationStatus.Acquiring
        viewModelScope.launch {
            val got = locationProvider.currentFix()
            if (got == null) {
                _locationStatus.value = LocationStatus.Unavailable
                return@launch
            }
            fix = got
            _locationStatus.value = LocationStatus.Got(got.accuracyM)
            _suggestedLocationId.value = sampleRepository.suggestPlace(got)
        }
    }

    private fun pastExpiry(sinceMs: Long): Boolean =
        clock.millis() - sinceMs > expiryMinutes * 60_000L

    fun save(values: CaptureFormValues) {
        if (_state.value != CaptureUiState.Answerable) return
        val valence = values.valence ?: return
        val arousal = values.arousal ?: return
        viewModelScope.launch {
            val photo = values.newPhotoUri?.let { photoStore.commit(Uri.parse(it)) }
            val ok = sampleRepository.saveAnswer(
                SampleEntity(
                    promptId = promptId,
                    valence = valence.coerceIn(MoodScale.MIN, MoodScale.MAX),
                    arousal = arousal.coerceIn(MoodScale.MIN, MoodScale.MAX),
                    pain = values.pain.coerceIn(PainScale.MIN, PainScale.MAX),
                    locationId = values.locationId,
                    socialId = values.socialId,
                    note = values.note.trim().ifEmpty { null },
                    createdAtEpochMs = 0,  // stamped by the repository
                    latitude = fix?.latitude,
                    longitude = fix?.longitude,
                    accuracyM = fix?.accuracyM,
                    fixAtEpochMs = fix?.atEpochMs,
                    photoPath = photo,
                ),
                activityIds = values.activityIds,
                deviceHashes = deviceHashes,
                answeredAtMs = clock.millis(),
            )
            if (ok) {
                scheduler.onPromptAnswered(promptId)
                _state.value = CaptureUiState.Saved
            } else {
                photoStore.delete(photo)
                _state.value = CaptureUiState.Expired
            }
        }
    }

    /** An option typed into an "Other…" chip, added (or matched) for this and future prompts; its id. */
    suspend fun addOption(type: CategoryType, label: String): Long? = categoryRepository.findOrAdd(type, label)

    fun skip(reason: String) {
        if (_state.value != CaptureUiState.Answerable) return
        viewModelScope.launch {
            scheduler.onPromptSkipped(promptId, reason)
            _state.value = CaptureUiState.Skipped
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
                categoryRepository = graph.categoryRepository,
                locationProvider = graph.locationProvider,
                bluetoothScanner = graph.bluetoothScanner,
                photoStore = graph.photoStore,
            )
        }
    }
}
