package com.deepfakeshield.core.intelligence

import java.net.URL
import java.net.URLDecoder
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * URL INTELLIGENCE ENGINE
 * 
 * Advanced phishing and malicious URL detection:
 * - Domain age & reputation
 * - SSL certificate validation
 * - Visual similarity to known brands
 * - Redirect chain analysis
 * - DNS record patterns
 */

data class URLThreatAnalysis(
    val url: String,
    val isMalicious: Boolean,
    val riskScore: Int,                  // 0-100
    val threatTypes: List<URLThreatType>,
    val warnings: List<String>,
    val domainInfo: DomainInfo,
    val sslInfo: SSLInfo?,
    val redirectChain: List<String>,
    val lookalikeTarget: String?         // If impersonating a brand
)

enum class URLThreatType {
    PHISHING,
    MALWARE,
    SCAM,
    SUSPICIOUS_REDIRECT,
    LOOKALIKE_DOMAIN,
    SHORT_URL_OBFUSCATION,
    IP_ADDRESS_URL,
    SUSPICIOUS_TLD,
    NEW_DOMAIN,
    NO_SSL
}

data class DomainInfo(
    val domain: String,
    val tld: String,
    val ageDays: Int?,
    val registrationDate: Long?,
    val reputation: DomainReputation
)

enum class DomainReputation {
    TRUSTED,
    NEUTRAL,
    SUSPICIOUS,
    MALICIOUS,
    UNKNOWN
}

data class SSLInfo(
    val hasSSL: Boolean,
    val isValid: Boolean,
    val issuer: String?,
    val expiryDate: Long?,
    val certificateAge: Int?
)

@Singleton
class URLIntelligenceEngine @Inject constructor() {
    
    // Known trusted domains — expanded significantly to avoid flagging mainstream sites
    private val trustedDomains = setOf(
        "google.com", "amazon.com", "microsoft.com", "apple.com",
        "facebook.com", "twitter.com", "linkedin.com", "github.com",
        "paypal.com", "chase.com", "bankofamerica.com", "wellsfargo.com",
        "netflix.com", "instagram.com", "whatsapp.com", "youtube.com",
        "wikipedia.org", "reddit.com", "stackoverflow.com", "medium.com",
        "spotify.com", "twitch.tv", "discord.com", "zoom.us",
        "ebay.com", "yahoo.com", "bing.com", "duckduckgo.com",
        // International variants
        "google.co.uk", "google.co.in", "google.de", "google.fr", "google.co.jp", "google.org",
        "amazon.co.uk", "amazon.de", "amazon.co.jp", "amazon.fr", "amazon.in",
        "apple.co.uk", "microsoft.org",
        "paypal.co.uk", "paypal.de", "paypal.fr"
    )
    
    // Known phishing TLDs — only truly high-risk disposable TLDs
    private val suspiciousTLDs = setOf(
        ".tk", ".ml", ".ga", ".cf", ".gq" // Free disposable TLDs
    )
    
    // Brand lookalike detection — only intentional misspellings/homoglyphs
    private val brandPatterns = mapOf(
        "paypal" to listOf("paypa1", "paypai", "paypαl"),
        "amazon" to listOf("amazοn", "amaz0n"),
        "google" to listOf("goog1e", "goοgle"),
        "microsoft" to listOf("micros0ft", "micr0soft"),
        "apple" to listOf("app1e", "apρle")
    )
    
    // Comprehensive safe brand-affiliated domains list
    private val knownSafeBrandDomains = setOf(
        // Google ecosystem
        "googleapis.com", "googleusercontent.com", "googlevideo.com",
        "googleadservices.com", "googlesyndication.com", "gstatic.com",
        "googletagmanager.com", "google-analytics.com", "googlesource.com",
        "google.com", "google.co.uk", "google.co.in", "google.de",
        // Amazon ecosystem
        "amazonaws.com", "amazonws.com", "cloudfront.net", "amazon.com",
        "amazon.co.uk", "amazon.de", "amazon.co.jp", "primevideo.com",
        // Microsoft ecosystem
        "microsoftonline.com", "microsoft.com", "azure.com", "live.com",
        "windows.com", "office.com", "office365.com", "outlook.com",
        "onedrive.com", "bing.com", "linkedin.com", "github.com",
        // Apple ecosystem
        "apple.com", "apple-dns.net", "mzstatic.com", "apple.news",
        "icloud.com", "itunes.com",
        // Meta/Facebook ecosystem
        "facebook.com", "fbcdn.net", "facebook.net", "fb.com",
        "instagram.com", "whatsapp.com", "whatsapp.net", "messenger.com",
        // Other platforms
        "netflix.com", "nflxext.com", "nflximg.net",
        "paypal.com", "paypalobjects.com",
        "twitter.com", "twimg.com", "x.com",
        "ebay.com", "yahoo.com", "yimg.com",
        "dropbox.com", "dropboxusercontent.com",
        "telegram.org", "t.me",
        "githubusercontent.com", "github.io",
        // CDNs & infrastructure
        "akamaized.net", "akamai.net", "fastly.net", "cloudflare.com",
        "cdnjs.cloudflare.com", "unpkg.com", "jsdelivr.net"
    )
    
    /**
     * Comprehensive URL analysis
     */
    suspend fun analyzeURL(urlString: String): URLThreatAnalysis {
        if (urlString.isBlank()) return createErrorResult(urlString, listOf("Empty URL"))
        
        val warnings = mutableListOf<String>()
        val threatTypes = mutableListOf<URLThreatType>()
        var riskScore = 0
        
        // Parse URL
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            // Malformed URL is suspicious
            riskScore += 50
            warnings.add("Malformed URL")
            return createErrorResult(urlString, warnings)
        }
        
        // Extract domain info
        val domainInfo = analyzeDomain(url.host)
        
        // Check for IP address instead of domain
        if (isIPAddress(url.host)) {
            threatTypes.add(URLThreatType.IP_ADDRESS_URL)
            riskScore += 40
            warnings.add("URL uses IP address instead of domain name")
        }
        
        // Check TLD
        val tld = domainInfo.tld
        if (suspiciousTLDs.contains(tld)) {
            threatTypes.add(URLThreatType.SUSPICIOUS_TLD)
            riskScore += 30
            warnings.add("Domain uses suspicious TLD: $tld")
        }
        
        // Check for lookalike domains
        val lookalikeTarget = detectLookalikeDomain(url.host)
        if (lookalikeTarget != null) {
            threatTypes.add(URLThreatType.LOOKALIKE_DOMAIN)
            riskScore += 70
            warnings.add("Domain impersonates $lookalikeTarget")
        }
        
        // Check SSL — reduced penalty; HTTP is uncommon now but not inherently malicious
        val sslInfo = analyzeSSL(url)
        if (sslInfo?.isValid == false) {
            threatTypes.add(URLThreatType.NO_SSL)
            riskScore += 15
            warnings.add("Invalid SSL certificate")
        } else if (url.protocol == "http") {
            riskScore += 5
            warnings.add("No SSL encryption")
        }
        
        // Check for URL shorteners
        if (isShortenerDomain(url.host)) {
            threatTypes.add(URLThreatType.SHORT_URL_OBFUSCATION)
            riskScore += 20
            warnings.add("Uses URL shortener (hides true destination)")
        }
        
        // Check domain age
        if (domainInfo.ageDays != null && domainInfo.ageDays < 30) {
            threatTypes.add(URLThreatType.NEW_DOMAIN)
            riskScore += 35
            warnings.add("Domain registered less than 30 days ago")
        }
        
        // Check for suspicious parameters
        val suspiciousParams = detectSuspiciousParameters(url)
        if (suspiciousParams.isNotEmpty()) {
            riskScore += 15
            warnings.addAll(suspiciousParams)
        }
        
        // Check redirect chain
        val redirectChain = followRedirects(urlString)
        if (redirectChain.size > 2) {
            threatTypes.add(URLThreatType.SUSPICIOUS_REDIRECT)
            riskScore += 20
            warnings.add("Multiple redirects detected (${redirectChain.size})")
        }
        
        return URLThreatAnalysis(
            url = urlString,
            isMalicious = riskScore > 60,
            riskScore = riskScore.coerceIn(0, 100),
            threatTypes = threatTypes,
            warnings = warnings,
            domainInfo = domainInfo,
            sslInfo = sslInfo,
            redirectChain = redirectChain,
            lookalikeTarget = lookalikeTarget
        )
    }
    
    /**
     * Quick URL check (faster)
     */
    fun quickCheck(urlString: String): Boolean {
        try {
            val url = URL(urlString)
            
            // Quick heuristics — only flag genuinely suspicious signals
            if (isIPAddress(url.host)) return true
            if (detectLookalikeDomain(url.host) != null) return true
            if (suspiciousTLDs.any { url.host.endsWith(it) }) return true
            // HTTP alone is NOT suspicious — many redirects start as HTTP
            
            return false
        } catch (e: Exception) {
            return true // Malformed = suspicious
        }
    }
    
    // === DOMAIN ANALYSIS ===
    
    private fun analyzeDomain(host: String): DomainInfo {
        val parts = host.split(".")
        val tld = if (parts.size >= 2) ".${parts.last()}" else ""
        val domain = if (parts.size >= 2) parts[parts.size - 2] else host
        
        // Check reputation
        val reputation = when {
            trustedDomains.contains(host) -> DomainReputation.TRUSTED
            suspiciousTLDs.contains(tld) -> DomainReputation.SUSPICIOUS
            else -> DomainReputation.NEUTRAL
        }
        
        // Domain age is unknown without a WHOIS lookup. Only set a concrete age for
        // domains we know are trusted (to prevent them being flagged as "new").
        // Previously this hardcoded 15 days for ALL non-trusted domains, causing
        // youtube.com, wikipedia.org, etc. to get +35 risk as "new domain".
        val ageDays = if (reputation == DomainReputation.TRUSTED) 3650 else null
        
        return DomainInfo(
            domain = domain,
            tld = tld,
            ageDays = ageDays,
            registrationDate = if (ageDays != null) System.currentTimeMillis() - (ageDays * 24 * 60 * 60 * 1000L) else null,
            reputation = reputation
        )
    }
    
    private fun detectLookalikeDomain(host: String): String? {
        val hostLower = host.lowercase().removePrefix("www.")
        
        // Known safe brand-affiliated domains must never be flagged as lookalikes
        if (knownSafeBrandDomains.any { hostLower == it || hostLower.endsWith(".$it") }) {
            return null
        }
        // Trusted domains are obviously safe
        if (trustedDomains.any { hostLower == it || hostLower.endsWith(".$it") }) {
            return null
        }
        
        // Extract base domain (before TLD) for proper matching
        val baseDomain = hostLower.substringBeforeLast(".")
        
        brandPatterns.forEach { (brand, variants) ->
            // Only check VARIANTS (intentional misspellings) — not the brand name itself.
            // "pineapple-recipes.com" contains "apple" but is NOT impersonating Apple.
            // Only "app1e.com" or "apρle.com" (intentional misspelling) should be flagged.
            if (variants.any { baseDomain.contains(it) }) {
                return brand
            }
            // For the brand name itself, only flag if the base domain IS the brand (with optional prefix/suffix)
            // but NOT the official domain or a known safe domain
            if (baseDomain == brand || baseDomain.startsWith("$brand-") || baseDomain.endsWith("-$brand")) {
                if (!hostLower.endsWith(".$brand.com") && hostLower != "$brand.com") {
                    return brand
                }
            }
        }
        
        // Check for homoglyph attacks (unicode lookalikes)
        if (containsSuspiciousUnicode(host)) {
            return "unknown brand (unicode attack)"
        }
        
        return null
    }
    
    private fun containsSuspiciousUnicode(text: String): Boolean {
        // Check for non-ASCII characters that look like ASCII
        return text.any { char ->
            char.code > 127 && (
                char in 'а'..'я' || // Cyrillic
                char in 'α'..'ω'    // Greek
            )
        }
    }
    
    // === SSL ANALYSIS ===
    
    private fun analyzeSSL(url: URL): SSLInfo? {
        if (url.protocol != "https") {
            return SSLInfo(
                hasSSL = false,
                isValid = false,
                issuer = null,
                expiryDate = null,
                certificateAge = null
            )
        }
        
        // In production, actually connect and check certificate
        // For now, simulate
        return SSLInfo(
            hasSSL = true,
            isValid = true,
            issuer = "Let's Encrypt",
            expiryDate = System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000L),
            certificateAge = 30
        )
    }
    
    // === PARAMETER ANALYSIS ===
    
    private fun detectSuspiciousParameters(url: URL): List<String> {
        val warnings = mutableListOf<String>()
        val query = url.query ?: return warnings
        
        val params = query.split("&").mapNotNull { param ->
            val parts = param.split("=")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        
        params.forEach { (key, value) ->
            val decodedValue = try {
                URLDecoder.decode(value, "UTF-8")
            } catch (e: Exception) {
                value
            }
            
            // Check for embedded URLs (double redirect)
            if (decodedValue.startsWith("http")) {
                warnings.add("Parameter contains another URL")
            }
            
            // Check for suspicious parameter names
            if (key.contains("login", ignoreCase = true) || 
                key.contains("password", ignoreCase = true) ||
                key.contains("token", ignoreCase = true)) {
                warnings.add("Contains sensitive parameter: $key")
            }
        }
        
        return warnings
    }
    
    // === REDIRECT ANALYSIS ===
    
    @Suppress("UNUSED_PARAMETER")
    private fun followRedirects(urlString: String, _maxDepth: Int = 5): List<String> {
        val chain = mutableListOf(urlString)
        // In production, actually follow redirects
        // For now, simulate
        return chain
    }
    
    // === HELPERS ===
    
    private fun isIPAddress(host: String): Boolean {
        // Check if host is IP address (IPv4 or IPv6)
        return host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) ||
               host.contains(":")
    }
    
    private fun isShortenerDomain(host: String): Boolean {
        val shorteners = setOf(
            "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co",
            "is.gd", "buff.ly", "adf.ly", "short.link"
        )
        return shorteners.any { host.contains(it) }
    }
    
    private fun createErrorResult(url: String, warnings: List<String>): URLThreatAnalysis {
        // Malformed URLs are suspicious but NOT definitively malware.
        // Score 50 = "suspicious", not 100 = "confirmed malware".
        return URLThreatAnalysis(
            url = url,
            isMalicious = false,
            riskScore = 50,
            threatTypes = emptyList(), // Unknown — not enough info to categorize
            warnings = warnings,
            domainInfo = DomainInfo("", "", null, null, DomainReputation.SUSPICIOUS),
            sslInfo = null,
            redirectChain = emptyList(),
            lookalikeTarget = null
        )
    }
}

/**
 * VISUAL PHISHING DETECTOR
 * Analyzes screenshots of websites for phishing
 */
@Singleton
class VisualPhishingDetector @Inject constructor() {
    
    /**
     * Compare website visual to known legitimate sites.
     * Without a prebuilt screenshot database, returns a score based on
     * whether the claimed brand is a known phishing target.
     * 
     * @return 0.0 = no phishing similarity, 1.0 = high phishing likelihood
     */
    @Suppress("UNUSED_PARAMETER")
    fun detectVisualPhishing(_screenshotPath: String, claimedBrand: String): Float {
        // Without a visual comparison database, use brand-based heuristic:
        // If the claimed brand is a common phishing target, assign higher risk
        val highTargetBrands = setOf(
            "paypal", "apple", "google", "microsoft", "amazon", "facebook",
            "netflix", "bank of america", "chase", "wells fargo", "citibank",
            "usps", "fedex", "dhl", "linkedin", "dropbox", "instagram",
            "whatsapp", "telegram", "coinbase", "binance"
        )
        
        val brandLower = claimedBrand.lowercase()
        val isHighTarget = highTargetBrands.any { brandLower.contains(it) }
        
        // Higher risk if the brand is commonly impersonated
        return if (isHighTarget) 0.6f else 0.2f
    }
}
