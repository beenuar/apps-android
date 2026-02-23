package com.deepfakeshield.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.enterprise.QuietHoursHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_digest"
        const val NOTIFICATION_ID = 2002

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                1, TimeUnit.DAYS, 2, TimeUnit.HOURS
            ).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(8, TimeUnit.HOURS)
                .addTag("daily_digest")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (QuietHoursHelper.shouldSuppressNotification(applicationContext)) return Result.success()

            val alerts = alertRepository.getAllAlerts().first()
            val now = System.currentTimeMillis()
            val dayAgo = now - (24 * 60 * 60 * 1000L)
            val todayAlerts = alerts.filter { it.timestamp >= dayAgo }
            val streak = userPreferences.dailyChallengeStreak.first()
            val threatsBlocked = todayAlerts.size
            val unhandled = alerts.count { !it.isHandled }

            val body = when {
                threatsBlocked > 0 && unhandled > 0 -> "$threatsBlocked threats blocked in 24h. $unhandled need your attention."
                threatsBlocked > 0 -> "$threatsBlocked threats blocked in 24h. All resolved."
                streak > 0 -> "All clear! No threats detected. Your ${streak}-day streak is going strong."
                else -> "All clear! No threats detected. Complete today's challenge to start a streak."
            }

            val intent = PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_INFO)
                .setContentTitle("\uD83D\uDEE1\uFE0F Daily Safety Digest")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(intent).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
