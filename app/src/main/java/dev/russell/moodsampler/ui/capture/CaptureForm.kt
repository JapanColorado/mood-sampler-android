package dev.russell.moodsampler.ui.capture

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import dev.russell.moodsampler.appGraph
import dev.russell.moodsampler.data.MoodScale
import dev.russell.moodsampler.data.PainScale
import dev.russell.moodsampler.data.SkipReason
import dev.russell.moodsampler.data.db.CategoryEntity
import dev.russell.moodsampler.data.db.CategoryType
import dev.russell.moodsampler.data.db.byActivityGroup
import dev.russell.moodsampler.ui.dashboard.charts.MoodChartColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Everything the user enters on the capture form; also the seed for the edit screen. */
data class CaptureFormValues(
    /** 0..10; null until picked (a fresh prompt starts unanswered). Always set on save. */
    val valence: Int? = null,
    /** 0..10; null until picked. Always set on save. */
    val arousal: Int? = null,
    val pain: Int = 0,
    val activityIds: List<Long> = emptyList(),
    val locationId: Long? = null,
    val socialId: Long? = null,
    val note: String = "",
    /** File name of the photo already stored with this answer (edit seed / kept on update). */
    val photoPath: String? = null,
    /** A photo taken on this form and not yet stored: the capture's content Uri. */
    val newPhotoUri: String? = null,
)

/**
 * The shared mood-capture form. Used for live prompts (with a "Can't answer now"
 * skip path) and for editing a past answer from History (no skip path).
 *
 * Built to be answered in a few taps: three tap-to-pick scales up top, the
 * context (place, activities, company) pre-shortened below, note and photo
 * tucked behind chips, and Save pinned to the bottom. Each context section ends in
 * an "Other…" chip that names a new option, kept for future prompts.
 */
@Composable
internal fun CaptureForm(
    initial: CaptureFormValues,
    activities: List<CategoryEntity>,
    locations: List<CategoryEntity>,
    socials: List<CategoryEntity>,
    /** Sample counts per category id; orders the short lists by "most used". */
    usage: Map<Long, Int>,
    locationStatus: LocationStatus,
    /** Place matched from the fix; preselected until the user touches the place row. */
    suggestedLocationId: Long?,
    companyStatus: CompanyStatus,
    /** Company matched from nearby devices; preselected until the user touches the company row. */
    suggestedSocialId: Long?,
    saveLabel: String,
    onSave: (CaptureFormValues) -> Unit,
    /** Adds (or matches) an option typed into an "Other…" chip; its id, or null if nothing was added. */
    onAddOption: suspend (CategoryType, String) -> Long?,
    onSkip: ((reason: String) -> Unit)?,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = { Text("Right now…", style = MaterialTheme.typography.headlineMedium) },
) {
    // Scale positions, UNPICKED until tapped (rememberSaveable has no nullable Int state).
    var valence by rememberSaveable { mutableIntStateOf(initial.valence ?: UNPICKED) }
    var arousal by rememberSaveable { mutableIntStateOf(initial.arousal ?: UNPICKED) }
    var pain by rememberSaveable { mutableIntStateOf(initial.pain) }
    val activityIds = rememberSaveable(saver = LongListSaver) { initial.activityIds.toMutableStateList() }
    var locationId by rememberSaveable { mutableStateOf(initial.locationId) }
    var socialId by rememberSaveable { mutableStateOf(initial.socialId) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var noteOpen by rememberSaveable { mutableStateOf(initial.note.isNotEmpty()) }
    var photoPath by rememberSaveable { mutableStateOf(initial.photoPath) }
    var newPhotoUri by rememberSaveable { mutableStateOf(initial.newPhotoUri) }
    val photoStore = LocalContext.current.appGraph.photoStore
    var showSkip by rememberSaveable { mutableStateOf(false) }
    var placeTouched by rememberSaveable { mutableStateOf(false) }
    var companyTouched by rememberSaveable { mutableStateOf(false) }
    var addingType by rememberSaveable { mutableStateOf<CategoryType?>(null) }
    val scope = rememberCoroutineScope()
    val ready = valence != UNPICKED && arousal != UNPICKED

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

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            header()

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TapScale(
                        title = "Valence",
                        range = MoodScale.MIN..MoodScale.MAX,
                        value = valence.takeIf { it != UNPICKED },
                        color = MoodChartColors.valence(),
                        lowAnchor = "Awful",
                        highAnchor = "Great",
                        onPick = { valence = it },
                    )
                    TapScale(
                        title = "Energy",
                        range = MoodScale.MIN..MoodScale.MAX,
                        value = arousal.takeIf { it != UNPICKED },
                        color = MoodChartColors.arousal(),
                        lowAnchor = "Drained",
                        highAnchor = "Wired",
                        onPick = { arousal = it },
                    )
                    TapScale(
                        title = "Pain",
                        range = PainScale.MIN..PainScale.MAX,
                        value = pain,
                        color = MoodChartColors.pain(),
                        lowAnchor = "None",
                        highAnchor = "Worst imaginable",
                        valueLabel = { "$it · ${PainScale.descriptor(it)}" },
                        onPick = { pain = it },
                    )
                }
            }

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
                if (!activityIds.remove(id)) activityIds.add(id)
            }
            CompanySection(socials, socialId, companyStatus, onOther = { addingType = CategoryType.SOCIAL }) {
                companyTouched = true
                socialId = if (socialId == it) null else it
            }

            Extras(
                noteOpen = noteOpen,
                onOpenNote = { noteOpen = true },
                takePhoto = takePhoto.takeIf { photo == null },
            )
            if (noteOpen) NoteField(note, autoFocus = initial.note.isEmpty()) { note = it }
            if (photo != null) {
                AttachedPhoto(
                    photo = photo,
                    onRetake = takePhoto,
                    onRemove = {
                        newPhotoUri?.let { photoStore.discardCapture(Uri.parse(it)) }
                        newPhotoUri = null
                        photoPath = null
                    },
                )
            }
        }

        BottomBar(
            saveLabel = saveLabel,
            ready = ready,
            onSkip = onSkip?.let { { showSkip = true } },
            onSave = {
                onSave(
                    CaptureFormValues(
                        valence = valence,
                        arousal = arousal,
                        pain = pain,
                        activityIds = activityIds.toList(),
                        locationId = locationId,
                        socialId = socialId,
                        note = note,
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
                    CategoryType.ACTIVITY -> if (id !in activityIds) activityIds.add(id)
                    CategoryType.SOCIAL -> { companyTouched = true; socialId = id }
                }
            }
        }
    }

    if (showSkip && onSkip != null) {
        SkipDialog(onDismiss = { showSkip = false }, onConfirm = { showSkip = false; onSkip(it) })
    }
}

private val LongListSaver = listSaver<SnapshotStateList<Long>, Long>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * A row of equal-width numbered cells; one tap picks a value. Cells up to the pick
 * fill with a soft tint of [color] so the row reads like a gauge.
 */
@Composable
private fun TapScale(
    title: String,
    range: IntRange,
    value: Int?,
    color: Color,
    lowAnchor: String,
    highAnchor: String,
    onPick: (Int) -> Unit,
    valueLabel: (Int) -> String = { it.toString() },
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(
                    valueLabel(value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            range.forEach { n ->
                ScaleCell(
                    n = n,
                    selected = n == value,
                    filled = value != null && n < value,
                    color = color,
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
    filled: Boolean,
    color: Color,
    description: String,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        when {
            selected -> color
            filled -> color.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "cell",
    )
    val content by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "cellText",
    )
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .background(background, RoundedCornerShape(10.dp))
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
            FilterChip(
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
        FilterChip(
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
                FilterChip(
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

/** "+ Note" and "Photo" chips; each disappears once its field is showing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Extras(noteOpen: Boolean, onOpenNote: () -> Unit, takePhoto: (() -> Unit)?) {
    if (noteOpen && takePhoto == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!noteOpen) ExtraChip(Icons.Outlined.EditNote, "Note", onOpenNote)
        if (takePhoto != null) ExtraChip(Icons.Outlined.PhotoCamera, "Photo", takePhoto)
    }
}

@Composable
private fun ExtraChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun NoteField(note: String, autoFocus: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = note,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text("Anything worth remembering?") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        minLines = 2,
        maxLines = 5,
    )
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Save pinned under the scrolling content, with the skip path beside it on live prompts. */
@Composable
private fun BottomBar(saveLabel: String, ready: Boolean, onSkip: (() -> Unit)?, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
    AssistChip(onClick = onClick, label = { Text(if (expanded) "Fewer" else "More…") })
}

/** Opens [AddOptionDialog] to name an option that isn't in the list yet. */
@Composable
private fun OtherChip(onClick: () -> Unit) {
    AssistChip(
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
            FilterChip(selected = code == selected, onClick = { onSelect(code) }, label = { Text(label) })
        }
    }
}

/** Section header for one [dev.russell.moodsampler.data.db.ActivityGroup] of chips. */
@Composable
internal fun GroupHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
