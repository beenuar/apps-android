package com.deepfakeshield.feature.shield

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.av.engine.SafeBrowsingChecker
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.notification.AlertNotifier
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.repository.VaultRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.ml.text.GovernmentGradeTextDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MessageShieldUiState(
    val smsEnabled: Boolean = false,
    val notificationEnabled: Boolean = false,
    val clipboardEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val scanResult: RiskResult? = null,
    val error: String? = null
)

private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

@HiltViewModel
class MessageShieldViewModel @Inject constructor(
    private val riskEngine: RiskIntelligenceEngine,
    private val textDetector: GovernmentGradeTextDetector,
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    private val vaultRepository: VaultRepository,
    private val alertNotifier: AlertNotifier,
    private val safeBrowsingChecker: SafeBrowsingChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageShieldUiState())
    val uiState: StateFlow<MessageShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.messageShieldEnabled,
                userPreferences.clipboardScanningEnabled,
                userPreferences.notificationsEnabled
            ) { message, clipboard, notification ->
                _uiState.update { it.copy(
                    smsEnabled = message,
                    clipboardEnabled = clipboard,
                    notificationEnabled = notification
                )}
            }.collect()
        }
    }

    private var currentScanJob: kotlinx.coroutines.Job? = null

    fun scanText(text: String) {
        // Cancel any previous scan to avoid overwriting results
        currentScanJob?.cancel()
        currentScanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanResult = null, error = null) }

            try {
                // Run BOTH the heuristic engine AND the GovernmentGrade ML text detector
                val heuristicResult = withContext(Dispatchers.Default) {
                    riskEngine.analyzeText(
                        text = text,
                        source = ThreatSource.MANUAL_SCAN
                    )
                }

                // Run GovernmentGrade detector (DistilBERT, RoBERTa, XLM-RoBERTa, GPT detector)
                val mlResult = withContext(Dispatchers.IO) {
                    try {
                        textDetector.analyzeText(text)
                    } catch (e: Exception) {
                        Log.w("MessageShieldVM", "GovernmentGrade text analysis failed", e)
                        null
                    }
                }

                // URL check: if Safe Browsing is configured, check any URLs in text
                var urlThreatResult: RiskResult? = null
                if (safeBrowsingChecker.isAvailable()) {
                    val urls = URL_REGEX.findAll(text).map { it.value }.distinct().take(5)
                    for (url in urls) {
                        val sb = safeBrowsingChecker.checkUrl(url)
                        if (sb.isThreat) {
                            urlThreatResult = RiskResult(
                                score = 90,
                                severity = com.deepfakeshield.core.model.RiskSeverity.CRITICAL,
                                confidence = 0.95f,
                                threatType = com.deepfakeshield.core.model.ThreatType.PHISHING_ATTEMPT,
                                reasons = listOf(com.deepfakeshield.core.model.Reason(
                                    type = com.deepfakeshield.core.model.ReasonType.TECHNICAL,
                                    title = "Malicious URL",
                                    explanation = "URL threat detected: ${sb.threatTypes.joinToString()}",
                                    evidence = url
                                )),
                                recommendedActions = emptyList(),
                                explainLikeImFive = "This link is dangerous. Do not open it.",
                                technicalDetails = "URL analysis: ${sb.threatTypes.joinToString()}"
                            )
                            break
                        }
                    }
                }

                // Blend: ML, URL threat, then heuristic
                val blended = when {
                    urlThreatResult != null -> urlThreatResult
                    mlResult != null && mlResult.isScam -> {
                        val mlScore = (mlResult.confidence * 100).toInt()
                        val boostedScore = maxOf(heuristicResult.score, mlScore)
                        heuristicResult.copy(
                            score = boostedScore,
                            confidence = maxOf(heuristicResult.confidence, mlResult.confidence)
                        )
                    }
                    else -> heuristicResult
                }

                val finalResult = blended

                _uiState.update { it.copy(isScanning = false, scanResult = finalResult) }
                userPreferences.incrementMessageScans()

                // Save alert if significant — then notify user and update bubble
                if (finalResult.shouldAlert) {
                    val alertId = alertRepository.insertAlert(
                        AlertEntity(
                            threatType = finalResult.threatType,
                            source = ThreatSource.MANUAL_SCAN,
                            severity = finalResult.severity,
                            score = finalResult.score,
                            confidence = finalResult.confidence,
                            title = "Suspicious ${finalResult.threatType.name.replace("_", " ").lowercase()}",
                            summary = finalResult.explainLikeImFive,
                            content = text,
                            senderInfo = null,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    vaultRepository.addEntry(
                        alertId = alertId,
                        entryType = "scam_message",
                        title = "Suspicious ${finalResult.threatType.name.replace("_", " ").lowercase()}",
                        description = finalResult.explainLikeImFive,
                        severity = finalResult.severity,
                        evidenceData = text
                    )
                    val count = alertRepository.getAlertCount()
                    alertNotifier.onAlertInserted(count, "Suspicious message detected", finalResult.explainLikeImFive)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(isScanning = false, error = "Scan failed: ${e.message}")
                }
            }
        }
    }

    fun enableSmsScanning() {
        viewModelScope.launch {
            userPreferences.setMessageShield(true)
        }
    }

    fun disableSmsScanning() {
        viewModelScope.launch {
            userPreferences.setMessageShield(false)
        }
    }

    fun enableNotificationScanning() {
        viewModelScope.launch {
            try {
                userPreferences.setNotifications(true)
            } catch (e: Exception) {
                Log.e("MessageShieldVM", "Failed to enable notification scanning", e)
                _uiState.update { it.copy(error = "Failed to enable notifications: ${e.message}") }
            }
        }
    }

    fun toggleClipboardScanning(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setClipboardScanning(enabled)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
