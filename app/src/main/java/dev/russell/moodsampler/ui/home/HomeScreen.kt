package dev.russell.moodsampler.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.russell.moodsampler.BuildConfig
import dev.russell.moodsampler.appGraph
import dev.russell.moodsampler.scheduling.DeviceClock
import dev.russell.moodsampler.ui.components.Aside
import dev.russell.moodsampler.ui.components.BigStat
import dev.russell.moodsampler.ui.components.Eyebrow
import dev.russell.moodsampler.ui.components.JournalCard
import dev.russell.moodsampler.ui.components.MoodDot
import dev.russell.moodsampler.ui.components.Rule
import dev.russell.moodsampler.ui.components.ScreenTitle
import dev.russell.moodsampler.ui.components.moodWash
import dev.russell.moodsampler.ui.dashboard.charts.MoodChartColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

@Composable
fun HomeScreen(
    onAnswerPrompt: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var now by remember { mutableStateOf(LocalDateTime.now(DeviceClock)) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        now = LocalDateTime.now(DeviceClock)
        onPauseOrDispose { }
    }

    // The page takes on the color of today's mood so far; plain paper until there's an answer.
    val todayValences = state.dots.mapNotNull { it.valence }
    val wash = if (todayValences.isEmpty()) Color.Transparent else MoodChartColors.valenceAt(todayValences.average())

    Column(
        Modifier
            .fillMaxSize()
            .moodWash(wash)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenTitle(greeting(now.hour), eyebrow = now.format(dateFmt))

        if (!notificationsEnabled) {
            JournalCard(
                color = MaterialTheme.colorScheme.errorContainer,
                border = null,
                spacing = 6.dp,
            ) {
                Text(
                    "Notifications are off",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Prompts can't reach you, so no data gets collected. " +
                        "Enable notifications to start sampling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Open notification settings") }
            }
        }

        state.openPromptId?.let { promptId -> WaitingPrompt(onAnswer = { onAnswerPrompt(promptId) }) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Today")
            state.todayNote?.let { Aside(it) }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (state.expectedToday == 0) "—" else "${state.answeredToday} of ${state.expectedToday}",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    if (state.missedToday > 0) "answered · ${state.missedToday} missed" else "answered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            if (state.dots.isNotEmpty()) DayStrip(state.dots)
        }

        Rule()

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("Last 7 days")
            val rate = state.weekResponseRate
            if (rate == null) {
                Aside("No concluded prompts yet — your response rate will appear here.")
            } else {
                Row(Modifier.fillMaxWidth()) {
                    BigStat("$rate%", "response rate", Modifier.weight(1f))
                    BigStat("${state.weekAnswered}", "samples", Modifier.weight(1f))
                }
                WeekBars(state.weekMeans)
            }
        }

        Rule()

        Aside(
            "Prompts arrive at random moments — that's what makes the picture honest. " +
                "Answer when you can; missed ones are fine.",
        )
        Text(
            state.samplingSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = {
                scope.launch { context.appGraph.promptScheduler.debugFireIn(10) }
            }) { Text("Debug: fire test prompt in 10 s") }
            OutlinedButton(onClick = {
                scope.launch { dev.russell.moodsampler.debug.seedFakeHistory(context.appGraph) }
            }) { Text("Debug: seed 30 days of fake data") }
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning."
    in 12..17 -> "Good afternoon."
    in 18..22 -> "Good evening."
    else -> "Still up?"
}

/** The one loud thing on the page: a coral sheet that opens the waiting prompt. */
@Composable
private fun WaitingPrompt(onAnswer: () -> Unit) {
    Surface(
        onClick = onAnswer,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("A prompt is waiting", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Swiped the notification away? You can still answer it until it expires.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Answer now", style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(20.dp))
            }
        }
    }
}

/** A dot per prompt: filled in today's mood colors, hollow if missed, small if still to come. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayStrip(dots: List<DayDot>) {
    val answered = dots.count { it.kind == DayDot.Kind.ANSWERED }
    val missed = dots.count { it.kind == DayDot.Kind.MISSED }
    val description = "$answered answered, $missed missed, ${dots.size - answered - missed} still open or to come"
    FlowRow(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        dots.forEach { dot ->
            Box(Modifier.size(DOT_SIZE), contentAlignment = Alignment.Center) {
                when (dot.kind) {
                    DayDot.Kind.ANSWERED -> MoodDot(
                        // Only a row whose answer fell outside the lookback has no valence.
                        dot.valence?.let { MoodChartColors.valenceAt(it.toDouble()) } ?: MaterialTheme.colorScheme.outline,
                        size = DOT_SIZE,
                    )
                    DayDot.Kind.MISSED -> Box(
                        Modifier
                            .size(DOT_SIZE)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    DayDot.Kind.OPEN -> PulsingDot()
                    DayDot.Kind.PENDING -> MoodDot(MaterialTheme.colorScheme.outlineVariant, size = 8.dp)
                }
            }
        }
    }
}

private val DOT_SIZE = 24.dp

@Composable
private fun PulsingDot() {
    val pulse by rememberInfiniteTransition(label = "open").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "openAlpha",
    )
    MoodDot(MaterialTheme.colorScheme.primary, Modifier.alpha(pulse), size = DOT_SIZE)
}

/** Seven small columns of daily mean valence, with a stub for days without an answer. */
@Composable
private fun WeekBars(days: List<DayMean>) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val description = day.valence?.let { "$name: mean valence %.1f".format(it) } ?: "$name: no answers"
            Column(
                Modifier.clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.height(WEEK_BAR_MAX), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(WEEK_BAR_WIDTH)
                            .height(day.valence?.let { (WEEK_BAR_MAX * (it / 10.0).toFloat()).coerceAtLeast(6.dp) } ?: 3.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                day.valence?.let { MoodChartColors.valenceAt(it) }
                                    ?: MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
                Text(
                    name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val WEEK_BAR_MAX = 64.dp
private val WEEK_BAR_WIDTH = 20.dp
