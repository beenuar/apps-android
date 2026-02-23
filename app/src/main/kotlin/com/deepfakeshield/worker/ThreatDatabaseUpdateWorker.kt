package com.deepfakeshield.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.deepfakeshield.av.engine.ThreatDatabaseUpdater
import com.deepfakeshield.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Autonomous threat database updates from open-source feeds.
 * - Periodic: runs every 24 hours (always, so DB is ready when user enables protection).
 * - Immediate: runs once on first install when no downloaded hashes exist yet.
 */
@HiltWorker
class ThreatDatabaseUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val threatDbUpdater: ThreatDatabaseUpdater,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Cap retries to avoid infinite battery drain (WorkManager backoff caps at ~5 hours)
        if (runAttemptCount > MAX_RETRIES) {
            android.util.Log.w("ThreatDbWorker", "Max retries ($MAX_RETRIES) exceeded, giving up until next scheduled run")
            return Result.failure()
        }

        return try {
            when (val result = threatDbUpdater.updateFromDefaultFeeds()) {
                is ThreatDatabaseUpdater.UpdateResult.Success -> {
                    android.util.Log.i("ThreatDbWorker", "Updated threat DB: ${result.hashesAdded} hashes added")
                    Result.success()
                }
                is ThreatDatabaseUpdater.UpdateResult.Failure -> {
                    android.util.Log.w("ThreatDbWorker", "Update failed: ${result.error}")
                    if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ThreatDbWorker", "Update exception (attempt $runAttemptCount): ${e.message}")
            if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "threat_db_update"
        private const val WORK_NAME_IMMEDIATE = "threat_db_immediate"
        private const val MAX_RETRIES = 5

        /** Schedule periodic 24h updates */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ThreatDatabaseUpdateWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).setConstraints(constraints).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Schedule an immediate one-time update. Call on app start when the user has no
         * downloaded hashes yet (first install). Ensures protection within minutes.
         */
        fun scheduleImmediateIfNeeded(context: Context) {
            val downloadedFile = File(context.filesDir, "malware_hashes_downloaded.txt")
            if (downloadedFile.exists()) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ThreatDatabaseUpdateWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
