package com.deepfakeshield.feature.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.av.engine.AvScanResult
import com.deepfakeshield.av.engine.MalwareSignatureDatabase
import com.deepfakeshield.av.engine.QuarantineEntry
import com.deepfakeshield.av.engine.QuarantineManager
import com.deepfakeshield.av.engine.RootDetector
import com.deepfakeshield.av.engine.ThreatDatabaseUpdater
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class PermissionStatus(
    val name: String,
    val granted: Boolean,
    val required: Boolean = true
)

data class SystemCheck(
    val name: String,
    val status: String,
    val passed: Boolean
)

data class ThreatDbStatus(
    val totalHashes: Int = 0,
    val bundledHashes: Int = 0,
    val downloadedHashes: Int = 0,
    val lastUpdateTimeMs: Long = 0L,
    val lastUpdateStatus: String = "Never updated",
    val isUpdating: Boolean = false,
    val updateMessage: String? = null
)

data class DiagnosticsUiState(
    val allSystemsHealthy: Boolean = true,
    val antivirusStatus: String = "Real-time active",
    val isRunningAvScan: Boolean = false,
    val avScanProgress: String = "",
    val avScanResults: List<AvScanResult> = emptyList(),
    val avThreatsFound: Int = 0,
    val videoShieldStatus: String = "Checking...",
    val videoShieldHealthy: Boolean = true,
    val messageShieldStatus: String = "Checking...",
    val messageShieldHealthy: Boolean = true,
    val callShieldStatus: String = "Checking...",
    val callShieldHealthy: Boolean = true,
    val permissions: List<PermissionStatus> = emptyList(),
    val systemChecks: List<SystemCheck> = emptyList(),
    val isRunningTest: Boolean = false,
    val testComplete: Boolean = false,
    val alertCount: Int = 0,
    val dbHealthy: Boolean = true,
    val rootStatus: RootDetector.RootStatus? = null,
    val error: String? = null,
    // Tier 1: Threat DB status
    val threatDbStatus: ThreatDbStatus = ThreatDbStatus(),
    // Tier 2: Quarantine
    val quarantinedItems: List<QuarantineEntry> = emptyList(),
    val quarantineMessage: String? = null,
    // Tier 2: AV Settings
    val autoQuarantineEnabled: Boolean = true,
    val batteryOptimizedScan: Boolean = true
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    private val antivirusEngine: AntivirusEngine,
    private val rootDetector: RootDetector,
    private val signatureDb: MalwareSignatureDatabase,
    private val threatDbUpdater: ThreatDatabaseUpdater,
    private val quarantineManager: QuarantineManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var avScanJob: kotlinx.coroutines.Job? = null
    private var selfTestJob: kotlinx.coroutines.Job? = null
    private var dbUpdateJob: kotlinx.coroutines.Job? = null

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val rootStatus = withContext(Dispatchers.IO) { rootDetector.checkRootStatus() }
            _uiState.update { it.copy(rootStatus = rootStatus) }

            // Load threat DB status
            refreshThreatDbStatus()

            // Load quarantine list
            refreshQuarantineList()

            // Load AV settings
            launch {
                userPreferences.autoQuarantineOnThreat.collect { enabled ->
                    _uiState.update { it.copy(autoQuarantineEnabled = enabled) }
                }
            }
            launch {
                userPreferences.batteryOptimizedScan.collect { enabled ->
                    _uiState.update { it.copy(batteryOptimizedScan = enabled) }
                }
            }

            // Check permissions
            val permissions = checkPermissions()
            _uiState.update { it.copy(permissions = permissions) }

            // Monitor shield states
            combine(
                userPreferences.videoShieldEnabled,
                userPreferences.messageShieldEnabled,
                userPreferences.callShieldEnabled,
                alertRepository.getUnhandledCount()
            ) { video, message, call, alertCount ->
                _uiState.update { current ->
                    val shieldsHealthy = video && message && call
                    current.copy(
                        videoShieldStatus = if (video) "Active" else "Disabled",
                        videoShieldHealthy = video,
                        messageShieldStatus = if (message) "Active" else "Disabled",
                        messageShieldHealthy = message,
                        callShieldStatus = if (call) "Active" else "Disabled",
                        callShieldHealthy = call,
                        allSystemsHealthy = if (current.testComplete) current.allSystemsHealthy else shieldsHealthy,
                        alertCount = alertCount
                    )
                }
            }.catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("DiagnosticsVM", "Flow error: ${e.message}")
            }.collect()
        }
    }

    // ─── Threat DB Management (Tier 1) ───

    private fun refreshThreatDbStatus() {
        _uiState.update { current ->
            current.copy(
                threatDbStatus = ThreatDbStatus(
                    totalHashes = signatureDb.getHashCount(),
                    bundledHashes = signatureDb.bundledHashCount,
                    downloadedHashes = signatureDb.downloadedHashCount,
                    lastUpdateTimeMs = threatDbUpdater.getLastUpdateTimeMs(),
                    lastUpdateStatus = threatDbUpdater.getLastUpdateStatus(),
                    isUpdating = current.threatDbStatus.isUpdating,
                    updateMessage = current.threatDbStatus.updateMessage
                )
            )
        }
    }

    fun forceUpdateThreatDb() {
        if (dbUpdateJob?.isActive == true) return
        dbUpdateJob = viewModelScope.launch {
            _uiState.update { it.copy(threatDbStatus = it.threatDbStatus.copy(isUpdating = true, updateMessage = "Fetching threat feeds...")) }
            try {
                val result = threatDbUpdater.updateFromDefaultFeeds()
                val msg = when (result) {
                    is ThreatDatabaseUpdater.UpdateResult.Success -> "Updated! ${result.hashesAdded} hashes from ${result.source}"
                    is ThreatDatabaseUpdater.UpdateResult.Failure -> "Failed: ${result.error}"
                }
                // Refresh counts FIRST from the now-reloaded signatureDb, then apply message
                _uiState.update { current ->
                    current.copy(
                        threatDbStatus = ThreatDbStatus(
                            totalHashes = signatureDb.getHashCount(),
                            bundledHashes = signatureDb.bundledHashCount,
                            downloadedHashes = signatureDb.downloadedHashCount,
                            lastUpdateTimeMs = threatDbUpdater.getLastUpdateTimeMs(),
                            lastUpdateStatus = threatDbUpdater.getLastUpdateStatus(),
                            isUpdating = false,
                            updateMessage = msg
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Also refresh counts on error so display is never stale
                _uiState.update { current ->
                    current.copy(
                        threatDbStatus = ThreatDbStatus(
                            totalHashes = signatureDb.getHashCount(),
                            bundledHashes = signatureDb.bundledHashCount,
                            downloadedHashes = signatureDb.downloadedHashCount,
                            lastUpdateTimeMs = threatDbUpdater.getLastUpdateTimeMs(),
                            lastUpdateStatus = threatDbUpdater.getLastUpdateStatus(),
                            isUpdating = false,
                            updateMessage = "Error: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun clearDbUpdateMessage() {
        _uiState.update { it.copy(threatDbStatus = it.threatDbStatus.copy(updateMessage = null)) }
    }

    // ─── Quarantine Management (Tier 2) ───

    private fun refreshQuarantineList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = quarantineManager.listQuarantined()
                _uiState.update { it.copy(quarantinedItems = items) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("DiagnosticsVM", "Failed to load quarantine: ${e.message}")
            }
        }
    }

    fun restoreQuarantinedItem(entry: QuarantineEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = quarantineManager.restore(entry)
                val items = quarantineManager.listQuarantined()
                _uiState.update { it.copy(
                    quarantineMessage = if (success) "Restored: ${entry.displayName}" else "Failed to restore: ${entry.displayName}",
                    quarantinedItems = items
                )}
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(quarantineMessage = "Error: ${e.message}") }
            }
        }
    }

    fun deleteQuarantinedItem(entry: QuarantineEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = quarantineManager.delete(entry)
                val items = quarantineManager.listQuarantined()
                _uiState.update { it.copy(
                    quarantineMessage = if (success) "Deleted: ${entry.displayName}" else "Failed to delete: ${entry.displayName}",
                    quarantinedItems = items
                )}
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(quarantineMessage = "Error: ${e.message}") }
            }
        }
    }

    fun clearQuarantineMessage() {
        _uiState.update { it.copy(quarantineMessage = null) }
    }

    // ─── AV Settings (Tier 2) ───

    fun setAutoQuarantine(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAutoQuarantineOnThreat(enabled)
        }
    }

    fun setBatteryOptimizedScan(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setBatteryOptimizedScan(enabled)
        }
    }

    // ─── AV Scan ───

    private fun checkPermissions(): List<PermissionStatus> {
        val permissions = mutableListOf<PermissionStatus>()

        val hasStorageAccess = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
        permissions.add(PermissionStatus("File Access (AV Scan)", hasStorageAccess, required = true))
        permissions.add(PermissionStatus("SMS Reading",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED, required = true))
        permissions.add(PermissionStatus("Phone State",
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED, required = true))
        permissions.add(PermissionStatus("Call Log",
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED, required = false))
        permissions.add(PermissionStatus("Notifications",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, required = true))
        permissions.add(PermissionStatus("Microphone",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, required = false))

        return permissions
    }

    fun runFullAvScan() {
        if (avScanJob?.isActive == true) return
        avScanJob = viewModelScope.launch {
            _uiState.update { it.copy(isRunningAvScan = true, avScanProgress = "Starting...", avScanResults = emptyList(), avThreatsFound = 0) }
            try {
                val results = antivirusEngine.runFullScan(
                    scanType = AntivirusEngine.SCAN_TYPE_ONDEMAND
                ) { scanned, total, current ->
                    _uiState.update { it.copy(avScanProgress = "Scanning $current ($scanned/$total)") }
                }
                val threats = results.filter { it.isInfected }
                val quarantineItems = try { quarantineManager.listQuarantined() } catch (_: Exception) { emptyList() }
                _uiState.update { it.copy(isRunningAvScan = false, avScanProgress = "Complete", avScanResults = results, avThreatsFound = threats.size, quarantinedItems = quarantineItems) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(
                    isRunningAvScan = false,
                    avScanProgress = "Error: ${e.message}",
                    error = "Scan failed: ${e.message ?: "Unknown error"}. Check storage permissions."
                )}
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun runSelfTest() {
        if (selfTestJob?.isActive == true) return
        selfTestJob = viewModelScope.launch {
            _uiState.update { it.copy(isRunningTest = true, testComplete = false, systemChecks = emptyList(), error = null) }

            val checks = mutableListOf<SystemCheck>()

            try {
                // Check 1: Database connectivity
                try {
                    val result = withTimeoutOrNull(5000L) { alertRepository.getAllAlerts().first() }
                    if (result != null) {
                        checks.add(SystemCheck("Database", "OK - ${result.size} alerts stored", true))
                        _uiState.update { it.copy(systemChecks = checks.toList(), dbHealthy = true) }
                    } else {
                        checks.add(SystemCheck("Database", "TIMEOUT - did not respond in 5s", false))
                        _uiState.update { it.copy(systemChecks = checks.toList(), dbHealthy = false) }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    checks.add(SystemCheck("Database", "FAILED: ${e.message}", false))
                    _uiState.update { it.copy(systemChecks = checks.toList(), dbHealthy = false) }
                }

                // Check 2: Threat DB
                val hashCount = signatureDb.getHashCount()
                val hasDownloaded = signatureDb.hasDownloadedHashes()
                checks.add(SystemCheck(
                    "Threat Database",
                    "$hashCount hashes loaded" + if (hasDownloaded) " (feeds active)" else " (bundled only - update needed)",
                    hashCount > 0
                ))
                _uiState.update { it.copy(systemChecks = checks.toList()) }

                // Check 3: Shield status
                val videoEnabled = userPreferences.videoShieldEnabled.first()
                val messageEnabled = userPreferences.messageShieldEnabled.first()
                val callEnabled = userPreferences.callShieldEnabled.first()
                checks.add(SystemCheck("Video Shield", if (videoEnabled) "ACTIVE" else "DISABLED", videoEnabled))
                checks.add(SystemCheck("Message Shield", if (messageEnabled) "ACTIVE" else "DISABLED", messageEnabled))
                checks.add(SystemCheck("Call Shield", if (callEnabled) "ACTIVE" else "DISABLED", callEnabled))
                _uiState.update { it.copy(systemChecks = checks.toList()) }

                // Check 4: Permissions
                val permissions = checkPermissions()
                val criticalPermissions = permissions.filter { it.required }
                val allCriticalGranted = criticalPermissions.all { it.granted }
                checks.add(SystemCheck(
                    "Permissions",
                    if (allCriticalGranted) "All critical granted" else "${criticalPermissions.count { !it.granted }} missing",
                    allCriticalGranted
                ))
                _uiState.update { it.copy(systemChecks = checks.toList(), permissions = permissions) }

                // Check 5: Storage access
                val hasStorageAccess = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                    else -> true
                }
                val freeSpace = context.cacheDir.freeSpace / (1024 * 1024)
                checks.add(SystemCheck(
                    "Storage Access",
                    if (hasStorageAccess) "Full access granted (${freeSpace}MB free)" else "NOT GRANTED",
                    hasStorageAccess
                ))
                _uiState.update { it.copy(systemChecks = checks.toList()) }

                // Check 6: Risk engine
                withContext(Dispatchers.IO) {
                    try {
                        val textAnalyzer = com.deepfakeshield.ml.heuristics.ProductionHeuristicTextAnalyzer()
                        val testResult = textAnalyzer.analyzeText("Hello, this is a test message.")
                        val engineWorking = testResult.confidence >= 0f
                        checks.add(SystemCheck("Risk Engine", if (engineWorking) "Operational (${(testResult.confidence * 100).toInt()}% risk)" else "ERROR", engineWorking))
                    } catch (e: Exception) {
                        checks.add(SystemCheck("Risk Engine", "FAILED: ${e.message}", false))
                    }
                }
                _uiState.update { it.copy(systemChecks = checks.toList()) }

                // Check 7: Heuristic analyzers
                withContext(Dispatchers.IO) {
                    var loadedCount = 0
                    val totalAnalyzers = 3
                    try { com.deepfakeshield.ml.heuristics.ProductionHeuristicTextAnalyzer(); loadedCount++ } catch (_: Exception) {}
                    try { com.deepfakeshield.ml.heuristics.ProductionHeuristicAudioAnalyzer(); loadedCount++ } catch (_: Exception) {}
                    try { com.deepfakeshield.ml.heuristics.ProductionHeuristicVideoAnalyzer(); loadedCount++ } catch (_: Exception) {}
                    checks.add(SystemCheck("Heuristic Analyzers", "$loadedCount/$totalAnalyzers loaded", loadedCount == totalAnalyzers))
                }
                _uiState.update { it.copy(systemChecks = checks.toList()) }

                // Final assessment
                val allPassed = checks.all { it.passed }
                _uiState.update { it.copy(isRunningTest = false, testComplete = true, allSystemsHealthy = allPassed, systemChecks = checks.toList()) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isRunningTest = false, testComplete = true, allSystemsHealthy = false, error = "Self-test failed: ${e.message}") }
            }
        }
    }
}
