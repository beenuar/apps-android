package com.deepfakeshield.feature.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.engine.PageContentAnalyzer
import com.deepfakeshield.core.engine.UrlSafetyEngine
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.intelligence.UnifiedUrlThreatCache
import com.deepfakeshield.intelligence.feed.ShortLinkResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Safe Browser ViewModel - combines UrlSafetyEngine, threat feeds, short link resolution.
 */
@HiltViewModel
class SafeBrowserViewModel @Inject constructor(
    private val urlSafetyEngine: UrlSafetyEngine,
    private val urlThreatCache: UnifiedUrlThreatCache,
    private val shortLinkResolver: ShortLinkResolver,
    private val pageContentAnalyzer: PageContentAnalyzer,
    userPreferences: UserPreferences
) : ViewModel() {

    private val _urlAnalysis = MutableStateFlow<UrlSafetyEngine.UrlAnalysis?>(null)
    val urlAnalysis: StateFlow<UrlSafetyEngine.UrlAnalysis?> = _urlAnalysis.asStateFlow()

    private val _blockAdultContent = MutableStateFlow(false)
    init {
        viewModelScope.launch {
            userPreferences.familyProtectionEnabled.collect { _blockAdultContent.value = it }
        }
    }

    /**
     * Synchronous analysis for WebView navigation - no short-link resolution.
     * Uses familyProtectionEnabled for blockAdultContent.
     */
    fun analyzeUrlSync(url: String, blockAdultContent: Boolean? = null): UrlSafetyEngine.UrlAnalysis {
        val blockAdult = blockAdultContent ?: _blockAdultContent.value
        var result = urlSafetyEngine.analyzeUrl(url, blockAdult)
        if (urlThreatCache.isUrlPhishing(url)) {
            result = result.copy(
                riskScore = maxOf(result.riskScore, 95),
                threats = result.threats + UrlSafetyEngine.UrlThreat(
                    "KNOWN_PHISHING", "CRITICAL",
                    "This URL is in PhishTank/OpenPhish phishing database"
                )
            )
        }
        if (urlThreatCache.isUrlMalware(url)) {
            result = result.copy(
                riskScore = maxOf(result.riskScore, 95),
                threats = result.threats + UrlSafetyEngine.UrlThreat(
                    "KNOWN_MALWARE", "CRITICAL",
                    "This URL is in URLhaus malware database"
                )
            )
        }
        return result
    }

    fun analyzeUrl(url: String) {
        viewModelScope.launch {
            try {
                var urlToAnalyze = url
                // Network I/O (short link resolution) must run on IO dispatcher
                if (shortLinkResolver.isShortLink(url)) {
                    val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        shortLinkResolver.resolve(url)
                    }
                    if (resolved.resolved) urlToAnalyze = resolved.finalUrl
                }
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    analyzeUrlSync(urlToAnalyze)
                }
                _urlAnalysis.value = result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Network failure during short link resolution — fall back to local analysis
                try {
                    _urlAnalysis.value = analyzeUrlSync(url)
                } catch (fallbackEx: Exception) { android.util.Log.e("SafeBrowserVM", "URL analysis failed", fallbackEx) }
            }
        }
    }

    fun refreshThreatCache(callback: (Result<UnifiedUrlThreatCache.RefreshStats>) -> Unit) {
        viewModelScope.launch {
            callback(urlThreatCache.refresh())
        }
    }

    fun needsCacheRefresh(): Boolean = urlThreatCache.needsRefresh()
    fun getCachedThreatCount(): Int = urlThreatCache.getCachedCount()

    /** Page content analysis - when HTML/body text is available */
    fun analyzePageContent(text: String): PageContentAnalyzer.PageAnalysis =
        pageContentAnalyzer.analyze(text)
}
