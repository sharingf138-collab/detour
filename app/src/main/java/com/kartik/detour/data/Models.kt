package com.kartik.detour.data

import kotlinx.serialization.Serializable

/** One day of content, exactly as written by pipeline/build_day.py. */
@Serializable
data class DayContent(
    val schema: Int = 1,
    val date: String,
    val generatedAt: String = "",
    val words: List<Word> = emptyList(),
    val loop: List<LoopCard> = emptyList(),
    val news: News = News(),
    val videos: Videos = Videos(),
    val podcasts: List<Podcast> = emptyList(),
    val onThisDay: List<HistoryItem> = emptyList(),
)

/** A moment from Wikipedia's "On this day", rewritten in one line. */
@Serializable
data class HistoryItem(
    val year: Int,
    val text: String,
    val kind: String = "event",   // "event" | "born" | "died"
    val url: String = "",
    val india: Boolean = false,
)

@Serializable
data class Word(
    val word: String,
    val pos: String = "",
    val pronunciation: String = "",
    val meaning: String,
    val insteadOf: String = "",
    val example: String = "",
)

@Serializable
data class LoopCard(
    val id: String = "",
    val type: String,
    val term: String,
    val meaning: String,
    val example: String = "",
    val context: String = "",
    val tone: String = "casual",
    val nsfw: Boolean = false,
)

@Serializable
data class News(
    val india: List<NewsItem> = emptyList(),
    val world: List<NewsItem> = emptyList(),
)

@Serializable
data class NewsItem(
    val text: String,
    val source: String = "",
    val url: String = "",
)

@Serializable
data class Videos(
    val hero: Video? = null,
    val more: List<Video> = emptyList(),
)

@Serializable
data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val category: String,
    val url: String,
    val thumbnail: String,
    val published: String = "",
)

@Serializable
data class Podcast(
    val id: String,
    val title: String,
    val show: String,
    val category: String = "",
    val audioUrl: String,
    val link: String = "",
    val durationSec: Int? = null,
)

/** The five kinds of "Stay in the loop" cards. */
enum class LoopType(val key: String, val label: String) {
    Slang("slang", "Slang"),
    Psych("psych", "Dating & psych"),
    Acronym("acronym", "Acronyms"),
    Paradox("paradox", "Paradoxes"),
    Meme("meme", "Memes");

    companion object {
        fun of(key: String): LoopType = entries.firstOrNull { it.key == key } ?: Slang
    }
}

/** Video categories from sources.json, with the names shown in the app. */
fun categoryLabel(key: String): String = when (key) {
    "india" -> "Indian history"
    "history" -> "History"
    "money" -> "Money"
    "mind" -> "Mind & behaviour"
    "ai" -> "AI & tech"
    "philosophy" -> "Philosophy"
    "science" -> "Science"
    "business" -> "Business"
    else -> key.replaceFirstChar { it.uppercase() }
}
