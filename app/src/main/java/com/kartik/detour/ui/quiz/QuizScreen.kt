package com.kartik.detour.ui.quiz

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.kartik.detour.container
import com.kartik.detour.data.LoopType
import com.kartik.detour.data.db.CardEntity
import com.kartik.detour.ui.components.Confetti
import com.kartik.detour.ui.components.ProgressBar
import com.kartik.detour.ui.components.StickerCard
import com.kartik.detour.ui.components.termStyle
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(onClose: () -> Unit) {
    val app = LocalContext.current.container
    // Snapshot the due list once, so grading doesn't reshuffle the session underneath you.
    var session by remember { mutableStateOf<List<CardEntity>?>(null) }
    LaunchedEffect(Unit) { session = app.study.due().first() }
    var index by remember { mutableIntStateOf(0) }
    var known by remember { mutableIntStateOf(0) }
    var flipped by remember(index) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val cards = session
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close quiz") }
                Spacer(Modifier.width(8.dp))
                ProgressBar(
                    if (cards.isNullOrEmpty()) 0f else index.toFloat() / cards.size,
                    Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                if (!cards.isNullOrEmpty()) {
                    Text("${minOf(index + 1, cards.size)}/${cards.size}", style = MaterialTheme.typography.labelLarge, color = Ink.Fog)
                }
            }
            Spacer(Modifier.height(20.dp))

            when {
                cards == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink.Sign)
                }
                cards.isEmpty() -> End(
                    title = "Nothing to review.",
                    body = "Cards you've seen come back here on the right days. Check in tomorrow.",
                    onClose = onClose,
                )
                index >= cards.size -> End(
                    title = "Reviewed ${cards.size}.",
                    body = "You knew $known of them. The ones you missed come back tomorrow.",
                    onClose = onClose,
                )
                else -> {
                    val card = cards[index]
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                flipped = !flipped
                            },
                    ) {
                        if (card.kind == "word") {
                            WordFlashcard(card, flipped)
                        } else {
                            StickerCard(
                                type = LoopType.of(card.type), term = card.term, meaning = card.meaning,
                                example = card.example, context = card.extra, tone = card.tone, nsfw = card.nsfw,
                                flipped = flipped, modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!flipped) {
                        Button(
                            onClick = { flipped = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink.Haze, contentColor = Ink.Paper),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 16.dp),
                        ) { Text("Show answer") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { app.study.grade(card, knewIt = false) }
                                    index++
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 16.dp),
                            ) { Text("Missed it", color = Ink.Paper) }
                            Button(
                                onClick = {
                                    scope.launch { app.study.grade(card, knewIt = true) }
                                    known++
                                    index++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 16.dp),
                            ) { Text("Knew it") }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        val finished = cards != null && cards.isNotEmpty() && index >= cards.size
        Confetti(if (finished) 1 else 0, Modifier.fillMaxSize())
    }
}

@Composable
private fun WordFlashcard(card: CardEntity, flipped: Boolean) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(420), label = "flip")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Ink.Dusk)
            .border(1.dp, Ink.Sign.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        if (rotation <= 90f) {
            Column(Modifier.fillMaxSize()) {
                Text("What does this mean?", style = MaterialTheme.typography.labelLarge, color = Ink.Sign)
                Spacer(Modifier.weight(1f))
                Text(card.term, fontFamily = Display, style = termStyle(card.term))
                if (card.tone.isNotBlank()) Text(card.tone, style = MaterialTheme.typography.bodyMedium, color = Ink.Dim)
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(card.term, style = MaterialTheme.typography.titleLarge, color = Ink.Sign)
                Spacer(Modifier.height(10.dp))
                Text(card.meaning, style = MaterialTheme.typography.headlineMedium)
                if (card.extra.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Use instead of “${card.extra}”", style = MaterialTheme.typography.bodyMedium, color = Ink.Fog)
                }
                Spacer(Modifier.height(20.dp))
                if (card.example.isNotBlank()) {
                    Text("“${card.example}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = Ink.Fog)
                }
            }
        }
    }
}

@Composable
private fun End(title: String, body: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Ink.Fog)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) { Text("Back to Today") }
        Spacer(Modifier.height(80.dp))
    }
}
