package com.gazai.fuel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vehicle photo loaded from app storage, or a plain placeholder with the vehicle's initial. */
@Composable
fun VehiclePhoto(path: String?, label: String, height: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().height(height),
        )
    } else {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Simple vertical bar chart; the last bar is highlighted. */
@Composable
fun BarChart(values: List<Double>, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val w = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = (v / max).toFloat() * size.height
            drawRect(
                color = if (i == values.lastIndex) bar else dim,
                topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                size = Size(w, h.coerceAtLeast(2f)),
            )
        }
    }
}

/** Simple line chart through the points, oldest to newest. */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val pad = 8f
        fun pt(i: Int): Offset {
            val x = pad + (size.width - 2 * pad) * i / (values.size - 1)
            val y = pad + (size.height - 2 * pad) * (1f - ((values[i] - lo) / span).toFloat())
            return Offset(x, y)
        }
        for (i in 1 until values.size) drawLine(line, pt(i - 1), pt(i), strokeWidth = 5f)
        values.indices.forEach { drawCircle(line, radius = if (it == values.lastIndex) 9f else 5f, center = pt(it)) }
    }
}
