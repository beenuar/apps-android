package com.deepfakeshield.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.deepfakeshield.intelligence.UnifiedUrlThreatCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Refreshes PhishTank/OpenPhish URL threat cache every 6 hours.
 * No API keys required for PhishTank and OpenPhish.
 */
@HiltWorker
class UrlThreatCacheRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val urlThreatCache: UnifiedUrlThreatCache
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = urlThreatCache.refresh(urlHausAuthKey = null)
            result.fold(
                onSuccess = { stats ->
                    android.util.Log.i(TAG, "URL threat cache refreshed: ${stats.totalCount} URLs")
                    Result.success()
                },
                onFailure = { e ->
                    android.util.Log.w(TAG, "URL threat cache refresh failed: ${e.message}")
                    if (runAttemptCount >= 5) Result.failure() else Result.retry()
                }
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "URL threat cache refresh error", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "UrlThreatCacheWorker"
        private const val WORK_NAME = "url_threat_cache_refresh"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<UrlThreatCacheRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
