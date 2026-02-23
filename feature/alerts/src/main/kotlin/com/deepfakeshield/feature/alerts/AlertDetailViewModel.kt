package com.deepfakeshield.feature.alerts

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.repository.AuditLogRepository
import com.deepfakeshield.data.repository.DomainReputationRepository
import com.deepfakeshield.data.repository.PhoneReputationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertDetailUiState(
    val alert: AlertEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val blockAndReportSuccess: Boolean = false
)

@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val phoneReputationRepository: PhoneReputationRepository,
    private val domainReputationRepository: DomainReputationRepository,
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val alertId: Long = savedStateHandle.get<Long>("alertId")
        ?: savedStateHandle.get<String>("alertId")?.toLongOrNull()
        ?: -1L  // -1 signals "no valid ID" — will produce "Alert not found" on load

    private val _uiState = MutableStateFlow(AlertDetailUiState())
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlert()
    }

    private fun loadAlert() {
        viewModelScope.launch {
            try {
                val alert = alertRepository.getAlertById(alertId)
                _uiState.update {
                    it.copy(
                        alert = alert,
                        isLoading = false,
                        error = if (alert == null) "Alert not found" else null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load alert: ${e.message}")
                }
            }
        }
    }

    fun markAsHandled() {
        viewModelScope.launch {
            try {
                alertRepository.markAsHandled(alertId)
                loadAlert()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to mark as handled: ${e.message}") }
            }
        }
    }

    /**
     * One-tap block and report: block sender + report as scam to local DB.
     * Returns Intent to open FTC fraud report (user can optionally submit).
     */
    fun blockAndReport(blocked: Boolean) {
        viewModelScope.launch {
            val alert = _uiState.value.alert ?: return@launch
            val sender = alert.senderInfo ?: return@launch
            try {
                when {
                    sender.matches(Regex("^[+]?[0-9\\s-]{10,}$")) -> {
                        phoneReputationRepository.reportAsScam(sender)
                        if (blocked) phoneReputationRepository.blockNumber(sender)
                    }
                    sender.contains(".") || sender.contains("@") -> {
                        val domain = sender.substringAfter("@").substringBefore("/").lowercase()
                        if (domain.isNotBlank()) {
                            domainReputationRepository.reportAsScam(domain, alert.threatType.name)
                        }
                    }
                }
                auditLogRepository.logAction(
                    action = if (blocked) "BLOCK_AND_REPORT" else "REPORT_SCAM",
                    entityType = "alert",
                    entityId = alertId.toString(),
                    metadata = org.json.JSONObject().put("sender", sender).toString()
                )
                val updatedAlert = alertRepository.getAlertById(alertId)
                _uiState.update { it.copy(blockAndReportSuccess = true, alert = updatedAlert) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = "Block failed: ${e.message}") }
            }
        }
    }

    /**
     * Creates Intent to open FTC Report Fraud page - user can submit report.
     */
    fun createFtcReportIntent(): Intent {
        @Suppress("UNUSED_VARIABLE")
        val alert = _uiState.value.alert
        val url = "https://reportfraud.ftc.gov/#/"
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage(null)
            data = android.net.Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
