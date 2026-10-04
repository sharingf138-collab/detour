package com.kartik.detour

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kartik.detour.data.db.NoteEntity
import com.kartik.detour.ui.loop.LoopScreen
import com.kartik.detour.ui.notes.NoteSheet
import com.kartik.detour.ui.notes.NotesScreen
import com.kartik.detour.ui.quiz.QuizScreen
import com.kartik.detour.ui.theme.DetourTheme
import com.kartik.detour.ui.theme.Ink
import com.kartik.detour.ui.today.TodayScreen
import com.kartik.detour.ui.watch.WatchScreen
import com.kartik.detour.ui.you.YouScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { DetourTheme { DetourRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Rounded.Route),
    Tab("loop", "Loop", Icons.Rounded.Style),
    Tab("watch", "Watch", Icons.Rounded.PlayCircle),
    Tab("notes", "Notes", Icons.Rounded.EditNote),
    Tab("you", "You", Icons.Rounded.Face),
)

@Composable
private fun DetourRoot() {
    val context = LocalContext.current
    val app = context.container
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Fetch today's stops whenever the app opens; cached content shows meanwhile.
    LaunchedEffect(Unit) { app.content.refresh() }

    // Back from a video? Offer to keep a note about it.
    var notePrompt by remember { mutableStateOf<NoteEntity?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val pending = app.prefs.pendingNote ?: return@LifecycleEventEffect
        app.prefs.pendingNote = null
        val away = System.currentTimeMillis() - pending.openedAt
        if (away > 45_000) {
            val now = System.currentTimeMillis()
            notePrompt = NoteEntity(
                body = "", videoId = pending.videoId, videoTitle = pending.title, channel = pending.channel,
                thumbnail = pending.thumbnail, videoUrl = pending.url, createdAt = now, updatedAt = now,
            )
        }
    }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Box(Modifier.fillMaxSize().background(Ink.Night)) {
        NavHost(nav, startDestination = "today", modifier = Modifier.fillMaxSize()) {
            composable("today") {
                TodayScreen(onOpenLoop = { go("loop") }, onOpenQuiz = { nav.navigate("quiz") }, onOpenWatch = { go("watch") })
            }
            composable("loop") { LoopScreen(onOpenQuiz = { nav.navigate("quiz") }) }
            composable("watch") { WatchScreen() }
            composable("notes") { NotesScreen() }
            composable("you") { YouScreen() }
            composable("quiz") { QuizScreen(onClose = { nav.popBackStack() }) }
        }
        if (route != "quiz") {
            BottomBar(current = route, onSelect = ::go, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    notePrompt?.let { n ->
        NoteSheet(note = n, onDismiss = { notePrompt = null }, prompt = "What stuck with you?")
    }
}

/** Floating dark bar; the selected tab gets an amber diamond-cornered pill. */
@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Ink.Dusk)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab.route) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(52.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(topStart = 15.dp, bottomEnd = 15.dp, topEnd = 6.dp, bottomStart = 6.dp))
                        .background(if (selected) Ink.Sign else Ink.Dusk),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (selected) Ink.SignInk else Ink.Fog,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Ink.Paper else Ink.Dim,
                )
            }
        }
    }
}
