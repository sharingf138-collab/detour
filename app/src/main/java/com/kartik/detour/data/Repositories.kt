package com.kartik.detour.data

import android.content.Context
import com.kartik.detour.BuildConfig
import com.kartik.detour.data.db.CardDao
import com.kartik.detour.data.db.CardEntity
import com.kartik.detour.data.db.DayDao
import com.kartik.detour.data.db.DayEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

fun today(): LocalDate = LocalDate.now()

/** Loads the day's content: cached copy first, then the network. */
class ContentRepository(
    private val context: Context,
    private val cards: CardDao,
    scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val cacheFile = File(context.filesDir, "latest.json")
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    private val _content = MutableStateFlow<DayContent?>(null)
    val content: StateFlow<DayContent?> = _content.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val local = loadLocal()
            if (local != null && _content.value == null) {
                _content.value = local
                remember(local)
            }
        }
    }

    private fun parse(text: String): DayContent = json.decodeFromString(DayContent.serializer(), text)

    private fun loadLocal(): DayContent? =
        runCatching { if (cacheFile.exists()) parse(cacheFile.readText()) else null }.getOrNull()
            ?: runCatching {
                context.assets.open("sample_latest.json").bufferedReader().use { parse(it.readText()) }
            }.getOrNull()

    /** Fetch latest.json. Keeps the cached day if the network fails or returns an older day. */
    suspend fun refresh(): Result<DayContent> = withContext(Dispatchers.IO) {
        _refreshing.value = true
        try {
            // A minute-granular query string gets past the raw.githubusercontent CDN cache.
            val url = "${BuildConfig.CONTENT_URL}?t=${System.currentTimeMillis() / 60_000}"
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("Server answered ${resp.code}")
                val body = resp.body?.string() ?: error("Empty response")
                val fresh = parse(body)
                val current = _content.value
                if (current == null || fresh.date >= current.date) {
                    cacheFile.writeText(body)
                    _content.value = fresh
                    remember(fresh)
                }
                Result.success(fresh)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _refreshing.value = false
        }
    }

    /** Every word and loop card shown becomes part of the archive and the quiz. */
    private suspend fun remember(c: DayContent) {
        val due = LocalDate.parse(c.date).plusDays(1).toString()
        val words = c.words.map {
            CardEntity(
                id = wordId(it), kind = "word", type = "word", term = it.word, meaning = it.meaning,
                example = it.example, extra = it.insteadOf, tone = it.pos, nsfw = false,
                firstSeen = c.date, due = due,
            )
        }
        val loop = c.loop.map {
            CardEntity(
                id = loopId(it), kind = "loop", type = it.type, term = it.term, meaning = it.meaning,
                example = it.example, extra = it.context, tone = it.tone, nsfw = it.nsfw,
                firstSeen = c.date, due = due,
            )
        }
        cards.insertNew(words + loop)
    }

    companion object {
        fun wordId(w: Word) = "word:" + w.word.lowercase().trim()
        fun loopId(c: LoopCard) = "loop:" + c.id.ifBlank { c.term.lowercase().trim() }
    }
}

/** Leitner-box spaced repetition over words + loop cards. */
class StudyRepository(private val cards: CardDao) {
    private val intervals = longArrayOf(1, 2, 4, 8, 16)

    fun archive(): Flow<List<CardEntity>> = cards.loopArchive()
    fun byIds(ids: List<String>): Flow<List<CardEntity>> = cards.byIds(ids)
    fun due(): Flow<List<CardEntity>> = cards.due(today().toString())
    fun dueCount(): Flow<Int> = cards.dueCount(today().toString())
    fun total(): Flow<Int> = cards.total()

    suspend fun grade(card: CardEntity, knewIt: Boolean) {
        val box = if (knewIt) (card.box + 1).coerceAtMost(5) else 1
        val t = today()
        cards.save(
            card.copy(
                box = box,
                due = t.plusDays(intervals[box - 1]).toString(),
                lastReviewed = t.toString(),
            )
        )
    }
}

/** Per-day counters: Instagram opens, detours taken, and whether Today was finished. */
class DayRepository(private val days: DayDao) {
    private val lock = Mutex()

    fun todayFlow(): Flow<DayEntity?> = days.watch(today().toString())
    fun recent(): Flow<List<DayEntity>> = days.recent()

    suspend fun update(change: (DayEntity) -> DayEntity) = lock.withLock {
        val d = today().toString()
        days.save(change(days.get(d) ?: DayEntity(d)))
    }

    suspend fun countInstaOpen() = update { it.copy(instaOpens = it.instaOpens + 1) }
    suspend fun countDetour() = update { it.copy(detours = it.detours + 1) }
    suspend fun markCompleted() = update { it.copy(completed = true) }
}

/** Consecutive finished days, counting back from today (or yesterday, if today isn't done yet). */
fun streak(days: List<DayEntity>, today: LocalDate = today()): Int {
    val done = days.filter { it.completed }.map { it.date }.toSet()
    var d = if (today.toString() in done) today else today.minusDays(1)
    var n = 0
    while (d.toString() in done) {
        n++
        d = d.minusDays(1)
    }
    return n
}
