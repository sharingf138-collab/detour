@file:OptIn(ExperimentalMaterial3Api::class)

package com.kartik.detour.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.container
import com.kartik.detour.data.DayContent
import com.kartik.detour.data.HistoryItem
import com.kartik.detour.data.LoopType
import com.kartik.detour.data.NewsItem
import com.kartik.detour.data.Word
import com.kartik.detour.data.streak
import com.kartik.detour.data.today
import com.kartik.detour.guard.GuardSettings
import com.kartik.detour.guard.GuardStatus
import com.kartik.detour.ui.components.Confetti
import com.kartik.detour.ui.components.DetourSign
import com.kartik.detour.ui.components.RouteStop
import com.kartik.detour.ui.components.VideoCard
import com.kartik.detour.ui.greeting
import com.kartik.detour.ui.toastOffline
import com.kartik.detour.ui.openUrl
import com.kartik.detour.ui.openVideo
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(onOpenLoop: () -> Unit, onOpenQuiz: () -> Unit, onOpenWatch: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val content by app.content.content.collectAsStateWithLifecycle()
    val refreshing by app.content.refreshing.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val day by remember { app.days.todayFlow() }.collectAsStateWithLifecycle(null)
    val days by remember { app.days.recent() }.collectAsStateWithLifecycle(emptyList())
    val due by remember { app.study.dueCount() }.collectAsStateWithLifecycle(0)
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    // The route lights up stop by stop as you scroll; it never un-lights.
    var furthest by remember { mutableIntStateOf(0) }
    val lastVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastVisible) { if (lastVisible > furthest) furthest = lastVisible }
    var burst by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    // Cleaner/booster apps force-stop Detour, and Android then switches its accessibility service off.
    var pauseOff by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pauseOff = settings.guardOn && !GuardStatus.read(context).accessibility
    }

    val c = content
    if (c == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink.Sign) }
        return
    }
    val loop = c.loop.filter { settings.showNsfw || !it.nsfw }
    val completed = day?.completed == true

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { app.content.refresh().onFailure { toastOffline(context) } } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "head") {
                    Header(
                        content = c,
                        name = settings.name,
                        streak = streak(days),
                        due = due,
                        loopCount = loop.size,
                        pauseOff = pauseOff,
                        onOpenQuiz = onOpenQuiz,
                    )
                }
                item(key = "words") {
                    RouteStop("Words to use today", Ink.Sign, reached = furthest >= 1) {
                        WordPager(c.words)
                    }
                }
                item(key = "loop") {
                    RouteStop(
                        "Stay in the loop", Ink.Slang, reached = furthest >= 2,
                        trailing = { TextButton(onClick = onOpenLoop) { Text("Open deck", style = MaterialTheme.typography.labelLarge, color = Ink.Slang) } },
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(loop, key = { it.term }) { card ->
                                val type = LoopType.of(card.type)
                                Column(
                                    Modifier
                                        .width(150.dp)
                                        .height(112.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Ink.sticker(type))
                                        .clickable(onClick = onOpenLoop)
                                        .padding(14.dp),
                                ) {
                                    Text(type.label, style = MaterialTheme.typography.labelMedium, color = Ink.StickerInk.copy(alpha = 0.65f))
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        card.term, fontFamily = Display, style = MaterialTheme.typography.displaySmall,
                                        color = Ink.StickerInk, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "india") {
                    RouteStop("India today", Ink.Saffron, reached = furthest >= 3) {
                        NewsList(c.news.india, Ink.Saffron)
                    }
                }
                item(key = "world") {
                    RouteStop("Around the world", Ink.Acronym, reached = furthest >= 4) {
                        NewsList(c.news.world, Ink.Acronym)
                    }
                }
                if (c.onThisDay.isNotEmpty()) {
                    item(key = "history") {
                        RouteStop("On this day", Ink.Paradox, reached = furthest >= 5) {
                            OnThisDay(c.onThisDay)
                        }
                    }
                }
                c.videos.hero?.let { hero ->
                    item(key = "watch") {
                        RouteStop(
                            "Watch instead", Ink.Psych, reached = furthest >= 6,
                            trailing = { TextButton(onClick = onOpenWatch) { Text("More", style = MaterialTheme.typography.labelLarge, color = Ink.Psych) } },
                        ) {
                            VideoCard(hero, large = true, onClick = { openVideo(context, hero) })
                        }
                    }
                }
                item(key = "arrive") {
                    Arrival(
                        completed = completed,
                        streak = streak(days),
                        onFinish = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            burst++
                            scope.launch { app.days.markCompleted() }
                        },
                    )
                }
            }
        }
        Confetti(burst, Modifier.fillMaxSize())
    }
}

@Composable
private fun Header(
    content: DayContent,
    name: String,
    streak: Int,
    due: Int,
    loopCount: Int,
    pauseOff: Boolean,
    onOpenQuiz: () -> Unit,
) {
    val context = LocalContext.current
    val now = LocalDate.now()
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.labelLarge, color = Ink.Fog, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = "Streak", tint = if (streak > 0) Ink.Sign else Ink.Dim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("$streak", style = MaterialTheme.typography.titleMedium, color = if (streak > 0) Ink.Sign else Ink.Dim)
        }
        Spacer(Modifier.height(18.dp))
        Text(greeting(name, LocalTime.now().hour), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            "${content.words.size} words, $loopCount terms and today's news. About 12 minutes, then you're done.",
            style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
        )
        if (content.date != today().toString()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Showing ${LocalDate.parse(content.date).format(DateTimeFormatter.ofPattern("d MMM"))}. Pull down to check for today's stops.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Paradox,
            )
        }
        if (pauseOff) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Ink.Sign, RoundedCornerShape(16.dp))
                    .clickable { GuardSettings.accessibility(context) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("The Instagram pause is off", style = MaterialTheme.typography.titleMedium, color = Ink.Sign)
                Text(
                    "Something switched it off, usually a cleaner or booster app. Tap to turn Detour back on in Accessibility.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Fog,
                )
            }
        }
        if (due > 0) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.Dusk)
                    .clickable(onClick = onOpenQuiz)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$due ${if (due == 1) "card" else "cards"} to review", style = MaterialTheme.typography.titleMedium)
                    Text("Two minutes, so yesterday's words stick", style = MaterialTheme.typography.bodySmall, color = Ink.Fog)
                }
                Text("Start", style = MaterialTheme.typography.labelLarge, color = Ink.Sign)
            }
        }
    }
}

@Composable
private fun WordPager(words: List<Word>) {
    if (words.isEmpty()) return
    val pager = rememberPagerState { words.size }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(end = 36.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
    ) { i ->
        val w = words[i]
        // Height follows the content, so long meanings and examples are never cut off.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 250.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ink.Dusk)
                .border(1.dp, Ink.Line, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Row {
                Text(w.pos, style = MaterialTheme.typography.labelMedium, color = Ink.Sign, modifier = Modifier.weight(1f))
                Text("${i + 1} of ${words.size}", style = MaterialTheme.typography.labelMedium, color = Ink.Dim)
            }
            Spacer(Modifier.height(28.dp))
            Text(w.word, fontFamily = Display, style = MaterialTheme.typography.displayMedium)
            if (w.pronunciation.isNotBlank()) {
                Text(w.pronunciation, style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
            }
            Spacer(Modifier.height(10.dp))
            Text(w.meaning, style = MaterialTheme.typography.bodyLarge)
            if (w.insteadOf.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Use instead of “${w.insteadOf}”", style = MaterialTheme.typography.bodySmall, color = Ink.Sign)
            }
            if (w.example.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(w.example, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Ink.Fog)
            }
        }
    }
}

@Composable
private fun NewsList(items: List<NewsItem>, marker: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { openUrl(context, n.url) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 9.dp, end = 12.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(marker),
                )
                Column(Modifier.weight(1f)) {
                    Text(n.text, style = MaterialTheme.typography.bodyLarge)
                    if (n.source.isNotBlank()) {
                        Text(n.source, style = MaterialTheme.typography.labelSmall, color = Ink.Dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnThisDay(items: List<HistoryItem>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { h ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { openUrl(context, h.url) }
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.width(64.dp)) {
                    Text(
                        "${h.year}", fontFamily = Display, style = MaterialTheme.typography.titleLarge,
                        color = if (h.india) Ink.Saffron else Ink.Acronym,
                    )
                    Text(
                        when (h.kind) { "born" -> "Born"; "died" -> "Died"; else -> if (h.india) "India" else "World" },
                        style = MaterialTheme.typography.labelSmall, color = Ink.Dim,
                    )
                }
                Text(h.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Arrival(completed: Boolean, streak: Int, onFinish: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
        DetourSign(Modifier.size(64.dp), tilt = if (completed) -8f else 0f)
        Spacer(Modifier.height(16.dp))
        if (completed) {
            Text("You've arrived.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (streak > 1) "$streak days in a row. New stops open at 6 AM." else "Day one done. New stops open at 6 AM.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
            )
        } else {
            Text("That's the whole route.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "No endless feed here. Finish today to keep your streak.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Finish today", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
