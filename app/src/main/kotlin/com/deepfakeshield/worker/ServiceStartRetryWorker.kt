package com.deepfakeshield.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.deepfakeshield.MainActivity
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.service.AntivirusForegroundService
import com.deepfakeshield.service.FloatingBubbleService
import com.deepfakeshield.service.ProtectionForegroundService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Retry starting protection services when user brings app to foreground.
 * Used when ForegroundServiceStartNotAllowedException occurred (Android 12+ background restriction).
 */
@HiltWorker
class ServiceStartRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val masterOn = userPreferences.masterProtectionEnabled.first()
            val onboardingDone = userPreferences.onboardingCompleted.first()
            if (!onboardingDone || !masterOn) return Result.success()

            val app = applicationContext
            try {
                val intent = Intent(app, ProtectionForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= 31 && e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
                    Log.e(TAG, "ForegroundServiceStartNotAllowedException — cannot retry from background", e)
                    return Result.failure()
                }
                Log.w(TAG, "Retry failed: ${e.message}")
                return if (runAttemptCount >= 3) Result.failure() else Result.retry()
            }

            if (android.provider.Settings.canDrawOverlays(app)) {
                try { FloatingBubbleService.start(app) } catch (_: Exception) { }
            }
            try { AntivirusForegroundService.start(app) } catch (_: Exception) { }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "ServiceStartRetry"
        private const val WORK_NAME = "service_start_retry"

        fun scheduleRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<ServiceStartRetryWorker>()
                .setInitialDelay(5, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
