package com.deepfakeshield.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.av.engine.AvScanResult
import com.deepfakeshield.av.engine.QuarantineManager
import com.deepfakeshield.av.service.RealTimeAvMonitor
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * CONTINUOUS ANTIVIRUS SERVICE
 *
 * Industry-leading: runs 24/7 with real-time file monitoring.
 * Scans every new file the moment it lands on device.
 */
@AndroidEntryPoint
class AntivirusForegroundService : Service() {

    @Inject
    lateinit var realTimeMonitor: RealTimeAvMonitor

    @Inject
    lateinit var antivirusEngine: AntivirusEngine

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var quarantineManager: QuarantineManager

    @Inject
    lateinit var userPreferences: UserPreferences

    companion object {
        const val NOTIFICATION_ID = 1005 // Unique - 1001=ProtectionService, 1003=Video, 1004=InCall
        const val ACTION_START = "com.deepfakeshield.AV_START"
        const val ACTION_STOP = "com.deepfakeshield.AV_STOP"

        fun start(context: android.content.Context) {
            val intent = Intent(context, AntivirusForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitorRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                // Also handle null intent (system restart with START_STICKY)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceCompat.startForeground(
                            this, NOTIFICATION_ID, createNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, createNotification())
                    }
                    // Guard against duplicate monitors from multiple start intents
                    if (!isMonitorRunning) {
                        realTimeMonitor.setOnThreatFound { result -> onThreatFound(result) }
                        realTimeMonitor.start()
                        isMonitorRunning = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AntivirusService", "Failed to start", e)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                realTimeMonitor.stop()
                isMonitorRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            scope.cancel()
        } finally {
            realTimeMonitor.stop()
        }
        super.onDestroy()
    }

    private fun onThreatFound(result: AvScanResult) {
        scope.launch {
            try {
                val autoQuarantine = userPreferences.autoQuarantineOnThreat.first()
                if (autoQuarantine) {
                    val quarantined = quarantineManager.quarantine(result)
                    if (quarantined != null) {
                        android.util.Log.i("AntivirusService", "Auto-quarantined: ${result.path}")
                    }
                }
                val riskResult = antivirusEngine.toRiskResult(result) ?: return@launch
                alertRepository.insertAlert(
                    AlertEntity(
                        threatType = when (result.threatName?.lowercase()) {
                            "adware" -> ThreatType.ADWARE
                            "spyware" -> ThreatType.SPYWARE
                            "trojan", "trojanhorse" -> ThreatType.TROJAN
                            "ransomware" -> ThreatType.RANSOMWARE
                            "pua" -> ThreatType.PUA
                            else -> ThreatType.MALWARE
                        },
                        source = ThreatSource.REAL_TIME_SCAN,
                        severity = riskResult.severity,
                        score = riskResult.score,
                        confidence = riskResult.confidence,
                        title = "Malware Detected",
                        summary = riskResult.explainLikeImFive,
                        content = result.path,
                        senderInfo = result.threatName,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val count = alertRepository.getAlertCount()
                FloatingBubbleService.updateStatus(this@AntivirusForegroundService, count, true)

                // Push rich threat details to bubble
                FloatingBubbleService.pushThreatDetected(
                    context = this@AntivirusForegroundService,
                    threatType = "malware",
                    source = result.path.substringAfterLast("/"),
                    score = riskResult.score,
                    summary = "Malware: ${result.threatName ?: "Suspicious file"}",
                    reasons = riskResult.reasons.joinToString("; ") { it.title }
                )
            } catch (e: Exception) {
                android.util.Log.e("AntivirusService", "Failed to handle threat", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_route", "antivirus")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DeepfakeShieldApplication.CHANNEL_SERVICE)
            .setContentTitle("Antivirus Active")
            .setContentText("Real-time protection scanning files 24/7")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Real-time antivirus protection active.\n\n" +
                "• File monitoring\n" +
                "• Signature detection\n" +
                "• Heuristic analysis\n" +
                "• PUA detection\n\n" +
                "Tap to open DeepFake Shield"
            ))
            .build()
    }
}
