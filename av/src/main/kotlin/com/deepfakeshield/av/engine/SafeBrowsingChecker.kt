package com.deepfakeshield.av.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local URL safety checker — all analysis is performed on-device using
 * heuristic pattern matching. No external API calls, no API keys required.
 */
@Singleton
class SafeBrowsingChecker @Inject constructor() {

    data class SafeBrowsingResult(val isThreat: Boolean, val threatTypes: List<String>)

    fun isAvailable(): Boolean = true

    suspend fun checkUrl(url: String): SafeBrowsingResult {
        val lower = url.lowercase()
        val threats = mutableListOf<String>()

        if (PHISHING_PATTERNS.any { lower.contains(it) }) threats.add("SOCIAL_ENGINEERING")
        if (MALWARE_TLDS.any { lower.endsWith(it) }) threats.add("MALWARE")
        if (SUSPICIOUS_PATTERNS.any { lower.contains(it) }) threats.add("UNWANTED_SOFTWARE")

        val domain = lower.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore("/").substringBefore("?")
        if (domain.count { it == '.' } > 4) threats.add("SOCIAL_ENGINEERING")
        if (domain.contains("--") || domain.matches(Regex(".*\\d{5,}.*"))) threats.add("POTENTIALLY_HARMFUL")
        if (IP_URL_PATTERN.matches(lower)) threats.add("SOCIAL_ENGINEERING")
        if (KNOWN_SAFE_DOMAINS.any { domain == it || domain.endsWith(".$it") }) threats.clear()

        return SafeBrowsingResult(threats.isNotEmpty(), threats.distinct())
    }

    companion object {
        private val PHISHING_PATTERNS = listOf(
            "login-verify", "account-confirm", "secure-update", "signin-alert",
            "password-reset.", "verify-account.", "confirm-identity.",
            "paypal-secure", "apple-id-verify", "amazon-alert",
            "bank-secure", "netflix-update", ".tk/login", ".ml/secure",
            "bit.ly/", "tinyurl.com/", "0rn.co/", "x.co/"
        )
        private val MALWARE_TLDS = listOf(
            ".xyz/", ".top/", ".club/", ".work/", ".date/",
            ".racing/", ".download/", ".stream/", ".gq/", ".cf/"
        )
        private val SUSPICIOUS_PATTERNS = listOf(
            "free-prize", "winner-claim", "urgent-action",
            "crypto-airdrop", "double-bitcoin", "free-iphone",
            "cheap-meds", "pharmacy-discount"
        )
        private val IP_URL_PATTERN = Regex("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")
        private val KNOWN_SAFE_DOMAINS = setOf(
            "google.com", "youtube.com", "facebook.com", "amazon.com",
            "apple.com", "microsoft.com", "github.com", "wikipedia.org",
            "twitter.com", "linkedin.com", "instagram.com", "reddit.com",
            "netflix.com", "paypal.com", "stackoverflow.com"
        )
    }
}
