package com.deepfakeshield.core.engine

import android.net.Uri
import com.deepfakeshield.core.intelligence.*
import com.deepfakeshield.core.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ENHANCED RISK INTELLIGENCE ENGINE
 * 
 * Integrates all 10 advanced intelligence engines:
 * 1. Community Threat Intelligence
 * 2. Behavioral Analysis
 * 3. Adaptive Learning
 * 4. Advanced Deepfake Detection
 * 5. URL Intelligence
 * 6. Multi-Language Detection
 * 7. Scammer Fingerprinting
 * 8. Contextual AI Assistant
 * 9. Predictive Threat Modeling
 * 10. Quantum-Safe Encryption
 */

@Singleton
class EnhancedRiskIntelligenceEngine @Inject constructor(
    private val baseEngine: RiskIntelligenceEngine,
    private val communityNetwork: CommunityThreatNetwork,
    private val behavioralAnalysis: BehavioralAnalysisEngine,
    private val adaptiveLearning: AdaptiveLearningEngine,
    private val deepfakeDetector: AdvancedDeepfakeDetector,
    private val urlIntelligence: URLIntelligenceEngine,
    private val multiLingualDetector: MultiLingualThreatDetector,
    private val scammerFingerprinting: ScammerFingerprintingEngine,
    private val aiAssistant: ContextualAIAssistant,
    private val predictiveEngine: PredictiveThreatEngine,
    private val quantumEncryption: QuantumSafeEncryption
) {
    
    /**
     * Enhanced text analysis with all intelligence engines
     */
    suspend fun analyzeTextEnhanced(
        text: String,
        source: ThreatSource,
        senderInfo: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): EnhancedRiskResult {
        // 1. Base analysis
        val baseResult = baseEngine.analyzeText(text, source, senderInfo, metadata)
        
        // 2. Community check — map ThreatSource to the appropriate community type
        val communityType = when (source) {
            ThreatSource.SMS, ThreatSource.MANUAL_SCAN -> com.deepfakeshield.core.intelligence.CommunityThreatType.SCAM_SMS
            ThreatSource.NOTIFICATION -> com.deepfakeshield.core.intelligence.CommunityThreatType.MALICIOUS_NOTIFICATION
            ThreatSource.CLIPBOARD -> com.deepfakeshield.core.intelligence.CommunityThreatType.PHISHING_URL
            else -> com.deepfakeshield.core.intelligence.CommunityThreatType.SCAM_SMS
        }
        val communityThreat = communityNetwork.checkThreat(text, communityType)
        
        // 3. Behavioral analysis
        val behavioralAnomalies = if (senderInfo != null) {
            behavioralAnalysis.analyzeMessageBehavior(senderInfo, text)
        } else emptyList()
        
        // 4. Multi-language analysis
        val multiLingualAnalysis = multiLingualDetector.analyzeText(text)
        
        // 5. Scammer fingerprint check
        val scammerFingerprint = senderInfo?.let {
            scammerFingerprinting.isKnownScammer(it)
        }
        
        // 6. Adaptive learning adjustment
        val adjustedScore = adaptiveLearning.calculateAdjustedScore(
            baseResult.score,
            baseResult.reasons.map { it.title }
        )
        
        // 7. AI Assistant guidance
        val assistance = aiAssistant.getAssistance(
            threatType = baseResult.threatType.toString(),
            severity = adjustedScore,
            _content = text,
            reasons = baseResult.reasons.map { it.explanation }
        )
        
        // 8. Calculate final score with all intelligence
        val finalScore = calculateEnhancedScore(
            baseScore = adjustedScore,
            communityConfidence = communityThreat?.reportCount ?: 0,
            behavioralRisk = behavioralAnomalies.sumOf { it.severity }.toFloat() / 100f,
            multiLingualRisk = multiLingualAnalysis.riskScore,
            knownScammer = scammerFingerprint != null
        )
        
        return EnhancedRiskResult(
            baseResult = baseResult.copy(score = finalScore),
            communityIntel = communityThreat,
            behavioralInsights = behavioralAnomalies,
            multiLingualAnalysis = multiLingualAnalysis,
            scammerProfile = scammerFingerprint,
            aiAssistance = assistance,
            enhancementVersion = "2.0"
        )
    }
    
    /**
     * Enhanced URL analysis
     */
    suspend fun analyzeURLEnhanced(url: String): URLThreatAnalysis {
        // Use URL Intelligence Engine
        val analysis = urlIntelligence.analyzeURL(url)
        
        // Check community reports
        val communityReport = communityNetwork.isKnownPhishingUrl(url)
        
        // Adjust score if community has reported it
        if (communityReport != null) {
            return analysis.copy(
                riskScore = (analysis.riskScore + 30).coerceAtMost(100),
                warnings = analysis.warnings + "Reported by ${communityReport.reportCount} users"
            )
        }
        
        return analysis
    }
    
    /**
     * Enhanced video analysis with advanced deepfake detection
     */
    suspend fun analyzeVideoEnhanced(videoUri: Uri): DeepfakeAnalysisResult {
        return deepfakeDetector.analyzeVideo(videoUri)
    }
    
    /**
     * Enhanced call analysis
     */
    suspend fun analyzeCallEnhanced(
        phoneNumber: String,
        durationSeconds: Int,
        isIncoming: Boolean
    ): EnhancedCallAnalysis {
        // Base analysis
        val baseResult = baseEngine.analyzeCall(phoneNumber, isIncoming, durationSeconds.toLong())
        
        // Community check
        val communityReport = communityNetwork.isKnownScammerPhone(phoneNumber)
        
        // Behavioral analysis
        val behavioralAnomalies = behavioralAnalysis.analyzeCallBehavior(
            callerId = phoneNumber,
            durationSeconds = durationSeconds,
            isIncoming = isIncoming
        )
        
        // Scammer fingerprint
        val scammerProfile = scammerFingerprinting.isKnownScammer(phoneNumber)
        
        // Calculate enhanced risk
        val enhancedScore = calculateEnhancedScore(
            baseScore = baseResult.score,
            communityConfidence = communityReport?.reportCount ?: 0,
            behavioralRisk = behavioralAnomalies.sumOf { it.severity }.toFloat() / 100f,
            multiLingualRisk = 0,
            knownScammer = scammerProfile != null
        )
        
        return EnhancedCallAnalysis(
            baseScore = enhancedScore,
            severity = baseResult.severity,
            communityReport = communityReport,
            behavioralAnomalies = behavioralAnomalies,
            scammerProfile = scammerProfile,
            recommendedAction = if (enhancedScore >= 80) "BLOCK" else if (enhancedScore >= 60) "SILENCE" else "ALLOW"
        )
    }
    
    /**
     * Record user feedback for adaptive learning
     */
    suspend fun recordFeedback(
        contentHash: String,
        wasActuallyThreat: Boolean,
        detectedAsThreat: Boolean,
        detectionScore: Int,
        matchedPatterns: List<String>
    ) {
        // Update adaptive learning
        adaptiveLearning.recordFeedback(
            contentHash = contentHash,
            detectedThreat = detectedAsThreat,
            userConfirmedThreat = wasActuallyThreat,
            detectionScore = detectionScore,
            matchedPatterns = matchedPatterns
        )
        
        // Update community network
        if (wasActuallyThreat) {
            communityNetwork.provideFeedback(
                contentHash = contentHash,
                wasActuallyThreat = true,
                falsePositive = false
            )
        } else if (detectedAsThreat) {
            // User confirmed it was NOT a threat but we flagged it — report false positive
            // so community severity score decreases over time
            communityNetwork.provideFeedback(
                contentHash = contentHash,
                wasActuallyThreat = false,
                falsePositive = true
            )
        }
    }
    
    /**
     * Get threat forecast
     */
    fun getThreatForecast(): ThreatForecast {
        return predictiveEngine.generateForecast()
    }
    
    /**
     * Get user risk profile
     */
    fun getUserRiskProfile(
        age: Int? = null,
        occupation: String? = null,
        techSavviness: String? = null,
        hasSharedPersonalInfo: Boolean = false,
        clickedSuspiciousLinks: Int = 0
    ): UserRiskProfile {
        return predictiveEngine.generateRiskProfile(
            age = age,
            _occupation = occupation,
            techSavviness = techSavviness,
            hasSharedPersonalInfo = hasSharedPersonalInfo,
            clickedSuspiciousLinks = clickedSuspiciousLinks
        )
    }
    
    /**
     * Get community network stats
     */
    fun getCommunityStats(): NetworkStats {
        return communityNetwork.getNetworkStats()
    }
    
    /**
     * Get learning statistics
     */
    fun getLearningStats(): LearningStats {
        return adaptiveLearning.getLearningStats()
    }
    
    /**
     * Encrypt sensitive data
     */
    /**
     * Encrypt sensitive data. Returns the full EncryptResult so the caller can
     * store the key via SecureKeyStorage. Returning only EncryptedData would
     * discard the randomly-generated AES key, making decryption impossible.
     */
    fun encryptSensitiveData(data: ByteArray, keyId: String): EncryptResult {
        return quantumEncryption.encrypt(data, keyId)
    }
    
    // === PRIVATE METHODS ===
    
    private fun calculateEnhancedScore(
        baseScore: Int,
        communityConfidence: Int,
        behavioralRisk: Float,
        multiLingualRisk: Int,
        knownScammer: Boolean
    ): Int {
        var enhanced = baseScore.toFloat()
        
        // Community intelligence boost
        enhanced += (communityConfidence * 2).coerceAtMost(30)
        
        // Behavioral risk
        enhanced += behavioralRisk
        
        // Multi-lingual risk (use float division to preserve low-value signals)
        enhanced += (multiLingualRisk / 4f)
        
        // Known scammer - major boost
        if (knownScammer) {
            enhanced += 40
        }
        
        return enhanced.toInt().coerceIn(0, 100)
    }
}

/**
 * Enhanced risk result with all intelligence data
 */
data class EnhancedRiskResult(
    val baseResult: RiskResult,
    val communityIntel: ThreatReport?,
    val behavioralInsights: List<BehaviorAnomaly>,
    val multiLingualAnalysis: MultiLingualAnalysis,
    val scammerProfile: ScammerFingerprint?,
    val aiAssistance: AssistantResponse,
    val enhancementVersion: String
)

/**
 * Enhanced call analysis
 */
data class EnhancedCallAnalysis(
    val baseScore: Int,
    val severity: RiskSeverity,
    val communityReport: ThreatReport?,
    val behavioralAnomalies: List<BehaviorAnomaly>,
    val scammerProfile: ScammerFingerprint?,
    val recommendedAction: String
)
