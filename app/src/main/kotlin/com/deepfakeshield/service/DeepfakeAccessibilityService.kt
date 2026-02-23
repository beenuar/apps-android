package com.deepfakeshield.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
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
 * Accessibility Service for SMS/notification content access.
 * User must explicitly enable in Settings > Accessibility.
 * Enables scanning of message content when NotificationListener cannot access body.
 * Uses EntryPointAccessors (not @AndroidEntryPoint) since system creates this service.
 */
class DeepfakeAccessibilityService : AccessibilityService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccessibilityEntryPoint {
        fun riskEngine(): RiskIntelligenceEngine
        fun alertRepository(): AlertRepository
        fun userPreferences(): UserPreferences
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val entryPoint: AccessibilityEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, AccessibilityEntryPoint::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val text = event.text.joinToString(" ")
                val pkg = event.packageName?.toString() ?: ""
                if (text.isNotBlank() && pkg in SMS_PACKAGES) {
                    scope.launch {
                        try {
                            // Check if protection is enabled
                            val prefs = entryPoint.userPreferences()
                            val messageShieldOn = prefs.messageShieldEnabled.first()
                            if (!messageShieldOn) return@launch

                            val riskEngine = entryPoint.riskEngine()
                            val result = riskEngine.analyzeText(text, ThreatSource.SMS)

                            if (result.score >= 30) {
                                Log.d(TAG, "Suspicious message from $pkg: score=${result.score}")
                                val alert = AlertEntity(
                                    threatType = ThreatType.SCAM_MESSAGE,
                                    source = ThreatSource.SMS,
                                    severity = when {
                                        result.score >= 80 -> RiskSeverity.CRITICAL
                                        result.score >= 60 -> RiskSeverity.HIGH
                                        result.score >= 40 -> RiskSeverity.MEDIUM
                                        else -> RiskSeverity.LOW
                                    },
                                    score = result.score,
                                    confidence = result.confidence,
                                    title = "Suspicious Message Detected",
                                    summary = result.explainLikeImFive,
                                    content = text.take(500),
                                    senderInfo = pkg,
                                    timestamp = System.currentTimeMillis()
                                )
                                entryPoint.alertRepository().insertAlert(alert)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to analyze notification text: ${e.message}")
                        }
                    }
                }
            }
            else -> { /* other event types */ }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        Log.d(TAG, "Cyble Accessibility Service connected")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DeepfakeAccessibility"
        private val SMS_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"
        )
    }
}
