package com.deepfakeshield.enterprise

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.deepfakeshield.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Centralized notification guard for B2C users.
 * Checks three conditions before allowing a user-facing notification:
 *   1. The user has enabled notifications in app settings (notificationsEnabled preference)
 *   2. The system-level POST_NOTIFICATIONS permission is granted (Android 13+)
 *   3. We are not within user-configured quiet hours
 *
 * Used by SmsReceiver, CallScreeningService, NotificationListenerService,
 * ScheduledScanWorker, WeeklySafetyReportWorker, AlertNotifierImpl, and
 * ProtectionForegroundService before showing user-facing notifications.
 *
 * NOTE: Foreground service notifications (required by Android) bypass this guard.
 */
object QuietHoursHelper {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface QuietHoursEntryPoint {
        fun userPreferences(): UserPreferences
    }

    /**
     * Returns true if the notification should be suppressed.
     * Checks: notificationsEnabled preference, POST_NOTIFICATIONS permission, and quiet hours.
     * Callers should skip showing the notification when this returns true.
     *
     * @param context Application or service context
     * @param isCritical If true, only quiet hours can suppress (user pref and permission still apply).
     *                   Critical = active malware, not just any threat.
     */
    suspend fun shouldSuppressNotification(context: Context, isCritical: Boolean = false): Boolean {
        return try {
            val appContext = context.applicationContext

            // Check 1: System-level notification permission (Android 13+)
            if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
                return true
            }

            val entryPoint = EntryPointAccessors.fromApplication(appContext, QuietHoursEntryPoint::class.java)
            val prefs = entryPoint.userPreferences()

            // Check 2: User has disabled notifications in app settings
            val notificationsOn = prefs.notificationsEnabled.first()
            if (!notificationsOn) {
                return true
            }

            // Check 3: Quiet hours (skip this check for critical alerts)
            if (!isCritical) {
                val quietEnabled = prefs.quietHoursEnabled.first()
                if (quietEnabled) {
                    val start = prefs.quietHoursStart.first()
                    val end = prefs.quietHoursEnd.first()
                    if (isCurrentlyQuiet(start, end)) {
                        return true
                    }
                }
            }

            false
        } catch (e: Exception) {
            android.util.Log.w("QuietHoursHelper", "Failed to check notification guard", e)
            false // On error, allow the notification
        }
    }

    /**
     * Legacy method — delegates to [shouldSuppressNotification].
     * Returns true if we are currently within quiet hours and should suppress
     * non-critical notifications.
     */
    suspend fun isWithinQuietHours(context: Context): Boolean {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(context, QuietHoursEntryPoint::class.java)
            val prefs = entryPoint.userPreferences()
            val enabled = prefs.quietHoursEnabled.first()
            if (!enabled) return false
            val start = prefs.quietHoursStart.first()
            val end = prefs.quietHoursEnd.first()
            isCurrentlyQuiet(start, end)
        } catch (e: Exception) {
            android.util.Log.w("QuietHoursHelper", "Failed to check quiet hours", e)
            false
        }
    }

    private fun isCurrentlyQuiet(startHour: Int, endHour: Int): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            startHour <= endHour -> hour in startHour until endHour
            else -> hour >= startHour || hour < endHour
        }
    }
}
