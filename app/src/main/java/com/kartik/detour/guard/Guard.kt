package com.kartik.detour.guard

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationManagerCompat
import com.kartik.detour.container
import java.time.LocalDate
import java.time.ZoneId

const val INSTAGRAM = "com.instagram.android"

/**
 * Watches for Instagram coming to the front and puts the pause screen over it.
 * Config (res/xml/insta_guard.xml) limits events to Instagram's package and
 * window changes only; window content is never read.
 */
class InstaGuardService : AccessibilityService() {
    private var lastShown = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != INSTAGRAM) return

        val prefs = container.prefs
        val now = System.currentTimeMillis()
        if (!prefs.settings.value.guardOn || prefs.inGrace(now)) return
        if (now - lastShown < 4_000) return // Instagram fires several window events per launch
        lastShown = now

        startActivity(
            Intent(this, PauseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    override fun onInterrupt() = Unit
}

/** Which of the permissions Detour needs are granted right now. */
data class GuardStatus(
    val accessibility: Boolean,
    val usageAccess: Boolean,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean,
) {
    companion object {
        fun read(context: Context) = GuardStatus(
            accessibility = accessibilityOn(context),
            usageAccess = usageAccessOn(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        private fun accessibilityOn(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, InstaGuardService::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }

        @Suppress("DEPRECATION")
        private fun usageAccessOn(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

object GuardSettings {
    fun accessibility(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun usageAccess(context: Context) = open(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    /** Needed on Android 13+ before a sideloaded app's accessibility toggle can be switched on. */
    fun appInfo(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    @Suppress("BatteryLife")
    fun battery(context: Context) = open(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )

    fun notifications(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    private fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Instagram foreground time from the system's usage events. Null when usage access isn't granted. */
object InstaUsage {
    fun minutesOn(context: Context, day: LocalDate): Long? {
        if (!GuardStatus.read(context).usageAccess) return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return 0
        return foregroundMillis(context, start, end) / 60_000
    }

    /** Last [n] days, oldest first, as (date, minutes) pairs. */
    fun lastDays(context: Context, n: Int): List<Pair<LocalDate, Long>>? {
        if (!GuardStatus.read(context).usageAccess) return null
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to (minutesOn(context, d) ?: 0)
        }
    }

    /** Save the last week of Instagram minutes into Detour's own records, so recaps outlive Android's history. */
    suspend fun recordRecent(context: Context) {
        val days = context.container.days
        val today = LocalDate.now()
        for (back in 0L..6L) {
            val d = today.minusDays(back)
            val m = minutesOn(context, d) ?: return
            // Android drops old usage events; a 0 for an older day usually means "forgotten", not "unused".
            if (m == 0L && back > 2) continue
            days.updateDate(d.toString()) { it.copy(instaMinutes = m.toInt()) }
        }
    }

    @Suppress("DEPRECATION")
    private fun foregroundMillis(context: Context, start: Long, end: Long): Long {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        var total = 0L
        var since: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != INSTAGRAM) continue
            val open = since
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (open == null) since = e.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (open != null) {
                        total += e.timeStamp - open
                        since = null
                    }
                }
            }
        }
        val open = since
        if (open != null) total += end - open
        return total
    }
}
