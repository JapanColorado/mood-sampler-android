package dev.russell.moodsampler.ui.capture

import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.russell.moodsampler.appGraph
import dev.russell.moodsampler.data.MoodScale
import dev.russell.moodsampler.data.PainScale
import dev.russell.moodsampler.data.SkipReason
import dev.russell.moodsampler.data.claude.AssistFill
import dev.russell.moodsampler.data.claude.ClaudeException
import dev.russell.moodsampler.data.db.CategoryEntity
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.db.byActivityGroup
import dev.russell.moodsampler.ui.components.ActionChip
import dev.russell.moodsampler.ui.components.Aside
import dev.russell.moodsampler.ui.components.JournalCard
import dev.russell.moodsampler.ui.components.OptionChip
import dev.russell.moodsampler.ui.components.Rule
import dev.russell.moodsampler.ui.components.moodWash
import dev.russell.moodsampler.ui.dashboard.charts.MoodChartColors
import dev.russell.moodsampler.ui.theme.AsideStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Everything the user enters on the capture form; also the seed for the edit screen. */
data class CaptureFormValues(
    /** 0..10; null until picked (a fresh prompt starts unanswered). Always set on save. */
    val valence: Int? = null,
    /** 0..10; null until picked. Always set on save. */
    val arousal: Int? = null,
    val pain: Int = 0,
    val painTypeIds: List<Long> = emptyList(),
    val activityIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val socialId: Long? = null,
    /** The narration: what the user typed or dictated. Stored as the sample's note. */
    val note: String = "",
    /** Claude's one-line summary of the narration, when Claude filled the form. */
    val summary: String? = null,
    /** Claude's free-form tags; the user can remove them before saving. */
    val tags: List<String> = emptyList(),
    /** File name of the photo already stored with this answer (edit seed / kept on update). */
    val photoPath: String? = null,
    /** A photo taken on this form and not yet stored: the capture's content Uri. */
    val newPhotoUri: String? = null,
)

/** Asks Claude to fill the form from the narration and photo; see [dev.russell.moodsampler.data.claude.CaptureAssistant.fill]. */
typealias AssistAction = suspend (narration: String, photo: Uri?, guessedPlaceId: Long?, guessedSocialId: Long?) -> AssistFill

/**
 * The shared mood-capture form. Used for live prompts (with a "Can't answer now"
 * skip path) and for editing a past answer from History (no skip path).
 *
 * Built so a capture can be mostly talked through: the narration box (type or
 * dictate) and photo come first, and "Fill in with Claude" turns them into the place,
 * activities, company and pain types, plus a summary and tags. The three tap scales
 * stay the user's own. The context chips sit in a collapsible "Manual" section that
 * shows what's picked at a glance; each ends in an "Other…" chip that names a new
 * option, kept for future prompts. Save is pinned to the bottom.
 */
@Composable
internal fun CaptureForm(
    initial: CaptureFormValues,
    activities: List<CategoryEntity>,
    locations: List<CategoryEntity>,
    socials: List<CategoryEntity>,
    painTypes: List<CategoryEntity>,
    /** Sample counts per category id; orders the short lists by "most used". */
    usage: Map<Long, Int>,
    locationStatus: LocationStatus,
    /** Place matched from the fix; preselected until the user touches the place row. */
    suggestedLocationId: Long?,
    companyStatus: CompanyStatus,
    /** Company matched from nearby devices; preselected until the user touches the company row. */
    suggestedSocialId: Long?,
    /** Company shown as picked while nothing else has set it; null (History edits) means none. */
    defaultSocialId: Long? = null,
    /** Null when no Claude API key is set: the form is filled by hand. */
    onAssist: AssistAction?,
    saveLabel: String,
    onSave: (CaptureFormValues) -> Unit,
    /** Adds (or matches) an option typed into an "Other…" chip; its id, or null if nothing was added. */
    onAddOption: suspend (CategoryType, String) -> Long?,
    onSkip: ((reason: String) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Tint the page with the picked valence; off for History edits, which sit under a top bar. */
    moodTint: Boolean = true,
    header: @Composable () -> Unit = { Text("Right now…", style = MaterialTheme.typography.displaySmall) },
) {
    // Scale positions, UNPICKED until tapped (rememberSaveable has no nullable Int state).
    var valence by rememberSaveable { mutableIntStateOf(initial.valence ?: UNPICKED) }
    var arousal by rememberSaveable { mutableIntStateOf(initial.arousal ?: UNPICKED) }
    var pain by rememberSaveable { mutableIntStateOf(initial.pain) }
    val painTypeIds = rememberSaveable(saver = LongListSaver) { initial.painTypeIds.toMutableStateList() }
    val activityIds = rememberSaveable(saver = LongListSaver) { initial.activityIds.toMutableStateList() }
    var locationId by rememberSaveable { mutableStateOf(initial.locationId) }
    var socialId by rememberSaveable { mutableStateOf(initial.socialId) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var summary by rememberSaveable { mutableStateOf(initial.summary) }
    val tags = rememberSaveable(saver = StringListSaver) { initial.tags.toMutableStateList() }
    var photoPath by rememberSaveable { mutableStateOf(initial.photoPath) }
    var newPhotoUri by rememberSaveable { mutableStateOf(initial.newPhotoUri) }
    val photoStore = LocalContext.current.appGraph.photoStore
    var showSkip by rememberSaveable { mutableStateOf(false) }
    var placeTouched by rememberSaveable { mutableStateOf(false) }
    var companyTouched by rememberSaveable { mutableStateOf(false) }
    var activitiesTouched by rememberSaveable { mutableStateOf(false) }
    var painTypesTouched by rememberSaveable { mutableStateOf(false) }
    // Without Claude the chips are the only way in, so start open.
    var manualOpen by rememberSaveable { mutableStateOf(onAssist == null) }
    var assisting by remember { mutableStateOf(false) }
    var assistError by rememberSaveable { mutableStateOf<String?>(null) }
    var addingType by rememberSaveable { mutableStateOf<CategoryType?>(null) }
    val scope = rememberCoroutineScope()
    val ready = valence != UNPICKED && arousal != UNPICKED
    // Kept out of socialId so the scan and Claude still replace it, and aren't told it as a guess.
    val pickedSocialId = socialId ?: defaultSocialId.takeIf { !companyTouched }

    // The fix may land after the form is on screen; only fill an untouched, empty place.
    LaunchedEffect(suggestedLocationId) {
        if (suggestedLocationId != null && !placeTouched && locationId == null) locationId = suggestedLocationId
    }
    // Same for company once the nearby-device scan finishes.
    LaunchedEffect(suggestedSocialId) {
        if (suggestedSocialId != null && !companyTouched && socialId == null) socialId = suggestedSocialId
    }

    val takePhoto = rememberPhotoCapture { uri ->
        newPhotoUri?.let { photoStore.discardCapture(Uri.parse(it)) }
        newPhotoUri = uri.toString()
    }
    val photo = newPhotoUri?.let(Uri::parse) ?: photoPath?.let(photoStore::uri)
    val dictation = rememberDictation { heard ->
        note = listOf(note.trimEnd(), heard).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Claude's picks fill whatever the user hasn't set by hand; an empty answer keeps what's there. */
    fun applyFill(fill: AssistFill) {
        summary = fill.summary
        tags.clear()
        tags.addAll(fill.tags)
        if (!placeTouched) fill.locationId?.let { locationId = it }
        if (!companyTouched) fill.socialId?.let { socialId = it }
        mergeInto(activityIds, fill.activityIds, replace = !activitiesTouched)
        mergeInto(painTypeIds, fill.painTypeIds, replace = !painTypesTouched)
        manualOpen = true
    }

    val askClaude: (() -> Unit)? = onAssist?.let { assist ->
        {
            assisting = true
            assistError = null
            scope.launch {
                try {
                    applyFill(
                        assist(note, photo, locationId.takeIf { !placeTouched }, socialId.takeIf { !companyTouched })
                    )
                } catch (e: ClaudeException) {
                    assistError = e.message
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Capture assist failed", e)
                    assistError = "Something went wrong asking Claude (${e.javaClass.simpleName}). Fill it in by hand."
                } finally {
                    assisting = false
                }
            }
        }
    }

    // Once rated, the valence colors the page. Never before: it mustn't lead the answer.
    val wash = if (moodTint && valence != UNPICKED) MoodChartColors.valenceAt(valence.toDouble()) else Color.Transparent

    Column(modifier.fillMaxSize().moodWash(wash).statusBarsPadding().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            header()

            NarrationCard(
                note = note,
                onNoteChange = { note = it },
                dictation = dictation,
                photo = photo,
                takePhoto = takePhoto,
                onRemovePhoto = {
                    newPhotoUri?.let { photoStore.discardCapture(Uri.parse(it)) }
                    newPhotoUri = null
                    photoPath = null
                },
                askClaude = askClaude?.takeIf { note.isNotBlank() || photo != null },
                claudeAvailable = onAssist != null,
                assisting = assisting,
                assistError = assistError,
                summary = summary,
                tags = tags,
                onRemoveTag = { tags.remove(it) },
                onClearSummary = {
                    summary = null
                    tags.clear()
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TapScale(
                    title = "Valence",
                    range = MoodScale.MIN..MoodScale.MAX,
                    value = valence.takeIf { it != UNPICKED },
                    colors = (MoodScale.MIN..MoodScale.MAX).map { MoodChartColors.valenceAt(it.toDouble()) },
                    lowAnchor = "Awful",
                    highAnchor = "Great",
                    onPick = { valence = it },
                )
                Rule()
                TapScale(
                    title = "Energy",
                    range = MoodScale.MIN..MoodScale.MAX,
                    value = arousal.takeIf { it != UNPICKED },
                    colors = (MoodScale.MIN..MoodScale.MAX).map { MoodChartColors.arousalAt(it.toDouble()) },
                    lowAnchor = "Drained",
                    highAnchor = "Wired",
                    onPick = { arousal = it },
                )
                Rule()
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TapScale(
                        title = "Pain",
                        range = PainScale.MIN..PainScale.MAX,
                        value = pain,
                        colors = (PainScale.MIN..PainScale.MAX).map { MoodChartColors.painAt(it.toDouble()) },
                        lowAnchor = "None",
                        highAnchor = "Worst imaginable",
                        valueCaption = PainScale::descriptor,
                        onPick = { pain = it },
                    )
                    if (pain > 0 || painTypeIds.isNotEmpty()) {
                        PainTypeSection(painTypes, painTypeIds, onOther = { addingType = CategoryType.PAIN }) { id ->
                            painTypesTouched = true
                            if (!painTypeIds.remove(id)) painTypeIds.add(id)
                        }
                    }
                }
            }

            ManualSection(
                open = manualOpen,
                onToggle = { manualOpen = !manualOpen },
                picked = remember(locationId, activityIds.toList(), pickedSocialId, locations, activities, socials) {
                    pickedSummary(locationId, activityIds, pickedSocialId, locations + activities + socials)
                },
            ) {
                PlaceSection(
                    locations = locations,
                    usage = usage,
                    selectedId = locationId,
                    suggestedId = suggestedLocationId,
                    status = locationStatus,
                    onOther = { addingType = CategoryType.LOCATION },
                ) {
                    placeTouched = true
                    locationId = if (locationId == it) null else it
                }
                ActivitySection(activities, usage, activityIds, onOther = { addingType = CategoryType.ACTIVITY }) { id ->
                    activitiesTouched = true
                    if (!activityIds.remove(id)) activityIds.add(id)
                }
                CompanySection(socials, pickedSocialId, companyStatus, onOther = { addingType = CategoryType.SOCIAL }) {
                    companyTouched = true
                    socialId = if (pickedSocialId == it) null else it
                }
            }
        }

        BottomBar(
            saveLabel = saveLabel,
            ready = ready && !assisting,
            onSkip = onSkip?.let { { showSkip = true } },
            onSave = {
                onSave(
                    CaptureFormValues(
                        valence = valence,
                        arousal = arousal,
                        pain = pain,
                        painTypeIds = painTypeIds.toList(),
                        activityIds = activityIds.toList(),
                        locationId = locationId,
                        socialId = pickedSocialId,
                        note = note,
                        summary = summary,
                        tags = tags.toList(),
                        photoPath = photoPath,
                        newPhotoUri = newPhotoUri,
                    )
                )
            },
        )
    }

    addingType?.let { type ->
        AddOptionDialog(type, onDismiss = { addingType = null }) { label ->
            addingType = null
            scope.launch {
                val id = onAddOption(type, label) ?: return@launch
                when (type) {
                    CategoryType.LOCATION -> { placeTouched = true; locationId = id }
                    CategoryType.ACTIVITY -> { activitiesTouched = true; if (id !in activityIds) activityIds.add(id) }
                    CategoryType.SOCIAL -> { companyTouched = true; socialId = id }
                    CategoryType.PAIN -> { painTypesTouched = true; if (id !in painTypeIds) painTypeIds.add(id) }
                }
            }
        }
    }

    if (showSkip && onSkip != null) {
        SkipDialog(onDismiss = { showSkip = false }, onConfirm = { showSkip = false; onSkip(it) })
    }
}

/** Replace [target] with [picked] (when [replace]) or add the new ones; an empty [picked] changes nothing. */
private fun mergeInto(target: SnapshotStateList<Long>, picked: List<Long>, replace: Boolean) {
    if (picked.isEmpty()) return
    if (replace) target.clear()
    picked.filter { it !in target }.forEach(target::add)
}

/** "Home · Work, Commuting · Alone" — what the collapsed Manual section shows. */
private fun pickedSummary(locationId: Long?, activityIds: List<Long>, socialId: Long?, options: List<CategoryEntity>): String {
    val labels = options.associate { it.id to it.label }
    return listOfNotNull(
        locationId?.let(labels::get),
        activityIds.mapNotNull(labels::get).joinToString(", ").ifEmpty { null },
        socialId?.let(labels::get),
    ).joinToString(" · ")
}

private val StringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

private val LongListSaver = listSaver<SnapshotStateList<Long>, Long>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * A row of equal-width numbered cells, each tinted with its own color from the scale,
 * so the whole range is legible before you touch it; one tap picks a value, which fills
 * solid and lifts slightly. The pick also appears large beside the title.
 */
@Composable
private fun TapScale(
    title: String,
    range: IntRange,
    value: Int?,
    /** One color per value in [range]. */
    colors: List<Color>,
    lowAnchor: String,
    highAnchor: String,
    onPick: (Int) -> Unit,
    valueCaption: ((Int) -> String)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (value != null) {
                if (valueCaption != null) {
                    Text(
                        valueCaption(value),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
                Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            range.forEach { n ->
                ScaleCell(
                    n = n,
                    selected = n == value,
                    color = colors[n - range.first],
                    description = "$title $n",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(n)
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Anchor(lowAnchor)
            Spacer(Modifier.weight(1f))
            Anchor(highAnchor)
        }
    }
}

@Composable
private fun RowScope.ScaleCell(
    n: Int,
    selected: Boolean,
    color: Color,
    description: String,
    onClick: () -> Unit,
) {
    val idle = lerp(MaterialTheme.colorScheme.surfaceContainer, color, CELL_TINT)
    val background by animateColorAsState(if (selected) color else idle, label = "cell")
    val content by animateColorAsState(
        if (selected) MoodChartColors.contentOn(color) else MaterialTheme.colorScheme.onSurface,
        label = "cellText",
    )
    val lift by animateFloatAsState(if (selected) 1.14f else 1f, label = "cellLift")
    Box(
        Modifier
            .weight(1f)
            .height(50.dp)
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .background(background, RoundedCornerShape(9.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}

private const val CELL_TINT = 0.32f

@Composable
private fun Anchor(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Small icon + label above each context section. */
@Composable
private fun SectionLabel(icon: ImageVector, text: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Place picker that needs zero taps most of the time: shows the suggested place,
 * the most-used places and the current selection; the full list is one tap away.
 */
@Composable
private fun PlaceSection(
    locations: List<CategoryEntity>,
    usage: Map<Long, Int>,
    selectedId: Long?,
    suggestedId: Long?,
    status: LocationStatus,
    onOther: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val shortlist = remember(locations, usage, suggestedId, selectedId) {
        shortlist(locations, usage, PLACE_SHORTLIST_SIZE, listOfNotNull(suggestedId, selectedId))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(Icons.Outlined.Place, "Where", locationCaption(status))
        ExpandableChips(locations, shortlist, onOther) { option ->
            OptionChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
            )
        }
    }
}

private fun locationCaption(status: LocationStatus): String? = when (status) {
    LocationStatus.Off -> null
    LocationStatus.Acquiring -> "locating…"
    LocationStatus.Unavailable -> "location unavailable"
    is LocationStatus.Got -> status.accuracyM?.let { "located ±${it.roundToInt()} m" } ?: "located"
}

/** Multi-select activities: the most-used plus anything picked; "More…" shows every group. */
@Composable
private fun ActivitySection(
    activities: List<CategoryEntity>,
    usage: Map<Long, Int>,
    selectedIds: List<Long>,
    onOther: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Pinned to the selection when the list was built, so toggling doesn't reshuffle chips.
    val shortlist = remember(activities, usage) {
        shortlist(activities, usage, ACTIVITY_SHORTLIST_SIZE, selectedIds)
    }
    val visible = shortlist + activities.filter { it.id in selectedIds && it !in shortlist }
    val chip: @Composable (CategoryEntity) -> Unit = { option ->
        OptionChip(
            selected = option.id in selectedIds,
            onClick = { onToggle(option.id) },
            label = { Text(option.label) },
        )
    }
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(Icons.AutoMirrored.Outlined.DirectionsRun, "Doing", "pick any")
        if (expanded) {
            val groups = remember(activities) { activities.byActivityGroup() }
            groups.forEach { (group, options) ->
                GroupHeader(group.label)
                ChipFlow { options.forEach { chip(it) } }
            }
            ChipFlow {
                OtherChip(onOther)
                MoreChip(expanded = true) { expanded = false }
            }
        } else {
            ChipFlow {
                visible.forEach { chip(it) }
                OtherChip(onOther)
                if (visible.size < activities.size) MoreChip(expanded = false) { expanded = true }
            }
        }
    }
}

/** Who you're with: every option shown (the list is short), plus the scan's caption. */
@Composable
private fun CompanySection(
    options: List<CategoryEntity>,
    selectedId: Long?,
    status: CompanyStatus,
    onOther: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(Icons.Outlined.Group, "With", companyCaption(status))
        ChipFlow {
            options.forEach { option ->
                OptionChip(
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.label) },
                )
            }
            OtherChip(onOther)
        }
    }
}

private fun companyCaption(status: CompanyStatus): String? = when (status) {
    CompanyStatus.Off, CompanyStatus.Scanned -> null
    CompanyStatus.Scanning -> "checking nearby devices…"
    CompanyStatus.Unavailable -> "Bluetooth off"
}

/**
 * The top of the form: the narration box (typed, or dictated with the mic), the photo,
 * and "Fill in with Claude" with its result — a summary and removable tags — or error.
 */
@Composable
private fun NarrationCard(
    note: String,
    onNoteChange: (String) -> Unit,
    dictation: Dictation?,
    photo: Uri?,
    takePhoto: (() -> Unit)?,
    onRemovePhoto: () -> Unit,
    /** Null while there's nothing to send yet or Claude isn't set up. */
    askClaude: (() -> Unit)?,
    claudeAvailable: Boolean,
    assisting: Boolean,
    assistError: String?,
    summary: String?,
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onClearSummary: () -> Unit,
) {
    JournalCard(Modifier.animateContentSize(), padding = PaddingValues(8.dp), spacing = 8.dp) {
        TextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Aside("What are you doing, thinking, feeling?") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            minLines = 3,
            maxLines = 8,
        )
        Column(
            Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dictation?.let { d ->
                when {
                    d.listening -> Caption(d.partial.ifEmpty { "Listening…" })
                    d.error != null -> Caption(d.error.orEmpty(), error = true)
                }
            }

            if (photo != null) AttachedPhoto(photo = photo, onRetake = takePhoto, onRemove = onRemovePhoto)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dictation?.let { d ->
                    FilledTonalIconButton(
                        onClick = d::toggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (d.listening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (d.listening) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            if (d.listening) Icons.Filled.Stop else Icons.Outlined.Mic,
                            contentDescription = if (d.listening) "Stop dictating" else "Dictate",
                        )
                    }
                }
                if (photo == null && takePhoto != null) {
                    FilledTonalIconButton(
                        onClick = takePhoto,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Add photo")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (claudeAvailable) {
                    FilledTonalButton(
                        onClick = { askClaude?.invoke() },
                        enabled = askClaude != null && !assisting,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        if (assisting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (summary == null) "Fill in with Claude" else "Fill in again")
                    }
                }
            }

            if (!claudeAvailable) {
                Caption("Add a Claude API key in Settings to fill in the rest from what you write.")
            }
            assistError?.let { Caption(it, error = true) }
            if (summary != null || tags.isNotEmpty()) {
                ClaudeResult(summary, tags, onRemoveTag, onClearSummary)
            }
        }
    }
}

/** Claude's summary and tags; tags come off with a tap, "Clear" drops both. */
@Composable
private fun ClaudeResult(summary: String?, tags: List<String>, onRemoveTag: (String) -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                summary ?: "Tags",
                style = if (summary != null) AsideStyle else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        if (tags.isNotEmpty()) {
            ChipFlow {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remove $tag", Modifier.size(InputChipDefaults.IconSize))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Kinds of pain, multi-select, under the pain scale. */
@Composable
private fun PainTypeSection(
    options: List<CategoryEntity>,
    selectedIds: List<Long>,
    onOther: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupHeader("Where / what kind")
        ChipFlow {
            options.forEach { option ->
                OptionChip(
                    selected = option.id in selectedIds,
                    onClick = { onToggle(option.id) },
                    label = { Text(option.label) },
                )
            }
            OtherChip(onOther)
        }
    }
}

/**
 * Place, activities and company by hand. Collapsed, it still shows what's picked —
 * from Claude, the location fix or the device scan — so a glance is enough to check.
 */
@Composable
private fun ManualSection(open: Boolean, onToggle: () -> Unit, picked: String, content: @Composable () -> Unit) {
    JournalCard(Modifier.animateContentSize(), padding = PaddingValues(0.dp), spacing = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Manual", style = MaterialTheme.typography.titleLarge)
                Text(
                    picked.ifEmpty { "Where, doing, with — nothing picked yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (open) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) { content() }
        }
    }
}

/** Save pinned under the scrolling content, with the skip path beside it on live prompts. */
@Composable
private fun BottomBar(saveLabel: String, ready: Boolean, onSkip: (() -> Unit)?, onSave: () -> Unit) {
    Column {
        Rule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onSkip != null) TextButton(onClick = onSkip) { Text("Can't now") }
            Button(onClick = onSave, enabled = ready, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(
                    if (ready) saveLabel else "Pick valence & energy",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Shows [shortlist] with a More/Fewer toggle that swaps in the full [options] list, then "Other…". */
@Composable
private fun ExpandableChips(
    options: List<CategoryEntity>,
    shortlist: List<CategoryEntity>,
    onOther: () -> Unit,
    chip: @Composable (CategoryEntity) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasMore = shortlist.size < options.size
    ChipFlow(Modifier.animateContentSize()) {
        (if (expanded || !hasMore) options else shortlist).forEach { chip(it) }
        OtherChip(onOther)
        if (hasMore) MoreChip(expanded) { expanded = !expanded }
    }
}

@Composable
private fun MoreChip(expanded: Boolean, onClick: () -> Unit) {
    ActionChip(onClick = onClick, label = { Text(if (expanded) "Fewer" else "More…") })
}

/** Opens [AddOptionDialog] to name an option that isn't in the list yet. */
@Composable
private fun OtherChip(onClick: () -> Unit) {
    ActionChip(
        onClick = onClick,
        label = { Text("Other…") },
        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** Names a new option for [type]; it's selected now and offered on every future prompt. */
@Composable
private fun AddOptionDialog(type: CategoryType, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (text.isNotBlank()) onConfirm(text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (type) {
                    CategoryType.LOCATION -> "Somewhere else?"
                    CategoryType.ACTIVITY -> "Doing something else?"
                    CategoryType.SOCIAL -> "With someone else?"
                    CategoryType.PAIN -> "Another kind of pain?"
                }
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                placeholder = {
                    Text(
                        when (type) {
                            CategoryType.LOCATION -> "e.g. Grandma's house"
                            CategoryType.ACTIVITY -> "e.g. Gardening"
                            CategoryType.SOCIAL -> "e.g. Roommates"
                            CategoryType.PAIN -> "e.g. Sciatica"
                        }
                    )
                },
                supportingText = { Text("Saved as an option for future prompts.") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** The [size] most-used options plus any [pinned] ids, kept in the options' own order. */
private fun shortlist(
    options: List<CategoryEntity>,
    usage: Map<Long, Int>,
    size: Int,
    pinned: List<Long>,
): List<CategoryEntity> {
    val top = options.sortedByDescending { usage[it.id] ?: 0 }.take(size).map { it.id }
    val keep = (pinned + top).toSet()
    return options.filter { it.id in keep }
}

private const val UNPICKED = -1
private const val TAG = "MoodSampler"
private const val PLACE_SHORTLIST_SIZE = 5
private const val ACTIVITY_SHORTLIST_SIZE = 8

@Composable
private fun SkipDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip this prompt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "It will count as missed — that keeps your response rate honest — " +
                        "but the reason is saved so you can see why later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ReasonChips(selected = reason, onSelect = { reason = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason ?: SkipReason.OTHER) }, enabled = reason != null) {
                Text("Skip")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}

@Composable
private fun ReasonChips(selected: String?, onSelect: (String) -> Unit) {
    ChipFlow {
        SkipReason.CAPTURE_CHOICES.forEach { (code, label) ->
            OptionChip(selected = code == selected, onClick = { onSelect(code) }, label = { Text(label) })
        }
    }
}

/** Section header for one [dev.russell.moodsampler.data.db.ActivityGroup] of chips. */
@Composable
internal fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
