package com.deepfakeshield.av.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only hash checker — cloud hash lookup has been removed.
 * All malware detection is handled by the on-device signature database
 * and heuristic analyzer. No external API calls or API keys required.
 */
@Singleton
class CloudHashChecker @Inject constructor() {

    data class CloudResult(val isMalicious: Boolean, val source: String, val threatName: String?)

    fun isCloudAvailable(): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun checkHash(sha256: String): CloudResult? = null
}
