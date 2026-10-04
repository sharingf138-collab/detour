package com.kartik.detour.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.container
import com.kartik.detour.data.Podcast
import com.kartik.detour.ui.components.FilterPill
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val audio by app.audio.state.collectAsStateWithLifecycle()
    val content by app.content.content.collectAsStateWithLifecycle()
    val p = audio.podcast

    // "Dim" turns the screen almost black for bed; tap anywhere to undo.
    var dim by remember { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(dim) {
        val w = activity?.window
        val lp = w?.attributes
        val old = lp?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (dim && w != null && lp != null) {
            lp.screenBrightness = 0.01f
            w.attributes = lp
        }
        onDispose {
            if (dim && w != null) {
                val a = w.attributes
                a.screenBrightness = old
                w.attributes = a
            }
        }
    }

    // Live countdown for the sleep timer label.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(audio.sleepAtMs) {
        while (audio.sleepAtMs != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.Night)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { dim = true }) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Ink.Psych, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dim screen", color = Ink.Psych)
                }
            }

            if (p == null) {
                Spacer(Modifier.height(80.dp))
                Text("Nothing playing.", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("Pick something from For tonight.", style = MaterialTheme.typography.bodyLarge, color = Ink.Fog)
                Spacer(Modifier.height(20.dp))
                content?.podcasts?.forEach { pod -> UpNext(pod) { app.audio.play(pod) } }
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink.Dusk),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Ink.Psych, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(p.show, style = MaterialTheme.typography.labelLarge, color = Ink.Psych)
            Spacer(Modifier.height(4.dp))
            Text(p.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)

            // Scrubber. While dragging, show the drag position instead of the live one.
            Spacer(Modifier.height(20.dp))
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val dur = audio.durationMs.coerceAtLeast(1)
            val shownPos = if (dragging) (dragValue * dur).toLong() else audio.positionMs
            Slider(
                value = if (dragging) dragValue else audio.positionMs.toFloat() / dur,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    app.audio.seekTo((dragValue * dur).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Ink.Sign, activeTrackColor = Ink.Sign, inactiveTrackColor = Ink.Haze,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(shownPos), style = MaterialTheme.typography.labelMedium, color = Ink.Fog)
                Spacer(Modifier.weight(1f))
                Text(
                    if (audio.durationMs > 0) "-" + clock(audio.durationMs - shownPos) else "",
                    style = MaterialTheme.typography.labelMedium, color = Ink.Fog,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { app.audio.seekBy(-10_000) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Ink.Sign)
                        .clickable { app.audio.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (audio.buffering && !audio.playing) {
                        CircularProgressIndicator(color = Ink.SignInk, modifier = Modifier.size(34.dp))
                    } else {
                        Icon(
                            if (audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (audio.playing) "Pause" else "Play",
                            tint = Ink.SignInk, modifier = Modifier.size(44.dp),
                        )
                    }
                }
                IconButton(onClick = { app.audio.seekBy(30_000) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(34.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            Text(
                audio.sleepAtMs?.let { "Stops in ${clock((it - now).coerceAtLeast(0))}" } ?: "Pauses the episode for you, so you can fall asleep to it.",
                style = MaterialTheme.typography.bodySmall, color = if (audio.sleepAtMs != null) Ink.Sign else Ink.Fog,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Off", audio.sleepAtMs == null, Ink.Sign) { app.audio.sleepIn(null) }
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterPill("$m min", false, Ink.Sign) { app.audio.sleepIn(m) }
                }
            }

            val others = content?.podcasts.orEmpty().filter { it.id != p.id }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text("Also for tonight", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                others.forEach { pod -> UpNext(pod) { app.audio.play(pod) } }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (dim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dim = false },
                contentAlignment = Alignment.Center,
            ) {
                Text("Tap to wake the screen", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2A2638))
            }
        }
    }
}

@Composable
private fun UpNext(p: Podcast, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Haze), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Ink.Psych, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.show, style = MaterialTheme.typography.labelMedium, color = Ink.Psych)
            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Slim bar above the tabs while something is loaded in the player. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val audio by app.audio.state.collectAsStateWithLifecycle()
    val p = audio.podcast ?: return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Haze)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Ink.Psych, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.show, style = MaterialTheme.typography.labelSmall, color = Ink.Dim, maxLines = 1)
        }
        IconButton(onClick = { app.audio.toggle() }) {
            Icon(
                if (audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (audio.playing) "Pause" else "Play",
                tint = Ink.Sign,
            )
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
