package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.api.MarketCandle

private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)

/**
 * Deliberately minimal — up to ~180 daily candles (≈6 months), no zoom/pan/
 * crosshair. That's a small enough dataset that a plain Canvas draw costs
 * nothing once laid out; no charting library needed for this.
 */
@Composable
fun CandlestickChart(candles: List<MarketCandle>, modifier: Modifier = Modifier) {
    if (candles.isEmpty()) {
        Text("No chart data available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        return
    }

    val maxPrice = candles.maxOf { it.high }
    val minPrice = candles.minOf { it.low }
    val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
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
    }
}
