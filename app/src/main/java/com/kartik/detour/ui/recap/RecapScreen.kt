package com.kartik.detour.ui.recap

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kartik.detour.container
import com.kartik.detour.guard.InstaUsage
import com.kartik.detour.ui.components.WeekBars
import com.kartik.detour.ui.formatMinutes
import com.kartik.detour.ui.theme.Display
import com.kartik.detour.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Monday-to-today of this week, compared with the same days of last week. */
data class WeekStats(
    val from: LocalDate,
    val to: LocalDate,
    val perDay: List<Pair<LocalDate, Long>>,
    val total: Long?,              // null when usage access is off and nothing was saved
    val lastWeekTotal: Long?,      // same weekdays last week; null if no data
    val opens: Int,
    val detours: Int,
    val finished: Int,
    val words: Int,
    val terms: Int,
    val reviewed: Int,
    val mastered: Int,
    val notes: Int,
)

suspend fun computeWeek(context: Context, today: LocalDate = LocalDate.now()): WeekStats = withContext(Dispatchers.IO) {
    val app = context.container
    InstaUsage.recordRecent(context)
    val from = today.with(DayOfWeek.MONDAY)
    val prevFrom = from.minusWeeks(1)
    val prevTo = today.minusWeeks(1)
    val logs = app.days.between(prevFrom, today).associateBy { it.date }

    fun minutes(d: LocalDate): Long? = logs[d.toString()]?.instaMinutes?.takeIf { it >= 0 }?.toLong()

    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    val prevDays = generateSequence(prevFrom) { it.plusDays(1) }.takeWhile { !it.isAfter(prevTo) }.toList()
    val perDay = days.map { it to (minutes(it) ?: 0L) }
    val known = days.mapNotNull { minutes(it) }
    val prevKnown = prevDays.mapNotNull { minutes(it) }
    val thisWeek = days.mapNotNull { logs[it.toString()] }

    val zone = ZoneId.systemDefault()
    val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    WeekStats(
        from = from,
        to = today,
        perDay = perDay,
        total = if (known.isEmpty()) null else known.sum(),
        // Only compare when last week is fully known, otherwise "less than last week" is a lie.
        lastWeekTotal = if (prevKnown.size == prevDays.size) prevKnown.sum() else null,
        opens = thisWeek.sumOf { it.instaOpens },
        detours = thisWeek.sumOf { it.detours },
        finished = thisWeek.count { it.completed },
        words = app.db.cards().seenBetween("word", from.toString(), today.toString()),
        terms = app.db.cards().seenBetween("loop", from.toString(), today.toString()),
        reviewed = app.db.cards().reviewedBetween(from.toString(), today.toString()),
        mastered = app.db.cards().mastered(),
        notes = app.notes.countBetween(fromMs, toMs),
    )
}

@Composable
fun RecapScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<WeekStats?>(null) }
    LaunchedEffect(Unit) { stats = computeWeek(context) }
    var selected by remember { mutableIntStateOf(-1) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close recap") }
        }
        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ink.Sign)
            }
            return@Column
        }
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        val sunday = s.to.dayOfWeek == DayOfWeek.SUNDAY
        Text(if (sunday) "Your week" else "Your week so far", style = MaterialTheme.typography.displayLarge)
        Text("${s.from.format(fmt)} to ${s.to.format(fmt)}", style = MaterialTheme.typography.bodyLarge, color = Ink.Fog)

        Spacer(Modifier.height(28.dp))
        Text("On Instagram", style = MaterialTheme.typography.labelLarge, color = Ink.Fog)
        Text(
            s.total?.let { formatMinutes(it) } ?: "Not tracked",
            fontFamily = Display, style = MaterialTheme.typography.displayLarge,
        )
        val (line, color) = comparison(s)
        Text(line, style = MaterialTheme.typography.bodyLarge, color = color)
        Spacer(Modifier.height(20.dp))
        WeekBars(s.perDay, selected = if (selected in s.perDay.indices) selected else s.perDay.lastIndex) { selected = it }

        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(
                if (s.opens > 0) "${s.detours} of ${s.opens}" else "${s.detours}",
                "times you took the detour when Instagram opened", Ink.Sign,
            )
            Stat("${s.finished} of ${s.perDay.size}", "days you finished the route", Ink.Meme)
            Stat("${s.words + s.terms}", "new words and terms (${s.words} words, ${s.terms} terms)", Ink.Slang)
            Stat("${s.reviewed}", "cards reviewed in the quiz", Ink.Psych)
            Stat("${s.mastered}", "cards you know well by now, all time", Ink.Acronym)
            Stat("${s.notes}", if (s.notes == 1) "note saved from a video" else "notes saved from videos", Ink.Paradox)
        }
        Spacer(Modifier.height(28.dp))
        Text(verdict(s), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value, fontFamily = Display, style = MaterialTheme.typography.displaySmall, color = color,
            modifier = Modifier.width(112.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private fun comparison(s: WeekStats): Pair<String, Color> {
    val now = s.total ?: return "Turn on usage access in You to see this." to Ink.Fog
    val before = s.lastWeekTotal ?: return "First week with Detour. Next Sunday you'll see the difference." to Ink.Fog
    val diff = before - now
    return when {
        abs(diff) < 15 -> "About the same as the same days last week." to Ink.Fog
        diff > 0 -> "${formatMinutes(diff)} less than the same days last week." to Ink.Meme
        else -> "${formatMinutes(-diff)} more than the same days last week." to Ink.Danger
    }
}

private fun verdict(s: WeekStats): String {
    val saved = (s.lastWeekTotal ?: return "Good start. Keep taking the detour.") - (s.total ?: 0)
    return when {
        saved >= 300 -> "You took back ${formatMinutes(saved)}. That's a whole evening."
        saved >= 120 -> "You took back ${formatMinutes(saved)}. That's a full movie."
        saved > 15 -> "Small win: ${formatMinutes(saved)} back. Keep going."
        saved >= -15 -> "Holding steady. Try finishing the route before opening Instagram."
        else -> "Instagram won this week. Try a stricter wait at night in You."
    }
}
