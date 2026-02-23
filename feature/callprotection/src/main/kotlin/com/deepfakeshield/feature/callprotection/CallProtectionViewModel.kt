package com.deepfakeshield.feature.callprotection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.repository.PhoneReputationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallProtectionUiState(
    val callScreeningEnabled: Boolean = false,
    val speakerphoneEnabled: Boolean = false,
    val totalCallsAnalyzed: Int = 0,
    val scamsDetected: Int = 0,
    val blockedNumbers: Int = 0
)

@HiltViewModel
class CallProtectionViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    private val phoneReputationRepository: PhoneReputationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallProtectionUiState())
    val uiState: StateFlow<CallProtectionUiState> = _uiState.asStateFlow()

    init {
        // Collect settings
        viewModelScope.launch {
            combine(
                userPreferences.callShieldEnabled,
                userPreferences.speakerphoneModeEnabled
            ) { callEnabled, speakerphone ->
                _uiState.update {
                    it.copy(
                        callScreeningEnabled = callEnabled,
                        speakerphoneEnabled = speakerphone
                    )
                }
            }.collect()
        }

        // Collect real call stats from database
        viewModelScope.launch {
            try {
                alertRepository.getAlertsBySource(ThreatSource.INCOMING_CALL).collect { callAlerts ->
                    _uiState.update {
                        it.copy(
                            totalCallsAnalyzed = callAlerts.size,
                            scamsDetected = callAlerts.count { alert -> alert.score >= 65 }
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("CallProtectionVM", "Failed to load call stats: ${e.message}")
            }
        }

        // Collect blocked numbers count
        viewModelScope.launch {
            try {
                phoneReputationRepository.getBlockedNumbers().collect { blocked ->
                    _uiState.update { it.copy(blockedNumbers = blocked.size) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("CallProtectionVM", "Failed to load blocked count: ${e.message}")
            }
        }
    }

    fun enableCallProtection() {
        viewModelScope.launch {
            userPreferences.setCallShield(true)
        }
    }

    fun checkCallScreeningRole(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                userPreferences.setCallShield(true)
            }
        }
    }

    fun toggleSpeakerphoneMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setSpeakerphoneMode(enabled)
        }
    }
}
