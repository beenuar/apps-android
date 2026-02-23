package com.deepfakeshield.intelligence.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves short URLs (bit.ly, t.co, etc.) to final destination for safety checking.
 * No API keys - uses HTTP HEAD/GET redirect following.
 */
@Singleton
class ShortLinkResolver @Inject constructor() {

    companion object {
        private val SHORT_DOMAINS = setOf(
            "bit.ly", "t.co", "goo.gl", "tinyurl.com", "ow.ly", "is.gd",
            "buff.ly", "adf.ly", "j.mp", "bit.do", "lnkd.in", "db.tt",
            "qr.ae", "cur.lv", "ity.im", "q.gs", "po.st", "bc.vc",
            "twitthis.com", "u.to", "cutt.ly", "short.link", "rb.gy"
        )
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    data class ResolveResult(
        val originalUrl: String,
        val finalUrl: String,
        val redirectCount: Int,
        val resolved: Boolean
    )

    fun isShortLink(url: String): Boolean {
        val domain = extractDomain(url) ?: return false
        return SHORT_DOMAINS.any { domain == it || domain.endsWith(".$it") }
    }

    suspend fun resolve(url: String): ResolveResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ResolveResult(url, url, 0, false)
        try {
            var current = url
            var count = 0
            while (count < MAX_REDIRECTS) {
                val conn = URL(current).openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
                try {
                    conn.requestMethod = "HEAD"
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = CONNECT_TIMEOUT_MS
                    conn.readTimeout = READ_TIMEOUT_MS
                    conn.setRequestProperty("User-Agent", "Cyble/1.0 ShortLinkResolver")

                    val code = conn.responseCode
                    val location = conn.getHeaderField("Location")

                    when {
                    code in 300..399 && location != null -> {
                        current = if (location.startsWith("http")) location else {
                            URL(URL(current), location).toString()
                        }
                        count++
                    }
                    code in 200..299 -> return@withContext ResolveResult(url, current, count, true)
                    else -> return@withContext ResolveResult(url, current, count, false)
                }
                } finally {
                    conn.disconnect()
                }
            }
            ResolveResult(url, current, count, false)
        } catch (e: Exception) {
            ResolveResult(url, url, 0, false)
        }
    }

    private fun extractDomain(url: String): String? {
        return try {
            val u = URL(if (url.startsWith("http")) url else "https://$url")
            u.host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }
}
