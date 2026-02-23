package com.deepfakeshield.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val masterProtectionEnabled: Boolean = false,
    val videoShieldEnabled: Boolean = false,
    val messageShieldEnabled: Boolean = false,
    val callShieldEnabled: Boolean = false,
    val overlayBubbleEnabled: Boolean = false,
    val unhandledAlertCount: Int = 0,
    val totalThreatsBlocked: Int = 0,
    val isLoading: Boolean = true,
    val streak: Int = 0,
    val totalScans: Int = 0,
    val lastScanTimestamp: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    // Split into two combine calls (max 5 params each) for type safety
    val uiState: StateFlow<HomeUiState> = combine(
        userPreferences.masterProtectionEnabled,
        userPreferences.videoShieldEnabled,
        userPreferences.messageShieldEnabled,
        userPreferences.callShieldEnabled,
        alertRepository.getUnhandledCount()
    ) { master, video, message, call, unhandled ->
        HomeUiState(
            masterProtectionEnabled = master,
            videoShieldEnabled = video,
            messageShieldEnabled = message,
            callShieldEnabled = call,
            unhandledAlertCount = unhandled,
            isLoading = false
        )
    }.combine(alertRepository.getAlertCountFlow()) { state, totalAlerts ->
        state.copy(totalThreatsBlocked = totalAlerts)
    }.combine(userPreferences.overlayBubbleEnabled) { state, overlayEnabled ->
        state.copy(overlayBubbleEnabled = overlayEnabled)
    }.combine(userPreferences.dailyChallengeStreak) { state, streak ->
        state.copy(streak = streak)
    }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e(TAG, "Error loading state", e)
        emit(HomeUiState(isLoading = false))
    }.distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    private var masterToggleJob: kotlinx.coroutines.Job? = null

    fun toggleMasterProtection(enabled: Boolean) {
        // Cancel any in-flight toggle to prevent interleaved writes on rapid tap
        masterToggleJob?.cancel()
        masterToggleJob = viewModelScope.launch {
            try {
                userPreferences.setMasterProtection(enabled)
                if (enabled) {
                    userPreferences.setVideoShield(true)
                    userPreferences.setMessageShield(true)
                    userPreferences.setCallShield(true)
                } else {
                    // Disable all shields when master is turned off
                    userPreferences.setVideoShield(false)
                    userPreferences.setMessageShield(false)
                    userPreferences.setCallShield(false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to toggle master protection", e)
            }
        }
    }

    fun toggleVideoShield(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setVideoShield(enabled)
                checkMasterProtection()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to toggle video shield", e)
            }
        }
    }

    fun toggleMessageShield(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setMessageShield(enabled)
                checkMasterProtection()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to toggle message shield", e)
            }
        }
    }

    fun toggleCallShield(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setCallShield(enabled)
                checkMasterProtection()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to toggle call shield", e)
            }
        }
    }

    /**
     * Toggle the floating overlay bubble preference.
     * The actual service start/stop is handled by the UI layer which has access
     * to Context (needed for Settings.canDrawOverlays and service intents).
     */
    fun toggleOverlayBubble(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setOverlayBubbleEnabled(enabled)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to toggle overlay bubble", e)
            }
        }
    }

    private suspend fun checkMasterProtection() {
        try {
            val video = userPreferences.videoShieldEnabled.first()
            val message = userPreferences.messageShieldEnabled.first()
            val call = userPreferences.callShieldEnabled.first()
            val anyEnabled = video || message || call
            userPreferences.setMasterProtection(anyEnabled)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Failed to check master protection state", e)
        }
    }
}
