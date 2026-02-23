package com.deepfakeshield.core.intelligence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * BEHAVIORAL ANALYSIS ENGINE
 * 
 * Analyzes patterns of behavior to detect threats
 * - Message timing patterns
 * - Call behavior analysis
 * - Sender history tracking
 * - Anomaly detection
 */

data class BehaviorProfile(
    val senderId: String,            // Phone number or identifier
    val messageCount: Int,
    val callCount: Int,
    val avgResponseTime: Long,       // milliseconds
    val avgMessageLength: Int,
    val activeHours: Set<Int>,       // Hours of day (0-23)
    val typingSpeed: Double?,        // Characters per second
    val suspiciousPatterns: List<String>,
    val trustScore: Int,             // 0-100 (higher = more trustworthy)
    val firstContact: Long,
    val lastContact: Long
)

data class BehaviorAnomaly(
    val type: AnomalyType,
    val severity: Int,               // 0-100
    val description: String,
    val evidence: List<String>
)

enum class AnomalyType {
    RAPID_MESSAGING,                 // Too many messages too fast
    UNUSUAL_TIMING,                  // Messages at odd hours
    PRESSURE_TACTICS,                // Urgency + short response time
    BOT_LIKE_BEHAVIOR,              // Consistent timing, no variation
    NEW_CONTACT_HIGH_URGENCY,       // First contact + urgent request
    COPY_PASTE_PATTERNS,            // Identical messages to multiple people
    VOICE_STRESS,                   // High stress in voice call
    BACKGROUND_NOISE_MISMATCH,      // Claims location but noise doesn't match
    SCRIPT_FOLLOWING                // Following a known scam script
}

data class MessageEvent(
    val senderId: String,
    val timestamp: Long,
    val messageLength: Int,
    val content: String?,            // Optional for privacy
    val responseTimeMs: Long? = null
)

data class CallEvent(
    val callerId: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val isIncoming: Boolean,
    val backgroundNoise: BackgroundNoiseProfile? = null
)

data class BackgroundNoiseProfile(
    val noiseLevel: Double,          // 0.0-1.0
    val hasVoices: Boolean,
    val hasTraffic: Boolean,
    val hasOfficeNoise: Boolean,
    val hasMusic: Boolean,
    val confidence: Float
)

@Singleton
class BehavioralAnalysisEngine @Inject constructor() {
    
    // Track behavior for each sender (thread-safe)
    private val behaviorProfiles = java.util.concurrent.ConcurrentHashMap<String, BehaviorProfile>()
    private val messageHistory = java.util.concurrent.ConcurrentHashMap<String, MutableList<MessageEvent>>()
    private val callHistory = java.util.concurrent.ConcurrentHashMap<String, MutableList<CallEvent>>()
    
    /**
     * Analyze message behavior for threats
     */
    suspend fun analyzeMessageBehavior(
        senderId: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ): List<BehaviorAnomaly> {
        val anomalies = mutableListOf<BehaviorAnomaly>()
        
        // Record the message
        val event = MessageEvent(
            senderId = senderId,
            timestamp = timestamp,
            messageLength = message.length,
            content = null // Privacy: don't store content
        )
        
        val senderMessages = messageHistory.getOrPut(senderId) { java.util.Collections.synchronizedList(mutableListOf()) }
        senderMessages.add(event)
        synchronized(senderMessages) {
            while (senderMessages.size > 200) {
                senderMessages.removeAt(0)
            }
        }
        
        // Get or create profile
        val profile = getOrCreateProfile(senderId)
        
        // Check for rapid messaging
        val rapidMessaging = detectRapidMessaging(senderId)
        if (rapidMessaging != null) {
            anomalies.add(rapidMessaging)
        }
        
        // Check for bot-like behavior
        val botBehavior = detectBotBehavior(senderId)
        if (botBehavior != null) {
            anomalies.add(botBehavior)
        }
        
        // Check for new contact + urgency
        if (isNewContact(profile) && detectsUrgency(message)) {
            anomalies.add(
                BehaviorAnomaly(
                    type = AnomalyType.NEW_CONTACT_HIGH_URGENCY,
                    severity = 80,
                    description = "First contact with urgent request",
                    evidence = listOf(
                        "No prior communication history",
                        "Message contains urgency indicators"
                    )
                )
            )
        }
        
        // Check for unusual timing
        val unusualTiming = detectUnusualTiming(timestamp)
        if (unusualTiming != null) {
            anomalies.add(unusualTiming)
        }
        
        // Update profile
        updateBehaviorProfile(senderId, event)
        
        return anomalies
    }
    
    /**
     * Analyze call behavior
     */
    suspend fun analyzeCallBehavior(
        callerId: String,
        durationSeconds: Int,
        isIncoming: Boolean,
        backgroundNoise: BackgroundNoiseProfile? = null,
        timestamp: Long = System.currentTimeMillis()
    ): List<BehaviorAnomaly> {
        val anomalies = mutableListOf<BehaviorAnomaly>()
        
        val event = CallEvent(
            callerId = callerId,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            isIncoming = isIncoming,
            backgroundNoise = backgroundNoise
        )
        
        val callerCalls = callHistory.getOrPut(callerId) { java.util.Collections.synchronizedList(mutableListOf()) }
        callerCalls.add(event)
        synchronized(callerCalls) {
            while (callerCalls.size > 200) {
                callerCalls.removeAt(0)
            }
        }
        
        // Analyze call patterns
        val callPatterns = analyzeCallPatterns(callerId)
        anomalies.addAll(callPatterns)
        
        // Analyze background noise if available
        if (backgroundNoise != null) {
            val noiseAnomaly = analyzeBackgroundNoise(backgroundNoise)
            if (noiseAnomaly != null) {
                anomalies.add(noiseAnomaly)
            }
        }
        
        return anomalies
    }
    
    /**
     * Get behavior profile for sender
     */
    fun getBehaviorProfile(senderId: String): BehaviorProfile? {
        return behaviorProfiles[senderId]
    }
    
    /**
     * Calculate trust score for sender
     */
    fun calculateTrustScore(senderId: String): Int {
        val profile = behaviorProfiles[senderId] ?: return 50 // Neutral for unknown
        
        var score = 50
        
        // Positive factors
        if (profile.firstContact < System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)) {
            score += 20 // Known for over 30 days
        }
        
        if (profile.messageCount > 10 && profile.suspiciousPatterns.isEmpty()) {
            score += 15 // Regular communication, no issues
        }
        
        // Negative factors
        score -= profile.suspiciousPatterns.size * 10
        
        if (profile.avgResponseTime < 1000 && profile.messageCount > 5) {
            score -= 15 // Too fast, possibly bot
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Detect if sender follows known scam script
     */
    fun detectScriptFollowing(messages: List<String>): BehaviorAnomaly? {
        // Common scam script patterns
        val scriptPatterns = listOf(
            listOf("urgent", "account", "verify"),
            listOf("winner", "prize", "claim"),
            listOf("suspended", "click", "immediately"),
            listOf("tax", "refund", "pending")
        )
        
        val messageText = messages.joinToString(" ").lowercase()
        
        scriptPatterns.forEach { pattern ->
            val matches = pattern.count { keyword -> messageText.contains(keyword) }
            if (matches >= 2) {
                return BehaviorAnomaly(
                    type = AnomalyType.SCRIPT_FOLLOWING,
                    severity = 70,
                    description = "Message follows known scam script pattern",
                    evidence = pattern.filter { messageText.contains(it) }
                )
            }
        }
        
        return null
    }
    
    // === PRIVATE DETECTION METHODS ===
    
    private fun detectRapidMessaging(senderId: String): BehaviorAnomaly? {
        val messages = messageHistory[senderId]?.toList() ?: return null
        if (messages.size < 3) return null
        
        // Check last 3 messages
        val recent = messages.takeLast(3)
        val timespan = recent.last().timestamp - recent.first().timestamp
        
        // More than 3 messages in 10 seconds = suspicious
        if (timespan < 10_000) {
            return BehaviorAnomaly(
                type = AnomalyType.RAPID_MESSAGING,
                severity = 60,
                description = "${recent.size} messages in ${timespan/1000} seconds",
                evidence = listOf("Possible automated bot", "Human typing would be slower")
            )
        }
        
        return null
    }
    
    private fun detectBotBehavior(senderId: String): BehaviorAnomaly? {
        val messages = messageHistory[senderId]?.toList() ?: return null
        if (messages.size < 5) return null
        
        // Calculate timing consistency
        val intervals = messages.zipWithNext { a, b -> b.timestamp - a.timestamp }
        if (intervals.isEmpty()) return null
        
        val avgInterval = intervals.average()
        val variance = intervals.map { abs(it - avgInterval) }.average()
        
        // Very consistent timing = bot-like
        if (variance < 1000 && avgInterval < 5000) {
            return BehaviorAnomaly(
                type = AnomalyType.BOT_LIKE_BEHAVIOR,
                severity = 70,
                description = "Unnaturally consistent message timing",
                evidence = listOf(
                    "Average interval: ${avgInterval.toLong()}ms",
                    "Low variance: ${variance.toLong()}ms"
                )
            )
        }
        
        return null
    }
    
    private fun detectUnusualTiming(timestamp: Long): BehaviorAnomaly? {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        // Messages between 2 AM and 5 AM are suspicious
        if (hour in 2..4) {
            return BehaviorAnomaly(
                type = AnomalyType.UNUSUAL_TIMING,
                severity = 40,
                description = "Message sent at unusual hour (${hour}:00)",
                evidence = listOf("Most legitimate contacts don't message at this hour")
            )
        }
        
        return null
    }
    
    private fun analyzeCallPatterns(callerId: String): List<BehaviorAnomaly> {
        val anomalies = mutableListOf<BehaviorAnomaly>()
        val calls = callHistory[callerId]?.toList() ?: return anomalies
        
        // Multiple short calls in succession = suspicious
        val recentCalls = calls.filter { 
            it.timestamp > System.currentTimeMillis() - (60 * 60 * 1000) 
        }
        
        if (recentCalls.size >= 3 && recentCalls.all { it.durationSeconds < 10 }) {
            anomalies.add(
                BehaviorAnomaly(
                    type = AnomalyType.PRESSURE_TACTICS,
                    severity = 75,
                    description = "${recentCalls.size} short calls in last hour",
                    evidence = listOf("Typical scammer harassment pattern")
                )
            )
        }
        
        return anomalies
    }
    
    private fun analyzeBackgroundNoise(noise: BackgroundNoiseProfile): BehaviorAnomaly? {
        // If caller claims to be from bank but has loud music/party noise
        if (noise.hasMusic && noise.noiseLevel > 0.7) {
            return BehaviorAnomaly(
                type = AnomalyType.BACKGROUND_NOISE_MISMATCH,
                severity = 60,
                description = "Background noise inconsistent with claimed identity",
                evidence = listOf("Loud music/party noise detected")
            )
        }
        
        return null
    }
    
    private fun detectsUrgency(message: String): Boolean {
        val urgencyWords = listOf(
            "urgent", "immediately", "now", "asap", "emergency",
            "expire", "deadline", "limited time", "act fast"
        )
        return urgencyWords.any { message.contains(it, ignoreCase = true) }
    }
    
    private fun isNewContact(profile: BehaviorProfile): Boolean {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return profile.firstContact > oneDayAgo && profile.messageCount <= 1
    }
    
    private fun getOrCreateProfile(senderId: String): BehaviorProfile {
        return behaviorProfiles.getOrPut(senderId) {
            BehaviorProfile(
                senderId = senderId,
                messageCount = 0,
                callCount = 0,
                avgResponseTime = 0,
                avgMessageLength = 0,
                activeHours = emptySet(),
                typingSpeed = null,
                suspiciousPatterns = emptyList(),
                trustScore = 50,
                firstContact = System.currentTimeMillis(),
                lastContact = System.currentTimeMillis()
            )
        }
    }
    
    private fun updateBehaviorProfile(senderId: String, event: MessageEvent) {
        val profile = behaviorProfiles[senderId] ?: return
        val messages = messageHistory[senderId]?.toList() ?: return
        
        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = event.timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)
        
        val updatedProfile = profile.copy(
            messageCount = messages.size,
            lastContact = event.timestamp,
            activeHours = profile.activeHours + hour,
            avgMessageLength = messages.map { it.messageLength }.average().toInt()
        )
        // Compute trust score and do a single write
        behaviorProfiles[senderId] = updatedProfile.copy(
            trustScore = calculateTrustScore(senderId)
        )
    }
}
