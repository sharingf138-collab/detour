package com.kartik.detour.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kartik.detour.MainActivity
import com.kartik.detour.R
import com.kartik.detour.container
import com.kartik.detour.data.today
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Pulls the new day's content shortly after the 6:00 AM pipeline run and posts the morning nudge. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        com.kartik.detour.guard.InstaUsage.recordRecent(applicationContext)
        val todayIso = today().toString()
        val got = c.content.refresh().getOrElse { return if (runAttemptCount < 12) Result.retry() else Result.failure() }
        // GitHub's 6:00 cron often starts late; keep checking (every 15 min, ~3 h) until today's stops land.
        if (got.date != todayIso && runAttemptCount < 12) return Result.retry()

        val morning = LocalTime.now().isBefore(LocalTime.of(11, 0))
        if (got.date == todayIso && morning && c.prefs.settings.value.morningNudge && c.prefs.lastNudgeDay != todayIso) {
            c.prefs.lastNudgeDay = todayIso
            Refresh.notifyReady(applicationContext, got.words.firstOrNull()?.word)
        }
        return Result.success()
    }
}

object Refresh {
    private const val CHANNEL = "morning"
    private const val DAILY = "daily-refresh"
    private const val NOW = "refresh-now"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Morning nudge", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Tells you when today's Detour is ready"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** Every 24 h, first run at the next 6:05 AM. */
    fun scheduleDaily(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(6, 5)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()

        val req = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One-off refresh, e.g. when the app opens on a new day. */
    fun now(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun notifyReady(context: Context, firstWord: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_detour)
            .setContentTitle(
                if (LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY) "Your week in Detour is ready" else "Today's Detour is ready"
            )
            .setContentText(
                if (firstWord != null) "First word: $firstWord. Open this before Instagram."
                else "New words, terms and the news. Open this before Instagram."
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}
