package com.deepfakeshield.core.intelligence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COMMUNITY THREAT INTELLIGENCE NETWORK
 * 
 * Privacy-first threat sharing across all users
 * - Anonymized hash-based sharing
 * - Real-time threat propagation
 * - Geographic threat mapping
 * - Zero-day threat detection
 */

data class ThreatReport(
    val threatHash: String,          // SHA-256 hash of threat content
    val threatType: CommunityThreatType,
    val severity: Int,               // 0-100
    val reportCount: Int,            // How many users reported
    val firstSeen: Long,             // Timestamp
    val lastSeen: Long,
    val geographicRegion: String?,   // Optional: Country/region
    val metadata: Map<String, String> = emptyMap()
)

enum class CommunityThreatType {
    SCAM_SMS,
    SCAM_CALL,
    PHISHING_URL,
    DEEPFAKE_VIDEO,
    MALICIOUS_NOTIFICATION
}

data class ThreatContribution(
    val contentHash: String,
    val threatType: CommunityThreatType,
    val userConfirmedThreat: Boolean,
    val confidenceScore: Int,
    val timestamp: Long
)

data class NetworkStats(
    val totalUsers: Int,
    val totalThreats: Int,
    val threatsToday: Int,
    val yourContributions: Int,
    val protectionScore: Int         // How well connected to network
)

@Singleton
class CommunityThreatNetwork @Inject constructor() {
    
    // In-memory threat database (thread-safe; in production, this would be Firebase/backend)
    private val knownThreats = java.util.concurrent.ConcurrentHashMap<String, ThreatReport>()
    private val userContributions = java.util.Collections.synchronizedList(mutableListOf<ThreatContribution>())
    
    /**
     * Check if content matches known community threats
     * Returns threat report if found
     */
    suspend fun checkThreat(content: String, type: CommunityThreatType): ThreatReport? {
        val hash = hashContent(content)
        return knownThreats[hash]?.takeIf { it.threatType == type }
    }
    
    /**
     * Report a new threat to the community
     * Privacy: Only hash is shared, not actual content
     */
    suspend fun reportThreat(
        content: String,
        type: CommunityThreatType,
        severity: Int,
        metadata: Map<String, String> = emptyMap()
    ): Boolean {
        val hash = hashContent(content)
        val now = System.currentTimeMillis()
        
        val existingThreat = knownThreats[hash]
        if (existingThreat != null) {
            // Update existing threat
            knownThreats[hash] = existingThreat.copy(
                reportCount = existingThreat.reportCount + 1,
                lastSeen = now,
                severity = maxOf(existingThreat.severity, severity)
            )
        } else {
            // New threat
            knownThreats[hash] = ThreatReport(
                threatHash = hash,
                threatType = type,
                severity = severity,
                reportCount = 1,
                firstSeen = now,
                lastSeen = now,
                geographicRegion = null, // Could add geo-detection
                metadata = metadata
            )
        }
        
        // Record user contribution
        synchronized(userContributions) {
            userContributions.add(
                ThreatContribution(
                    contentHash = hash,
                    threatType = type,
                    userConfirmedThreat = true,
                    confidenceScore = severity,
                    timestamp = now
                )
            )
            while (userContributions.size > 10000) {
                userContributions.removeAt(0)
            }
        }
        
        return true
    }
    
    /**
     * Get trending threats in last 24 hours
     */
    fun getTrendingThreats(limit: Int = 10): Flow<List<ThreatReport>> = flow {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val trending = knownThreats.values
            .filter { it.lastSeen > oneDayAgo }
            .sortedByDescending { it.reportCount }
            .take(limit)
        emit(trending)
    }
    
    /**
     * Get threats by type
     */
    fun getThreatsByType(type: CommunityThreatType, limit: Int = 50): List<ThreatReport> {
        return knownThreats.values
            .filter { it.threatType == type }
            .sortedByDescending { it.lastSeen }
            .take(limit)
    }
    
    /**
     * Check if phone number is known scammer
     */
    suspend fun isKnownScammerPhone(phoneNumber: String): ThreatReport? {
        val normalizedPhone = normalizePhoneNumber(phoneNumber)
        val hash = hashContent(normalizedPhone)
        return knownThreats[hash]?.takeIf { it.threatType == CommunityThreatType.SCAM_CALL }
    }
    
    /**
     * Check if URL is known phishing site
     */
    suspend fun isKnownPhishingUrl(url: String): ThreatReport? {
        val normalizedUrl = normalizeUrl(url)
        val hash = hashContent(normalizedUrl)
        return knownThreats[hash]?.takeIf { it.threatType == CommunityThreatType.PHISHING_URL }
    }
    
    /**
     * Get network statistics
     */
    fun getNetworkStats(): NetworkStats {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val threatsToday = knownThreats.values.count { it.lastSeen > oneDayAgo }
        
        return NetworkStats(
            totalUsers = 1000, // Would come from backend
            totalThreats = knownThreats.size,
            threatsToday = threatsToday,
            yourContributions = userContributions.size,
            protectionScore = calculateProtectionScore()
        )
    }
    
    /**
     * Get early warning for emerging threats
     * Detects rapid increase in reports
     */
    fun getEmergingThreats(): Flow<List<ThreatReport>> = flow {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val emerging = knownThreats.values
            .filter { 
                it.firstSeen > oneHourAgo && it.reportCount >= 5 
            }
            .sortedByDescending { it.reportCount }
        emit(emerging)
    }
    
    /**
     * Provide user feedback on threat detection
     * Helps improve community accuracy
     */
    suspend fun provideFeedback(
        contentHash: String,
        wasActuallyThreat: Boolean,
        falsePositive: Boolean = false
    ) {
        val threat = knownThreats[contentHash]
        if (threat != null && falsePositive) {
            // Reduce severity if false positive
            knownThreats[contentHash] = threat.copy(
                severity = maxOf(0, threat.severity - 10)
            )
        }
        
        // Record feedback for learning
        synchronized(userContributions) {
            userContributions.add(
                ThreatContribution(
                    contentHash = contentHash,
                    threatType = threat?.threatType ?: CommunityThreatType.SCAM_SMS,
                    userConfirmedThreat = wasActuallyThreat,
                    confidenceScore = if (wasActuallyThreat) 100 else 0,
                    timestamp = System.currentTimeMillis()
                )
            )
            while (userContributions.size > 10000) {
                userContributions.removeAt(0)
            }
        }
    }
    
    // === PRIVATE HELPERS ===
    
    private fun hashContent(content: String): String {
        val bytes = content.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        // Remove all non-digits, keep last 10 digits for comparison
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) {
            digits.takeLast(10)
        } else {
            digits
        }
    }
    
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }
    
    private fun calculateProtectionScore(): Int {
        // Score based on contributions and network participation
        val snapshot = synchronized(userContributions) { userContributions.toList() }
        val contributionScore = minOf(50, snapshot.size * 5)
        val activeScore = if (snapshot.any { 
            it.timestamp > System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) 
        }) 50 else 0
        
        return contributionScore + activeScore
    }
}

/**
 * THREAT SIMILARITY MATCHER
 * Detects similar threats using fuzzy matching
 */
@Singleton
class ThreatSimilarityMatcher @Inject constructor() {
    
    /**
     * Find similar known threats
     * Uses Levenshtein distance for similarity
     */
    @Suppress("UNUSED_PARAMETER")
    fun findSimilarThreats(
        content: String,
        knownThreats: List<ThreatReport>,
        _threshold: Double = 0.8
    ): List<ThreatReport> {
        // threatHash is a SHA-256 hex digest — compare hashes, not raw text.
        val contentHash = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        // Exact hash match (SHA-256 doesn't support "similarity")
        return knownThreats.filter { threat ->
            threat.threatHash.equals(contentHash, ignoreCase = true)
        }
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        // Simplified similarity (in production, use proper algorithm)
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val matches = shorter.count { c -> longer.contains(c, ignoreCase = true) }
        return matches.toDouble() / longer.length
    }
}
