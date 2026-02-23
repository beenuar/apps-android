package com.deepfakeshield.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.repository.AlertRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class InstallScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val antivirusEngine: AntivirusEngine,
    private val alertRepository: AlertRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val NOTIFICATION_ID = 2005
    }

    override suspend fun doWork(): Result {
        val packageName = inputData.getString("package_name") ?: return Result.failure()

        return try {
            Log.i("InstallScan", "Scanning newly installed: $packageName")
            val result = antivirusEngine.scanInstalledApp(packageName, AntivirusEngine.SCAN_TYPE_ONDEMAND)

            if (!result.isClean) {
                alertRepository.insertAlert(AlertEntity(
                    threatType = ThreatType.MALWARE,
                    source = ThreatSource.REAL_TIME_SCAN,
                    severity = if (result.isInfected) RiskSeverity.CRITICAL else RiskSeverity.MEDIUM,
                    score = if (result.isInfected) 90 else 50,
                    confidence = 0.8f,
                    title = "Suspicious App Installed",
                    summary = "Newly installed app '${result.displayName}' flagged: ${result.threatName ?: "suspicious behavior"}",
                    content = "Package: $packageName\nThreat: ${result.threatName}\nLevel: ${result.threatLevel}",
                    senderInfo = packageName,
                    timestamp = System.currentTimeMillis()
                ))

                val intent = PendingIntent.getActivity(applicationContext, 0,
                    Intent(applicationContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP); putExtra("open_route", "alerts") },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val notification = NotificationCompat.Builder(applicationContext, DeepfakeShieldApplication.CHANNEL_ALERTS)
                    .setContentTitle("\u26A0\uFE0F Suspicious App Detected")
                    .setContentText("${result.displayName}: ${result.threatName ?: "suspicious"}")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(intent).setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).build()

                (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIFICATION_ID, notification)
            } else {
                Log.i("InstallScan", "$packageName is clean")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("InstallScan", "Scan failed for $packageName", e)
            if (runAttemptCount >= 2) Result.failure() else Result.retry()
        }
    }
}
