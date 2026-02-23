package com.deepfakeshield.intelligence

import com.deepfakeshield.intelligence.feed.OpenPhishFeed
import com.deepfakeshield.intelligence.feed.PhishTankFeed
import com.deepfakeshield.intelligence.feed.UrlHausFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified URL threat cache - aggregates PhishTank, OpenPhish, URLhaus (if key provided).
 * Caches locally for offline use. No API keys required for PhishTank/OpenPhish.
 */
@Singleton
class UnifiedUrlThreatCache constructor(
    private val phishTankFeed: PhishTankFeed,
    private val openPhishFeed: OpenPhishFeed,
    private val urlHausFeed: UrlHausFeed
) {
    private val mutex = Mutex()
    @Volatile
    private var cachedPhishingUrls: Set<String> = emptySet()
    @Volatile
    private var cachedMalwareUrls: Set<String> = emptySet()
    private var lastRefreshMs: Long = 0
    private var cacheDir: File? = null

    companion object {
        private const val CACHE_VALIDITY_MS = 4 * 60 * 60 * 1000L // 4 hours
        private const val CACHE_FILE = "url_threat_cache.txt"
    }

    fun setCacheDirectory(dir: File) {
        cacheDir = dir
    }

    fun isUrlPhishing(url: String): Boolean {
        val normalized = normalizeForLookup(url)
        return cachedPhishingUrls.contains(normalized) ||
            cachedPhishingUrls.any { cached ->
                normalized.startsWith(cached) &&
                    (normalized.length == cached.length ||
                     (normalized.length > cached.length &&
                      normalized[cached.length] in charArrayOf('/', '?', '#', ':')))
            }
    }

    fun isUrlMalware(url: String): Boolean {
        val normalized = normalizeForLookup(url)
        return cachedMalwareUrls.contains(normalized) ||
            cachedMalwareUrls.any { cached ->
                normalized.startsWith(cached) &&
                    (normalized.length == cached.length ||
                     (normalized.length > cached.length &&
                      normalized[cached.length] in charArrayOf('/', '?', '#', ':')))
            }
    }

    fun isUrlKnownThreat(url: String): Boolean = isUrlPhishing(url) || isUrlMalware(url)

    suspend fun refresh(urlHausAuthKey: String? = null): Result<RefreshStats> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val phishSet = mutableSetOf<String>()

            phishTankFeed.fetchPhishingUrlSet().getOrElse { emptySet() }.let { phishSet.addAll(it) }
            openPhishFeed.fetchPhishingUrls().getOrElse { emptySet() }.let { phishSet.addAll(it) }

            val malwareSet = urlHausFeed.fetchMalwareUrls(urlHausAuthKey).getOrElse { emptyList() }
                .map { it.url }.toSet()

            cachedPhishingUrls = phishSet
            cachedMalwareUrls = malwareSet
            lastRefreshMs = System.currentTimeMillis()
            persistCache()
            Result.success(
                RefreshStats(
                    phishSet.size,
                    malwareSet.size,
                    phishSet.size + malwareSet.size,
                    emptyList()
                )
            )
        }
    }

    fun needsRefresh(): Boolean = System.currentTimeMillis() - lastRefreshMs > CACHE_VALIDITY_MS

    fun getCachedCount(): Int = cachedPhishingUrls.size + cachedMalwareUrls.size

    private fun normalizeForLookup(url: String): String =
        url.lowercase().trim().removeSuffix("/")

    private fun persistCache() {
        try {
            val dir = cacheDir ?: return
            val file = File(dir, CACHE_FILE)
            file.bufferedWriter().use { w ->
                w.write("# Phishing URLs\n")
                cachedPhishingUrls.forEach { w.write("$it\n") }
                w.write("# Malware URLs\n")
                cachedMalwareUrls.forEach { w.write("$it\n") }
            }
        } catch (_: Exception) { /* ignore */ }
    }

    suspend fun loadFromCache() {
        try {
            val dir = cacheDir ?: return
            val file = File(dir, CACHE_FILE)
            if (!file.exists()) return
            val phish = mutableSetOf<String>()
            val malware = mutableSetOf<String>()
            var section = 0
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("# Phishing") -> section = 1
                        line.startsWith("# Malware") -> section = 2
                        line.isNotBlank() && !line.startsWith("#") -> {
                            if (section == 1) phish.add(line.trim())
                            else if (section == 2) malware.add(line.trim())
                        }
                    }
                }
            }
            // Acquire the same mutex used by refresh() so we don't race with a
            // concurrent network update and end up with inconsistent cache state.
            mutex.withLock {
                cachedPhishingUrls = phish
                cachedMalwareUrls = malware
            }
        } catch (_: Exception) { /* ignore */ }
    }

    data class RefreshStats(
        val phishingCount: Int,
        val malwareCount: Int,
        val totalCount: Int,
        val errors: List<String>
    )
}
