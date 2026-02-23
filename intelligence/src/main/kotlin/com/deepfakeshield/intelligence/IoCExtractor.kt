package com.deepfakeshield.intelligence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts Indicators of Compromise (IOCs) from text - IPs, domains, hashes, URLs.
 * Used for forensic reports, evidence chains, and threat hunting.
 */
@Singleton
class IoCExtractor @Inject constructor() {

    data class ExtractedIocs(
        val ipv4: List<String> = emptyList(),
        val ipv6: List<String> = emptyList(),
        val domains: List<String> = emptyList(),
        val urls: List<String> = emptyList(),
        val md5Hashes: List<String> = emptyList(),
        val sha256Hashes: List<String> = emptyList(),
        val emails: List<String> = emptyList()
    )

    private val ipv4Regex = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
    private val ipv6Regex = Regex("""\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b""")
    private val md5Regex = Regex("""\b[a-fA-F0-9]{32}\b""")
    private val sha256Regex = Regex("""\b[a-fA-F0-9]{64}\b""")
    private val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
    private val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val domainRegex = Regex("""\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b""")

    fun extract(text: String): ExtractedIocs {
        val safeText = if (text.length > 100_000) text.take(100_000) else text
        val urls = urlRegex.findAll(safeText).map { it.value }.distinct().toList()
        val withoutUrls = urlRegex.replace(safeText, " ")
        val domains = domainRegex.findAll(withoutUrls)
            .map { it.value }
            .filter { !it.startsWith("www.") || it.length > 4 }
            .filter { d -> !urls.any { u -> d in u } }
            .distinct()
            .toList()
        val ipv4 = ipv4Regex.findAll(safeText).map { it.value }.distinct().toList()
        val ipv6 = ipv6Regex.findAll(safeText).map { it.value }.distinct().toList()
        val md5 = md5Regex.findAll(safeText)
            .map { it.value }
            .filter { it.length == 32 }
            .distinct()
            .toList()
        val sha256 = sha256Regex.findAll(safeText)
            .map { it.value }
            .filter { it.length == 64 }
            .distinct()
            .toList()
        val emails = emailRegex.findAll(safeText).map { it.value }.distinct().toList()
        return ExtractedIocs(
            ipv4 = ipv4,
            ipv6 = ipv6,
            domains = domains,
            urls = urls,
            md5Hashes = md5,
            sha256Hashes = sha256,
            emails = emails
        )
    }

    fun extractAsStrings(text: String): List<String> {
        val iocs = extract(text)
        return (iocs.ipv4 + iocs.ipv6 + iocs.domains + iocs.urls +
            iocs.md5Hashes + iocs.sha256Hashes + iocs.emails).distinct()
    }
}
