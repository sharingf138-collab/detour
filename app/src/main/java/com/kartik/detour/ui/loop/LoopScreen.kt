package com.kartik.detour.ui.loop

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.container
import com.kartik.detour.data.ContentRepository
import com.kartik.detour.data.LoopType
import com.kartik.detour.data.db.CardEntity
import com.kartik.detour.data.today
import com.kartik.detour.ui.components.FilterPill
import com.kartik.detour.ui.components.ScreenTitle
import com.kartik.detour.ui.components.StickerCard
import com.kartik.detour.ui.components.toneLabel
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LoopScreen(onOpenQuiz: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Stay in the loop", "Slang, terms and references people drop mid-conversation.")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Today's deck", tab == 0, Ink.Sign) { tab = 0 }
            FilterPill("All terms", tab == 1, Ink.Sign) { tab = 1 }
        }
        Spacer(Modifier.height(16.dp))
        if (tab == 0) Deck(onOpenQuiz = onOpenQuiz, onBrowse = { tab = 1 }) else Archive()
    }
}

@Composable
private fun Deck(onOpenQuiz: () -> Unit, onBrowse: () -> Unit) {
    val app = LocalContext.current.container
    val content by app.content.content.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val ids = remember(content, settings.showNsfw) {
        content?.loop.orEmpty().filter { settings.showNsfw || !it.nsfw }.map { ContentRepository.loopId(it) }
    }
    val cards by remember(ids) { app.study.byIds(ids) }.collectAsStateWithLifecycle(emptyList())
    val due by remember { app.study.dueCount() }.collectAsStateWithLifecycle(0)
    val todayIso = today().toString()
    // Keep today's order; drop the ones already swiped today.
    val remaining = ids.mapNotNull { id -> cards.firstOrNull { it.id == id } }.filter { it.lastReviewed != todayIso }
    val scope = rememberCoroutineScope()

    if (remaining.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
            Text("Deck cleared.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Anything you swiped left comes back tomorrow. The rest returns in a few days so it sticks.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
            )
            Spacer(Modifier.height(24.dp))
            if (due > 0) {
                Button(
                    onClick = onOpenQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) { Text("Review $due due cards") }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) {
                Text("Browse all terms", color = Ink.Paper)
            }
            Spacer(Modifier.height(120.dp))
        }
        return
    }

    SwipeDeck(
        cards = remaining,
        total = ids.size,
        onSwiped = { card, knewIt -> scope.launch { app.study.grade(card, knewIt) } },
    )
}

@Composable
private fun SwipeDeck(cards: List<CardEntity>, total: Int, onSwiped: (CardEntity, Boolean) -> Unit) {
    val top = cards.first()
    val offset = remember(top.id) { Animatable(0f) }
    var flipped by remember(top.id) { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(1000f) }
    var gone by remember(top.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Throw the top card off-screen, then record the verdict.
    fun fling(knewIt: Boolean) {
        if (gone) return
        gone = true
        scope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            offset.animateTo(if (knewIt) width * 1.4f else -width * 1.4f, tween(240))
            onSwiped(top, knewIt)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "${total - cards.size + 1} of $total. Swipe right if you knew it, left to see it again tomorrow.",
            style = MaterialTheme.typography.bodySmall, color = Ink.Dim,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
        ) {
            cards.getOrNull(1)?.let { next ->
                StickerCard(
                    type = LoopType.of(next.type), term = next.term, meaning = next.meaning,
                    example = next.example, context = next.extra, tone = next.tone, nsfw = next.nsfw,
                    flipped = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pull = (abs(offset.value) / width).coerceIn(0f, 1f)
                            scaleX = 0.92f + 0.08f * pull
                            scaleY = 0.92f + 0.08f * pull
                            translationY = 36f * (1f - pull)
                        },
                )
            }
            StickerCard(
                type = LoopType.of(top.type), term = top.term, meaning = top.meaning,
                example = top.example, context = top.extra, tone = top.tone, nsfw = top.nsfw,
                flipped = flipped,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset.value
                        rotationZ = offset.value / width * 10f
                    }
                    .pointerInput(top.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offset.value) > width * 0.28f) fling(offset.value > 0)
                                else scope.launch { offset.animateTo(0f, spring()) }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                            onHorizontalDrag = { change, drag ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + drag) }
                            },
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        flipped = !flipped
                    },
            )
            // Verdict stamp that fades in while dragging.
            val pull = (offset.value / (width * 0.28f)).coerceIn(-1f, 1f)
            if (abs(pull) > 0.05f) {
                Text(
                    if (pull > 0) "Knew it" else "Again tomorrow",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink.Paper,
                    modifier = Modifier
                        .align(if (pull > 0) Alignment.TopStart else Alignment.TopEnd)
                        .padding(20.dp)
                        .alpha(abs(pull))
                        .clip(RoundedCornerShape(50))
                        .background(Ink.Night.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { fling(false) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text("Teach me again", color = Ink.Paper) }
            Button(
                onClick = { fling(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text("Knew it") }
        }
        Spacer(Modifier.height(112.dp))
    }
}

@Composable
private fun Archive() {
    val app = LocalContext.current.container
    val all by remember { app.study.archive() }.collectAsStateWithLifecycle(emptyList())
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }

    val shown = all.filter { c ->
        (settings.showNsfw || !c.nsfw) &&
            (filter == null || c.type == filter) &&
            (query.isBlank() || c.term.contains(query, true) || c.meaning.contains(query, true))
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search terms") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Sign, unfocusedBorderColor = Ink.Line, cursorColor = Ink.Sign,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterPill("All", filter == null) { filter = null } }
                items(LoopType.entries.toList()) { t ->
                    FilterPill(t.label, filter == t.key, Ink.sticker(t)) { filter = if (filter == t.key) null else t.key }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (all.isEmpty()) "Terms you see in the deck collect here." else "Nothing matches. Try another word or clear the filter.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        items(shown, key = { it.id }) { c ->
            val type = LoopType.of(c.type)
            val expanded = open == c.id
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (expanded) null else c.id }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .animateContentSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Ink.sticker(type)))
                    Spacer(Modifier.width(12.dp))
                    Text(c.term, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(type.label, style = MaterialTheme.typography.labelSmall, color = Ink.Dim)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    c.meaning, style = MaterialTheme.typography.bodyMedium, color = Ink.Fog,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    modifier = Modifier.padding(start = 22.dp),
                )
                if (expanded) {
                    Column(Modifier.padding(start = 22.dp, top = 8.dp)) {
                        if (c.example.isNotBlank()) {
                            Text("“${c.example}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (c.extra.isNotBlank()) Text(c.extra, style = MaterialTheme.typography.bodySmall, color = Ink.Fog)
                        Spacer(Modifier.height(6.dp))
                        Text(toneLabel(c.tone), style = MaterialTheme.typography.labelSmall, color = Ink.sticker(type))
                    }
                }
            }
        }
    }
}
