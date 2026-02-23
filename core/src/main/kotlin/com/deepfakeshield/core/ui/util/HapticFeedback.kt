package com.deepfakeshield.core.ui.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Haptic Feedback Utility
 * 
 * Provides rich haptic feedback throughout the app for:
 * - Button presses
 * - Toggle changes
 * - Scan completion
 * - Threat detection (alert pulse)
 * - Navigation transitions
 */
object HapticFeedback {
    
    /**
     * Light tap - for button presses, toggle switches
     */
    fun lightTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    
    /**
     * Medium tap - for confirmations, navigation
     */
    fun mediumTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    /**
     * Heavy tap - for important actions, alerts
     */
    fun heavyTap(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    /**
     * Success feedback - for scan completion, safe result
     */
    fun success(context: Context) {
        vibrate(context, longArrayOf(0, 30, 60, 30), intArrayOf(0, 80, 0, 120))
    }
    
    /**
     * Warning feedback - medium threat detected
     */
    fun warning(context: Context) {
        vibrate(context, longArrayOf(0, 50, 50, 50, 50, 50), intArrayOf(0, 100, 0, 100, 0, 100))
    }
    
    /**
     * Alert feedback - critical threat detected
     */
    fun alert(context: Context) {
        vibrate(context, longArrayOf(0, 100, 80, 100, 80, 200), intArrayOf(0, 200, 0, 200, 0, 255))
    }
    
    /**
     * Error feedback - scan failed, error occurred
     */
    fun error(context: Context) {
        vibrate(context, longArrayOf(0, 80, 40, 80), intArrayOf(0, 150, 0, 150))
    }
    
    /**
     * Scan progress tick - subtle feedback during scanning
     */
    fun scanTick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    
    /**
     * Toggle on/off feedback
     */
    fun toggle(view: View, isOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                view.performHapticFeedback(
                    if (isOn) HapticFeedbackConstants.TOGGLE_ON 
                    else HapticFeedbackConstants.TOGGLE_OFF
                )
            } catch (e: Exception) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    
    @SuppressLint("MissingPermission") // VIBRATE is declared in app manifest
    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (e: Throwable) {
            // Haptic feedback not available, fail silently
        }
    }
}

/**
 * Composable utility to get haptic feedback from the view
 */
@Composable
fun rememberHapticView(): View {
    return LocalView.current
}
