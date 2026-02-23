package com.deepfakeshield.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.av.engine.ThreatDatabaseUpdater
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.enterprise.ManagedConfigHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val isManagedConfig: Boolean = false,
    val isExportAllowedByAdmin: Boolean = true,
    val appVersion: String = "1.0.0",
    val isBatteryOptimized: Boolean = false,
    val masterProtection: Boolean = true,
    val videoShield: Boolean = true,
    val messageShield: Boolean = true,
    val callShield: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val clipboardScanning: Boolean = false,
    val protectionLevel: String = "balanced",
    val alertSensitivity: String = "medium",
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val dataRetentionDays: Int = 90,
    val autoDeleteHandled: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val crashlyticsEnabled: Boolean = false,
    val alertCount: Int = 0,
    val threatDbHashCount: Int = 0,
    val threatDbUpdateInProgress: Boolean = false,
    val threatDbUpdateMessage: String? = null,
    val autoQuarantineOnThreat: Boolean = true,
    val themeMode: String = "system",
    val exportStatus: ExportStatus = ExportStatus.IDLE,
    val exportError: String? = null,
    val batteryExemptionRequested: Boolean = false
)

enum class ExportStatus { IDLE, EXPORTING, SUCCESS, ERROR }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    private val threatDbUpdater: ThreatDatabaseUpdater,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.masterProtectionEnabled,
                userPreferences.videoShieldEnabled,
                userPreferences.messageShieldEnabled,
                userPreferences.callShieldEnabled,
                userPreferences.notificationsEnabled
            ) { master, video, message, call, notifications ->
                _uiState.update {
                    it.copy(
                        masterProtection = master,
                        videoShield = video,
                        messageShield = message,
                        callShield = call,
                        notificationsEnabled = notifications
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            combine(
                userPreferences.clipboardScanningEnabled,
                userPreferences.protectionLevel,
                userPreferences.alertSensitivity,
                userPreferences.quietHoursEnabled,
                userPreferences.dataRetentionDays
            ) { clipboard, protection, sensitivity, quietHours, retention ->
                _uiState.update {
                    it.copy(
                        clipboardScanning = clipboard,
                        protectionLevel = protection,
                        alertSensitivity = sensitivity,
                        quietHoursEnabled = quietHours,
                        dataRetentionDays = retention
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            userPreferences.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
        // Collect quiet hours start/end separately
        viewModelScope.launch {
            combine(
                userPreferences.quietHoursStart,
                userPreferences.quietHoursEnd
            ) { start, end ->
                _uiState.update {
                    it.copy(quietHoursStart = start, quietHoursEnd = end)
                }
            }.collect()
        }
        viewModelScope.launch {
            combine(
                userPreferences.autoDeleteHandled,
                userPreferences.analyticsEnabled,
                userPreferences.crashlyticsEnabled,
                userPreferences.autoQuarantineOnThreat,
                alertRepository.getUnhandledCount()
            ) { autoDelete, analytics, crashlytics, autoQuarantine, alertCount ->
                _uiState.update {
                    it.copy(
                        autoDeleteHandled = autoDelete,
                        analyticsEnabled = analytics,
                        crashlyticsEnabled = crashlytics,
                        autoQuarantineOnThreat = autoQuarantine,
                        alertCount = alertCount
                    )
                }
            }.catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SettingsVM", "Flow error: ${e.message}")
            }.collect()
        }
        // Load settings that may involve disk I/O off the main thread
        viewModelScope.launch(Dispatchers.IO) {
            val hashCount = try { threatDbUpdater.getCurrentHashCount() } catch (_: Exception) { 0 }
            val isManaged = try { ManagedConfigHelper.isManaged(context) } catch (_: Exception) { false }
            val exportAllowed = try { ManagedConfigHelper.isExportAllowed(context) } catch (_: Exception) { true }
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) { "1.0.0" }
            val batteryOptimized = isAppBatteryOptimized(context)
            _uiState.update {
                it.copy(
                    threatDbHashCount = hashCount,
                    isManagedConfig = isManaged,
                    isExportAllowedByAdmin = exportAllowed,
                    appVersion = version,
                    isBatteryOptimized = batteryOptimized
                )
            }
        }
    }

    fun updateThreatDatabase() {
        viewModelScope.launch {
            _uiState.update { it.copy(threatDbUpdateInProgress = true, threatDbUpdateMessage = null) }
            when (val r = threatDbUpdater.updateFromDefaultFeeds()) {
                is ThreatDatabaseUpdater.UpdateResult.Success -> {
                    _uiState.update {
                        it.copy(
                            threatDbUpdateInProgress = false,
                            threatDbUpdateMessage = "✓ Added ${r.hashesAdded} malware hashes from open-source feed",
                            threatDbHashCount = threatDbUpdater.getCurrentHashCount()
                        )
                    }
                }
                is ThreatDatabaseUpdater.UpdateResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            threatDbUpdateInProgress = false,
                            threatDbUpdateMessage = "Failed: ${r.error}"
                        )
                    }
                }
            }
        }
    }

    fun clearThreatDbMessage() {
        _uiState.update { it.copy(threatDbUpdateMessage = null) }
    }

    fun setMasterProtection(enabled: Boolean) { viewModelScope.launch { userPreferences.setMasterProtection(enabled) } }
    fun setVideoShield(enabled: Boolean) { viewModelScope.launch { userPreferences.setVideoShield(enabled) } }
    fun setMessageShield(enabled: Boolean) { viewModelScope.launch { userPreferences.setMessageShield(enabled) } }
    fun setCallShield(enabled: Boolean) { viewModelScope.launch { userPreferences.setCallShield(enabled) } }
    fun setNotifications(enabled: Boolean) { viewModelScope.launch { userPreferences.setNotifications(enabled) } }
    fun setClipboardScanning(enabled: Boolean) { viewModelScope.launch { userPreferences.setClipboardScanning(enabled) } }
    fun setAutoQuarantineOnThreat(enabled: Boolean) { viewModelScope.launch { userPreferences.setAutoQuarantineOnThreat(enabled) } }
    fun setProtectionLevel(level: String) { viewModelScope.launch { userPreferences.setProtectionLevel(level) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { userPreferences.setThemeMode(mode) } }
    fun setAlertSensitivity(sensitivity: String) { viewModelScope.launch { userPreferences.setAlertSensitivity(sensitivity) } }
    fun setQuietHours(enabled: Boolean) { viewModelScope.launch { userPreferences.setQuietHours(enabled) } }
    fun setQuietHoursStart(hour: Int) {
        viewModelScope.launch {
            val enabled = userPreferences.quietHoursEnabled.first()
            userPreferences.setQuietHours(enabled, start = hour)
        }
    }
    fun setQuietHoursEnd(hour: Int) {
        viewModelScope.launch {
            val enabled = userPreferences.quietHoursEnabled.first()
            userPreferences.setQuietHours(enabled, end = hour)
        }
    }
    fun setDataRetentionDays(days: Int) { viewModelScope.launch { userPreferences.setDataRetentionDays(days) } }
    fun setAutoDeleteHandled(enabled: Boolean) { viewModelScope.launch { userPreferences.setAutoDeleteHandled(enabled) } }
    fun setAnalyticsEnabled(enabled: Boolean) { viewModelScope.launch { userPreferences.setAnalyticsEnabled(enabled) } }
    fun setCrashlyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setCrashlyticsEnabled(enabled) }
    }

    fun clearExportStatus() {
        _uiState.update { it.copy(exportStatus = ExportStatus.IDLE, exportError = null) }
    }

    fun exportAllData(format: ExportFormat = ExportFormat.JSON) {
        viewModelScope.launch {
            try {
                if (!ManagedConfigHelper.isExportAllowed(context)) {
                    _uiState.update { it.copy(exportStatus = ExportStatus.ERROR, exportError = "Export disabled by admin policy") }
                    return@launch
                }
                _uiState.update { it.copy(exportStatus = ExportStatus.EXPORTING, exportError = null) }
                val alerts = alertRepository.getAllAlertsUnbounded().first()
                val (content, file, mimeType) = when (format) {
                    ExportFormat.JSON -> {
                        val json = buildString {
                            append("""{"export_date":${System.currentTimeMillis()},"format":"GDPR","alerts":[""")
                            alerts.forEachIndexed { i, a ->
                                val esc = escapeJsonString(a.summary)
                                val contentEsc = escapeJsonString(a.content ?: "")
                                append("""{"type":"${escapeJsonString(a.threatType.name)}","severity":"${escapeJsonString(a.severity.name)}","score":${a.score},"summary":"$esc","content":"$contentEsc","timestamp":${a.timestamp}}""")
                                if (i < alerts.lastIndex) append(",")
                            }
                            appendLine("]}")
                        }
                        Triple(json, File(context.cacheDir, "deepfakeshield_export.json"), "application/json")
                    }
                    ExportFormat.CSV -> {
                        val csv = buildString {
                            appendLine("timestamp,type,severity,score,summary,content,source")
                            alerts.forEach { a ->
                                val summaryEsc = a.summary.replace("\r", "").replace("\n", " ").replace("\"", "\"\"")
                                val contentEsc = (a.content ?: "").replace("\r", "").replace("\n", " ").replace("\"", "\"\"")
                                val sourceEsc = (a.senderInfo ?: "").replace("\r", "").replace("\n", " ").replace("\"", "\"\"")
                                appendLine("${a.timestamp},\"${a.threatType}\",\"${a.severity}\",${a.score},\"$summaryEsc\",\"$contentEsc\",\"$sourceEsc\"")
                            }
                        }
                        Triple(csv, File(context.cacheDir, "deepfakeshield_export.csv"), "text/csv")
                    }
                }
                withContext(Dispatchers.IO) { file.writeText(content) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setTypeAndNormalize(mimeType)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "Export Data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    _uiState.update { it.copy(exportStatus = ExportStatus.ERROR, exportError = "No app available to share exported data") }
                    return@launch
                }
                _uiState.update { it.copy(exportStatus = ExportStatus.SUCCESS) }
                // Auto-reset export status after 3 seconds so button text reverts
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(exportStatus = ExportStatus.IDLE, exportError = null) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SettingsVM", "Export failed: ${e.message}", e)
                _uiState.update { it.copy(exportStatus = ExportStatus.ERROR, exportError = "Export failed: ${e.message}") }
            }
        }
    }

    fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _uiState.update { it.copy(batteryExemptionRequested = true) }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Could not open battery settings: ${e.message}")
            }
        }
    }

    /** Re-check battery optimization status (call after returning from settings) */
    fun refreshBatteryStatus() {
        _uiState.update { it.copy(isBatteryOptimized = isAppBatteryOptimized(context)) }
    }

    private fun escapeJsonString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace(Regex("[\\u0000-\\u001F&&[^\\n\\r\\t\\b\\u000C]]")) { "\\u%04x".format(it.value[0].code) }
    }

    private fun isAppBatteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

enum class ExportFormat { JSON, CSV }
