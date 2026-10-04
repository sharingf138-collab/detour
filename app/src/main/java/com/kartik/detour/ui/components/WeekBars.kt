package com.kartik.detour.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kartik.detour.ui.theme.Ink
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Bars of Instagram minutes per day; the selected one in amber. Tap a bar to select it. */
@Composable
fun WeekBars(week: List<Pair<LocalDate, Long>>, selected: Int, onSelect: (Int) -> Unit = {}) {
    val max = (week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(30L)
    val gapDp = 10.dp
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointerInput(week.size) {
                    detectTapGestures { pos ->
                        val gap = gapDp.toPx()
                        val w = (size.width - gap * (week.size - 1)) / week.size
                        val i = (pos.x / (w + gap)).toInt().coerceIn(0, week.lastIndex)
                        onSelect(i)
                    }
                },
        ) {
            val n = week.size
            val gap = gapDp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            week.forEachIndexed { i, (_, mins) ->
                val h = (mins.toFloat() / max) * size.height
                val x = i * (w + gap)
                drawRoundRect(
                    color = Ink.Haze,
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                drawRoundRect(
                    color = if (i == selected) Ink.Sign else Ink.Psych.copy(alpha = 0.55f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(w, h.coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, (d, mins) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) Ink.Sign else Ink.Dim, textAlign = TextAlign.Center,
                    )
                    Text(if (mins >= 60) "${mins / 60}h" else "${mins}m", style = MaterialTheme.typography.labelSmall, color = Ink.Fog, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
