package com.deepfakeshield.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.engine.EnhancedRiskIntelligenceEngine
import com.deepfakeshield.core.intelligence.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IntelligenceDashboardUiState(
    val networkStats: NetworkStats = NetworkStats(0, 0, 0, 0, 0),
    val learningStats: LearningStats = LearningStats(0, 0.0, 0.0, 0, 0),
    val threatForecast: ThreatForecast = ThreatForecast("", emptyList(), AlertLevel.LOW),
    val userRiskProfile: UserRiskProfile? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class IntelligenceDashboardViewModel @Inject constructor(
    private val enhancedEngine: EnhancedRiskIntelligenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntelligenceDashboardUiState())
    val uiState: StateFlow<IntelligenceDashboardUiState> = _uiState.asStateFlow()

    init {
        loadIntelligenceData()
    }

    private fun loadIntelligenceData() {
        viewModelScope.launch {
            try {
                // Load all intelligence data
                val networkStats = enhancedEngine.getCommunityStats()
                val learningStats = enhancedEngine.getLearningStats()
                val forecast = enhancedEngine.getThreatForecast()
                val riskProfile = enhancedEngine.getUserRiskProfile()

                _uiState.update {
                    it.copy(
                        networkStats = networkStats,
                        learningStats = learningStats,
                        threatForecast = forecast,
                        userRiskProfile = riskProfile,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load intelligence data") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val networkStats = enhancedEngine.getCommunityStats()
                val learningStats = enhancedEngine.getLearningStats()
                val forecast = enhancedEngine.getThreatForecast()
                val riskProfile = enhancedEngine.getUserRiskProfile()
                _uiState.update { it.copy(networkStats = networkStats, learningStats = learningStats, threatForecast = forecast, userRiskProfile = riskProfile, isLoading = false, error = null) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to refresh") }
            }
        }
    }
}
