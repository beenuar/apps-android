package com.deepfakeshield.feature.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyChallengeUiState(
    val streak: Int = 0,
    val totalXp: Int = 0,
    val completedIds: Set<String> = emptySet()
)

@HiltViewModel
class DailyChallengeViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyChallengeUiState())
    val uiState: StateFlow<DailyChallengeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.dailyChallengeStreak,
                userPreferences.dailyChallengeTotalXp,
                userPreferences.dailyChallengeCompletedIds
            ) { streak, xp, ids -> Triple(streak, xp, ids) }
                .collect { (streak, xp, ids) ->
                    _uiState.update {
                        it.copy(streak = streak, totalXp = xp, completedIds = ids)
                    }
                }
        }
    }

    fun onChallengeCompleted(challengeId: Int, xpEarned: Int) {
        viewModelScope.launch {
            userPreferences.setDailyChallengeCompleted(challengeId, xpEarned)
        }
    }
}
