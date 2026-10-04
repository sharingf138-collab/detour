package com.kartik.detour.guard

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.MainActivity
import com.kartik.detour.container
import com.kartik.detour.data.LoopType
import com.kartik.detour.data.Podcast
import com.kartik.detour.data.Video
import com.kartik.detour.ui.components.DetourSign
import com.kartik.detour.ui.components.ProgressBar
import com.kartik.detour.ui.components.VideoCard
import com.kartik.detour.ui.formatMinutes
import com.kartik.detour.ui.openUrl
import com.kartik.detour.ui.openVideo
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.DetourTheme
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Full-screen pause shown in front of Instagram. */
class PauseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val app = container
        if (savedInstanceState == null) app.scope.launch { app.days.countInstaOpen() }
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            DetourTheme {
                PauseScreen(
                    onWatch = { video ->
                        app.scope.launch { app.days.countDetour() }
                        leaveInstagram()
                        openVideo(this, video)
                        finish()
                    },
                    onListen = { pod ->
                        app.scope.launch { app.days.countDetour() }
                        leaveInstagram()
                        openUrl(this, pod.link.ifBlank { pod.audioUrl })
                        finish()
                    },
                    onOpenDetour = {
                        app.scope.launch { app.days.countDetour() }
                        leaveInstagram()
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onOpenInstagram = {
                        app.prefs.allowedUntil = System.currentTimeMillis() + app.prefs.settings.value.graceMinutes * 60_000L
                        finish()
                        packageManager.getLaunchIntentForPackage(INSTAGRAM)?.let {
                            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onHome = { goHome() },
                )
            }
        }
    }

    /** Push Instagram into the background so "back" from YouTube lands on the home screen, not the feed. */
    private fun leaveInstagram() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun goHome() {
        leaveInstagram()
        finish()
    }
}

private val lines = listOf(
    "Your thumb got here before you did.",
    "Instagram will still be there in a few seconds.",
    "One good video beats forty reels.",
    "Quick check: did you mean to open this?",
    "The feed never ends. This screen does.",
    "You've seen this feed before. It hasn't changed much.",
    "Future you is going to ask where the evening went.",
)

@Composable
private fun PauseScreen(
    onWatch: (Video) -> Unit,
    onListen: (Podcast) -> Unit,
    onOpenDetour: () -> Unit,
    onOpenInstagram: () -> Unit,
    onHome: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.container
    val content by app.content.content.collectAsStateWithLifecycle()
    val day by remember { app.days.todayFlow() }.collectAsStateWithLifecycle(null)
    val settings = app.prefs.settings.value
    val night = remember { app.prefs.isNight() }

    var minutes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { minutes = withContext(Dispatchers.IO) { InstaUsage.minutesOn(context, LocalDate.now()) } }

    // Countdown before "Open Instagram" unlocks.
    val wait = settings.waitSeconds
    var left by remember { mutableIntStateOf(wait) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { bar.animateTo(1f, tween(wait * 1000, easing = LinearEasing)) }
        while (left > 0) {
            delay(1000)
            left--
        }
    }

    // The one big moment: the sign swings in like it was just hammered into the road.
    val swing = remember { Animatable(-28f) }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        swing.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = Spring.StiffnessLow))
    }

    val opens = (day?.instaOpens ?: 1).coerceAtLeast(1)
    val line = lines[opens % lines.size]
    val loopCard = content?.loop?.filter { settings.showNsfw || !it.nsfw }?.let { if (it.isEmpty()) null else it[opens % it.size] }
    val hero = content?.videos?.hero
    val podcast = content?.podcasts?.firstOrNull()

    Column(Modifier.fillMaxSize().background(Ink.Night).systemBarsPadding()) {
        // Scrolling middle: sign, stats, the alternative. Buttons below stay pinned.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            DetourSign(
                Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        rotationZ = swing.value
                        scaleX = pop.value
                        scaleY = pop.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
            )
            Spacer(Modifier.height(16.dp))
            Text("Detour ahead.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(10.dp))
            Text(line, style = MaterialTheme.typography.bodyLarge, color = Ink.Fog)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Instagram open number $opens today")
                    minutes?.let { append(", ${formatMinutes(it)} so far") }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium, color = Ink.Sign,
            )
            Spacer(Modifier.height(24.dp))

            if (night && podcast != null) {
                Text("It's late. Listen instead, screen off", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.Dusk)
                        .clickable { onListen(podcast) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Ink.Psych, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(podcast.show, style = MaterialTheme.typography.labelMedium, color = Ink.Psych)
                        Text(podcast.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (hero != null) {
                Text("Watch this instead", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                VideoCard(hero, large = true, onClick = { onWatch(hero) })
            }

            loopCard?.let { card ->
                val type = LoopType.of(card.type)
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.sticker(type))
                        .clickable(onClick = onOpenDetour)
                        .padding(16.dp),
                ) {
                    Text("Or learn one thing: ${type.label.lowercase()}", style = MaterialTheme.typography.labelMedium, color = Ink.StickerInk.copy(alpha = 0.65f))
                    Spacer(Modifier.height(4.dp))
                    Text(card.term, fontFamily = Display, style = MaterialTheme.typography.displaySmall, color = Ink.StickerInk)
                    Text(card.meaning, style = MaterialTheme.typography.bodyMedium, color = Ink.StickerInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)) {
            if (night && podcast != null) {
                PrimaryButton("Listen instead") { onListen(podcast) }
            } else if (hero != null) {
                PrimaryButton("Watch this instead") { onWatch(hero) }
            } else {
                PrimaryButton("Open Detour") { onOpenDetour() }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenInstagram,
                enabled = left == 0,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Text(if (left > 0) "Open Instagram in $left" else "Open Instagram", color = if (left > 0) Ink.Dim else Ink.Paper)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(bar.value, Modifier.fillMaxWidth().padding(horizontal = 24.dp), height = 3.dp, color = Ink.Dim)
            TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now, go to home screen", color = Ink.Fog)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 18.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
