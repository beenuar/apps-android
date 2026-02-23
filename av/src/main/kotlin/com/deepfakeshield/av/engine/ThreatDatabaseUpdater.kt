package com.deepfakeshield.av.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches malware hash feeds from active, maintained open-source threat intelligence URLs.
 * Updates MalwareSignatureDatabase with new hashes for blocking known malware.
 *
 * Feed sources (all free, no API keys required, actively maintained):
 * 1. MalwareBazaar by abuse.ch — recent malware samples (SHA256)
 * 2. URLhaus by abuse.ch — payloads from malicious URLs (SHA256)
 * 3. Feodo Tracker by abuse.ch — banking trojan hashes
 * 4. ThreatFox by abuse.ch — IOCs including file hashes
 *
 * Supported formats:
 * - Plain text: one SHA256 hash per line (64 hex chars)
 * - JSON: various formats from different providers
 * - CSV: abuse.ch format with hash columns
 */
@Singleton
class ThreatDatabaseUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureDb: MalwareSignatureDatabase
) {
    companion object {
        private const val TAG = "ThreatDbUpdater"
        private const val PREFS_NAME = "threat_db_prefs"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        private const val KEY_LAST_UPDATE_STATUS = "last_update_status"
        private const val KEY_LAST_HASHES_ADDED = "last_hashes_added"

        /**
         * Active, maintained open-source threat intel feeds — no API keys required.
         * Diverse sources for maximum hash coverage.
         */
        val DEFAULT_FEED_URLS = listOf(
            // abuse.ch — most reliable open threat intel
            "https://bazaar.abuse.ch/export/txt/sha256/recent/",
            "https://feodotracker.abuse.ch/downloads/malware_hashes.csv",
            "https://threatfox-api.abuse.ch/api/v1/",
            "https://sslbl.abuse.ch/blacklist/sslblacklist.csv",
            // URLhaus — malicious URLs/payloads (abuse.ch)
            "https://urlhaus-api.abuse.ch/v1/payloads/recent/",
            // Botvrij (Netherlands CERT)
            "https://www.botvrij.eu/data/ioclist.sha256",
            // DigitalSide Threat-Intel (community IOCs)
            "https://raw.githubusercontent.com/davidonzo/Threat-Intel/master/lists/latestips.txt",
            // Stamparm maltrail — malware trails
            "https://raw.githubusercontent.com/stamparm/maltrail/master/trails/static/malware/generic.txt",
            // Phishing Army — phishing domain blocklist
            "https://phishing.army/download/phishing_army_blocklist.txt"
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    sealed class UpdateResult {
        data class Success(val hashesAdded: Int, val source: String) : UpdateResult()
        data class Failure(val error: String) : UpdateResult()
    }

    // ─── Public API ───

    suspend fun updateFromDefaultFeeds(): UpdateResult = withContext(Dispatchers.IO) {
        val allHashes = mutableSetOf<String>()
        val successSources = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (urlString in DEFAULT_FEED_URLS) {
            try {
                val hashes = fetchHashesFromUrl(urlString)
                if (hashes.isNotEmpty()) {
                    allHashes.addAll(hashes)
                    successSources.add(urlString.substringAfter("://").substringBefore("/"))
                    Log.i(TAG, "Fetched ${hashes.size} hashes from $urlString (total: ${allHashes.size})")
                }
            } catch (e: Exception) {
                val msg = "${urlString.substringAfter("://").substringBefore("/")}: ${e.message}"
                errors.add(msg)
                Log.w(TAG, "Failed to fetch $urlString: ${e.message}")
            }
        }

        if (allHashes.isNotEmpty()) {
            val text = allHashes.sorted().joinToString("\n")
            signatureDb.saveDownloadedAndReload(text)
            val source = successSources.joinToString(", ")
            Log.i(TAG, "Updated threat DB: ${allHashes.size} hashes from ${successSources.size} feeds")

            // Record update metadata
            prefs.edit()
                .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_UPDATE_STATUS, "Success: ${allHashes.size} hashes from ${successSources.size} feeds")
                .putInt(KEY_LAST_HASHES_ADDED, allHashes.size)
                .apply()

            return@withContext UpdateResult.Success(allHashes.size, source)
        }

        val errorMsg = if (errors.isNotEmpty()) "All feeds failed: ${errors.joinToString("; ")}"
        else "Could not fetch from any feed. Ensure you have internet access."

        prefs.edit()
            .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            .putString(KEY_LAST_UPDATE_STATUS, "Failed: $errorMsg")
            .putInt(KEY_LAST_HASHES_ADDED, 0)
            .apply()

        UpdateResult.Failure(errorMsg)
    }

    suspend fun updateFromUrl(urlString: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val hashes = fetchHashesFromUrl(urlString)
            if (hashes.isNotEmpty()) {
                signatureDb.saveDownloadedAndReload(hashes.joinToString("\n"))
                Log.i(TAG, "Updated threat DB: ${hashes.size} hashes from custom URL")

                prefs.edit()
                    .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                    .putString(KEY_LAST_UPDATE_STATUS, "Success: ${hashes.size} hashes from custom URL")
                    .putInt(KEY_LAST_HASHES_ADDED, hashes.size)
                    .apply()

                return@withContext UpdateResult.Success(hashes.size, urlString)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $urlString: ${e.message}")
        }
        UpdateResult.Failure("Failed to fetch or parse: $urlString")
    }

    fun getCurrentHashCount(): Int = signatureDb.getHashCount()

    fun getLastUpdateTimeMs(): Long = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)

    fun getLastUpdateStatus(): String = prefs.getString(KEY_LAST_UPDATE_STATUS, "Never updated") ?: "Never updated"

    fun getLastHashesAdded(): Int = prefs.getInt(KEY_LAST_HASHES_ADDED, 0)

    // ─── Feed Fetching ───

    private fun fetchHashesFromUrl(urlString: String): Set<String> {
        return when {
            // ThreatFox API needs a POST request
            urlString.contains("threatfox-api") -> fetchThreatFoxHashes(urlString)
            // URLhaus API needs a POST request
            urlString.contains("urlhaus-api") -> fetchUrlhausHashes(urlString)
            // CERT.PL API (JSON response)
            urlString.contains("mwdb.cert.pl") -> fetchMwdbHashes(urlString)
            // ViriBack tracker (HTML table with hashes)
            urlString.contains("viriback.com") -> fetchViriBackHashes(urlString)
            // CSV format (Feodo Tracker, SSLBL)
            urlString.endsWith(".csv") -> fetchCsvHashes(urlString)
            // VirusShare .md5 file (MD5 hashes — we store them too)
            urlString.contains("virusshare.com") -> fetchVirusShareHashes(urlString)
            // Plain text (MalwareBazaar export, Botvrij, etc.)
            else -> fetchPlainTextHashes(urlString)
        }
    }

    /**
     * MalwareBazaar plain text export: one SHA256 hash per line, comments start with #
     */
    private fun fetchPlainTextHashes(urlString: String): Set<String> {
        val content = httpGet(urlString) ?: return emptySet()
        return content.lines()
            .map { it.trim().lowercase() }
            .filter { isValidSha256(it) }
            .toSet()
    }

    /**
     * Feodo Tracker CSV: columns include sha256_hash
     * Format: first_seen,sha256_hash,md5_hash,file_name,...
     */
    private fun fetchCsvHashes(urlString: String): Set<String> {
        val content = httpGet(urlString) ?: return emptySet()
        val hashes = mutableSetOf<String>()
        for (line in content.lines()) {
            if (line.startsWith("#") || line.isBlank()) continue
            // Try to extract SHA256 from CSV columns
            val parts = line.split(",")
            for (part in parts) {
                val cleaned = part.trim().removeSurrounding("\"").lowercase()
                if (isValidSha256(cleaned)) {
                    hashes.add(cleaned)
                    break // Only take the first valid hash per line
                }
            }
        }
        return hashes
    }

    /**
     * ThreatFox API: POST with query to get recent IOCs including file hashes.
     */
    private fun fetchThreatFoxHashes(urlString: String): Set<String> {
        val body = """{"query": "get_iocs", "days": 7}"""
        val content = httpPost(urlString, body) ?: return emptySet()
        val hashes = mutableSetOf<String>()
        try {
            val root = JSONObject(content)
            val data = root.optJSONArray("data") ?: return emptySet()
            for (i in 0 until data.length()) {
                val entry = data.optJSONObject(i) ?: continue
                val iocType = entry.optString("ioc_type", "")
                if (iocType == "sha256_hash") {
                    val hash = entry.optString("ioc", "").trim().lowercase()
                    if (isValidSha256(hash)) hashes.add(hash)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing ThreatFox JSON: ${e.message}")
        }
        return hashes
    }

    /**
     * URLhaus payload API: POST to get recent payloads with SHA256 hashes.
     */
    private fun fetchUrlhausHashes(urlString: String): Set<String> {
        // URLhaus recent payloads endpoint expects an empty POST or form data
        val content = httpPost(urlString, "") ?: return emptySet()
        val hashes = mutableSetOf<String>()
        try {
            val root = JSONObject(content)
            val payloads = root.optJSONArray("payloads") ?: return emptySet()
            for (i in 0 until payloads.length()) {
                val entry = payloads.optJSONObject(i) ?: continue
                val sha256 = entry.optString("sha256_hash", "").trim().lowercase()
                if (isValidSha256(sha256)) hashes.add(sha256)
            }
        } catch (e: Exception) {
            // Try plain text fallback
            for (line in content.lines()) {
                val hash = line.trim().lowercase()
                if (isValidSha256(hash)) hashes.add(hash)
            }
        }
        return hashes
    }

    /**
     * VirusShare: lines of MD5 hashes (32 hex chars). We accept both MD5 and SHA256.
     */
    private fun fetchVirusShareHashes(urlString: String): Set<String> {
        val content = httpGet(urlString) ?: return emptySet()
        return content.lines()
            .map { it.trim().lowercase() }
            .filter { isValidHash(it) }
            .toSet()
    }

    /**
     * CERT.PL MWDB API: JSON array of file objects with sha256 field.
     */
    private fun fetchMwdbHashes(urlString: String): Set<String> {
        val content = httpGet(urlString) ?: return emptySet()
        val hashes = mutableSetOf<String>()
        try {
            val arr = JSONArray(content)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sha256 = obj.optString("sha256", "").trim().lowercase()
                if (isValidSha256(sha256)) hashes.add(sha256)
            }
        } catch (e: Exception) {
            // Fallback: try plain text extraction
            for (line in content.lines()) {
                val hash = line.trim().lowercase()
                if (isValidSha256(hash)) hashes.add(hash)
            }
        }
        return hashes
    }

    /**
     * ViriBack tracker: dump page with hashes in plain text / HTML.
     * Extract any SHA256 or MD5 hashes found in the output.
     */
    private fun fetchViriBackHashes(urlString: String): Set<String> {
        val content = httpGet(urlString) ?: return emptySet()
        val hashes = mutableSetOf<String>()
        val sha256Regex = Regex("[a-f0-9]{64}")
        val md5Regex = Regex("[a-f0-9]{32}")
        for (match in sha256Regex.findAll(content.lowercase())) {
            hashes.add(match.value)
        }
        for (match in md5Regex.findAll(content.lowercase())) {
            if (match.value.length == 32) hashes.add(match.value)
        }
        return hashes
    }

    /**
     * Accept both MD5 (32 hex) and SHA256 (64 hex) hashes.
     */
    private fun isValidHash(hash: String): Boolean {
        return (hash.length == 64 || hash.length == 32) &&
               hash.all { it in '0'..'9' || it in 'a'..'f' } &&
               !hash.startsWith("#")
    }

    // ─── HTTP Helpers ───

    private fun httpGet(urlString: String): String? {
        val url = URL(urlString)
        val proxy = com.deepfakeshield.core.network.TorNetworkModule.getProxy()
        val conn = (url.openConnection(proxy) as? HttpURLConnection) ?: return null
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Cyble/1.0")
            if (conn.responseCode == 200) readResponse(conn) else null
        } catch (e: Exception) {
            Log.w(TAG, "GET $urlString failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(urlString: String, body: String): String? {
        val url = URL(urlString)
        val proxy = com.deepfakeshield.core.network.TorNetworkModule.getProxy()
        val conn = (url.openConnection(proxy) as? HttpURLConnection) ?: return null
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", "Cyble/1.0")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            if (body.isNotEmpty()) {
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode == 200) readResponse(conn) else null
        } catch (e: Exception) {
            Log.w(TAG, "POST $urlString failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        // Cap response at 8MB to prevent OOM
        return conn.inputStream.bufferedReader().use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var totalRead = 0
            var n: Int
            while (reader.read(buf).also { n = it } != -1) {
                totalRead += n
                if (totalRead > 8 * 1024 * 1024) break
                sb.append(buf, 0, n)
            }
            sb.toString()
        }
    }

    private fun isValidSha256(hash: String): Boolean {
        return hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' } && !hash.startsWith("#")
    }
}
