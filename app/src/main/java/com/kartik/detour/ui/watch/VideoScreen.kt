package com.kartik.detour.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kartik.detour.container
import com.kartik.detour.data.categoryLabel
import com.kartik.detour.ui.components.Tag
import com.kartik.detour.ui.openVideo
import com.kartik.detour.ui.theme.Ink

/** A video's page: read what it teaches first, then choose to open it on YouTube. */
@Composable
fun VideoScreen(videoId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val content by context.container.content.content.collectAsStateWithLifecycle()
    val video = content?.videos?.let { v -> listOfNotNull(v.hero) + v.more }?.firstOrNull { it.id == videoId }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close") }
        }
        if (video == null) {
            Text(
                "This video isn't in today's picks any more.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Box {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ink.Haze),
                )
                Tag(categoryLabel(video.category), Ink.category(video.category), Modifier.padding(14.dp), filled = true)
            }
            Spacer(Modifier.height(24.dp))
            if (video.learn.isNotBlank()) {
                Text("You'll learn", style = MaterialTheme.typography.labelLarge, color = Ink.Sign)
                Spacer(Modifier.height(4.dp))
                Text(video.learn, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
            }
            Text(video.title, style = MaterialTheme.typography.titleMedium, color = Ink.Fog)
            Spacer(Modifier.height(6.dp))
            Text(
                video.channel + (video.minutes?.let { ", $it min" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = Ink.Dim,
            )
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(
                onClick = { openVideo(context, video) },
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                contentPadding = PaddingValues(vertical = 18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Watch on YouTube", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "When you come back, Detour asks what stuck with you.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Dim,
                modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}
