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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class MonthlySafetyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alertRepository: AlertRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "monthly_safety_report"
        const val NOTIFICATION_ID = 2004

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlySafetyReportWorker>(
                30, TimeUnit.DAYS, 2, TimeUnit.DAYS
            ).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(30, TimeUnit.DAYS).addTag("monthly_report").build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val alerts = alertRepository.getAllAlerts().first()
            val now = System.currentTimeMillis()
            val monthAgo = now - (30L * 24 * 60 * 60 * 1000)
            val monthAlerts = alerts.filter { it.timestamp >= monthAgo }
            val total = monthAlerts.size
            val handled = monthAlerts.count { it.isHandled }

            val body = when {
                total == 0 -> "Perfect month! Zero threats detected. Your shields kept you safe all month."
                total < 5 -> "$total threats detected and $handled resolved this month. You're well protected."
                else -> "$total threats this month ($handled resolved). Stay vigilant — review your security settings."
            }

            val intent = PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); putExtra("open_route", "weekly_report") },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_INFO)
                .setContentTitle("\uD83D\uDCCA Monthly Safety Report")
                .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(intent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()

            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
