package com.deepfakeshield.intelligence.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PhishTank phishing URL feed - FREE, no API key required.
 * Source: https://phishtank.org/
 * Data: https://data.phishtank.com/data/online-valid.json
 */
@Singleton
class PhishTankFeed @Inject constructor() {

    companion object {
        private const val FEED_URL = "https://data.phishtank.com/data/online-valid.json"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    data class PhishEntry(
        val url: String,
        val phishId: String,
        val phishDetailUrl: String,
        val submissionTime: String,
        val verified: String,
        val verificationTime: String?,
        val online: String,
        val target: String?
    )

    /**
     * Fetch current phishing URLs from PhishTank.
     * Returns list of URLs known to be phishing - used for blocklist.
     */
    suspend fun fetchPhishingUrls(): Result<List<PhishEntry>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(FEED_URL)
            conn = url.openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Cyble/1.0 PhishTank-Integration")
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("PhishTank returned ${conn.responseCode}"))
            }

            // PhishTank's JSON feed is 40-100MB+. Loading it all into memory causes OOM.
            // Stream through the response and extract URLs line by line using simple pattern
            // matching. This avoids both OOM and the truncated-JSON parsing failure.
            val entries = mutableListOf<PhishEntry>()
            val maxEntries = 50_000
            conn.inputStream.bufferedReader().use { reader ->
                val urlPattern = Regex(""""url"\s*:\s*"([^"]+)"""")
                val idPattern = Regex(""""phish_id"\s*:\s*"?(\w+)"?""")
                var currentUrl: String? = null
                var currentId = ""
                // Use manual readLine loop instead of forEachLine to allow early break
                var line = reader.readLine()
                while (line != null) {
                    if (entries.size >= maxEntries) break
                    val urlMatch = urlPattern.find(line)
                    if (urlMatch != null) {
                        currentUrl = urlMatch.groupValues[1].trim()
                    }
                    val idMatch = idPattern.find(line)
                    if (idMatch != null) {
                        currentId = idMatch.groupValues[1]
                    }
                    // Each entry ends with "}" — emit when we have a URL
                    if (line.trimEnd().endsWith("}") && currentUrl != null) {
                        entries.add(PhishEntry(
                            url = currentUrl,
                            phishId = currentId,
                            phishDetailUrl = "",
                            submissionTime = "",
                            verified = "yes",
                            verificationTime = null,
                            online = "yes",
                            target = null
                        ))
                        currentUrl = null
                        currentId = ""
                    }
                    line = reader.readLine()
                }
            }
            Result.success(entries.filter { it.url.isNotBlank() })
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Extract just the URLs for fast lookup
     */
    suspend fun fetchPhishingUrlSet(): Result<Set<String>> = withContext(Dispatchers.IO) {
        fetchPhishingUrls().map { entries ->
            entries.map { normalizeUrl(it.url) }.toSet()
        }
    }

    private fun normalizeUrl(url: String): String =
        url.lowercase().trim().removeSuffix("/")
}
