package com.deepfakeshield.feature.vault

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.data.entity.VaultEntryEntity
import com.deepfakeshield.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class VaultEntry(
    val id: Long,
    val alertId: Long?,
    val type: String,
    val description: String,
    val timestamp: Long,
    val severity: RiskSeverity
)

data class VaultUiState(
    val entries: List<VaultEntry> = emptyList(),
    val searchResults: List<VaultEntry>? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                vaultRepository.getAllEntries().collect { entries ->
                    _uiState.update {
                        it.copy(
                            entries = entries.map { entry ->
                                VaultEntry(
                                    id = entry.id,
                                    alertId = entry.alertId,
                                    type = entry.title,
                                    description = entry.description,
                                    timestamp = entry.createdAt,
                                    severity = entry.severity
                                )
                            },
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load vault")
                }
            }
        }
    }

    fun exportVault() {
        exportJob?.cancel() // Cancel any in-flight export
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccess = false, error = null) }

            try {
                val entries = vaultRepository.getAllEntries().first()

                if (entries.isEmpty()) {
                    _uiState.update { it.copy(isExporting = false, error = "No entries to export") }
                    return@launch
                }

                // Write JSON to file
                val file = withContext(Dispatchers.IO) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
                    val fileName = "vault_export_${dateFormat.format(Date())}.json"
                    val exportFile = File(context.cacheDir, fileName)

                    val jsonData = buildString {
                        appendLine("{")
                        appendLine("  \"export_date\": \"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())}\",")
                        appendLine("  \"app_version\": \"1.0.0\",")
                        appendLine("  \"entry_count\": ${entries.size},")
                        appendLine("  \"entries\": [")
                        entries.forEachIndexed { index, entry ->
                            appendLine("    {")
                            appendLine("      \"id\": ${entry.id},")
                            appendLine("      \"alert_id\": ${entry.alertId ?: "null"},")
                            appendLine("      \"entry_type\": \"${jsonEscape(entry.entryType)}\",")
                            appendLine("      \"title\": \"${jsonEscape(entry.title)}\",")
                            appendLine("      \"description\": \"${jsonEscape(entry.description)}\",")
                            appendLine("      \"severity\": \"${entry.severity}\",")
                            appendLine("      \"created_at\": ${entry.createdAt},")
                            appendLine("      \"is_archived\": ${entry.isArchived}")
                            appendLine("    }${if (index < entries.size - 1) "," else ""}")
                        }
                        appendLine("  ]")
                        appendLine("}")
                    }

                    exportFile.writeText(jsonData)
                    exportFile
                }

                // Share the file via Android share sheet
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Cyble - Vault Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(shareIntent, "Export Vault Data")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                _uiState.update { it.copy(isExporting = false, exportSuccess = true) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(isExporting = false, error = "Export failed: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearExportSuccess() {
        _uiState.update { it.copy(exportSuccess = false) }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(isExporting = false, error = null, exportSuccess = false) }
    }

    /** Threat hunting - search vault by IOC, hash, domain, keyword */
    fun searchVault(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(searchQuery = "", searchResults = null) }
                return@launch
            }
            _uiState.update { it.copy(searchQuery = query) }
            try {
                val results = withContext(Dispatchers.IO) {
                    vaultRepository.searchEntries(query)
                }
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        searchResults = results.map { entry ->
                            VaultEntry(
                                id = entry.id,
                                alertId = entry.alertId,
                                type = entry.title,
                                description = entry.description,
                                timestamp = entry.createdAt,
                                severity = entry.severity
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(searchResults = emptyList(), error = "Search failed") }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = null) }
    }

    private fun jsonEscape(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace(Regex("[\\u0000-\\u001F&&[^\\n\\r\\t\\b\\u000C]]")) {
                "\\u%04x".format(it.value[0].code)
            }
    }
}
