package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.api.MarketCandle

private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)
private val MOVING_AVERAGE_COLOR = Color(0xFF2196F3)
private val TREND_LINE_COLOR = Color(0xFFFFA000)
private val FIBONACCI_COLOR = Color(0xFFBA68C8)
private const val MOVING_AVERAGE_PERIOD = 20

// Standard retracement ratios — drawn between the visible range's high and
// low, same convention every charting platform uses (0%/100% are the
// extremes themselves, the rest are the actual retracement levels traders
// watch for support/resistance).
private val FIBONACCI_RATIOS = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)

/**
 * Deliberately minimal — up to ~180 daily candles (≈6 months), no zoom/pan/
 * crosshair. That's a small enough dataset that a plain Canvas draw costs
 * nothing once laid out; no charting library needed for this.
 *
 * [showMovingAverage] overlays a simple 20-period moving average computed
 * from this same candle list — no new data fetch. The trend line is a
 * session-only annotation: drag anywhere on the chart to draw one, matching
 * the chart's own "deliberately minimal" philosophy rather than building a
 * persisted drawing-tools subsystem.
 */
@Composable
fun CandlestickChart(
    candles: List<MarketCandle>,
    modifier: Modifier = Modifier,
    showMovingAverage: Boolean = false,
    showFibonacci: Boolean = false
) {
    if (candles.isEmpty()) {
        Text("No chart data available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        return
    }

    var trendLineStart by remember(candles) { mutableStateOf<Offset?>(null) }
    var trendLineEnd by remember(candles) { mutableStateOf<Offset?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val maxPrice = candles.maxOf { it.high }
    val minPrice = candles.minOf { it.low }
    val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

    val movingAverages: List<Double?> = remember(candles, showMovingAverage) {
        if (!showMovingAverage) emptyList()
        else candles.indices.map { i ->
            if (i < MOVING_AVERAGE_PERIOD - 1) null
            else candles.subList(i - MOVING_AVERAGE_PERIOD + 1, i + 1).map { it.close }.average()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(candles) {
                detectDragGestures(
                    onDragStart = { offset ->
                        trendLineStart = offset
                        trendLineEnd = offset
                    },
                    onDrag = { change, _ -> trendLineEnd = change.position }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val slot = width / candles.size
        val bodyWidth = (slot * 0.6f).coerceAtLeast(1f)

        fun yFor(price: Double): Float {
            val fraction = (price - minPrice) / range
            return height - (fraction * height).toFloat()
        }

        candles.forEachIndexed { index, candle ->
            val x = index * slot + slot / 2f
            val isUp = candle.close >= candle.open
            val color = if (isUp) GREEN else RED

            // Wick
            drawLine(
                color = color,
                start = Offset(x, yFor(candle.high)),
                end = Offset(x, yFor(candle.low)),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )

            // Body
            val top = yFor(maxOf(candle.open, candle.close))
            val bottom = yFor(minOf(candle.open, candle.close))
            drawRect(
                color = color,
                topLeft = Offset(x - bodyWidth / 2f, top),
                size = androidx.compose.ui.geometry.Size(bodyWidth, (bottom - top).coerceAtLeast(1f))
            )
        }

        if (showMovingAverage) {
            var previousPoint: Offset? = null
            movingAverages.forEachIndexed { index, average ->
                if (average == null) return@forEachIndexed
                val point = Offset(index * slot + slot / 2f, yFor(average))
                previousPoint?.let { drawLine(color = MOVING_AVERAGE_COLOR, start = it, end = point, strokeWidth = 2f) }
                previousPoint = point
            }
        }

        if (showFibonacci) {
            FIBONACCI_RATIOS.forEach { ratio ->
                // Standard top-down convention: 0% at the range high, 100% at
                // the range low — matches how retracement levels are quoted
                // everywhere else (price "retraces" down from a high).
                val price = maxPrice - ratio * range
                val y = yFor(price)
                drawLine(
                    color = FIBONACCI_COLOR.copy(alpha = 0.6f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${(ratio * 100).toInt()}% ${"%.4f".format(price)}",
                    topLeft = Offset(4f, (y - 14f).coerceIn(0f, height - 14f)),
                    style = TextStyle(color = FIBONACCI_COLOR, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        val start = trendLineStart
        val end = trendLineEnd
        if (start != null && end != null) {
            drawLine(
                color = TREND_LINE_COLOR,
                start = start,
                end = end,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
        }
    }
}
