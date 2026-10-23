package dev.russell.moodsampler.data.claude

import android.net.Uri
import dev.russell.moodsampler.data.CategoryRepository
import dev.russell.moodsampler.data.PhotoStore
import dev.russell.moodsampler.data.db.AppDatabase
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.settings.SettingsRepository
import dev.russell.moodsampler.scheduling.DeviceClock
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What Claude filled in, as category ids ready for the form, plus the summary and tags. */
data class AssistFill(
    val summary: String?,
    val tags: List<String>,
    val locationId: Long?,
    val activityIds: List<Long>,
    val socialId: Long?,
    val painTypeIds: List<Long>,
)

/**
 * Fills a capture from the user's narration (and photo) with Claude. Labels Claude
 * picks are matched to existing options; a new one is added like an "Other…" entry.
 */
class CaptureAssistant(
    private val db: AppDatabase,
    private val categories: CategoryRepository,
    private val keyStore: ClaudeKeyStore,
    private val settings: SettingsRepository,
    private val photos: PhotoStore,
    private val client: ClaudeClient = ClaudeClient(),
    private val clock: Clock = DeviceClock,
) {
    /** Whether an API key is saved, so the form can offer "Fill in with Claude". */
    val available: Flow<Boolean> = keyStore.apiKey.map { it != null }

    /**
     * @param guessedPlaceId place pre-selected from the location fix (a hint, not the user's pick)
     * @param guessedSocialId company pre-selected from the nearby-device scan
     * @throws ClaudeException with a user-facing message.
     */
    suspend fun fill(narration: String, photo: Uri?, guessedPlaceId: Long?, guessedSocialId: Long?): AssistFill {
        val apiKey = keyStore.current() ?: throw ClaudeException("Add a Claude API key in Settings first.")
        val model = ClaudeModel.parse(settings.current().claudeModel)
        val all = db.categoryDao().getAll()
        fun active(type: CategoryType) =
            all.filter { it.type == type && !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.label }
        val options = AssistOptions(
            places = active(CategoryType.LOCATION),
            activities = active(CategoryType.ACTIVITY),
            company = active(CategoryType.SOCIAL),
            painTypes = active(CategoryType.PAIN),
        )
        val labels = all.associate { it.id to it.label }
        val image = photo?.let { photos.jpegForUpload(it) }
        val text = AssistPrompt.userText(
            narration = narration,
            options = options,
            localTime = ZonedDateTime.now(clock),
            guessedPlace = guessedPlaceId?.let(labels::get),
            guessedCompany = guessedSocialId?.let(labels::get),
            hasPhoto = image != null,
        )
        val raw = client.structured(apiKey, model, AssistPrompt.SYSTEM, text, image, AssistPrompt.SCHEMA)
        val reply = try {
            AssistPrompt.parse(raw)
        } catch (e: IllegalArgumentException) {  // includes SerializationException
            throw ClaudeException("Claude's reply didn't make sense. Try again.", e)
        }
        val picks = AssistPrompt.clean(reply, options)
        suspend fun idOf(type: CategoryType, label: String?) = label?.let { categories.findOrAdd(type, it) }
        return AssistFill(
            summary = picks.summary,
            tags = picks.tags,
            locationId = idOf(CategoryType.LOCATION, picks.place),
            activityIds = picks.activities.mapNotNull { idOf(CategoryType.ACTIVITY, it) },
            socialId = idOf(CategoryType.SOCIAL, picks.company),
            painTypeIds = picks.painTypes.mapNotNull { idOf(CategoryType.PAIN, it) },
        )
    }
}
