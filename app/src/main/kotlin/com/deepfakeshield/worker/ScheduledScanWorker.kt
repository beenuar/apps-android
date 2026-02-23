package com.deepfakeshield.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.enterprise.QuietHoursHelper
import com.deepfakeshield.service.FloatingBubbleService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Scheduled full device scan - industry standard daily scan.
 */
@HiltWorker
class ScheduledScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val antivirusEngine: AntivirusEngine,
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val masterOn = userPreferences.masterProtectionEnabled.first()
        if (!masterOn) return Result.success()

        return try {
            val results = antivirusEngine.runFullScan(
                scanType = AntivirusEngine.SCAN_TYPE_SCHEDULED
            )
            val infected = results.filter { it.isInfected }
            for (r in infected) {
                val riskResult = antivirusEngine.toRiskResult(r) ?: continue
                alertRepository.insertAlert(
                    AlertEntity(
                        threatType = riskResult.threatType,
                        source = ThreatSource.SCHEDULED_SCAN,
                        severity = riskResult.severity,
                        score = riskResult.score,
                        confidence = riskResult.confidence,
                        title = "Malware Detected (Scheduled Scan)",
                        summary = riskResult.explainLikeImFive,
                        content = r.path,
                        senderInfo = r.threatName,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            if (infected.isNotEmpty()) {
                if (!QuietHoursHelper.shouldSuppressNotification(applicationContext, isCritical = true)) {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(
                    SCHEDULED_SCAN_ALERT_ID,
                    NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_CRITICAL_ALERTS)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Malware Found in Scheduled Scan")
                        .setContentText("${infected.size} threat(s) detected. Tap to review.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    )
                }
                FloatingBubbleService.updateStatus(applicationContext, alertRepository.getAlertCount(), true)
            }
            Result.success(workDataOf("scanned" to results.size, "infected" to infected.size))
        } catch (e: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "scheduled_av_scan"
        private const val SCHEDULED_SCAN_ALERT_ID = 2100

        /**
         * Schedule the scan. Pass batteryOptimizedScan=true to add requiresBatteryNotLow constraint.
         */
        fun schedule(context: Context, batteryOptimizedScan: Boolean = true) {
            val constraintsBuilder = androidx.work.Constraints.Builder()
            if (batteryOptimizedScan) {
                constraintsBuilder.setRequiresBatteryNotLow(true)
            }
            val request = PeriodicWorkRequestBuilder<ScheduledScanWorker>(24, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(constraintsBuilder.build())
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
