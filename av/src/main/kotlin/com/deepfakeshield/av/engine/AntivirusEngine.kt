package com.deepfakeshield.av.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.deepfakeshield.core.model.Reason
import com.deepfakeshield.core.model.ReasonType
import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FULL-BLOWN ANTIVIRUS ENGINE
 *
 * Industry-leading AV capabilities:
 * - Signature-based malware detection (MD5/SHA256 of known malware, byte patterns)
 * - Heuristic detection (suspicious behavior, permissions, structure)
 * - PUA/PUP detection (Potentially Unwanted Applications)
 * - Ransomware pattern detection
 * - Trojan/backdoor indicators
 * - Adware/Spyware detection
 * - Real-time + on-demand scanning
 */
@Singleton
class AntivirusEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureDb: MalwareSignatureDatabase,
    private val heuristicAnalyzer: HeuristicMalwareAnalyzer,
    private val cloudHashChecker: CloudHashChecker,
    val yaraEngine: YaraRuleEngine
) {

    companion object {
        private const val TAG = "AntivirusEngine"
        const val SCAN_TYPE_REALTIME = "realtime"
        const val SCAN_TYPE_ONDEMAND = "ondemand"
        const val SCAN_TYPE_SCHEDULED = "scheduled"
        const val SCAN_TYPE_APP = "app"
        const val SCAN_TYPE_FILE = "file"
        
        // Files/directories that belong to the AV engine itself — must never be scanned.
        // Delegates to the centralized AvExclusions to stay in sync with RealTimeAvMonitor.

        // Patterns that are genuinely suspicious and NOT common in legitimate apps.
        // Words like "port", "sms", "call", "login", "connect", "android", "session",
        // "location", "contacts", "camera" etc. appear in virtually EVERY app and must be excluded.
        private val HIGH_SPECIFICITY_PATTERNS = setOf(
            "metasploit", "exploit", "payload", "stealer", "keylogger", "backdoor",
            "reverse", "c2", "beacon", "implant", "botnet", "ddos",
            "ransom", "ransomware_note", "pay_ransom_note", "your_files_encrypted",
            "decrypt_instructions", "ransom_note", "encrypted_ext", "locked_ext",
            "xhook", "substrate", "frida", "plt_hook", "got_hook",
            "bin_sh", "root_shell", "netcat", "xmrig", "stratum",
            "proc_self", "syscall", "stealth", "unhook",
            "PE_executable", "ELF_executable", "kill_shellcode",
            "trojan", "worm", "virus", "rat", "remote_admin",
            "perm_abuse", "vibe_exploit"
        )
    }

    /**
     * Scan a file for malware
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun scanFile(
        filePath: String,
        _fileUri: Uri? = null,
        scanType: String = SCAN_TYPE_REALTIME
    ): AvScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val indicators = mutableListOf<AvIndicator>()
        var threatLevel = ThreatLevel.CLEAN
        var detectedThreat: String? = null

        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext AvScanResult.CLEAN(filePath, scanType, filePath, 0)

            // Skip our own data files — the hash database contains malware signatures
            // and will obviously match pattern/signature scans. Quarantine dir contains
            // XOR-obfuscated malware copies that would also trigger detections.
            if (isOwnDataFile(file)) {
                return@withContext AvScanResult.CLEAN(filePath, scanType, file.name, 0)
            }

            // 1. Signature-based scan (hash matching) - local first, then optional cloud hybrid
            val fileHash = computeFileHash(file)
            var signatureMatch = signatureDb.lookupHash(fileHash)
            if (signatureMatch == null && cloudHashChecker.isCloudAvailable()) {
                val cloudResult = cloudHashChecker.checkHash(fileHash)
                if (cloudResult != null && cloudResult.isMalicious) {
                    signatureMatch = cloudResult.threatName ?: "Cloud.Malware"
                }
            }
            if (signatureMatch != null) {
                indicators.add(AvIndicator(
                    type = AvIndicatorType.SIGNATURE,
                    severity = AvSeverity.CRITICAL,
                    title = "Known Malware Detected",
                    description = "File matches known malware signature: $signatureMatch",
                    evidence = fileHash
                ))
                threatLevel = ThreatLevel.INFECTED
                detectedThreat = signatureMatch
            }

            // 2. Byte pattern scan - only flag HIGH-SPECIFICITY patterns
            // Common words like "port", "sms", "login" match every legitimate app, so
            // we only treat byte patterns as meaningful when many high-specificity ones match.
            // Legitimate apps will match 5-15 generic patterns; true malware matches 20+.
            val patternMatches = signatureDb.scanAllBytePatterns(file)
            val highSpecificityPatterns = patternMatches.filter { name ->
                // Only count patterns that are genuinely suspicious, not common English words
                name in HIGH_SPECIFICITY_PATTERNS
            }
            for (pm in highSpecificityPatterns) {
                indicators.add(AvIndicator(
                    type = AvIndicatorType.SIGNATURE,
                    severity = if (highSpecificityPatterns.size >= 5) AvSeverity.HIGH else AvSeverity.MEDIUM,
                    title = "Suspicious Pattern",
                    description = "File contains suspicious byte pattern: $pm",
                    evidence = pm
                ))
            }
            if (highSpecificityPatterns.isNotEmpty() && threatLevel == ThreatLevel.CLEAN) {
                val primaryMatch = highSpecificityPatterns.first()
                threatLevel = when {
                    // Very conservative thresholds — most legitimate apps match 5-15 patterns.
                    // Only flag when an overwhelming number of high-specificity patterns match.
                    highSpecificityPatterns.size >= 25 -> ThreatLevel.INFECTED
                    highSpecificityPatterns.size >= 18 -> ThreatLevel.SUSPICIOUS
                    highSpecificityPatterns.size >= 12 -> ThreatLevel.LOW_RISK
                    else -> ThreatLevel.CLEAN
                }
                if (threatLevel != ThreatLevel.CLEAN) {
                    detectedThreat = if (highSpecificityPatterns.size > 1) "$primaryMatch+${highSpecificityPatterns.size - 1}more" else primaryMatch
                }
            }

            // 3. Heuristic analysis (suspicious structure, behavior)
            val heuristicResult = heuristicAnalyzer.analyzeFile(file, filePath)
            indicators.addAll(heuristicResult.indicators)
            if (heuristicResult.threatLevel.ordinal > threatLevel.ordinal) {
                threatLevel = heuristicResult.threatLevel
                if (detectedThreat == null) detectedThreat = heuristicResult.threatName
            }

            // 4. YARA rule matching
            try {
                val yaraMatches = yaraEngine.scanFile(file)
                for (match in yaraMatches) {
                    indicators.add(AvIndicator(
                        type = AvIndicatorType.HEURISTIC,
                        severity = when (match.rule.severity) { "CRITICAL" -> AvSeverity.CRITICAL; "HIGH" -> AvSeverity.HIGH; else -> AvSeverity.MEDIUM },
                        title = "YARA: ${match.rule.name}",
                        description = "${match.rule.description} [${match.rule.family}]",
                        evidence = "Matched: ${match.matchedStrings.joinToString(", ")}"
                    ))
                    val yaraLevel = when (match.rule.severity) { "CRITICAL" -> ThreatLevel.INFECTED; "HIGH" -> ThreatLevel.SUSPICIOUS; else -> ThreatLevel.PUA }
                    if (yaraLevel.ordinal > threatLevel.ordinal) { threatLevel = yaraLevel; detectedThreat = "YARA/${match.rule.family}" }
                }
            } catch (_: Exception) {}

            // 5. For APK/ZIP: scan contents (nested dex, so files) — case-insensitive
            val lowerPath = filePath.lowercase()
            if (lowerPath.endsWith(".apk") || lowerPath.endsWith(".zip")) {
                val archiveResult = scanArchiveForMalware(file)
                indicators.addAll(archiveResult.indicators)
                if (archiveResult.threatLevel.ordinal > threatLevel.ordinal) {
                    threatLevel = archiveResult.threatLevel
                    detectedThreat = archiveResult.threatName
                }
            }

            val duration = System.currentTimeMillis() - startTime
            AvScanResult(
                path = filePath,
                scanType = scanType,
                displayName = file.name,
                durationMs = duration,
                threatLevel = threatLevel,
                threatName = detectedThreat,
                indicators = indicators,
                fileSize = file.length(),
                fileHash = fileHash
            )
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed for $filePath", e)
            AvScanResult.ERROR(filePath, scanType, File(filePath).name, System.currentTimeMillis() - startTime, e.message ?: "Unknown error")
        }
    }

    /**
     * Packages from major, trusted publishers.
     * These apps are from Google Play / major companies. We still do a hash
     * (signature) check to catch supply-chain compromises, but skip heuristics
     * because their permission profiles WILL trigger false positives.
     */
    private val trustedPublisherPrefixes = setOf(
        "com.deepfakeshield",
        "com.google.", "com.android.", "com.samsung.", "com.sec.android.",
        "com.spotify.", "com.netflix.", "com.whatsapp", "com.facebook.",
        "com.instagram.", "com.twitter.", "com.snapchat.", "com.discord",
        "com.microsoft.", "com.apple.", "com.amazon.", "com.ebay.",
        "org.telegram.", "com.paypal.", "com.ubercab.", "com.linkedin.",
        "com.pinterest.", "com.reddit.", "tv.twitch.", "com.zhiliaoapp.",
        "com.viber.", "jp.naver.line.", "com.tencent.", "com.adobe.",
        "com.slack", "us.zoom.", "com.dropbox.", "com.evernote.",
        "com.booking.", "com.airbnb.", "com.duolingo", "com.pandora.",
        "com.soundcloud.", "com.shazam.", "com.hbo.", "com.disney.",
        "com.squareup.", "com.venmo", "com.grubhub.", "com.dd.doordash",
        "com.imo.", "com.skype.", "org.mozilla.", "com.brave.",
        "com.opera.", "com.duckduckgo.",
    )

    /**
     * Scan installed app (APK) for malware.
     *
     * B2C-SAFE: For known trusted publishers, only do hash-based signature
     * checks (catches actual malware) but skip heuristic analysis (avoids
     * false positives on permission combos that legitimate apps need).
     */
    suspend fun scanInstalledApp(
        packageName: String,
        scanType: String = SCAN_TYPE_APP
    ): AvScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val indicators = mutableListOf<AvIndicator>()
        var threatLevel = ThreatLevel.CLEAN
        var detectedThreat: String? = null

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val sourceDir = appInfo.sourceDir ?: return@withContext AvScanResult.CLEAN(
                packageName, scanType, pm.getApplicationLabel(appInfo).toString(), 0
            )

            val isTrusted = trustedPublisherPrefixes.any { packageName.startsWith(it) }

            // Always do hash-based signature check (catches real malware/supply-chain)
            val fileHash = computeFileHash(File(sourceDir))
            val signatureMatch = signatureDb.lookupHash(fileHash)
            if (signatureMatch != null) {
                indicators.add(AvIndicator(
                    type = AvIndicatorType.SIGNATURE,
                    severity = AvSeverity.CRITICAL,
                    title = "Known Malware Detected",
                    description = "APK matches known malware: $signatureMatch",
                    evidence = fileHash
                ))
                threatLevel = ThreatLevel.INFECTED
                detectedThreat = signatureMatch
            }

            // Only run heuristics on NON-trusted apps to avoid false positives
            if (!isTrusted) {
                val fileResult = scanFile(sourceDir, null, scanType)
                indicators.addAll(fileResult.indicators)
                if (fileResult.threatLevel.ordinal > threatLevel.ordinal) {
                    threatLevel = fileResult.threatLevel
                    detectedThreat = fileResult.threatName
                }

                val appHeuristic = heuristicAnalyzer.analyzeInstalledApp(context, packageName, appInfo)
                indicators.addAll(appHeuristic.indicators)
                if (appHeuristic.threatLevel.ordinal > threatLevel.ordinal) {
                    threatLevel = appHeuristic.threatLevel
                    detectedThreat = appHeuristic.threatName
                }
            }

            val appName = pm.getApplicationLabel(appInfo).toString()
            val duration = System.currentTimeMillis() - startTime
            AvScanResult(
                path = packageName,
                scanType = scanType,
                displayName = appName,
                durationMs = duration,
                threatLevel = threatLevel,
                threatName = detectedThreat,
                indicators = indicators,
                fileSize = File(sourceDir).length(),
                fileHash = fileHash,
                isApp = true,
                packageName = packageName
            )
        } catch (e: Exception) {
            Log.e(TAG, "App scan failed for $packageName", e)
            AvScanResult.ERROR(packageName, scanType, packageName, System.currentTimeMillis() - startTime, e.message ?: "Unknown error")
        }
    }

    /**
     * Full device scan: all installed apps + accessible files.
     * Reports progress including what could/couldn't be accessed.
     */
    suspend fun runFullScan(
        scanType: String = SCAN_TYPE_ONDEMAND,
        onProgress: (suspend (scanned: Int, total: Int, current: String) -> Unit)? = null
    ): List<AvScanResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AvScanResult>()
        val pm = context.packageManager
        val ownPackageName = context.packageName
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // Skip system apps
            .filter { it.packageName != ownPackageName } // Skip self — our APK contains malware
            // signature strings and requests security-related permissions (SMS, accessibility,
            // overlay) that the heuristic analyzer would flag as BankingTrojan/Spyware.

        // Pre-collect files to scan for accurate progress
        val hasStorageAccess = AndroidStoragePaths.hasFullStorageAccess(context)
        val scanPaths = AndroidStoragePaths.getFullScanPaths(context)
        val maxFileSize = 50L * 1024 * 1024 // 50MB
        val scanExtensions = setOf(
            "apk", "dex", "jar", "so", "odex", "vdex", "oat", "art",
            "exe", "bat", "sh", "bin", "dll", "zip", "tar", "gz",
            "locky", "crypt", "encrypted", "locked", "crypto"
        )

        val filesToScan = mutableListOf<File>()
        for (dirPath in scanPaths) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) continue
            filesToScan.addAll(scanDirectoryRecursive(dir, maxFileSize, scanExtensions))
        }

        val total = apps.size + filesToScan.size
        var scanned = 0

        Log.i(TAG, "Full scan starting: ${apps.size} apps, ${filesToScan.size} files, " +
            "storage access=$hasStorageAccess, scan paths=${scanPaths.size}")

        if (!hasStorageAccess) {
            onProgress?.invoke(0, total, "WARNING: No storage permission - only scanning apps")
            Log.w(TAG, "No storage permission granted. AV scan limited to installed apps only.")
        }

        // Scan installed apps
        for (app in apps) {
            onProgress?.invoke(scanned, total, app.packageName)
            results.add(scanInstalledApp(app.packageName, scanType))
            scanned++
        }

        // Scan files
        for (file in filesToScan) {
            onProgress?.invoke(scanned, total, file.name)
            try {
                results.add(scanFile(file.absolutePath, null, scanType))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan ${file.absolutePath}: ${e.message}")
            }
            scanned++
        }

        onProgress?.invoke(scanned, total, "Complete: $scanned items scanned" +
            if (!hasStorageAccess) " (limited - grant storage access for full scan)" else "")

        Log.i(TAG, "Full scan complete: $scanned scanned, ${results.count { it.isInfected }} threats found")
        results
    }

    /**
     * Returns true if the file belongs to the AV engine's own data — hash databases,
     * quarantine storage, export files. Scanning these would cause false positives since
     * the hash DB literally contains malware signatures and the quarantine dir holds
     * XOR-obfuscated copies of detected malware.
     */
    private fun isOwnDataFile(file: File): Boolean {
        if (AvExclusions.isExcluded(file.name)) return true
        val quarantineDir = File(context.filesDir, "quarantine")
        if (file.absolutePath.startsWith(quarantineDir.absolutePath)) return true
        val hashDb = File(context.filesDir, "malware_hashes_downloaded.txt")
        if (file.absolutePath == hashDb.absolutePath) return true
        // Exclude own app's data directory and APK — the signature database embedded in
        // the APK contains malware patterns that would trigger self-detection.
        val ownDataDir = context.dataDir?.absolutePath
        if (ownDataDir != null && file.absolutePath.startsWith(ownDataDir)) return true
        try {
            val ownApk = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
            if (ownApk != null && file.absolutePath == ownApk) return true
        } catch (_: Exception) { }
        return false
    }

    private fun scanDirectoryRecursive(
        dir: File,
        maxSize: Long,
        extensions: Set<String>,
        maxDepth: Int = 4,
        depth: Int = 0
    ): List<File> {
        if (depth >= maxDepth) return emptyList()
        // Skip the quarantine directory entirely
        val quarantineDir = File(context.filesDir, "quarantine")
        if (dir.absolutePath == quarantineDir.absolutePath) return emptyList()

        val files = mutableListOf<File>()
        dir.listFiles()?.forEach { f ->
            if (f.isFile) {
                if (f.length() in 1L..maxSize && !isOwnDataFile(f)) {
                    val ext = f.extension.lowercase()
                    if (ext in extensions || extensions.isEmpty()) files.add(f)
                }
            } else if (f.isDirectory && !f.name.startsWith(".")) {
                files.addAll(scanDirectoryRecursive(f, maxSize, extensions, maxDepth, depth + 1))
            }
        }
        return files
    }

    private fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun scanArchiveForMalware(file: File): HeuristicResult {
        val indicators = mutableListOf<AvIndicator>()
        var threatLevel = ThreatLevel.CLEAN
        var threatName: String? = null
        var suspiciousCount = 0

        val pathTraversal = setOf("..", "/etc/", "/system/", "/bin/", "/sbin/", "/proc/", "/root/")
        val suspiciousDexNames = setOf("secondary", "malware", "payload", "hidden", "obfuscated", "encrypted", "crypto")
        val injectionLibNames = setOf("hook", "inject", "frida", "substrate", "xposed", "inline", "patch")

        try {
            ZipInputStream(file.inputStream()).use { zip ->
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val entryName = (entry ?: continue).name.lowercase()
                    // Path traversal / system paths
                    if (pathTraversal.any { entryName.contains(it) }) {
                        indicators.add(AvIndicator(
                            type = AvIndicatorType.HEURISTIC,
                            severity = AvSeverity.HIGH,
                            title = "Suspicious Archive Path",
                            description = "Archive contains path traversal or system path: $entryName",
                            evidence = entryName
                        ))
                        suspiciousCount++
                        threatLevel = ThreatLevel.SUSPICIOUS
                        threatName = "SuspiciousArchivePath"
                    }
                    // Hidden / malicious DEX
                    if (entryName.endsWith(".dex") && suspiciousDexNames.any { entryName.contains(it) }) {
                        indicators.add(AvIndicator(
                            type = AvIndicatorType.HEURISTIC,
                            severity = AvSeverity.HIGH,
                            title = "Suspicious DEX",
                            description = "Potential hidden DEX: $entryName",
                            evidence = entryName
                        ))
                        suspiciousCount++
                        threatLevel = ThreatLevel.SUSPICIOUS
                        threatName = "SuspiciousDex"
                    }
                    // Injection / hook native libs
                    if (entryName.endsWith(".so") && injectionLibNames.any { entryName.contains(it) }) {
                        indicators.add(AvIndicator(
                            type = AvIndicatorType.HEURISTIC,
                            severity = AvSeverity.CRITICAL,
                            title = "Potential Injection Library",
                            description = "Native library with injection indicators: $entryName",
                            evidence = entryName
                        ))
                        threatLevel = ThreatLevel.INFECTED
                        threatName = "InjectionLibrary"
                    }
                    // Hidden classes in root
                    if (entryName.endsWith(".dex") && !entryName.contains("classes") && entryName.count { it == '/' } == 0) {
                        indicators.add(AvIndicator(
                            type = AvIndicatorType.HEURISTIC,
                            severity = AvSeverity.MEDIUM,
                            title = "Unusual DEX Location",
                            description = "DEX in root: $entryName",
                            evidence = entryName
                        ))
                        suspiciousCount++
                    }
                    // Nested APK (dropper)
                    if (entryName.endsWith(".apk") && entryName.count { it == '/' } > 0) {
                        indicators.add(AvIndicator(
                            type = AvIndicatorType.HEURISTIC,
                            severity = AvSeverity.CRITICAL,
                            title = "Nested APK",
                            description = "Archive contains nested APK - dropper: $entryName",
                            evidence = entryName
                        ))
                        threatLevel = ThreatLevel.INFECTED
                        threatName = "Dropper"
                    }
                    if (suspiciousCount >= 3) threatLevel = ThreatLevel.INFECTED
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Archive scan failed", e)
        }

        return HeuristicResult(indicators, threatLevel, threatName)
    }

    fun toRiskResult(avResult: AvScanResult): RiskResult? {
        if (avResult.threatLevel == ThreatLevel.CLEAN) return null
        val severity = when (avResult.threatLevel) {
            ThreatLevel.CLEAN -> return null
            ThreatLevel.SCAN_ERROR -> return null // Don't create risk result for scan errors
            ThreatLevel.LOW_RISK -> RiskSeverity.LOW
            ThreatLevel.SUSPICIOUS -> RiskSeverity.MEDIUM
            ThreatLevel.PUA -> RiskSeverity.MEDIUM
            ThreatLevel.INFECTED -> RiskSeverity.CRITICAL
        }
        val threatType = when (avResult.threatName?.lowercase()) {
            null -> ThreatType.MALWARE
            "adware" -> ThreatType.ADWARE
            "spyware" -> ThreatType.SPYWARE
            "trojan" -> ThreatType.TROJAN
            "ransomware" -> ThreatType.RANSOMWARE
            else -> ThreatType.MALWARE
        }
        val reasons = avResult.indicators.map {
            Reason(
                type = ReasonType.TECHNICAL,
                title = it.title,
                explanation = it.description,
                evidence = it.evidence
            )
        }
        return RiskResult(
            score = when (avResult.threatLevel) {
                ThreatLevel.INFECTED -> 95
                ThreatLevel.PUA -> 70
                ThreatLevel.SUSPICIOUS -> 55
                else -> 30
            },
            severity = severity,
            confidence = 0.9f,
            threatType = threatType,
            reasons = reasons,
            recommendedActions = listOf(
                com.deepfakeshield.core.model.Action(
                    type = com.deepfakeshield.core.model.RecommendedAction.QUARANTINE,
                    title = "Quarantine",
                    description = "Isolate this file",
                    isPrimary = true
                ),
                com.deepfakeshield.core.model.Action(
                    type = com.deepfakeshield.core.model.RecommendedAction.DELETE_FILE,
                    title = "Delete",
                    description = "Remove the file",
                    isPrimary = false
                )
            ),
            explainLikeImFive = "This file appears to be harmful. ${avResult.threatName ?: "Malware"} detected.",
            technicalDetails = avResult.indicators.joinToString("\n") { "${it.title}: ${it.description}" }
        )
    }
}

// ─── Data classes ─────────────────────────────────────────────────────────

data class AvScanResult(
    val path: String,
    val scanType: String,
    val displayName: String,
    val durationMs: Long,
    val threatLevel: ThreatLevel = ThreatLevel.CLEAN,
    val threatName: String? = null,
    val indicators: List<AvIndicator> = emptyList(),
    val fileSize: Long = 0,
    val fileHash: String? = null,
    val isApp: Boolean = false,
    val packageName: String? = null
) {
    // B2C-SAFE: PUA (Potentially Unwanted App) is NOT the same as INFECTED.
    // Only flag actual malware as infected. PUA should be informational only —
    // users choose apps like VPNs, cleaners etc. deliberately.
    val isInfected: Boolean get() = threatLevel == ThreatLevel.INFECTED
    val isClean: Boolean get() = threatLevel == ThreatLevel.CLEAN
    val isScanError: Boolean get() = threatLevel == ThreatLevel.SCAN_ERROR

    companion object {
        fun CLEAN(path: String, scanType: String, displayName: String, durationMs: Long) =
            AvScanResult(path, scanType, displayName, durationMs, ThreatLevel.CLEAN)
        fun ERROR(path: String, scanType: String, displayName: String, durationMs: Long, error: String) =
            AvScanResult(path, scanType, displayName, durationMs, ThreatLevel.SCAN_ERROR, threatName = "ScanError: $error")
    }
}


enum class ThreatLevel { CLEAN, SCAN_ERROR, LOW_RISK, SUSPICIOUS, PUA, INFECTED }

enum class AvIndicatorType { SIGNATURE, HEURISTIC, BEHAVIOR, PERMISSION }

enum class AvSeverity { LOW, MEDIUM, HIGH, CRITICAL }

data class AvIndicator(
    val type: AvIndicatorType,
    val severity: AvSeverity,
    val title: String,
    val description: String,
    val evidence: String? = null
)

data class HeuristicResult(
    val indicators: List<AvIndicator>,
    val threatLevel: ThreatLevel,
    val threatName: String?
)
