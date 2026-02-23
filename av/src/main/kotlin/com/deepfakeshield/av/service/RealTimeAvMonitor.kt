package com.deepfakeshield.av.service

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.av.engine.AndroidStoragePaths
import com.deepfakeshield.av.engine.AvExclusions
import com.deepfakeshield.av.engine.AvScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time file system monitor - industry standard continuous protection.
 *
 * Watches: Downloads, app-specific storage for new/modified files.
 * On any change: immediate scan before user opens file.
 */
@Singleton
class RealTimeAvMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val antivirusEngine: AntivirusEngine
) {
    companion object {
        private const val TAG = "RealTimeAvMonitor"

        // AV engine's own files/dirs are excluded via the centralized AvExclusions object.

        /** How long (ms) a temporarily-suppressed path stays excluded from real-time scanning. */
        private const val SUPPRESS_DURATION_MS = 10_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val observers = java.util.Collections.synchronizedList(mutableListOf<FileObserver>())
    private var onThreatFound: ((AvScanResult) -> Unit)? = null
    @Volatile
    private var isRunning = false

    /**
     * Paths temporarily suppressed from real-time scanning.
     * Used by QuarantineManager during restore to prevent the restored file from
     * being immediately re-detected and re-quarantined (self-referential loop).
     * Maps absolute path → expiry timestamp.
     */
    private val suppressedPaths = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Temporarily suppress real-time scanning for [absolutePath].
     * The suppression expires after [SUPPRESS_DURATION_MS].
     * Call this BEFORE writing the file to disk so the FileObserver event is ignored.
     */
    fun suppressPathTemporarily(absolutePath: String) {
        suppressedPaths[absolutePath] = System.currentTimeMillis() + SUPPRESS_DURATION_MS
        Log.d(TAG, "Suppressing real-time scan for restored file: $absolutePath")
    }

    /** Remove an explicit suppression early (e.g. if restore fails). */
    fun removeSuppression(absolutePath: String) {
        suppressedPaths.remove(absolutePath)
    }

    private fun isPathSuppressed(absolutePath: String): Boolean {
        val expiry = suppressedPaths[absolutePath] ?: return false
        if (System.currentTimeMillis() > expiry) {
            suppressedPaths.remove(absolutePath)
            return false
        }
        return true
    }

    fun setOnThreatFound(callback: (AvScanResult) -> Unit) {
        onThreatFound = callback
    }

    fun start() {
        if (isRunning) return
        val hasStorageAccess = AndroidStoragePaths.hasFullStorageAccess(context)
        val paths = AndroidStoragePaths.getScanPaths(context)

        if (!hasStorageAccess) {
            Log.w(TAG, "No storage permission - real-time monitoring limited to app-private directories")
        }

        var watchCount = 0
        for (path in paths) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue
            if (!dir.canRead()) {
                Log.w(TAG, "Cannot read directory: $path (no permission)")
                continue
            }
            try {
                val mask = FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
                @Suppress("DEPRECATION")
                val observer = object : FileObserver(dir.absolutePath, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path.isNullOrBlank()) return
                        // Skip our own data files — hash DB, quarantine, metadata, exports
                        if (AvExclusions.isExcluded(path)) return
                        val fullPath = File(dir, path).absolutePath
                        // Skip paths temporarily suppressed (e.g. file being restored from quarantine)
                        if (isPathSuppressed(fullPath)) {
                            Log.d(TAG, "Skipping suppressed path (restore in progress): $fullPath")
                            return
                        }
                        val targetFile = File(fullPath)
                        if (!targetFile.isFile) return
                        scope.launch {
                            delay(500)
                            if (!File(fullPath).exists()) return@launch
                            // Re-check suppression after delay — restore may have started just before
                            if (isPathSuppressed(fullPath)) {
                                Log.d(TAG, "Skipping suppressed path after delay: $fullPath")
                                return@launch
                            }
                            try {
                                val result = antivirusEngine.scanFile(fullPath, null, AntivirusEngine.SCAN_TYPE_REALTIME)
                                if (result.isInfected) {
                                    Log.w(TAG, "REAL-TIME THREAT: $fullPath - ${result.threatName}")
                                    onThreatFound?.invoke(result)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error scanning $fullPath: ${e.message}")
                            }
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
                watchCount++
                Log.d(TAG, "Watching: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to watch $path", e)
            }
        }
        isRunning = true
        Log.i(TAG, "Real-time monitor started: watching $watchCount directories (storage access=$hasStorageAccess)")
    }

    fun stop() {
        val snapshot = synchronized(observers) {
            val copy = observers.toList()
            observers.clear()
            copy
        }
        snapshot.forEach {
            try { it.stopWatching() } catch (e: Exception) { Log.w(TAG, "Error stopping observer", e) }
        }
        // Cancel all pending scan coroutines to prevent threat callbacks after stop
        scope.coroutineContext.cancelChildren()
        isRunning = false
    }
}
