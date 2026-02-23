package com.deepfakeshield.intelligence.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificate Transparency log lookup via crt.sh - FREE, no API key.
 * Detects suspicious certificates, newly issued certs for domains.
 */
@Singleton
class CertificateTransparencyFeed @Inject constructor() {

    companion object {
        private const val CRT_SH_URL = "https://crt.sh/?q=%s&output=json"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    data class CtEntry(
        val issuerName: String?,
        val commonName: String?,
        val nameValue: String?,
        val entryTimestamp: String?,
        val notBefore: String?,
        val notAfter: String?
    )

    /**
     * Query crt.sh for certificates issued for the given domain.
     * Returns entries if domain has certificates in CT logs.
     */
    suspend fun lookupDomain(domain: String): Result<List<CtEntry>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode("%.$domain", Charsets.UTF_8.name())
            val url = URL(CRT_SH_URL.format(encoded))
            conn = url.openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Cyble/1.0")
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("crt.sh returned ${conn.responseCode}"))
            }

            // Cap response size at 2MB to prevent OOM on domains with massive CT logs
            val content = conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                var totalRead = 0
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    totalRead += n
                    if (totalRead > 2 * 1024 * 1024) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }

            if (content.isBlank()) return@withContext Result.success(emptyList())
            val arr = try {
                JSONArray(content)
            } catch (e: org.json.JSONException) {
                return@withContext Result.failure(Exception("Invalid JSON from crt.sh: ${e.message}", e))
            }
            val entries = mutableListOf<CtEntry>()
            for (i in 0 until arr.length().coerceAtMost(100)) {
                val obj = arr.getJSONObject(i)
                entries.add(
                    CtEntry(
                        issuerName = obj.optString("issuer_name").takeIf { it != "null" },
                        commonName = obj.optString("common_name").takeIf { it != "null" },
                        nameValue = obj.optString("name_value").takeIf { it != "null" },
                        entryTimestamp = obj.optString("entry_timestamp").takeIf { it != "null" },
                        notBefore = obj.optString("not_before").takeIf { it != "null" },
                        notAfter = obj.optString("not_after").takeIf { it != "null" }
                    )
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Check if domain has very new certificates (issued in last 7 days) - potential phishing indicator.
     */
    suspend fun hasVeryNewCertificates(domain: String): Boolean = withContext(Dispatchers.IO) {
        lookupDomain(domain).getOrNull()?.any { entry ->
            val notBefore = entry.notBefore ?: return@any false
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                val issued = format.parse(notBefore)?.time ?: 0L
                System.currentTimeMillis() - issued < 7L * 24 * 60 * 60 * 1000
            } catch (_: Exception) { false }
        } ?: false
    }
}
