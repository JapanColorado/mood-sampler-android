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
import com.russellwhite.moodsampler.ui.dashboard.CategoryMood
import com.russellwhite.moodsampler.ui.dashboard.DayMood
import com.russellwhite.moodsampler.ui.dashboard.HourMood
import java.time.format.DateTimeFormatter

/**
 * Chart series colors — a CVD-validated blue/orange pair (dataviz-checked
 * against light #fcfcfb and dark #1a1a19 surfaces). Valence is always blue,
 * arousal always orange; color follows the measure, never position.
 */
object MoodChartColors {
    @Composable
    fun valence(): Color = Color(0xFF3B82F6)

    @Composable
    fun arousal(): Color =
        if (isSystemInDarkTheme()) Color(0xFFDD640E) else Color(0xFFF97316)
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d")

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
    val bottomFormatter = remember(days) {
        CartesianValueFormatter { _, value, _ ->
            days.getOrNull(value.toInt())?.date?.format(dateFmt) ?: ""
        }
    }
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
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = -1.0, maxY = 1.0) },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(valenceColor, "Pleasantness")
            LegendItem(arousalColor, "Energy")
        }
    }
}

@Composable
fun HourOfDayChart(hours: List<HourMood>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hours) {
        modelProducer.runTransaction {
            columnSeries { series(x = hours.map { it.hour }, y = hours.map { it.valence }) }
        }
    }
    val valenceColor = MoodChartColors.valence()
    val bottomFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}h" }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(valenceColor), thickness = 10.dp),
                ),
                rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = -1.0, maxY = 1.0) },
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(200.dp),
    )
}

/** Horizontal mean-valence bars diverging from a zero midline (Vico has no horizontal bars). */
@Composable
fun CategoryBars(rows: List<CategoryMood>, modifier: Modifier = Modifier) {
    val barColor = MoodChartColors.valence()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val midline = MaterialTheme.colorScheme.outline
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
                    val mid = size.width / 2f
                    val radius = CornerRadius(4.dp.toPx())
                    drawRoundRect(color = track, cornerRadius = radius)
                    // 1px midline marks zero so direction reads instantly.
                    drawLine(
                        color = midline,
                        start = Offset(mid, 0f),
                        end = Offset(mid, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val extent = (row.valence.toFloat() / 2f) * size.width
                    val left = if (extent >= 0) mid else mid + extent
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, 2.dp.toPx()),
                        size = Size(kotlin.math.abs(extent), size.height - 4.dp.toPx()),
                        cornerRadius = radius,
                    )
                }
                Text(
                    "%+.2f · n=%d".format(row.valence, row.n),
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
