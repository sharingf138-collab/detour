package com.kartik.detour.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kartik.detour.data.LoopType
import com.kartik.detour.data.Video
import com.kartik.detour.data.categoryLabel
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.Ink
import kotlin.random.Random

/** The brand mark: an amber road-sign diamond with an arrow bending away. */
@Composable
fun DetourSign(modifier: Modifier = Modifier, tilt: Float = 0f) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        rotate(45f + tilt, c) {
            val side = s / 1.4142f
            drawRoundRect(
                color = Ink.Sign,
                topLeft = Offset(c.x - side / 2, c.y - side / 2),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.1f),
            )
        }
        // Arrow, laid out in the same 54-unit box as the launcher icon.
        val k = s / 54f
        fun px(x: Float) = c.x + (x - 54f) * k
        fun py(y: Float) = c.y + (y - 54f) * k
        rotate(tilt, c) {
            val stem = Path().apply {
                moveTo(px(48f), py(68f))
                lineTo(px(48f), py(58f))
                quadraticTo(px(48f), py(49f), px(57f), py(49f))
                lineTo(px(61f), py(49f))
            }
            drawPath(stem, Ink.SignInk, style = Stroke(width = 4.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val head = Path().apply {
                moveTo(px(60f), py(42.5f))
                lineTo(px(68.5f), py(49f))
                lineTo(px(60f), py(55.5f))
                close()
            }
            drawPath(head, Ink.SignInk)
        }
    }
}

/**
 * One stop on the Today route. Draws the road line and the stop marker in the
 * left gutter; the line turns amber once the reader has reached this stop.
 */
@Composable
fun RouteStop(
    title: String,
    color: Color,
    reached: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val lit by animateFloatAsState(if (reached) 1f else 0f, tween(500), label = "route")
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 26.dp.toPx()
                val w = 3.dp.toPx()
                drawLine(Ink.Line, Offset(x, 0f), Offset(x, size.height), strokeWidth = w)
                drawLine(Ink.Sign.copy(alpha = 0.85f * lit), Offset(x, 0f), Offset(x, size.height * lit), strokeWidth = w)
                // Stop marker: a small diamond, filled once reached.
                val cy = 18.dp.toPx()
                val r = 8.dp.toPx()
                val diamond = Path().apply {
                    moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close()
                }
                drawPath(diamond, Ink.Night)
                drawPath(diamond, color, style = Stroke(width = 2.dp.toPx()))
                drawPath(diamond, color, alpha = lit)
            }
            .padding(start = 52.dp, end = 20.dp, bottom = 36.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Small rounded label. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) Ink.StickerInk else color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Selectable filter chip in the Detour style. */
@Composable
fun FilterPill(text: String, selected: Boolean, color: Color = Ink.Paper, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Ink.StickerInk else Ink.Fog,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Ink.Dusk)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * A "Stay in the loop" card: solid sticker colour, term on the front, meaning on the back.
 * [flipped] controls which side shows; the flip is animated.
 */
@Composable
fun StickerCard(
    type: LoopType,
    term: String,
    meaning: String,
    example: String,
    context: String,
    tone: String,
    nsfw: Boolean,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(420), label = "flip")
    val color = Ink.sticker(type)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .padding(24.dp),
    ) {
        if (rotation <= 90f) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.label, style = MaterialTheme.typography.labelLarge, color = Ink.StickerInk.copy(alpha = 0.7f))
                    if (nsfw) {
                        Spacer(Modifier.width(8.dp))
                        Tag("18+", Ink.StickerInk)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(term, fontFamily = Display, style = MaterialTheme.typography.displayLarge, color = Ink.StickerInk)
                Spacer(Modifier.height(12.dp))
                Text("Tap to see what it means", style = MaterialTheme.typography.bodyMedium, color = Ink.StickerInk.copy(alpha = 0.6f))
            }
        } else {
            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Text(term, style = MaterialTheme.typography.titleLarge, color = Ink.StickerInk)
                Spacer(Modifier.height(10.dp))
                Text(meaning, style = MaterialTheme.typography.titleMedium, color = Ink.StickerInk)
                if (example.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("“$example”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Ink.StickerInk.copy(alpha = 0.85f))
                }
                Spacer(Modifier.weight(1f))
                if (context.isNotBlank()) {
                    Text(context, style = MaterialTheme.typography.bodyMedium, color = Ink.StickerInk.copy(alpha = 0.75f))
                    Spacer(Modifier.height(12.dp))
                }
                Tag(toneLabel(tone), Ink.StickerInk)
            }
        }
    }
}

fun toneLabel(tone: String) = when (tone) {
    "playful" -> "Said playfully"
    "mocking" -> "Used to roast"
    "serious" -> "Serious term"
    "crude" -> "Crude, know it but careful"
    else -> "Casual"
}

/** Video thumbnail + title + channel. [large] for the hero layout. */
@Composable
fun VideoCard(video: Video, large: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (large) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Ink.Dusk)
                .clickable(onClick = onClick),
        ) {
            Box {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Ink.Haze),
                )
                Tag(categoryLabel(video.category), Ink.category(video.category), Modifier.padding(12.dp), filled = true)
            }
            Column(Modifier.padding(16.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Ink.Fog)
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.Haze),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(categoryLabel(video.category), style = MaterialTheme.typography.labelMedium, color = Ink.category(video.category))
                Spacer(Modifier.height(2.dp))
                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Ink.Dim, maxLines = 1)
            }
        }
    }
}

/** Screen title block used at the top of each tab. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.displayMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink.Fog)
        }
    }
}

/** Amber pill progress bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, color: Color = Ink.Sign) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Box(modifier.height(height).clip(CircleShape).background(Ink.Haze)) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(CircleShape).background(color))
    }
}

private class Bit(
    val x0: Float, val vx: Float, val vy: Float, val spin: Float,
    val w: Float, val h: Float, val color: Color,
)

/** One burst of sticker-coloured confetti each time [burst] changes (0 = none). */
@Composable
fun Confetti(burst: Int, modifier: Modifier = Modifier) {
    if (burst == 0) return
    val t = remember(burst) { Animatable(0f) }
    LaunchedEffect(burst) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    val bits = remember(burst) {
        val colors = listOf(Ink.Sign, Ink.Slang, Ink.Psych, Ink.Acronym, Ink.Paradox, Ink.Meme)
        List(110) {
            Bit(
                x0 = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 1.3f,
                vy = -0.5f - Random.nextFloat() * 0.8f,
                spin = (Random.nextFloat() - 0.5f) * 1440f,
                w = 8f + Random.nextFloat() * 8f,
                h = 5f + Random.nextFloat() * 5f,
                color = colors[it % colors.size],
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val d = density
        bits.forEach { b ->
            val x = size.width * (b.x0 + b.vx * p)
            val y = size.height * (0.55f + b.vy * p + 1.5f * p * p)
            rotate(b.spin * p, Offset(x, y)) {
                drawRect(
                    color = b.color,
                    topLeft = Offset(x - b.w * d / 2, y - b.h * d / 2),
                    size = Size(b.w * d, b.h * d),
                    alpha = (1f - p * p).coerceIn(0f, 1f),
                )
            }
        }
    }
}

/** Shared horizontal arrangement for rows of pills. */
val PillSpacing = Arrangement.spacedBy(8.dp)
