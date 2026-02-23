package com.deepfakeshield.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import android.content.ClipboardManager
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that shows protection status
 * in the notification bar. Provides always-on visibility and
 * quick access to scanning features.
 *
 * Also: Listens for active calls and shows InCallOverlayService
 * when user is on a call (so they can enable live detection).
 */
class ProtectionForegroundService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProtectionEntryPoint {
        fun userPreferences(): UserPreferences
        fun riskEngine(): RiskIntelligenceEngine
        fun alertRepository(): AlertRepository
        fun phoneReputationRepository(): com.deepfakeshield.data.repository.PhoneReputationRepository
    }

    private val entryPoint: ProtectionEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ProtectionEntryPoint::class.java)
    }

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.deepfakeshield.STOP_PROTECTION"
        const val ACTION_QUICK_SCAN = "com.deepfakeshield.QUICK_SCAN"

        /**
         * Set to true while any DeepfakeShield activity is in the foreground.
         * The clipboard monitor skips scans when the app is in the foreground because
         * any clipboard change is from the user copying text within the app's own UI
         * (threat details, demo scam text, alert summaries) which would create
         * false-positive alerts from our own content.
         *
         * Activities should call `isAppInForeground = true` in onResume and
         * `isAppInForeground = false` in onPause.
         */
        @Volatile
        var isAppInForeground = false
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                        scope.launch {
                            val callShieldEnabled = try {
                                entryPoint.userPreferences().callShieldEnabled.first()
                            } catch (_: Exception) { false }
                            if (callShieldEnabled) {
                                try {
                                    InCallOverlayService.start(this@ProtectionForegroundService)
                                } catch (e: Exception) {
                                    android.util.Log.w("ProtectionService", "Could not start InCallOverlay: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        }

        // Sync blocked numbers from DB to the risk engine's session cache
        syncBlockedNumbers()

        // Clipboard monitoring - scans copied text for scam/phishing content
        startClipboardMonitoring()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("ProtectionService", "Failed to start foreground", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUICK_SCAN -> {
                try {
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("open_scan", true)
                    }
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    android.util.Log.e("ProtectionService", "Failed to launch scan", e)
                }
            }
        }
        
        // Update notification
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            android.util.Log.e("ProtectionService", "Failed to update notification", e)
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncBlockedNumbers() {
        scope.launch {
            try {
                val phoneRepo = entryPoint.phoneReputationRepository()
                val riskEngine = entryPoint.riskEngine()
                phoneRepo.getBlockedNumbers().first().let { blockedEntities ->
                    val numbers = blockedEntities.map { it.phoneNumber }
                    riskEngine.syncBlockedNumbers(numbers)
                    android.util.Log.i("ProtectionService", "Synced ${numbers.size} blocked numbers to risk engine")
                }
            } catch (e: Exception) {
                android.util.Log.w("ProtectionService", "Failed to sync blocked numbers: ${e.message}")
            }
        }
    }

    private fun startClipboardMonitoring() {
        try {
            clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                scope.launch {
                    try {
                        // Skip clipboard scans when our own app is in the foreground.
                        // The user is copying text from our own UI (threat descriptions, demo
                        // scam text, alert summaries, scan results) which contains scam keywords
                        // and would create false-positive duplicate alerts.
                        if (isAppInForeground) return@launch

                        val prefs = entryPoint.userPreferences()
                        val clipboardEnabled = prefs.clipboardScanningEnabled.first()
                        if (!clipboardEnabled) return@launch

                        val clip = clipboardManager?.primaryClip ?: return@launch
                        if (clip.itemCount == 0) return@launch
                        val text = clip.getItemAt(0)?.text?.toString() ?: return@launch
                        if (text.isBlank() || text.length < 10) return@launch // Skip short clips

                        val riskEngine = entryPoint.riskEngine()
                        val riskResult = riskEngine.analyzeText(
                            text = text,
                            source = ThreatSource.CLIPBOARD,
                            metadata = mapOf("source" to "clipboard")
                        )

                        if (riskResult.shouldAlert) {
                            val alertRepo = entryPoint.alertRepository()
                            alertRepo.insertAlert(
                                AlertEntity(
                                    threatType = riskResult.threatType,
                                    source = ThreatSource.CLIPBOARD,
                                    severity = riskResult.severity,
                                    score = riskResult.score,
                                    confidence = riskResult.confidence,
                                    title = "Suspicious Clipboard Content",
                                    summary = riskResult.explainLikeImFive,
                                    content = text.take(500),
                                    senderInfo = "Clipboard",
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            val count = alertRepo.getAlertCount()
                            FloatingBubbleService.updateStatus(this@ProtectionForegroundService, count, true)

                            // Push rich threat details to bubble
                            FloatingBubbleService.pushThreatDetected(
                                context = this@ProtectionForegroundService,
                                threatType = "clipboard",
                                source = "Clipboard",
                                score = riskResult.score,
                                summary = "Scam text in clipboard",
                                reasons = riskResult.reasons.joinToString("; ") { it.title }
                            )

                            // B2C: Respect notification preferences before showing clipboard threats
                            val suppress = try {
                                com.deepfakeshield.enterprise.QuietHoursHelper.shouldSuppressNotification(
                                    this@ProtectionForegroundService
                                )
                            } catch (_: Exception) { false }

                            if (!suppress) {
                                val nm = getSystemService(NotificationManager::class.java)
                                val notification = NotificationCompat.Builder(
                                    this@ProtectionForegroundService,
                                    DeepfakeShieldApplication.CHANNEL_ALERTS
                                )
                                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                                    .setContentTitle("Suspicious Clipboard Content")
                                    .setContentText("Risk: ${riskResult.score}% — Copied text may contain a scam")
                                    .setStyle(NotificationCompat.BigTextStyle()
                                        .bigText("Risk Score: ${riskResult.score}%\n\n${riskResult.explainLikeImFive}"))
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setAutoCancel(true)
                                    .build()
                                nm?.notify(9001, notification)
                            }

                            android.util.Log.w("ProtectionService", "Clipboard threat detected: score=${riskResult.score}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ProtectionService", "Clipboard scan error: ${e.message}")
                    }
                }
            }
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
            // On Android 10+, background apps can't read clipboard content (privacy restriction).
            // The listener still fires, but primaryClip returns null unless app was recently in foreground.
            // This monitoring is most useful when app is in foreground or recently backgrounded.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.util.Log.i("ProtectionService", "Clipboard monitoring started (limited on Android 10+ - works when app is recently used)")
            } else {
                android.util.Log.d("ProtectionService", "Clipboard monitoring started")
            }
        } catch (e: Exception) {
            android.util.Log.w("ProtectionService", "Failed to start clipboard monitoring: ${e.message}")
        }
    }

    override fun onDestroy() {
        clipboardListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        clipboardListener = null
        clipboardManager = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
        telephonyCallback = null
        telephonyManager = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProtectionForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scanIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ProtectionForegroundService::class.java).apply {
                action = ACTION_QUICK_SCAN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DeepfakeShieldApplication.CHANNEL_SERVICE)
            .setContentTitle("DeepFake Shield Active")
            .setContentText("All shields are monitoring for threats")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_search,
                "Quick Scan",
                scanIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }
}
