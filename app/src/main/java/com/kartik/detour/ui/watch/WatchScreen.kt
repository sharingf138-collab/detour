package com.kartik.detour.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.container
import com.kartik.detour.data.Podcast
import com.kartik.detour.ui.components.ScreenTitle
import com.kartik.detour.ui.components.VideoCard
import com.kartik.detour.ui.formatMinutes
import com.kartik.detour.ui.openUrl
import com.kartik.detour.ui.openVideo
import com.kartik.detour.ui.theme.Ink

@Composable
fun WatchScreen() {
    val context = LocalContext.current
    val app = context.container
    val content by app.content.content.collectAsStateWithLifecycle()
    val videos = content?.videos

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { ScreenTitle("Watch instead", "One good video beats forty reels. Your note is waiting when you come back.") }
        videos?.hero?.let { hero ->
            item {
                VideoCard(hero, large = true, onClick = { openVideo(context, hero) }, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(28.dp))
            }
        }
        if (!videos?.more.isNullOrEmpty()) {
            item { Section("More to explore") }
            items(videos.more, key = { it.id }) { v ->
                VideoCard(v, large = false, onClick = { openVideo(context, v) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        val pods = content?.podcasts.orEmpty()
        if (pods.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                Section("For tonight", "Audio only, so the screen can go dark.")
            }
            items(pods, key = { it.id }) { p -> PodcastRow(p) { openUrl(context, p.link.ifBlank { p.audioUrl }) } }
        }
        if (videos?.hero == null) {
            item {
                Text(
                    "No videos today yet. Pull down on Today to check for new stops.",
                    style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink.Fog)
    }
}

@Composable
private fun PodcastRow(p: Podcast, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Haze),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Ink.Psych) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.show, style = MaterialTheme.typography.labelMedium, color = Ink.Psych)
            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.durationSec?.let { Text(formatMinutes(it / 60L), style = MaterialTheme.typography.bodySmall, color = Ink.Dim) }
        }
    }
}
