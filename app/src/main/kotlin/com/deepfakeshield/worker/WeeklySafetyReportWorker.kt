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
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.enterprise.QuietHoursHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Weekly safety report notification worker.
 * Sends a push notification with a summary of the user's protection status.
 */
@HiltWorker
class WeeklySafetyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alertRepository: AlertRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_safety_report"
        const val NOTIFICATION_ID = 2001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<WeeklySafetyReportWorker>(
                7, TimeUnit.DAYS,
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(7, TimeUnit.DAYS)
                .addTag("safety_report")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val alerts = alertRepository.getAllAlerts().first()
            
            val now = System.currentTimeMillis()
            val weekAgo = now - (7 * 24 * 60 * 60 * 1000L)
            val twoWeeksAgo = now - (14 * 24 * 60 * 60 * 1000L)
            val weeklyAlerts = alerts.filter { it.timestamp >= weekAgo }
            val previousWeekAlerts = alerts.filter { it.timestamp in twoWeeksAgo until weekAgo }
            
            val totalThreats = weeklyAlerts.size
            val prevWeekTotal = previousWeekAlerts.size
            val trend = when {
                totalThreats < prevWeekTotal -> "down from last week"
                totalThreats > prevWeekTotal -> "up from last week"
                else -> "same as last week"
            }
            val criticalThreats = weeklyAlerts.count { 
                it.severity == com.deepfakeshield.core.model.RiskSeverity.CRITICAL ||
                it.severity == com.deepfakeshield.core.model.RiskSeverity.HIGH
            }
            val handledCount = weeklyAlerts.count { it.isHandled }
            val totalAllTime = alerts.size
            
            val milestone = when {
                totalAllTime >= 50 -> " You've blocked 50+ threats — Guardian status!"
                totalAllTime >= 10 -> " 10+ threats blocked — you're a scam spotter!"
                totalAllTime >= 1 -> " First threat blocked — shield activated!"
                else -> ""
            }
            
            val title = "Weekly Safety Report"
            val body = when {
                totalThreats == 0 -> "Great news! No threats detected this week. Your shields are working perfectly.$milestone"
                criticalThreats > 0 -> "$totalThreats threats blocked this week ($criticalThreats critical, $trend). $handledCount resolved.$milestone"
                else -> "$totalThreats threats blocked this week ($trend). $handledCount resolved. You're well protected!$milestone"
            }
            
            if (!QuietHoursHelper.shouldSuppressNotification(applicationContext)) {
                showNotification(title, body)
            }
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeeklyReport", "Worker failed", e)
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_analytics", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_INFO)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
