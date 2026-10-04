@file:OptIn(ExperimentalMaterial3Api::class)

package com.kartik.detour.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kartik.detour.container
import com.kartik.detour.data.db.NoteEntity
import com.kartik.detour.ui.components.ScreenTitle
import com.kartik.detour.ui.openUrl
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    val app = context.container
    val notes by remember { app.notes.all() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }

    val shown = notes.filter {
        query.isBlank() || it.body.contains(query, true) || (it.videoTitle?.contains(query, true) == true)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item { ScreenTitle("Notes", "What you learned, in your own words.") }
            if (notes.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search notes") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ink.Sign, unfocusedBorderColor = Ink.Line, cursorColor = Ink.Sign,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                item {
                    Text(
                        "Watch something from the Watch tab, and Detour asks what you took away when you come back. Or start a note now.",
                        style = MaterialTheme.typography.bodyLarge, color = Ink.Fog,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { n -> NoteRow(n) { editing = n } }
        }
        FloatingActionButton(
            onClick = {
                val now = System.currentTimeMillis()
                editing = NoteEntity(body = "", createdAt = now, updatedAt = now)
            },
            containerColor = Ink.Sign,
            contentColor = Ink.SignInk,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp),
        ) { Icon(Icons.Rounded.Add, contentDescription = "New note") }
    }

    editing?.let { n ->
        NoteSheet(
            note = n,
            onDismiss = { editing = null },
            onOpenVideo = { n.videoUrl?.let { openUrl(context, it) } },
        )
    }
}

@Composable
private fun NoteRow(n: NoteEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (n.thumbnail != null) {
            AsyncImage(
                model = n.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(88.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Ink.Haze),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            n.videoTitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Ink.Psych, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                n.body.ifBlank { "Empty note" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(formatDate(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = Ink.Dim)
        }
    }
}

private fun formatDate(ms: Long): String = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ms))

/** Bottom sheet for writing or editing a note. Used here and for the "save a note?" prompt after a video. */
@Composable
fun NoteSheet(note: NoteEntity, onDismiss: () -> Unit, onOpenVideo: (() -> Unit)? = null, prompt: String? = null) {
    val app = LocalContext.current.container
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var body by rememberSaveable(note.id) { mutableStateOf(note.body) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Ink.Dusk) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (prompt != null) {
                Text(prompt, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
            }
            note.videoTitle?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = Ink.Psych,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpenVideo != null) Modifier.clickable(onClick = onOpenVideo) else Modifier,
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("The one idea worth keeping was…") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Sign, unfocusedBorderColor = Ink.Line, cursorColor = Ink.Sign,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.id != 0L) {
                    TextButton(onClick = {
                        app.scope.launch { app.notes.delete(note.id) }
                        onDismiss()
                    }) { Text("Delete", color = Ink.Danger) }
                } else {
                    TextButton(onClick = onDismiss) { Text("Skip", color = Ink.Fog) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (body.isNotBlank()) {
                            app.scope.launch { app.notes.save(note.copy(body = body.trim(), updatedAt = System.currentTimeMillis())) }
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink.Sign, contentColor = Ink.SignInk),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                ) { Text("Save note") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
