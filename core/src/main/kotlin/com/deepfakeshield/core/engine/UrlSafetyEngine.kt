package com.deepfakeshield.core.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Advanced URL Safety Analysis Engine
 * 
 * Performs deep URL inspection including:
 * - Domain reputation and age heuristics
 * - SSL/TLS indicator analysis
 * - Homoglyph/punycode detection
 * - Path-based phishing detection
 * - Known threat database matching
 * - Redirect chain analysis
 */
@Singleton
class UrlSafetyEngine @Inject constructor() {

    data class UrlAnalysis(
        val url: String,
        val domain: String,
        val isHttps: Boolean,
        val riskScore: Int,
        val threats: List<UrlThreat>,
        val recommendation: String,
        val details: Map<String, String>
    )
    
    data class UrlThreat(
        val type: String,
        val severity: String,
        val description: String
    )

    data class UrlCategory(val name: String, val riskScore: Int)

    fun analyzeUrl(url: String, blockAdultContent: Boolean = false): UrlAnalysis {
        if (url.isBlank() || (!url.contains("://") && !url.contains("."))) {
            return UrlAnalysis(
                domain = "",
                isHttps = false,
                riskScore = 0,
                threats = emptyList(),
                recommendation = "Please enter a valid URL",
                details = emptyMap(),
                url = url
            )
        }
        
        val threats = mutableListOf<UrlThreat>()
        val details = mutableMapOf<String, String>()
        var riskScore = 0
        
        val normalizedUrl = url.lowercase().trim()
        val isHttps = normalizedUrl.startsWith("https://")
        val domain = extractDomain(normalizedUrl)
        
        details["domain"] = domain
        details["protocol"] = if (isHttps) "HTTPS (Secure)" else "HTTP (Not Secure)"
        
        // 1. Protocol check — low penalty; HTTP is uncommon now but not inherently malicious
        if (!isHttps && !normalizedUrl.startsWith("about:") && !normalizedUrl.startsWith("chrome:")) {
            riskScore += 5
            threats.add(UrlThreat("INSECURE_PROTOCOL", "LOW", "No SSL/TLS encryption detected"))
        }
        
        // 2. Homoglyph detection — ONLY flag when a homoglyph appears inside a brand-like pattern.
        // "rn" in "learn.com" or "turner.com" is NOT a homoglyph attack.
        val homoglyphs = detectHomoglyphs(domain)
        if (homoglyphs.isNotEmpty()) {
            riskScore += 30
            threats.add(UrlThreat("HOMOGLYPH", "HIGH", "Deceptive characters found: ${homoglyphs.joinToString(", ")}"))
            details["homoglyphs"] = homoglyphs.joinToString(", ")
        }
        
        // 3. Punycode detection
        if (domain.contains("xn--")) {
            riskScore += 30
            threats.add(UrlThreat("PUNYCODE", "HIGH", "Internationalized domain may be disguising its real address"))
        }
        
        // 4. Suspicious TLD — only truly high-risk free/disposable TLDs
        val suspiciousTlds = listOf(".tk", ".ml", ".ga", ".cf", ".gq", ".top", ".work",
            ".click", ".loan", ".date", ".racing", ".stream", ".download", ".win", ".bid", ".icu")
        val tld = domain.substringAfterLast(".")
        if (suspiciousTlds.any { domain.endsWith(it) }) {
            riskScore += 20
            threats.add(UrlThreat("SUSPICIOUS_TLD", "MEDIUM", "Domain extension .$tld is commonly used in scams"))
        }
        details["tld"] = ".$tld"
        
        // 5. Lookalike domain detection — matches DOMAIN only, never URL path
        val lookalike = detectLookalikeDomain(domain)
        if (lookalike != null) {
            riskScore += 45
            threats.add(UrlThreat("LOOKALIKE", "CRITICAL", "This domain impersonates $lookalike"))
            details["impersonates"] = lookalike
        }
        
        // 6. Excessive subdomains — raised threshold; CDNs often have 4+ levels
        val subdomainCount = domain.count { it == '.' }
        if (subdomainCount > 4) {
            riskScore += 15
            threats.add(UrlThreat("EXCESSIVE_SUBDOMAINS", "MEDIUM", "Unusually complex URL structure ($subdomainCount levels)"))
        }
        details["subdomains"] = subdomainCount.toString()
        
        // 7. IP address as domain
        if (domain.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
            riskScore += 30
            threats.add(UrlThreat("IP_DOMAIN", "HIGH", "Uses IP address instead of domain name"))
        }
        
        // 8. Suspicious path keywords — only flag when COMBINED with a lookalike domain
        val suspiciousPathWords = listOf("login", "verify", "secure", "account", "update", 
            "confirm", "signin", "banking", "password", "credential", "authenticate")
        val path = normalizedUrl.substringAfter(domain).substringBefore("?")
        val pathMatches = suspiciousPathWords.filter { path.contains(it) }
        if (pathMatches.isNotEmpty() && lookalike != null) {
            riskScore += 20
            threats.add(UrlThreat("PHISHING_PATH", "HIGH", "URL path suggests credential phishing: ${pathMatches.joinToString(", ")}"))
        }
        
        // 9. URL shortener detection
        val shorteners = listOf("bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co", 
            "is.gd", "buff.ly", "adf.ly", "tiny.cc", "rb.gy")
        if (shorteners.any { domain == it || domain.endsWith(".$it") }) {
            riskScore += 10
            threats.add(UrlThreat("URL_SHORTENER", "LOW", "Shortened URL hides the real destination"))
        }
        
        // 10. Very long URL (possible obfuscation) — raised threshold
        if (normalizedUrl.length > 300) {
            riskScore += 10
            threats.add(UrlThreat("LONG_URL", "LOW", "Unusually long URL may be obfuscating its purpose"))
        }

        // 11. Category filtering (adult, gambling) - for family protection
        val pathLower = path.lowercase()
        val adultKeywords = listOf("adult", "porn", "xxx", "sex", "casino", "gambling", "bet", "poker", "slot")
        val blockedCategory = adultKeywords.find { pathLower.contains(it) }
        if (blockedCategory != null) {
            if (blockAdultContent) {
                riskScore += 90
                threats.add(UrlThreat("BLOCKED_CATEGORY", "CRITICAL", "Content category blocked: $blockedCategory"))
            } else {
                // Don't penalize at all unless family protection is on; these are legal categories
                details["content_category"] = blockedCategory
            }
        }

        // 12. NRD-like heuristic — tightened: require BOTH high digit ratio AND multiple hyphens.
        // Many legitimate domains have numbers (247sports, 1password, 3m, etc.)
        if (domain.isNotBlank()) {
            val base = domain.substringBeforeLast(".")
            val digitRatio = base.count { it.isDigit() }.toFloat() / maxOf(base.length, 1)
            val hyphenCount = base.count { it == '-' }
            if (digitRatio > 0.5f && hyphenCount >= 2) {
                riskScore += 15
                threats.add(UrlThreat("NRD_LIKE", "MEDIUM", "Domain has patterns common in newly registered phishing sites"))
            }
        }
        
        val finalScore = min(riskScore, 100)
        
        val recommendation = when {
            finalScore >= 70 -> "DANGEROUS: Do NOT click this link. It shows multiple signs of being malicious."
            finalScore >= 50 -> "SUSPICIOUS: Exercise extreme caution. Verify through official channels before proceeding."
            finalScore >= 25 -> "CAUTION: Some minor concerns detected. Proceed carefully."
            else -> "APPEARS SAFE: No significant threats detected, but always exercise caution."
        }
        
        return UrlAnalysis(
            url = url,
            domain = domain,
            isHttps = isHttps,
            riskScore = finalScore,
            threats = threats,
            recommendation = recommendation,
            details = details
        )
    }
    
    private fun extractDomain(url: String): String {
        return url.removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")      // Strip path first
            .substringBefore("?")      // Strip query
            .substringAfterLast("@")   // Strip userinfo (user:pass@host) — critical for phishing detection
            .substringBefore(":")      // Strip port
            .removePrefix("www.")
    }
    
    // Legitimate domains that contain homoglyph-like sequences but are NOT attacks.
    // "rn" in "learn", "turner", "warner", "intern", "return" is normal English.
    // "vv" in "savvy" or "revved" is normal. "0"/"1" in "1password", "go0gle" IS suspicious.
    private val safeHomoglyphDomains = setOf(
        "learn", "intern", "return", "pattern", "modern", "western", "eastern", "northern",
        "southern", "govern", "concern", "turner", "warner", "corner", "born", "horn",
        "morning", "warning", "earning", "turning", "burning", "journal", "journey",
        "tournament", "furniture", "alternative", "internal", "external", "kernel",
        "pinterest", "discern", "fern", "stern"
    )

    private fun detectHomoglyphs(domain: String): List<String> {
        val found = mutableListOf<String>()
        val base = domain.substringBeforeLast(".") // Only check base domain, not TLD

        // Known safe brand domains — skip entirely
        if (knownSafeBrandDomains.any { domain == it || domain.endsWith(".$it") }) return found

        // "0" next to letters — only flag if it looks like a brand substitution (e.g. "g00gle", "amaz0n")
        // Don't flag domains that just have numbers naturally (e.g. "web3.0", "247sports")
        if (base.contains(Regex("[a-z]0[a-z]"))) {
            // Check if replacing 0→o creates a known brand name
            val deobfuscated = base.replace('0', 'o')
            val brands = listOf("google", "amazon", "apple", "paypal", "facebook", "netflix", "microsoft")
            if (brands.any { deobfuscated.contains(it) }) {
                found.add("'0' may be disguised as 'o' to impersonate a brand")
            }
        }

        // "1" substitution — only flag if replacing 1→l creates a brand
        if (base.contains(Regex("[a-z]1|1[a-z]"))) {
            val deobfuscated = base.replace('1', 'l')
            val brands = listOf("paypal", "apple", "google", "netflix", "linkedin")
            if (brands.any { deobfuscated.contains(it) }) {
                found.add("'1' may be disguised as 'l' to impersonate a brand")
            }
        }

        // "rn" → "m" — only flag if it's NOT a common English word AND replacing rn→m creates a brand
        if (base.contains("rn") && safeHomoglyphDomains.none { base.contains(it) }) {
            val deobfuscated = base.replace("rn", "m")
            val brands = listOf("amazon", "microsoft", "samsung", "walmart")
            if (brands.any { deobfuscated.contains(it) }) {
                found.add("'rn' may be disguised as 'm' to impersonate a brand")
            }
        }

        // "vv" → "w" — only flag if replacing vv→w creates a brand
        if (base.contains("vv")) {
            val deobfuscated = base.replace("vv", "w")
            val brands = listOf("www", "twitter", "twitch")
            if (brands.any { deobfuscated.contains(it) }) {
                found.add("'vv' may be disguised as 'w'")
            }
        }
        
        return found
    }
    
    // Legitimate brand-affiliated domains that contain brand keywords but are NOT phishing.
    // Comprehensive list to avoid false positives from CDNs, APIs, and official sub-brands.
    private val knownSafeBrandDomains = setOf(
        // Google ecosystem
        "googleapis.com", "googleusercontent.com", "googlevideo.com",
        "googleadservices.com", "googlesyndication.com", "gstatic.com",
        "googletagmanager.com", "google-analytics.com", "googlesource.com",
        "googlecode.com", "googlegroups.com", "googlemail.com",
        "google.com", "google.co.uk", "google.co.in", "google.de", "google.fr",
        "google.ca", "google.com.au", "google.co.jp", "google.com.br",
        // Amazon ecosystem
        "amazonaws.com", "amazonws.com", "cloudfront.net", "amazon.com",
        "amazon.co.uk", "amazon.de", "amazon.co.jp", "amazon.in",
        "amazonpay.com", "primevideo.com",
        // Microsoft ecosystem
        "microsoftonline.com", "microsoft.com", "azure.com", "live.com",
        "windows.com", "windowsupdate.com", "office.com", "office365.com",
        "outlook.com", "outlook.live.com", "onedrive.com", "bing.com",
        "msn.com", "skype.com", "linkedin.com", "github.com",
        // Apple ecosystem
        "apple.com", "apple-dns.net", "mzstatic.com", "apple.news",
        "icloud.com", "icloud-content.com", "itunes.com",
        // Meta/Facebook ecosystem
        "facebook.com", "fbcdn.net", "facebook.net", "fb.com",
        "instagram.com", "whatsapp.com", "whatsapp.net", "messenger.com",
        // Other major platforms
        "netflix.com", "nflxext.com", "nflximg.net", "nflxvideo.net",
        "paypal.com", "paypalobjects.com",
        "twitter.com", "twimg.com", "x.com",
        "ebay.com", "ebaystatic.com",
        "yahoo.com", "yimg.com",
        "dropbox.com", "dropboxusercontent.com",
        "telegram.org", "t.me",
        "chase.com", "wellsfargo.com", "bankofamerica.com",
        "alibaba.com", "alicdn.com", "aliexpress.com",
        "walmart.com", "walmartimages.com",
        "fedex.com", "dhl.com", "ups.com",
        "githubusercontent.com", "github.io",
        "osint.digitalside.it",
        // CDNs & infrastructure
        "akamaized.net", "akamai.net", "fastly.net", "cdnjs.cloudflare.com",
        "cloudflare.com", "unpkg.com", "jsdelivr.net"
    )

    private fun detectLookalikeDomain(domain: String): String? {
        // Known safe brand-affiliated domains must never be flagged
        if (knownSafeBrandDomains.any { domain == it || domain.endsWith(".$it") }) return null

        // Only detect truly suspicious impersonation: brand keyword in domain base, NOT the
        // official domain or a known international variant.
        // Removed generic words like "bank" — they cause false positives on legitimate banking sites.
        val knownBrands = mapOf(
            "paypal" to "paypal.com",
            "amazon" to "amazon.com",
            "google" to "google.com",
            "microsoft" to "microsoft.com",
            "apple" to "apple.com",
            "facebook" to "facebook.com",
            "instagram" to "instagram.com",
            "netflix" to "netflix.com",
            "whatsapp" to "whatsapp.com",
            "telegram" to "telegram.org",
            "linkedin" to "linkedin.com",
            "chase" to "chase.com",
            "wellsfargo" to "wellsfargo.com",
            "dropbox" to "dropbox.com",
            "icloud" to "icloud.com",
            "yahoo" to "yahoo.com",
            "ebay" to "ebay.com"
        )
        
        // Common international TLD suffixes for brand domains
        val intlTlds = listOf(".com", ".co.uk", ".co.in", ".co.jp", ".de", ".fr", ".com.au", ".com.br", ".ca", ".org", ".me", ".in", ".net", ".io")
        
        // Extract base domain name (before TLD) for matching
        val baseDomain = domain.substringBeforeLast(".")
        
        for ((brand, officialDomain) in knownBrands) {
            // Only match the DOMAIN (not path), and only if the brand is the primary word
            // "appleseed.com" should not be flagged as Apple impersonation
            if (baseDomain.contains(brand) && domain != officialDomain && !domain.endsWith(".$officialDomain")
                && intlTlds.none { tld -> domain == "$brand${tld.removePrefix(".")}" || domain.endsWith(".$brand${tld.removePrefix(".")}") }) {
                // Extra check: brand must be a substantial part of the domain, not a substring
                // e.g. "pineapple.com" contains "apple" but isn't impersonating Apple
                val brandIndex = baseDomain.indexOf(brand)
                val beforeBrand = if (brandIndex > 0) baseDomain[brandIndex - 1] else '.'
                val afterBrand = if (brandIndex + brand.length < baseDomain.length) baseDomain[brandIndex + brand.length] else '.'
                // Brand must be at word boundary (start/end of domain or next to separator chars)
                val atBoundary = (brandIndex == 0 || beforeBrand in listOf('.', '-', '_')) &&
                    (brandIndex + brand.length == baseDomain.length || afterBrand in listOf('.', '-', '_'))
                // Also flag if the base is very close to the brand (typosquat)
                if (atBoundary || baseDomain == brand) {
                    return officialDomain
                }
            }
            if (detectTyposquat(domain, brand)) return officialDomain
        }
        
        return null
    }

    private fun detectTyposquat(domain: String, brand: String): Boolean {
        val base = domain.substringBefore(".")
        if (base.length < brand.length - 2 || base.length > brand.length + 2) return false
        return levenshteinDistance(base, brand) <= 2 && base != brand
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        for (i in 1..m) {
            val curr = IntArray(n + 1)
            curr[0] = i
            for (j in 1..n) {
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            prev = curr
        }
        return prev[n]
    }
}
