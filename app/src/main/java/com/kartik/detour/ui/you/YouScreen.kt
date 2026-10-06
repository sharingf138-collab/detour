@file:OptIn(ExperimentalMaterial3Api::class)

package com.kartik.detour.ui.you

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartik.detour.container
import com.kartik.detour.data.streak
import com.kartik.detour.guard.GuardSettings
import com.kartik.detour.guard.GuardStatus
import com.kartik.detour.guard.InstaUsage
import com.kartik.detour.ui.components.FilterPill
import com.kartik.detour.ui.components.ScreenTitle
import com.kartik.detour.ui.components.WeekBars
import com.kartik.detour.ui.formatMinutes
import com.kartik.detour.ui.toastOffline
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun YouScreen(onOpenRecap: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val days by remember { app.days.recent() }.collectAsStateWithLifecycle(emptyList())
    val content by app.content.content.collectAsStateWithLifecycle()
    val refreshing by app.content.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Re-read permissions and usage every time the screen comes back (e.g. from system settings).
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    var status by remember { mutableStateOf(GuardStatus.read(context)) }
    var week by remember { mutableStateOf<List<Pair<LocalDate, Long>>?>(null) }
    LaunchedEffect(resumes) {
        status = GuardStatus.read(context)
        week = withContext(Dispatchers.IO) { InstaUsage.lastDays(context, 7) }
    }

    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumes++ }
    var pickBedtime by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(-1) }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 140.dp)) {
        item { ScreenTitle("You") }

        // ---- Stats
        item {
            // Tap a bar to see that day; defaults to today.
            val shown = week?.let { it.getOrNull(selectedDay) ?: it.lastOrNull() }
            val shownDate = shown?.first ?: LocalDate.now()
            val isToday = shownDate == LocalDate.now()
            val log = days.firstOrNull { it.date == shownDate.toString() }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    when {
                        isToday -> "Instagram today"
                        shownDate == LocalDate.now().minusDays(1) -> "Instagram yesterday"
                        else -> "Instagram on ${shownDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))}"
                    },
                    style = MaterialTheme.typography.labelLarge, color = Ink.Fog,
                )
                Text(
                    shown?.second?.let { formatMinutes(it) } ?: "Not tracked",
                    fontFamily = Display, style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.height(4.dp))
                val opens = log?.instaOpens ?: 0
                val detours = log?.detours ?: 0
                Text(
                    buildString {
                        if (log == null && !isToday) {
                            append("Detour wasn't counting Instagram opens yet that day.")
                            return@buildString
                        }
                        append("Opened $opens ${if (opens == 1) "time" else "times"}, took the detour $detours.")
                        if (isToday) append(" Streak ${streak(days)} ${if (streak(days) == 1) "day" else "days"}.")
                        else if (log?.completed == true) append(" Finished the route that day.")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Fog,
                )
                TextButton(onClick = onOpenRecap, contentPadding = PaddingValues(0.dp)) {
                    Text("See this week's recap", color = Ink.Sign)
                }
                Spacer(Modifier.height(12.dp))
                week?.let { w ->
                    WeekBars(w, selected = if (selectedDay in w.indices) selectedDay else w.lastIndex) { selectedDay = it }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // ---- Guard setup
        item {
            Card("Set up the Instagram pause") {
                StepRow(
                    done = status.accessibility,
                    title = "Turn on Detour in Accessibility",
                    body = "This is what lets Detour step in when Instagram opens. It only watches for Instagram, never your screen.",
                    action = "Open settings",
                ) { GuardSettings.accessibility(context) }
                if (!status.accessibility) {
                    Text(
                        "Toggle greyed out? Open App info, tap the three dots at the top right, choose Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall, color = Ink.Paradox,
                        modifier = Modifier.padding(start = 34.dp, bottom = 4.dp),
                    )
                    TextButton(onClick = { GuardSettings.appInfo(context) }, modifier = Modifier.padding(start = 22.dp)) {
                        Text("Open App info", color = Ink.Paradox)
                    }
                }
                StepRow(
                    done = status.usageAccess,
                    title = "Allow usage access",
                    body = "So Detour can show how long you spent on Instagram.",
                    action = "Open settings",
                ) { GuardSettings.usageAccess(context) }
                StepRow(
                    done = status.batteryUnrestricted,
                    title = "Let Detour run in the background",
                    body = "Some phones stop background apps. This keeps the pause working.",
                    action = "Allow",
                ) { GuardSettings.battery(context) }
                StepRow(
                    done = status.notifications,
                    title = "Morning nudge",
                    body = "A notification at 6 AM when today's stops are ready.",
                    action = "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else GuardSettings.notifications(context)
                }
            }
        }

        // ---- Pause settings
        item {
            Card("Pause screen") {
                SwitchRow("Pause before Instagram", settings.guardOn) { on -> app.prefs.update { it.copy(guardOn = on) } }
                Label("Wait before \"Open Instagram\" unlocks")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { s ->
                        FilterPill("$s sec", settings.waitSeconds == s, Ink.Sign) { app.prefs.update { it.copy(waitSeconds = s) } }
                    }
                }
                Label("After bedtime, wait this long instead")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { s ->
                        FilterPill("$s sec", settings.nightWaitSeconds == s, Ink.Psych) { app.prefs.update { it.copy(nightWaitSeconds = s) } }
                    }
                }
                Label("After you open it anyway, leave Instagram alone for")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterPill("$m min", settings.graceMinutes == m, Ink.Sign) { app.prefs.update { it.copy(graceMinutes = m) } }
                    }
                }
                Label("Bedtime: after this the wait gets longer, and the pause offers a podcast instead of a video")
                Text(
                    clock(settings.bedtimeMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink.Sign,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { pickBedtime = true }
                        .padding(vertical = 6.dp),
                )
            }
        }

        // ---- Content settings
        item {
            Card("Content") {
                SwitchRow("Show 18+ slang and terms", settings.showNsfw) { on -> app.prefs.update { it.copy(showNsfw = on) } }
                SwitchRow("Morning nudge at 6 AM", settings.morningNudge) { on -> app.prefs.update { it.copy(morningNudge = on) } }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        content?.date?.let { "Stops from ${LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE d MMM"))}" } ?: "No stops loaded",
                        style = MaterialTheme.typography.bodyMedium, color = Ink.Fog, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scope.launch { app.content.ensureToday().onFailure { toastOffline(context) } } }, enabled = !refreshing) {
                        Text(if (refreshing) "Checking…" else "Check for update", color = Ink.Sign)
                    }
                }
            }
        }
    }

    if (pickBedtime) {
        val state = rememberTimePickerState(settings.bedtimeMinutes / 60, settings.bedtimeMinutes % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickBedtime = false },
            containerColor = Ink.Dusk,
            confirmButton = {
                TextButton(onClick = {
                    app.prefs.update { it.copy(bedtimeMinutes = state.hour * 60 + state.minute) }
                    pickBedtime = false
                }) { Text("Set bedtime", color = Ink.Sign) }
            },
            dismissButton = { TextButton(onClick = { pickBedtime = false }) { Text("Cancel", color = Ink.Fog) } },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Ink.Haze,
                        selectorColor = Ink.Sign,
                        timeSelectorSelectedContainerColor = Ink.Sign,
                        timeSelectorSelectedContentColor = Ink.SignInk,
                        periodSelectorSelectedContainerColor = Ink.Sign,
                        periodSelectorSelectedContentColor = Ink.SignInk,
                    ),
                )
            },
        )
    }
}

private fun clock(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.Dusk)
            .padding(18.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.Fog, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Ink.Sign, checkedThumbColor = Ink.SignInk,
                uncheckedTrackColor = Ink.Haze, uncheckedThumbColor = Ink.Fog, uncheckedBorderColor = Ink.Line,
            ),
        )
    }
}

@Composable
private fun StepRow(done: Boolean, title: String, body: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) Ink.Meme else Ink.Line,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (done) Ink.Fog else Ink.Paper)
            if (!done) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = Ink.Fog)
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action, color = Ink.Sign) }
            }
        }
    }
}
