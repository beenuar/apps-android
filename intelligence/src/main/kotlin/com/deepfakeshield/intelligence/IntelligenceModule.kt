package com.deepfakeshield.intelligence

import android.content.Context
import com.deepfakeshield.intelligence.feed.OpenPhishFeed
import com.deepfakeshield.intelligence.feed.PhishTankFeed
import com.deepfakeshield.intelligence.feed.UrlHausFeed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Singleton
    fun provideUnifiedUrlThreatCache(
        phishTankFeed: PhishTankFeed,
        openPhishFeed: OpenPhishFeed,
        urlHausFeed: UrlHausFeed,
        @ApplicationContext context: Context
    ): UnifiedUrlThreatCache {
        val cache = UnifiedUrlThreatCache(phishTankFeed, openPhishFeed, urlHausFeed)
        cache.setCacheDirectory(File(context.filesDir, "intelligence_cache").also { it.mkdirs() })
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                cache.loadFromCache()
            } catch (e: Exception) {
                android.util.Log.w("IntelligenceModule", "Cache load failed: ${e.message}")
            }
        }
        return cache
    }
}
