package com.deepfakeshield.av.engine

/**
 * Centralized list of file names and directory prefixes that belong to the AV engine
 * and must be excluded from scanning.
 *
 * Both [AntivirusEngine] and [com.deepfakeshield.av.service.RealTimeAvMonitor] reference
 * this single source of truth, preventing the two copies from drifting apart.
 */
object AvExclusions {

    /** File names (relative, not full paths) that must never be scanned. */
    val SELF_FILE_NAMES: Set<String> = setOf(
        "malware_hashes_downloaded.txt",   // Downloaded threat hash database
        "malware_hashes.txt",              // Bundled asset hash database
        "metadata.json",                   // Quarantine metadata
        "metadata.json.tmp",               // Quarantine metadata atomic-write temp
        "deepfakeshield_export.json",      // User data export
        "deepfakeshield_export.csv",       // User data export
        "threat_cache.json"                // Threat intelligence cache
    )

    /** Directory name prefixes (relative) that must be skipped entirely. */
    val SELF_DIR_PREFIXES: Set<String> = setOf(
        "quarantine",   // XOR-obfuscated malware copies
        "datastore"     // Jetpack DataStore preferences
    )

    /** Returns true if [fileName] (relative, as delivered by FileObserver) is excluded. */
    fun isExcluded(fileName: String): Boolean =
        fileName in SELF_FILE_NAMES || SELF_DIR_PREFIXES.any { fileName == it || fileName.startsWith("$it/") || fileName.startsWith("$it.") }
}
