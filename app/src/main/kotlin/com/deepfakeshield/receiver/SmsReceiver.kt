package com.deepfakeshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.di.AlertNotifierImpl
import com.deepfakeshield.enterprise.QuietHoursHelper
import com.deepfakeshield.service.FloatingBubbleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * PRODUCTION SMS RECEIVER - REAL ANALYSIS
 * Fixed: goAsync().finish() is called exactly once after all messages are processed.
 */
private const val SMS_NOTIFICATION_BASE = 30000

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var riskEngine: RiskIntelligenceEngine

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                if (!::riskEngine.isInitialized || !::alertRepository.isInitialized || !::userPreferences.isInitialized) {
                    Log.w("SmsReceiver", "Dependencies not injected yet, skipping analysis")
                    return@launch  // finally block will call pendingResult.finish()
                }
                if (!userPreferences.messageShieldEnabled.first()) {
                    Log.d("SmsReceiver", "Message Shield disabled, skipping SMS analysis")
                    return@launch  // finally block will call pendingResult.finish()
                }
                messages.forEach { message ->
                    try {
                        val sender = message.displayOriginatingAddress ?: "Unknown"
                        val body = message.messageBody ?: return@forEach

                        Log.d("SmsReceiver", "SMS received for analysis (${body.length} chars)")

                        val riskResult = riskEngine.analyzeText(
                            text = body,
                            source = ThreatSource.SMS,
                            metadata = mapOf(
                                "sender" to sender,
                                "timestamp" to System.currentTimeMillis().toString()
                            )
                        )

                        if (riskResult.shouldAlert) {
                            alertRepository.insertAlert(
                                AlertEntity(
                                    threatType = riskResult.threatType,
                                    source = ThreatSource.SMS,
                                    severity = riskResult.severity,
                                    score = riskResult.score,
                                    confidence = riskResult.confidence,
                                    title = "Suspicious SMS Detected",
                                    summary = riskResult.explainLikeImFive,
                                    content = body,
                                    senderInfo = sender,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            val isCritical = riskResult.score >= 70
                            if (!QuietHoursHelper.shouldSuppressNotification(context, isCritical)) {
                                showThreatNotification(context, sender, riskResult.severity.name, riskResult.score, riskResult.explainLikeImFive)
                            }
                            val count = alertRepository.getAlertCount()
                            FloatingBubbleService.updateStatus(context, count, true)

                            // Push rich threat details to bubble
                            FloatingBubbleService.pushThreatDetected(
                                context = context,
                                threatType = "sms",
                                source = sender,
                                score = riskResult.score,
                                summary = "Scam SMS from $sender",
                                reasons = riskResult.reasons.joinToString("; ") { it.title }
                            )
                            Log.w("SmsReceiver", "THREAT DETECTED: ${riskResult.severity} from $sender")
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Analysis failed for message", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "SMS processing failed", e)
            } finally {
                // Called exactly once after all messages are processed
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun showThreatNotification(
        context: Context,
        sender: String,
        severity: String,
        score: Int,
        explanation: String
    ) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val tapIntent = android.app.PendingIntent.getActivity(
                context, sender.hashCode(),
                Intent(context, com.deepfakeshield.MainActivity::class.java).apply {
                    putExtra("open_route", "alerts")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // B2C-SAFE: Use ALERTS channel (not CRITICAL_ALERTS) to be less intrusive.
            // Only truly critical events (active malware) should use CRITICAL channel.
            val channel = if (score >= 70) DeepfakeShieldApplication.CHANNEL_CRITICAL_ALERTS
                          else DeepfakeShieldApplication.CHANNEL_ALERTS
            val priority = if (score >= 70) NotificationCompat.PRIORITY_HIGH
                           else NotificationCompat.PRIORITY_DEFAULT
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Suspicious SMS from $sender")
                .setContentText("Risk: $score% — $explanation")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Risk Score: $score%\nSeverity: $severity\n\n$explanation"))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(SMS_NOTIFICATION_BASE + AlertNotifierImpl.nextNotificationSequence(), notification)
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Failed to show notification", e)
        }
    }
}
