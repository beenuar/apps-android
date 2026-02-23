package com.deepfakeshield.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.enterprise.ManagedConfigHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily cleanup worker that removes old alerts based on retention settings.
 */
@HiltWorker
class DailyCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(1, TimeUnit.DAYS)
                .addTag("cleanup")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = ManagedConfigHelper.getDataRetentionOverride(applicationContext)
                ?: userPreferences.dataRetentionDays.first()
            alertRepository.cleanupOldAlerts(retentionDays)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyCleanup", "Worker failed", e)
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
