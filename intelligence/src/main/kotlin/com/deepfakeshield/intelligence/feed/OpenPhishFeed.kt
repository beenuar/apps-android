package com.deepfakeshield.intelligence.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenPhish community-driven phishing feed - FREE.
 * Source: https://openphish.com/
 * Format: One URL per line
 */
@Singleton
class OpenPhishFeed @Inject constructor() {

    companion object {
        private const val FEED_URL = "https://openphish.com/feed.txt"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    suspend fun fetchPhishingUrls(): Result<Set<String>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(FEED_URL)
            conn = url.openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Cyble/1.0 OpenPhish-Integration")

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("OpenPhish returned ${conn.responseCode}"))
            }

            val urls = conn.inputStream.bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && !it.startsWith("#") && it.contains(".") }
                    .toSet()
            }
            Result.success(urls)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
