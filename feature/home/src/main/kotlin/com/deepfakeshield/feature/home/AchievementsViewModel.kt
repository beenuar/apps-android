package com.deepfakeshield.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementsUiState(
    val alertCount: Int = 0,
    val threatsDetected: Int = 0,
    val allShieldsEnabled: Boolean = false,
    val unlockedIds: Set<String> = emptySet()
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                alertRepository.getAlertCountFlow(),
                alertRepository.getUnhandledCount(),
                userPreferences.videoShieldEnabled,
                userPreferences.messageShieldEnabled,
                userPreferences.callShieldEnabled,
                userPreferences.unlockedAchievements
            ) { values ->
                val totalAlerts = (values[0] as? Int) ?: 0
                val video = (values[2] as? Boolean) ?: false
                val message = (values[3] as? Boolean) ?: false
                val call = (values[4] as? Boolean) ?: false
                val unlocked = (values[5] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                _uiState.update {
                    it.copy(
                        alertCount = totalAlerts,
                        threatsDetected = totalAlerts,
                        allShieldsEnabled = video && message && call,
                        unlockedIds = unlocked
                    )
                }
            }.distinctUntilChanged()
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("AchievementsVM", "Flow error: ${e.message}")
            }.collect()
        }
    }

    fun persistUnlock(achievementId: String) {
        viewModelScope.launch {
            userPreferences.unlockAchievement(achievementId)
        }
    }
}
