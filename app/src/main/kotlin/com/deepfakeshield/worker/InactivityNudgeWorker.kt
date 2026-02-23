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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class InactivityNudgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "inactivity_nudge"
        const val NOTIFICATION_ID = 2003

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InactivityNudgeWorker>(
                3, TimeUnit.DAYS, 6, TimeUnit.HOURS
            ).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(3, TimeUnit.DAYS)
                .addTag("inactivity_nudge")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val lastScan = userPreferences.lastScanTimestamp.first()
            val now = System.currentTimeMillis()
            val daysSinceLastScan = ((now - lastScan) / (24 * 60 * 60 * 1000L)).toInt()

            if (daysSinceLastScan < 3 || lastScan == 0L) return Result.success()

            val body = when {
                daysSinceLastScan >= 14 -> "It's been ${daysSinceLastScan} days since your last scan. New threats emerge daily — run a quick check."
                daysSinceLastScan >= 7 -> "Your last security check was ${daysSinceLastScan} days ago. A 30-second scan keeps you safe."
                else -> "Haven't scanned in ${daysSinceLastScan} days. Tap to run a quick security check."
            }

            val intent = PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); putExtra("open_scan", true) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_INFO)
                .setContentTitle("\uD83D\uDD12 Security Check Overdue")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(intent).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW).build()

            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
