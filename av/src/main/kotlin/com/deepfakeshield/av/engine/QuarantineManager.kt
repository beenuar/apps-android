package com.deepfakeshield.av.engine

import android.content.Context
import android.util.Log
import com.deepfakeshield.av.service.RealTimeAvMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quarantine manager - isolates threats per industry standard.
 * Persists metadata to JSON for listQuarantined().
 */
@Singleton
class QuarantineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realTimeAvMonitor: RealTimeAvMonitor
) {
    private val quarantineDir: File by lazy {
        File(context.filesDir, "quarantine").also { it.mkdirs() }
    }

    private val metadataFile: File by lazy {
        File(quarantineDir, "metadata.json")
    }

    private val entriesLock = Any()

    companion object {
        // XOR key for obfuscating quarantined files so they can't be directly executed
        private const val XOR_KEY: Byte = 0x5A
    }

    fun quarantine(scanResult: AvScanResult): QuarantineEntry? {
        val source = File(scanResult.path)
        if (!source.exists()) return null
        // Add .quarantined extension and XOR-obfuscate to prevent execution
        val name = "quarantine_${System.currentTimeMillis()}_${source.name}.quarantined"
        val dest = File(quarantineDir, name)
        return try {
            // Stream XOR-obfuscation to avoid OOM on large files (up to 50MB)
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        for (i in 0 until bytesRead) buffer[i] = (buffer[i].toInt() xor XOR_KEY.toInt()).toByte()
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            if (!source.delete()) {
                // Rollback: remove quarantined copy since original couldn't be removed
                dest.delete()
                return null
            }
            val entry = QuarantineEntry(
                id = name,
                originalPath = scanResult.path,
                quarantinedPath = dest.absolutePath,
                threatName = scanResult.threatName,
                displayName = scanResult.displayName,
                quarantinedAt = System.currentTimeMillis(),
                fileHash = scanResult.fileHash
            )
            addEntryToMetadata(entry)
            entry
        } catch (e: Exception) {
            // Clean up partially written quarantine file to prevent corrupted entries
            try { dest.delete() } catch (_: Exception) { /* best effort cleanup */ }
            Log.e("QuarantineManager", "Quarantine failed for ${scanResult.path}", e)
            null
        }
    }

    private fun addEntryToMetadata(entry: QuarantineEntry) {
        synchronized(entriesLock) {
            val entries = loadMetadata().toMutableList()
            entries.add(entry)
            saveMetadata(entries)
        }
    }

    fun restore(entry: QuarantineEntry): Boolean {
        val file = File(entry.quarantinedPath)
        if (!file.exists()) return false
        val destFile = File(entry.originalPath)
        // Path traversal protection: block restoring to app-internal or system paths
        val canonical = destFile.canonicalPath
        if (canonical.startsWith(context.filesDir.canonicalPath)
            || canonical.startsWith(context.cacheDir.canonicalPath)
            || canonical.startsWith("/system/")
            || canonical.contains("/../")) {
            Log.w("QuarantineManager", "Restore blocked: invalid destination path")
            return false
        }
        // Suppress real-time scanning on the destination path BEFORE writing.
        // Without this, the FileObserver would detect the restored file, re-scan it,
        // find the same malware, and auto-quarantine it again — an infinite loop.
        realTimeAvMonitor.suppressPathTemporarily(destFile.absolutePath)
        return try {
            destFile.parentFile?.mkdirs()
            // Stream de-obfuscation to avoid OOM
            file.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        for (i in 0 until bytesRead) buffer[i] = (buffer[i].toInt() xor XOR_KEY.toInt()).toByte()
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            if (!file.delete()) {
                // Quarantine copy couldn't be removed — log it but still succeed.
                // The metadata entry IS removed so the orphan won't confuse the UI.
                // This is safer than failing the entire restore.
                Log.w("QuarantineManager", "Could not delete quarantine copy: ${entry.quarantinedPath}")
            }
            removeEntryFromMetadata(entry.id)
            Log.i("QuarantineManager", "Restored file to ${entry.originalPath} (scan suppressed)")
            true
        } catch (e: Exception) {
            // Restore failed — remove the suppression immediately since the file
            // either wasn't written or is incomplete.
            realTimeAvMonitor.removeSuppression(destFile.absolutePath)
            Log.e("QuarantineManager", "Restore failed for ${entry.originalPath}", e)
            false
        }
    }

    fun delete(entry: QuarantineEntry): Boolean {
        val ok = File(entry.quarantinedPath).delete()
        if (ok) removeEntryFromMetadata(entry.id)
        return ok
    }

    private fun removeEntryFromMetadata(id: String) {
        synchronized(entriesLock) {
            val entries = loadMetadata().filter { it.id != id }
            saveMetadata(entries)
        }
    }

    private fun loadMetadata(): List<QuarantineEntry> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val json = JSONArray(metadataFile.readText())
            (0 until json.length()).mapNotNull { i ->
                val obj = json.getJSONObject(i)
                val path = File(obj.getString("quarantinedPath"))
                if (!path.canonicalPath.startsWith(quarantineDir.canonicalPath)) return@mapNotNull null
                if (path.exists()) QuarantineEntry(
                    id = obj.getString("id"),
                    originalPath = obj.getString("originalPath"),
                    quarantinedPath = obj.getString("quarantinedPath"),
                    threatName = obj.optString("threatName").takeIf { it.isNotEmpty() },
                    displayName = obj.getString("displayName"),
                    quarantinedAt = obj.getLong("quarantinedAt"),
                    fileHash = obj.optString("fileHash").takeIf { it.isNotEmpty() }
                ) else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMetadata(entries: List<QuarantineEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("originalPath", e.originalPath)
                put("quarantinedPath", e.quarantinedPath)
                put("threatName", e.threatName ?: "")
                put("displayName", e.displayName)
                put("quarantinedAt", e.quarantinedAt)
                put("fileHash", e.fileHash ?: "")
            })
        }
        // Atomic write: write to temp then rename to prevent corruption on crash.
        // If renameTo fails (some filesystems), fall back to direct copy.
        val tempFile = File(quarantineDir, "metadata.json.tmp")
        tempFile.writeText(arr.toString())
        if (!tempFile.renameTo(metadataFile)) {
            // Fallback: copy content directly (less atomic but prevents silent data loss)
            tempFile.copyTo(metadataFile, overwrite = true)
            tempFile.delete()
        }
    }

    fun listQuarantined(): List<QuarantineEntry> {
        return loadMetadata()
    }
}

data class QuarantineEntry(
    val id: String,
    val originalPath: String,
    val quarantinedPath: String,
    val threatName: String?,
    val displayName: String,
    val quarantinedAt: Long,
    val fileHash: String?
)
