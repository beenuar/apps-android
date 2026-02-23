package com.deepfakeshield.intelligence.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * URLhaus malware URL feed from abuse.ch.
 * FREE auth key from https://auth.abuse.ch/ - pass via urlHausAuthKey (null = disabled).
 */
@Singleton
class UrlHausFeed @Inject constructor() {

    companion object {
        private const val FEED_URL = "https://urlhaus.abuse.ch/downloads/text_online/"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    data class UrlHausEntry(
        val url: String,
        val threatType: String,
        val tags: List<String>
    )

    /**
     * Fetch malware URLs. Requires free auth key from auth.abuse.ch.
     * Returns empty list if no key provided.
     */
    suspend fun fetchMalwareUrls(urlHausAuthKey: String? = null): Result<List<UrlHausEntry>> = withContext(Dispatchers.IO) {
        if (urlHausAuthKey.isNullOrBlank()) return@withContext Result.success(emptyList())
        var conn: HttpURLConnection? = null
        try {
            val encodedKey = URLEncoder.encode(urlHausAuthKey, "UTF-8")
            val urlStr = "$FEED_URL?auth-key=$encodedKey"
            val url = URL(urlStr)
            conn = url.openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Cyble/1.0 URLhaus-Integration")

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("URLhaus returned ${conn.responseCode}"))
            }

            val entries = conn.inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || trimmed.isBlank()) return@mapNotNull null
                    val parts = trimmed.split("\t")
                    if (parts.isNotEmpty() && parts[0].startsWith("http")) {
                        UrlHausEntry(
                            url = parts[0].trim().lowercase(),
                            threatType = parts.getOrNull(4) ?: "malware",
                            tags = parts.getOrNull(5)?.split(",")?.map { it.trim() } ?: emptyList()
                        )
                    } else null
                }.toList()
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
