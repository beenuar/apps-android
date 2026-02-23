package com.deepfakeshield.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.enterprise.QuietHoursHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * PRODUCTION CALL SCREENING SERVICE
 * Uses EntryPointAccessors instead of @AndroidEntryPoint
 * because system-bound services can't use Hilt injection directly.
 */
private const val CALL_NOTIFICATION_BASE = 40000

class CallScreeningService : CallScreeningService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CallScreeningEntryPoint {
        fun riskEngine(): RiskIntelligenceEngine
        fun alertRepository(): AlertRepository
        fun userPreferences(): UserPreferences
        fun phoneReputationRepository(): com.deepfakeshield.data.repository.PhoneReputationRepository
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun CallResponse.Builder.setSilenceCallCompat(silence: Boolean): CallResponse.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setSilenceCall(silence)
        }
        return this
    }

    private val entryPoint: CallScreeningEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, CallScreeningEntryPoint::class.java)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "Unknown"

        Log.d("CallScreening", "Screening call from: ***${phoneNumber.takeLast(4)}")

        scope.launch {
            try {
                val userPreferences = entryPoint.userPreferences()
                if (!userPreferences.callShieldEnabled.first()) {
                    Log.d("CallScreening", "Call Shield disabled, allowing call")
                    withContext(Dispatchers.Main) {
                        respondToCall(callDetails, android.telecom.CallScreeningService.CallResponse.Builder()
                            .setRejectCall(false)
                            .setDisallowCall(false)
                            .setSilenceCallCompat(false)
                            .build())
                    }
                    return@launch
                }
                val riskEngine = entryPoint.riskEngine()
                val alertRepository = entryPoint.alertRepository()
                val phoneRepo = entryPoint.phoneReputationRepository()

                // Check if number is blocked in database FIRST
                val isBlockedInDb = try {
                    val rep = phoneRepo.getReputation(phoneNumber)
                    rep?.isBlocked == true
                } catch (e: Exception) {
                    Log.w("CallScreening", "Failed to check blocked list: ${e.message}")
                    false
                }

                // Also sync blocked numbers to the risk engine's session cache
                if (isBlockedInDb) {
                    riskEngine.blockNumber(phoneNumber)
                }

                // Check if whitelisted
                val isWhitelisted = try {
                    phoneRepo.isWhitelisted(phoneNumber)
                } catch (_: Exception) { false }

                // Reject blocked numbers IMMEDIATELY — before any analysis that could timeout
                if (isBlockedInDb) {
                    Log.w("CallScreening", "BLOCKED NUMBER REJECTED (pre-analysis): ***${phoneNumber.takeLast(4)}")
                    withContext(Dispatchers.Main) {
                        respondToCall(callDetails, CallResponse.Builder()
                            .setRejectCall(true)
                            .setDisallowCall(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .build())
                    }
                    return@launch
                }

                if (isWhitelisted) {
                    Log.d("CallScreening", "Number is whitelisted, allowing: ***${phoneNumber.takeLast(4)}")
                    withContext(Dispatchers.Main) {
                        respondToCall(callDetails, CallResponse.Builder()
                            .setRejectCall(false)
                            .setDisallowCall(false)
                            .setSilenceCallCompat(false)
                            .build())
                    }
                    return@launch
                }

                val riskResult = withTimeout(4000) {
                    riskEngine.analyzeCall(
                        phoneNumber = phoneNumber,
                        isIncoming = true,
                        callDuration = 0,
                        metadata = mapOf("timestamp" to System.currentTimeMillis().toString())
                    )
                }

                val responseBuilder = CallResponse.Builder()
                
                when {
                    riskResult.score >= 65 -> {
                        responseBuilder
                            .setRejectCall(true)
                            .setDisallowCall(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                        Log.w("CallScreening", "HIGH RISK CALL BLOCKED: ***${phoneNumber.takeLast(4)}")
                    }
                    riskResult.score >= 45 -> {
                        responseBuilder
                            .setRejectCall(false)
                            .setSilenceCallCompat(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                        Log.w("CallScreening", "MEDIUM RISK CALL SILENCED: ***${phoneNumber.takeLast(4)}")
                    }
                    else -> {
                        responseBuilder
                            .setRejectCall(false)
                            .setDisallowCall(false)
                            .setSilenceCallCompat(false)
                        Log.d("CallScreening", "Low risk call allowed: ***${phoneNumber.takeLast(4)}")
                    }
                }

                // Alert persistence is best-effort — failures must NEVER override the
                // already-decided call response (reject/silence/allow).  Previously, a
                // SQLiteException here would jump to the outer catch which sends an
                // unconditional "allow" response, letting scam calls through.
                if (riskResult.shouldAlert) {
                    try {
                        val isCritical = riskResult.score >= 70
                        val showNotification = !QuietHoursHelper.shouldSuppressNotification(this@CallScreeningService, isCritical)
                        val actionTaken = when {
                            riskResult.score >= 65 -> "BLOCKED"
                            riskResult.score >= 45 -> "SILENCED"
                            else -> "WARNED"
                        }
                        alertRepository.insertAlert(
                            AlertEntity(
                                threatType = riskResult.threatType,
                                source = ThreatSource.INCOMING_CALL,
                                severity = riskResult.severity,
                                score = riskResult.score,
                                confidence = riskResult.confidence,
                                title = "Suspicious Call $actionTaken",
                                summary = riskResult.explainLikeImFive,
                                content = "Call from: $phoneNumber\nAction: $actionTaken\nReasons: ${riskResult.reasons.joinToString(", ") { it.title }}",
                                senderInfo = phoneNumber,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        val count = alertRepository.getAlertCount()
                        FloatingBubbleService.updateStatus(this@CallScreeningService, count, true)

                        // Push rich threat details to bubble
                        FloatingBubbleService.pushThreatDetected(
                            context = this@CallScreeningService,
                            threatType = "call",
                            source = phoneNumber,
                            score = riskResult.score,
                            summary = "Scam call $actionTaken: ***${phoneNumber.takeLast(4)}",
                            reasons = riskResult.reasons.joinToString("; ") { it.title }
                        )

                        if (showNotification) {
                            showCallWarning(phoneNumber, riskResult.severity.name, riskResult.score, riskResult.explainLikeImFive)
                        }
                    } catch (e: Exception) {
                        Log.e("CallScreening", "Failed to persist alert, still sending correct call response", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    respondToCall(callDetails, responseBuilder.build())
                }
                
            } catch (e: CancellationException) {
                // Service destroyed mid-screening — still respond to avoid leaving call in limbo
                Log.w("CallScreening", "Screening cancelled (service destroyed), allowing call")
                withContext(Dispatchers.Main + NonCancellable) {
                    respondToCall(callDetails, CallResponse.Builder()
                        .setRejectCall(false)
                        .setDisallowCall(false)
                        .setSilenceCallCompat(false)
                        .build())
                }
            } catch (e: Exception) {
                Log.e("CallScreening", "Analysis failed, allowing call", e)
                val response = CallResponse.Builder()
                    .setRejectCall(false)
                    .setDisallowCall(false)
                    .setSilenceCallCompat(false)
                    .build()
                
                withContext(Dispatchers.Main + NonCancellable) {
                    respondToCall(callDetails, response)
                }
            }
        }
    }

    private fun showCallWarning(phoneNumber: String, severity: String, score: Int, explanation: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // B2C-SAFE: Use appropriate channel based on score.
            // Only truly dangerous calls (score >= 70) use CRITICAL channel.
            val channel = if (score >= 70) DeepfakeShieldApplication.CHANNEL_CRITICAL_ALERTS
                          else DeepfakeShieldApplication.CHANNEL_ALERTS
            val priority = if (score >= 70) NotificationCompat.PRIORITY_HIGH
                           else NotificationCompat.PRIORITY_DEFAULT
            val notification = NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Suspicious Call from $phoneNumber")
                .setContentText("Risk: $score% — $severity")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Risk Score: $score%\nSeverity: $severity\n\n$explanation\n\nBe cautious. Do not share OTPs or financial information."))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .build()

            val notificationId = CALL_NOTIFICATION_BASE + com.deepfakeshield.di.AlertNotifierImpl.nextNotificationSequence()
            manager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e("CallScreening", "Failed to show notification", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
