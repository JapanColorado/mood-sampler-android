package dev.russell.moodsampler.ui.dashboard.charts

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import dev.russell.moodsampler.R
import dev.russell.moodsampler.data.MoodScale
import dev.russell.moodsampler.ui.dashboard.CategoryMood
import dev.russell.moodsampler.ui.dashboard.DayMood
import dev.russell.moodsampler.ui.dashboard.DayPain
import dev.russell.moodsampler.ui.dashboard.HourMood
import dev.russell.moodsampler.ui.dashboard.Measure
import dev.russell.moodsampler.ui.dashboard.PainLevelMood
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Measure colors — a CVD-validated teal/amber pair (dataviz-checked against the
 * paper and ink card surfaces). Valence is always teal, arousal always amber, pain
 * always berry; color follows the measure, never position. Pain never shares a chart
 * with the other two, so its hue only has to read well against the surface.
 *
 * The ramps color a single value on its own, for scale cells and mood dots. Valence
 * is bipolar, so it diverges clay–sand–teal; the other two run pale to saturated.
 */
object MoodChartColors {
    @Composable
    fun valence(): Color = if (isSystemInDarkTheme()) Color(0xFF25A095) else VALENCE_LIGHT

    @Composable
    fun arousal(): Color = if (isSystemInDarkTheme()) Color(0xFFC98214) else Color(0xFFC8790F)

    @Composable
    fun pain(): Color = if (isSystemInDarkTheme()) Color(0xFFD65C95) else Color(0xFF9C3D6E)

    @Composable
    fun of(measure: Measure): Color = when (measure) {
        Measure.VALENCE -> valence()
        Measure.ENERGY -> arousal()
        Measure.PAIN -> pain()
    }

    /** Color for a valence of [value] on the 0–10 scale. */
    @Composable
    fun valenceAt(value: Double): Color {
        val dark = isSystemInDarkTheme()
        val t = (value / MOOD_MAX).toFloat().coerceIn(0f, 1f)
        val low = if (dark) Color(0xFFD08A4E) else Color(0xFFA6551F)
        val mid = if (dark) Color(0xFF8F8470) else Color(0xFFCDB891)
        return if (t < 0.5f) lerp(low, mid, t * 2) else lerp(mid, valence(), (t - 0.5f) * 2)
    }

    @Composable
    fun arousalAt(value: Double): Color = sequential(arousal(), value)

    @Composable
    fun painAt(value: Double): Color = sequential(pain(), value)

    @Composable
    private fun sequential(top: Color, value: Double): Color {
        val t = (value / MOOD_MAX).toFloat().coerceIn(0f, 1f)
        return lerp(top.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.surface), top, t)
    }

    /** Readable text on a solid fill of [color]. */
    fun contentOn(color: Color): Color = if (color.luminance() > 0.3f) Color(0xFF1C1917) else Color(0xFFFFF8F2)

    private val VALENCE_LIGHT = Color(0xFF008C80)
}

private val MOOD_MIN = MoodScale.MIN.toDouble()
private val MOOD_MAX = MoodScale.MAX.toDouble()

private val dateFmt = DateTimeFormatter.ofPattern("MMM d")

/**
 * The dates behind a time-series model's x indices. They ride in the model's extra
 * store so the axis always labels the model it is drawing: the model updates
 * asynchronously, so a formatter keyed on the composition's dates would briefly label
 * an older, longer model and return "" for its extra points, which Vico rejects with a
 * crash (seen when flipping the range quickly).
 */
private val DatesKey = ExtraStore.Key<List<LocalDate>>()

private val dateFormatter = CartesianValueFormatter { context, value, _ ->
    // Never "": Vico throws on an empty label. The fallback can't trigger while the dates travel with the model.
    context.model.extraStore.getOrNull(DatesKey)?.getOrNull(value.toInt())?.format(dateFmt)
        ?: value.toInt().toString()
}

@Composable
private fun axisLabel(): TextComponent {
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.DEFAULT }
    return rememberAxisLabelComponent(color = MaterialTheme.colorScheme.onSurfaceVariant, typeface = typeface)
}

/** Value axis: clean even ticks over solid hairlines, with no axis line of its own. */
@Composable
private fun valueAxis() = VerticalAxis.rememberStart(
    line = null,
    label = axisLabel(),
    tick = null,
    tickLength = 0.dp,
    guideline = rememberAxisGuidelineComponent(
        fill = fill(MaterialTheme.colorScheme.outlineVariant),
        thickness = 1.dp,
        shape = Shape.Rectangle,
    ),
    itemPlacer = remember { VerticalAxis.ItemPlacer.step({ 2.0 }) },
)

/** Bottom axis, labeling every [spacing]th x so the labels never collide. */
@Composable
private fun labelAxis(formatter: CartesianValueFormatter, spacing: Int = 1) = HorizontalAxis.rememberBottom(
    line = rememberAxisLineComponent(fill = fill(MaterialTheme.colorScheme.outline), thickness = 1.dp),
    label = axisLabel(),
    tick = null,
    tickLength = 0.dp,
    guideline = null,
    valueFormatter = formatter,
    itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) },
)

/** A 2dp smoothed line, over a faint wash of its own color when [wash] (one series only: washes muddy each other). */
@Composable
private fun moodLine(color: Color, wash: Boolean = false) = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(fill(color)),
    stroke = LineCartesianLayer.LineStroke.continuous(2.dp),
    areaFill = if (wash) LineCartesianLayer.AreaFill.single(fill(color.copy(alpha = 0.1f))) else null,
    pointConnector = remember { LineCartesianLayer.PointConnector.cubic() },
)

/** A column with rounded ends, thin enough to leave air between neighbors. */
@Composable
private fun moodColumn(color: Color, thickness: Dp) = rememberLineComponent(
    fill = fill(color),
    thickness = thickness,
    shape = remember { CorneredShape.rounded(allPercent = 30) },
)

/** Shows the whole range at once: no sideways scrolling to find the rest of the data. */
@Composable
private fun FittedChart(
    chart: CartesianChart,
    modelProducer: CartesianChartModelProducer,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Roughly [LABELS_PER_AXIS] date labels across, whatever the range. */
private fun dateLabelSpacing(points: Int) = (points / LABELS_PER_AXIS).coerceAtLeast(1)

private const val LABELS_PER_AXIS = 4

@Composable
fun MoodTimeSeriesChart(days: List<DayMood>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(days) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = days.indices.toList(), y = days.map { it.valence })
                series(x = days.indices.toList(), y = days.map { it.arousal })
            }
            extras { it[DatesKey] = days.map { day -> day.date } }
        }
    }
    val valenceColor = MoodChartColors.valence()
    val arousalColor = MoodChartColors.arousal()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FittedChart(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(moodLine(valenceColor), moodLine(arousalColor)),
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = MOOD_MIN, maxY = MOOD_MAX) },
                ),
                startAxis = valueAxis(),
                bottomAxis = labelAxis(dateFormatter, dateLabelSpacing(days.size)),
            ),
            modelProducer = modelProducer,
            height = 220.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(valenceColor, "Valence")
            LegendItem(arousalColor, "Energy")
        }
    }
}

/** Daily mean pain on its own 0–10 axis. */
@Composable
fun PainTimeSeriesChart(days: List<DayPain>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(days) {
        modelProducer.runTransaction {
            lineSeries { series(x = days.indices.toList(), y = days.map { it.pain }) }
            extras { it[DatesKey] = days.map { day -> day.date } }
        }
    }
    val painColor = MoodChartColors.pain()
    FittedChart(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(moodLine(painColor, wash = true)),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 10.0) },
            ),
            startAxis = valueAxis(),
            bottomAxis = labelAxis(dateFormatter, dateLabelSpacing(days.size)),
        ),
        modelProducer = modelProducer,
        height = 200.dp,
        modifier = modifier,
    )
}

/** Mean of the selected measure per hour of day. */
@Composable
fun HourOfDayChart(hours: List<HourMood>, measure: Measure, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hours) {
        modelProducer.runTransaction {
            columnSeries { series(x = hours.map { it.hour }, y = hours.map { it.value }) }
        }
    }
    val color = MoodChartColors.of(measure)
    val bottomFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}h" }
    }
    // Keyed on the measure: an unkeyed remember would pin the axis to the first range shown.
    val rangeProvider = remember(measure) {
        CartesianLayerRangeProvider.fixed(minY = measure.min, maxY = measure.max)
    }
    FittedChart(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(moodColumn(color, 10.dp)),
                rangeProvider = rangeProvider,
            ),
            startAxis = valueAxis(),
            bottomAxis = labelAxis(bottomFormatter, spacing = 3),
        ),
        modelProducer = modelProducer,
        height = 200.dp,
        modifier = modifier,
    )
}

/** Mean valence at each reported pain level (0–10) — the mood-vs-pain view. */
@Composable
fun PainLevelChart(levels: List<PainLevelMood>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(levels) {
        modelProducer.runTransaction {
            columnSeries { series(x = levels.map { it.level }, y = levels.map { it.valence }) }
        }
    }
    val valenceColor = MoodChartColors.valence()
    val bottomFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}" }
    }
    FittedChart(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(moodColumn(valenceColor, 14.dp)),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 10.0, minY = MOOD_MIN, maxY = MOOD_MAX) },
            ),
            startAxis = valueAxis(),
            bottomAxis = labelAxis(bottomFormatter),
        ),
        modelProducer = modelProducer,
        height = 200.dp,
        modifier = modifier,
    )
}

/**
 * Horizontal mean bars per category (Vico has no horizontal bars), each under its own
 * label so long names never truncate. Diverging measures grow from a zero midline;
 * one-sided measures (pain) grow from the left.
 */
@Composable
fun CategoryBars(rows: List<CategoryMood>, measure: Measure, modifier: Modifier = Modifier) {
    val barColor = MoodChartColors.of(measure)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val midline = MaterialTheme.colorScheme.outline
    val span = (measure.max - measure.min).toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("%.1f".format(row.value), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "  n=${row.n}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color = track, cornerRadius = radius)
                    val value = row.value.toFloat().coerceIn(measure.min.toFloat(), measure.max.toFloat())
                    if (measure.diverging) {
                        val mid = size.width / 2f
                        val extent = ((value - measure.midpoint.toFloat()) / span) * size.width
                        val left = if (extent >= 0) mid else mid + extent
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, 0f),
                            size = Size(abs(extent), size.height),
                            cornerRadius = radius,
                        )
                        // Marks the scale's neutral middle so direction reads instantly.
                        drawLine(
                            color = midline,
                            start = Offset(mid, -2.dp.toPx()),
                            end = Offset(mid, size.height + 2.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    } else {
                        val extent = ((value - measure.min.toFloat()) / span) * size.width
                        drawRoundRect(color = barColor, size = Size(extent, size.height), cornerRadius = radius)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp)) {
            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
