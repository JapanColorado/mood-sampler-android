package com.russellwhite.moodsampler.ui.dashboard.charts

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
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.russellwhite.moodsampler.data.MoodScale
import com.russellwhite.moodsampler.ui.dashboard.CategoryMood
import com.russellwhite.moodsampler.ui.dashboard.DayMood
import com.russellwhite.moodsampler.ui.dashboard.DayPain
import com.russellwhite.moodsampler.ui.dashboard.HourMood
import com.russellwhite.moodsampler.ui.dashboard.Measure
import com.russellwhite.moodsampler.ui.dashboard.PainLevelMood
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Chart series colors — a CVD-validated blue/orange pair (dataviz-checked
 * against light #fcfcfb and dark #1a1a19 surfaces). Valence is always blue,
 * arousal always orange, pain always red; color follows the measure, never
 * position. Pain never shares a chart with the other two, so its hue only has
 * to read well against the surface.
 */
object MoodChartColors {
    @Composable
    fun valence(): Color = Color(0xFF3B82F6)

    @Composable
    fun arousal(): Color =
        if (isSystemInDarkTheme()) Color(0xFFDD640E) else Color(0xFFF97316)

    @Composable
    fun pain(): Color =
        if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFDC2626)

    @Composable
    fun of(measure: Measure): Color = when (measure) {
        Measure.VALENCE -> valence()
        Measure.ENERGY -> arousal()
        Measure.PAIN -> pain()
    }
}

private val MOOD_MIN = MoodScale.MIN.toDouble()
private val MOOD_MAX = MoodScale.MAX.toDouble()

private val dateFmt = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun dateFormatter(dates: List<LocalDate>) = remember(dates) {
    CartesianValueFormatter { _, value, _ -> dates.getOrNull(value.toInt())?.format(dateFmt) ?: "" }
}

@Composable
fun MoodTimeSeriesChart(days: List<DayMood>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(days) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = days.indices.toList(), y = days.map { it.valence })
                series(x = days.indices.toList(), y = days.map { it.arousal })
            }
        }
    }
    val valenceColor = MoodChartColors.valence()
    val arousalColor = MoodChartColors.arousal()
    val bottomFormatter = dateFormatter(days.map { it.date })
    Column(modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(valenceColor)),
                        ),
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(arousalColor)),
                        ),
                    ),
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = MOOD_MIN, maxY = MOOD_MAX) },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(220.dp),
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
        }
    }
    val painColor = MoodChartColors.pain()
    val bottomFormatter = dateFormatter(days.map { it.date })
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(painColor))),
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 10.0) },
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(200.dp),
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
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(color), thickness = 10.dp),
                ),
                rangeProvider = rangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(200.dp),
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
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(valenceColor), thickness = 14.dp),
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 10.0, minY = MOOD_MIN, maxY = MOOD_MAX) },
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(200.dp),
    )
}

/**
 * Horizontal mean bars per category (Vico has no horizontal bars). Diverging
 * measures grow from a zero midline; one-sided measures (pain) grow from the left.
 */
@Composable
fun CategoryBars(rows: List<CategoryMood>, measure: Measure, modifier: Modifier = Modifier) {
    val barColor = MoodChartColors.of(measure)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val midline = MaterialTheme.colorScheme.outline
    val span = (measure.max - measure.min).toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                ) {
                    val radius = CornerRadius(4.dp.toPx())
                    drawRoundRect(color = track, cornerRadius = radius)
                    val value = row.value.toFloat().coerceIn(measure.min.toFloat(), measure.max.toFloat())
                    if (measure.diverging) {
                        val mid = size.width / 2f
                        // 1px midline marks the scale's neutral middle so direction reads instantly.
                        drawLine(
                            color = midline,
                            start = Offset(mid, 0f),
                            end = Offset(mid, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        val extent = ((value - measure.midpoint.toFloat()) / span) * size.width
                        val left = if (extent >= 0) mid else mid + extent
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, 2.dp.toPx()),
                            size = Size(abs(extent), size.height - 4.dp.toPx()),
                            cornerRadius = radius,
                        )
                    } else {
                        val extent = ((value - measure.min.toFloat()) / span) * size.width
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(0f, 2.dp.toPx()),
                            size = Size(extent, size.height - 4.dp.toPx()),
                            cornerRadius = radius,
                        )
                    }
                }
                Text(
                    "%.1f · n=%d".format(row.value, row.n),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(88.dp),
                    maxLines = 1,
                )
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
