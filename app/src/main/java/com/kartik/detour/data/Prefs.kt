package com.kartik.detour.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

data class Settings(
    val name: String = "Kartik",
    val guardOn: Boolean = true,
    val graceMinutes: Int = 10,
    val waitSeconds: Int = 10,
    val bedtimeMinutes: Int = 23 * 60,     // minutes after midnight
    val wakeMinutes: Int = 6 * 60,
    val morningNudge: Boolean = true,
    val showNsfw: Boolean = true,
)

/** A video the user left the app to watch; on return we offer to save a note about it. */
data class PendingNote(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val url: String,
    val openedAt: Long,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("detour", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        name = sp.getString("name", "Kartik") ?: "Kartik",
        guardOn = sp.getBoolean("guardOn", true),
        graceMinutes = sp.getInt("graceMinutes", 10),
        waitSeconds = sp.getInt("waitSeconds", 10),
        bedtimeMinutes = sp.getInt("bedtimeMinutes", 23 * 60),
        wakeMinutes = sp.getInt("wakeMinutes", 6 * 60),
        morningNudge = sp.getBoolean("morningNudge", true),
        showNsfw = sp.getBoolean("showNsfw", true),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        sp.edit {
            putString("name", s.name)
            putBoolean("guardOn", s.guardOn)
            putInt("graceMinutes", s.graceMinutes)
            putInt("waitSeconds", s.waitSeconds)
            putInt("bedtimeMinutes", s.bedtimeMinutes)
            putInt("wakeMinutes", s.wakeMinutes)
            putBoolean("morningNudge", s.morningNudge)
            putBoolean("showNsfw", s.showNsfw)
        }
        _settings.value = s
    }

    /** After "Open Instagram anyway", leave Instagram alone for the grace period. */
    var allowedUntil: Long
        get() = sp.getLong("allowedUntil", 0)
        set(v) = sp.edit { putLong("allowedUntil", v) }

    fun inGrace(now: Long = System.currentTimeMillis()) = now < allowedUntil

    /** True between bedtime and wake-up, when the pause screen offers audio instead of video. */
    fun isNight(now: LocalTime = LocalTime.now()): Boolean {
        val s = _settings.value
        val m = now.hour * 60 + now.minute
        return if (s.bedtimeMinutes > s.wakeMinutes) m >= s.bedtimeMinutes || m < s.wakeMinutes
        else m in s.bedtimeMinutes until s.wakeMinutes
    }

    var pendingNote: PendingNote?
        get() {
            val id = sp.getString("pn_id", null) ?: return null
            return PendingNote(
                videoId = id,
                title = sp.getString("pn_title", "") ?: "",
                channel = sp.getString("pn_channel", "") ?: "",
                thumbnail = sp.getString("pn_thumb", "") ?: "",
                url = sp.getString("pn_url", "") ?: "",
                openedAt = sp.getLong("pn_at", 0),
            )
        }
        set(v) = sp.edit {
            if (v == null) {
                listOf("pn_id", "pn_title", "pn_channel", "pn_thumb", "pn_url", "pn_at").forEach { remove(it) }
            } else {
                putString("pn_id", v.videoId)
                putString("pn_title", v.title)
                putString("pn_channel", v.channel)
                putString("pn_thumb", v.thumbnail)
                putString("pn_url", v.url)
                putLong("pn_at", v.openedAt)
            }
        }

    /** Day (ISO) for which the morning notification was last posted. */
    var lastNudgeDay: String
        get() = sp.getString("lastNudgeDay", "") ?: ""
        set(v) = sp.edit { putString("lastNudgeDay", v) }
}
