package com.deepfakeshield.data.repository

import com.deepfakeshield.data.entity.CommunityThreatEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Community Threat Intelligence Database
 * 
 * Maintains a local database of known threats shared by the community.
 * All data is hashed for privacy - no raw content is stored.
 */
@Singleton
class CommunityThreatRepository @Inject constructor(
    private val communityThreatDao: com.deepfakeshield.data.dao.CommunityThreatDao
) {
    
    private val _threatDatabase = MutableStateFlow<Map<String, CommunityThreatInfo>>(
        buildDefaultThreatDatabase()
    )
    val threatDatabase = _threatDatabase.asStateFlow()
    
    // In-memory cache backed by Room persistence
    private val _reportedNumbers = MutableStateFlow<Set<String>>(emptySet())
    val reportedNumbers = _reportedNumbers.asStateFlow()
    
    private val _reportedDomains = MutableStateFlow<Set<String>>(emptySet())
    val reportedDomains = _reportedDomains.asStateFlow()
    
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    
    // Load persisted reports on init
    init {
        scope.launch {
            try {
                val phoneThreats = communityThreatDao.getThreatsByType("phone", 1000)
                _reportedNumbers.value = phoneThreats.map { it.threatHash }.toSet()
                
                val domainThreats = communityThreatDao.getThreatsByType("domain", 1000)
                _reportedDomains.value = domainThreats.map { it.threatHash }.toSet()
                
                android.util.Log.i("CommunityThreat", "Loaded ${phoneThreats.size} phone + ${domainThreats.size} domain reports from DB")
            } catch (e: Exception) {
                android.util.Log.w("CommunityThreat", "Failed to load persisted reports: ${e.message}")
            }
        }
    }
    
    data class CommunityThreatInfo(
        val hash: String,
        val threatType: String,
        val severity: Int,
        val reportCount: Int,
        val firstSeen: Long,
        val lastSeen: Long,
        val region: String? = null
    )
    
    /**
     * Check if a phone number has been reported
     */
    fun checkPhoneNumber(phoneNumber: String): ThreatReport? {
        val normalized = normalizePhoneNumber(phoneNumber)
        val hash = sha256(normalized)
        
        val knownScamPrefixes = listOf(
            "+1900", "+1976", "+44870", "+44871", "+44872",
            "+23481", "+23480", "+23490", "+2349",
            "+91800", "+91900"
        )
        
        if (knownScamPrefixes.any { normalized.startsWith(it) }) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 70,
                reportCount = 100,
                category = "Known scam prefix",
                recommendation = "This number uses a prefix commonly associated with scam operations."
            )
        }
        
        if (_reportedNumbers.value.contains(hash)) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 80,
                reportCount = 5,
                category = "User reported",
                recommendation = "This number has been reported by users as suspicious."
            )
        }
        
        return null
    }
    
    // Infrastructure domains the app relies on — must never be flagged.
    // Mirrors the same protection in DomainReputationRepository.
    private val protectedInfrastructureDomains = setOf(
        "googleapis.com",          // Google infrastructure
        "osint.digitalside.it",    // Threat intelligence feed
        "githubusercontent.com",   // Threat intelligence feed (GitHub raw)
        "raw.githubusercontent.com",
        "virustotal.com",          // Security vendor
        "phishtank.org",           // Phishing feed
        "openphish.com",           // Phishing feed
        "urlhaus-api.abuse.ch",    // Malware URL feed
        "urlhaus.abuse.ch"         // URLhaus feed
    )

    private fun isProtectedDomain(domain: String): Boolean {
        val lower = domain.lowercase()
        return protectedInfrastructureDomains.any { lower == it || lower.endsWith(".$it") }
    }

    /**
     * Check if a domain/URL is known to be malicious
     */
    fun checkDomain(url: String): ThreatReport? {
        val domain = extractDomain(url)

        // Never flag infrastructure domains the app depends on
        if (isProtectedDomain(domain)) return null

        val hash = sha256(domain)
        
        val knownMaliciousTlds = listOf(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".top", ".xyz",
            ".work", ".click", ".loan", ".date", ".racing"
        )
        
        if (knownMaliciousTlds.any { domain.endsWith(it) }) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 60,
                reportCount = 50,
                category = "Suspicious TLD",
                recommendation = "This domain uses a top-level domain frequently associated with scams."
            )
        }
        
        val knownPhishingPatterns = listOf(
            "login-", "-verify", "-secure", "-update", "-confirm",
            "account-", "banking-", "paypal-", "amazon-", "apple-"
        )
        
        if (knownPhishingPatterns.any { domain.contains(it) }) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 75,
                reportCount = 200,
                category = "Phishing pattern",
                recommendation = "This domain matches known phishing URL patterns."
            )
        }
        
        if (_reportedDomains.value.contains(hash)) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 85,
                reportCount = 10,
                category = "User reported",
                recommendation = "This domain has been reported by users as malicious."
            )
        }
        
        return null
    }
    
    /**
     * Check text content against known scam templates
     */
    fun checkMessageTemplate(text: String): ThreatReport? {
        val normalizedText = text.lowercase().trim()
        
        val knownScamTemplates = listOf(
            "your account has been compromised",
            "you have won a prize",
            "click here to verify your account",
            "your package could not be delivered",
            "unusual activity detected on your account",
            "confirm your identity to avoid suspension",
            "your tax refund is ready",
            "you have been selected for a special offer",
            "your social security number has been compromised",
            "send your otp to verify",
            "your payment of",
            "dear customer your",
            "we have detected suspicious",
            "your bank account will be",
            "click the link below to",
            "reply with your",
            "share your verification code"
        )
        
        val matchedTemplates = knownScamTemplates.filter { normalizedText.contains(it) }
        
        if (matchedTemplates.isNotEmpty()) {
            return ThreatReport(
                isKnownThreat = true,
                threatLevel = 85,
                reportCount = matchedTemplates.size * 50,
                category = "Known scam template",
                recommendation = "This message matches ${matchedTemplates.size} known scam template(s). Do not respond or click any links."
            )
        }
        
        return null
    }
    
    /**
     * Report a phone number as suspicious
     */
    fun reportPhoneNumber(phoneNumber: String) {
        val hash = sha256(normalizePhoneNumber(phoneNumber))
        // Persist first, then update in-memory. Revert on failure.
        scope.launch {
            try {
                val existing = communityThreatDao.getThreatByHash(hash)
                if (existing != null) {
                    communityThreatDao.insert(existing.copy(
                        reportCount = existing.reportCount + 1,
                        lastSeen = System.currentTimeMillis()
                    ))
                } else {
                    communityThreatDao.insert(CommunityThreatEntity(
                        threatHash = hash,
                        threatType = "phone",
                        severity = 70,
                        reportCount = 1,
                        firstSeen = System.currentTimeMillis(),
                        lastSeen = System.currentTimeMillis(),
                        geographicRegion = null,
                        metadata = null
                    ))
                }
                // Only update in-memory after successful persistence
                _reportedNumbers.update { it + hash }
            } catch (e: Exception) {
                android.util.Log.w("CommunityThreat", "Failed to persist phone report: ${e.message}")
            }
        }
    }
    
    /**
     * Report a domain as suspicious  
     */
    fun reportDomain(url: String) {
        val domain = extractDomain(url)
        // Never allow reporting infrastructure domains the app depends on
        if (isProtectedDomain(domain)) return
        val hash = sha256(domain)
        // Persist first, then update in-memory. Revert on failure.
        scope.launch {
            try {
                val existing = communityThreatDao.getThreatByHash(hash)
                if (existing != null) {
                    communityThreatDao.insert(existing.copy(
                        reportCount = existing.reportCount + 1,
                        lastSeen = System.currentTimeMillis()
                    ))
                } else {
                    communityThreatDao.insert(CommunityThreatEntity(
                        threatHash = hash,
                        threatType = "domain",
                        severity = 75,
                        reportCount = 1,
                        firstSeen = System.currentTimeMillis(),
                        lastSeen = System.currentTimeMillis(),
                        geographicRegion = null,
                        metadata = null
                    ))
                }
                // Only update in-memory after successful persistence
                _reportedDomains.update { it + hash }
            } catch (e: Exception) {
                android.util.Log.w("CommunityThreat", "Failed to persist domain report: ${e.message}")
            }
        }
    }
    
    fun getThreatStats(): ThreatStats {
        return ThreatStats(
            knownScamPrefixes = 11,
            knownMaliciousTlds = 12,
            knownPhishingPatterns = 10,
            knownScamTemplates = 17,
            userReportedNumbers = _reportedNumbers.value.size,
            userReportedDomains = _reportedDomains.value.size,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^+\\d]"), "")
    }
    
    private fun extractDomain(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .substringAfterLast("@")  // Strip userinfo to prevent bypass (e.g. paypal.com@evil.com)
            .substringBefore(":")
            .removePrefix("www.")
    }
    
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun buildDefaultThreatDatabase(): Map<String, CommunityThreatInfo> {
        return emptyMap()
    }
    
    data class ThreatReport(
        val isKnownThreat: Boolean,
        val threatLevel: Int,
        val reportCount: Int,
        val category: String,
        val recommendation: String
    )
    
    data class ThreatStats(
        val knownScamPrefixes: Int,
        val knownMaliciousTlds: Int,
        val knownPhishingPatterns: Int,
        val knownScamTemplates: Int,
        val userReportedNumbers: Int,
        val userReportedDomains: Int,
        val lastUpdated: Long
    )
}
