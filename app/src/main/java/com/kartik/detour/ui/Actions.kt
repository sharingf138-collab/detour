package com.kartik.detour.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.kartik.detour.container
import com.kartik.detour.data.PendingNote
import com.kartik.detour.data.Video

/** Open a video in the YouTube app (or browser) and remember it so we can offer a note on return. */
fun openVideo(context: Context, video: Video) {
    context.container.prefs.pendingNote = PendingNote(
        videoId = video.id,
        title = video.title,
        channel = video.channel,
        thumbnail = video.thumbnail,
        url = video.url,
        openedAt = System.currentTimeMillis(),
    )
    openUrl(context, video.url)
}

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

fun greeting(name: String, hour: Int): String = when (hour) {
    in 4..11 -> "Good morning, $name."
    in 12..16 -> "Good afternoon, $name."
    in 17..21 -> "Good evening, $name."
    else -> "Still up, $name?"
}

fun formatMinutes(min: Long): String = when {
    min < 60 -> "$min min"
    min % 60 == 0L -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

fun toastOffline(context: Context) {
    Toast.makeText(context, "Couldn't reach the server. Showing the last saved stops.", Toast.LENGTH_SHORT).show()
}
