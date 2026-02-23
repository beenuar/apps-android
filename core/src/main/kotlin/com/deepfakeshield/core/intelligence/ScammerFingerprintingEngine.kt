package com.deepfakeshield.core.intelligence

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SCAMMER FINGERPRINTING ENGINE
 * 
 * Identifies and tracks scammers across multiple platforms
 * - Device fingerprinting
 * - Behavioral signatures
 * - Cross-platform tracking
 * - Campaign detection
 */

data class ScammerFingerprint(
    val fingerprintId: String,
    val phoneNumbers: Set<String>,
    val deviceSignatures: Set<String>,
    val writingStyleHash: String,
    val activeHours: Set<Int>,
    val targetedVictims: Int,
    val campaignIds: Set<String>,
    val confidence: Float,
    val firstSeen: Long,
    val lastSeen: Long
)

data class ScamCampaign(
    val campaignId: String,
    val campaignType: String,
    val affectedUsers: Int,
    val scammerCount: Int,
    val messages: List<String>,
    val commonPatterns: List<String>,
    val startDate: Long,
    val isActive: Boolean
)

@Singleton
class ScammerFingerprintingEngine @Inject constructor() {
    
    private val knownScammers = java.util.concurrent.ConcurrentHashMap<String, ScammerFingerprint>()
    private val activeCampaigns = java.util.concurrent.ConcurrentHashMap<String, ScamCampaign>()
    
    /**
     * Generate fingerprint from contact
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateFingerprint(
        phoneNumber: String?,
        messageContent: String?,
        _metadata: Map<String, String> = emptyMap()
    ): ScammerFingerprint? {
        if (phoneNumber == null && messageContent == null) return null
        
        // Create fingerprint
        val writingStyle = messageContent?.let { analyzeWritingStyle(it) } ?: ""
        val fingerprintId = generateFingerprintId(phoneNumber, writingStyle)
        
        // Check if scammer already known
        val existing = knownScammers[fingerprintId]
        if (existing != null) {
            // Update existing
            knownScammers[fingerprintId] = existing.copy(
                phoneNumbers = phoneNumber?.let { existing.phoneNumbers + it } ?: existing.phoneNumbers,
                lastSeen = System.currentTimeMillis(),
                targetedVictims = existing.targetedVictims + 1
            )
            return knownScammers[fingerprintId]
        }
        
        // New scammer
        val newFingerprint = ScammerFingerprint(
            fingerprintId = fingerprintId,
            phoneNumbers = phoneNumber?.let { setOf(it) } ?: emptySet(),
            deviceSignatures = emptySet(),
            writingStyleHash = writingStyle,
            activeHours = setOf(getCurrentHour()),
            targetedVictims = 1,
            campaignIds = emptySet(),
            confidence = 0.6f,
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )
        
        knownScammers[fingerprintId] = newFingerprint
        return newFingerprint
    }
    
    /**
     * Check if contact matches known scammer
     */
    fun isKnownScammer(phoneNumber: String): ScammerFingerprint? {
        return knownScammers.values.find { 
            phoneNumber in it.phoneNumbers 
        }
    }
    
    /**
     * Detect coordinated scam campaigns
     */
    suspend fun detectCampaign(messages: List<Pair<String, String>>): ScamCampaign? {
        if (messages.size < 3) return null
        
        // Find common patterns
        val commonPatterns = findCommonPatterns(messages.map { it.second })
        if (commonPatterns.isEmpty()) return null
        
        // Generate campaign ID
        val campaignId = hashPatterns(commonPatterns)
        
        // Check if campaign exists
        val existing = activeCampaigns[campaignId]
        if (existing != null) {
            activeCampaigns[campaignId] = existing.copy(
                affectedUsers = existing.affectedUsers + 1,
                messages = (existing.messages + messages.map { it.second }).distinct()
            )
            return activeCampaigns[campaignId]
        }
        
        // New campaign
        val campaign = ScamCampaign(
            campaignId = campaignId,
            campaignType = inferCampaignType(commonPatterns),
            affectedUsers = 1,
            scammerCount = messages.map { it.first }.distinct().size,
            messages = messages.map { it.second },
            commonPatterns = commonPatterns,
            startDate = System.currentTimeMillis(),
            isActive = true
        )
        
        activeCampaigns[campaignId] = campaign
        return campaign
    }
    
    /**
     * Link scammers using similar tactics
     */
    fun findRelatedScammers(fingerprintId: String): List<ScammerFingerprint> {
        val target = knownScammers[fingerprintId] ?: return emptyList()
        
        return knownScammers.values.filter { scammer ->
            scammer.fingerprintId != fingerprintId &&
            (
                // Similar writing style
                calculateSimilarity(scammer.writingStyleHash, target.writingStyleHash) > 0.7 ||
                // Same campaign
                scammer.campaignIds.intersect(target.campaignIds).isNotEmpty() ||
                // Similar active hours
                scammer.activeHours.intersect(target.activeHours).size >= 3
            )
        }
    }
    
    // === PRIVATE METHODS ===
    
    private fun analyzeWritingStyle(text: String): String {
        // Create hash of writing characteristics
        val characteristics = listOf(
            text.length.toString(),
            text.count { it.isUpperCase() }.toString(),
            text.count { it == '!' }.toString(),
            text.split(" ").size.toString(),
            if (text.contains("urgent", ignoreCase = true)) "1" else "0"
        )
        
        return hash(characteristics.joinToString(":"))
    }
    
    private fun generateFingerprintId(phoneNumber: String?, writingStyle: String): String {
        val components = listOfNotNull(
            phoneNumber?.takeLast(4),
            writingStyle.take(8)
        )
        return hash(components.joinToString(":"))
    }
    
    private fun findCommonPatterns(messages: List<String>): List<String> {
        val patterns = mutableListOf<String>()
        
        // Find common phrases (3+ words)
        val allPhrases = messages.flatMap { message ->
            val words = message.lowercase().split(Regex("\\s+"))
            words.windowed(3).map { it.joinToString(" ") }
        }
        
        val phraseCounts = allPhrases.groupingBy { it }.eachCount()
        patterns.addAll(
            phraseCounts.filter { it.value >= maxOf(2, messages.size / 2) }.keys
        )
        
        return patterns
    }
    
    private fun inferCampaignType(patterns: List<String>): String {
        val allText = patterns.joinToString(" ").lowercase()
        return when {
            allText.contains("bank") || allText.contains("account") -> "banking_scam"
            allText.contains("prize") || allText.contains("winner") -> "prize_scam"
            allText.contains("tax") || allText.contains("refund") -> "tax_scam"
            allText.contains("package") || allText.contains("delivery") -> "delivery_scam"
            else -> "unknown"
        }
    }
    
    private fun calculateSimilarity(hash1: String, hash2: String): Double {
        val common = hash1.zip(hash2).count { it.first == it.second }
        return common.toDouble() / maxOf(hash1.length, hash2.length)
    }
    
    private fun getCurrentHour(): Int {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }
    
    private fun hash(input: String): String {
        val bytes = input.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    private fun hashPatterns(patterns: List<String>): String {
        return hash(patterns.sorted().joinToString("|"))
    }
}
