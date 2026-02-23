package com.deepfakeshield.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadAlerts()
    }

    private fun loadAlerts() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                alertRepository.getAllAlerts().collect { alerts ->
                    _uiState.update {
                        it.copy(
                            alerts = alerts.sortedByDescending { a -> a.timestamp },
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load alerts")
                }
            }
        }
    }

    fun retryLoad() {
        _uiState.update { it.copy(isLoading = true, error = null, alerts = emptyList()) }
        loadAlerts()
    }

    fun markAsHandled(alertId: Long) {
        viewModelScope.launch {
            try {
                alertRepository.markAsHandled(alertId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = "Failed to mark as handled: ${e.message}") }
            }
        }
    }

    fun markAllAsHandled() {
        viewModelScope.launch {
            try {
                val unhandled = _uiState.value.alerts.filter { !it.isHandled }
                unhandled.forEach { alertRepository.markAsHandled(it.id) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = "Failed: ${e.message}") }
            }
        }
    }

    fun deleteAllHandled() {
        viewModelScope.launch {
            try {
                val handled = _uiState.value.alerts.filter { it.isHandled }
                handled.forEach { alertRepository.deleteAlert(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(error = "Failed: ${e.message}") }
            }
        }
    }
}
