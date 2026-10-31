package dev.russell.moodsampler.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.russell.moodsampler.ui.theme.AsideStyle
import dev.russell.moodsampler.ui.theme.EyebrowStyle

/** Letterspaced caps over a section, in place of a heavy card title. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, style = EyebrowStyle, color = color)
}

/** The top of a tab: an optional eyebrow over a large serif title. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}

/** Hairline between loose sections; cards are the exception in this design, not the rule. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun Aside(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, style = AsideStyle, color = color)
}

/** A slightly raised sheet of paper with a hairline edge; no shadows anywhere. */
@Composable
fun JournalCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        border = border?.let { BorderStroke(1.dp, it) },
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** A big serif number over a small label. */
@Composable
fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A pill-shaped single choice, in place of the stock segmented button and its checkmark. */
@Composable
fun <T> PillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                label = "pill",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "pillText",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

private val ChipShape: Shape = CircleShape
private val ChipHeight = 36.dp

/** A pickable option: a pill that fills with the accent once chosen. */
@Composable
fun OptionChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.height(ChipHeight),
        shape = ChipShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

/** A pill that does something (add, expand, open an editor) rather than holding a choice. */
@Composable
fun ActionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.height(ChipHeight),
        leadingIcon = leadingIcon,
        shape = ChipShape,
        colors = AssistChipDefaults.assistChipColors(labelColor = labelColor),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * A soft glow of [color] falling from the top of the screen, fading as it changes.
 * Pass [Color.Transparent] for plain paper. Draws under the status bar, so apply it
 * before any status bar padding.
 */
@Composable
fun Modifier.moodWash(color: Color, reach: Dp = 420.dp): Modifier {
    val animated by animateColorAsState(color, label = "wash")
    return drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to animated.copy(alpha = animated.alpha * WASH_ALPHA),
                1f to animated.copy(alpha = 0f),
                startY = 0f,
                endY = reach.toPx(),
            ),
        )
    }
}

private const val WASH_ALPHA = 0.28f

/** A filled circle, the app's recurring mark for one sampled moment. */
@Composable
fun MoodDot(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
