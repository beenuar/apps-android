package com.deepfakeshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Keys
    private object Keys {
        val MASTER_PROTECTION_ENABLED = booleanPreferencesKey("master_protection_enabled")
        val VIDEO_SHIELD_ENABLED = booleanPreferencesKey("video_shield_enabled")
        val MESSAGE_SHIELD_ENABLED = booleanPreferencesKey("message_shield_enabled")
        val CALL_SHIELD_ENABLED = booleanPreferencesKey("call_shield_enabled")
        
        val PROTECTION_LEVEL = stringPreferencesKey("protection_level") // "gentle", "balanced", "strict"
        val ALERT_STYLE = stringPreferencesKey("alert_style") // "overlay", "notification"
        
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SIMPLE_MODE_ENABLED = booleanPreferencesKey("simple_mode_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        
        val SMS_PERMISSION_GRANTED = booleanPreferencesKey("sms_permission_granted")
        val NOTIFICATION_PERMISSION_GRANTED = booleanPreferencesKey("notification_permission_granted")
        val CALL_PERMISSION_GRANTED = booleanPreferencesKey("call_permission_granted")
        val OVERLAY_PERMISSION_GRANTED = booleanPreferencesKey("overlay_permission_granted")
        
        val CLIPBOARD_SCANNING_ENABLED = booleanPreferencesKey("clipboard_scanning_enabled")
        val SPEAKERPHONE_MODE_ENABLED = booleanPreferencesKey("speakerphone_mode_enabled")
        
        val DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")
        val AUTO_DELETE_HANDLED = booleanPreferencesKey("auto_delete_handled")
        
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val CRASHLYTICS_ENABLED = booleanPreferencesKey("crashlytics_enabled") // Separate from analytics
        
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start") // Hour (0-23)
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end") // Hour (0-23)
        
        val ALERT_SENSITIVITY = stringPreferencesKey("alert_sensitivity") // "low", "medium", "high"
        val MIN_ALERT_SEVERITY = stringPreferencesKey("min_alert_severity") // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        val VIDEO_SCANS_COUNT = intPreferencesKey("video_scans_count")
        val MESSAGE_SCANS_COUNT = intPreferencesKey("message_scans_count")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        
        val DAILY_CHALLENGE_STREAK = intPreferencesKey("daily_challenge_streak")
        val LAST_CHALLENGE_DAY = intPreferencesKey("last_challenge_day")
        val DAILY_CHALLENGE_TOTAL_XP = intPreferencesKey("daily_challenge_total_xp")
        val DAILY_CHALLENGE_COMPLETED_IDS = stringSetPreferencesKey("daily_challenge_completed_ids")
        val FAMILY_PROTECTION_ENABLED = booleanPreferencesKey("family_protection_enabled")
        val BATTERY_OPTIMIZED_SCAN = booleanPreferencesKey("battery_optimized_scan")
        val AUTO_QUARANTINE_ON_THREAT = booleanPreferencesKey("auto_quarantine_on_threat")
        val UNLOCKED_ACHIEVEMENTS = stringSetPreferencesKey("unlocked_achievements")
        val OVERLAY_BUBBLE_ENABLED = booleanPreferencesKey("overlay_bubble_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark", "amoled"
        val TOR_ENABLED = booleanPreferencesKey("tor_enabled")
        val TOR_PROXY_HOST = stringPreferencesKey("tor_proxy_host")
        val TOR_PROXY_PORT = intPreferencesKey("tor_proxy_port")
        val TOR_DISCLOSURE_ACCEPTED = booleanPreferencesKey("tor_disclosure_accepted")
        val TOR_LAST_TEST_RESULT = stringPreferencesKey("tor_last_test_result")
        val TOR_EXIT_COUNTRY = stringPreferencesKey("tor_exit_country")
    }

    // Flows
    val masterProtectionEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.MASTER_PROTECTION_ENABLED] ?: true }
    val videoShieldEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.VIDEO_SHIELD_ENABLED] ?: true }
    val messageShieldEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.MESSAGE_SHIELD_ENABLED] ?: true }
    val callShieldEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CALL_SHIELD_ENABLED] ?: true }
    
    val protectionLevel: Flow<String> = dataStore.data.map { it[Keys.PROTECTION_LEVEL] ?: "balanced" }
    val alertStyle: Flow<String> = dataStore.data.map { it[Keys.ALERT_STYLE] ?: "overlay" }
    
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val simpleModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SIMPLE_MODE_ENABLED] ?: false }
    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val torEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.TOR_ENABLED] ?: false }
    val torProxyHost: Flow<String> = dataStore.data.map { it[Keys.TOR_PROXY_HOST] ?: "127.0.0.1" }
    val torProxyPort: Flow<Int> = dataStore.data.map { it[Keys.TOR_PROXY_PORT] ?: 9050 }
    val torDisclosureAccepted: Flow<Boolean> = dataStore.data.map { it[Keys.TOR_DISCLOSURE_ACCEPTED] ?: false }
    val torLastTestResult: Flow<String> = dataStore.data.map { it[Keys.TOR_LAST_TEST_RESULT] ?: "" }
    val torExitCountry: Flow<String> = dataStore.data.map { it[Keys.TOR_EXIT_COUNTRY] ?: "auto" }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    
    val clipboardScanningEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CLIPBOARD_SCANNING_ENABLED] ?: false }
    val speakerphoneModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SPEAKERPHONE_MODE_ENABLED] ?: false }
    
    val dataRetentionDays: Flow<Int> = dataStore.data.map { it[Keys.DATA_RETENTION_DAYS] ?: 90 }
    val autoDeleteHandled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_DELETE_HANDLED] ?: false }
    
    val analyticsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.ANALYTICS_ENABLED] ?: false }
    val crashlyticsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CRASHLYTICS_ENABLED] ?: false }
    
    val quietHoursEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.QUIET_HOURS_ENABLED] ?: false }
    val quietHoursStart: Flow<Int> = dataStore.data.map { it[Keys.QUIET_HOURS_START] ?: 22 }
    val quietHoursEnd: Flow<Int> = dataStore.data.map { it[Keys.QUIET_HOURS_END] ?: 7 }
    
    val alertSensitivity: Flow<String> = dataStore.data.map { it[Keys.ALERT_SENSITIVITY] ?: "medium" }
    val minAlertSeverity: Flow<String> = dataStore.data.map { it[Keys.MIN_ALERT_SEVERITY] ?: "MEDIUM" }
    val videoScansCount: Flow<Int> = dataStore.data.map { it[Keys.VIDEO_SCANS_COUNT] ?: 0 }
    val messageScansCount: Flow<Int> = dataStore.data.map { it[Keys.MESSAGE_SCANS_COUNT] ?: 0 }
    val lastScanTimestamp: Flow<Long> = dataStore.data.map { it[Keys.LAST_SCAN_TIMESTAMP] ?: 0L }
    
    val dailyChallengeStreak: Flow<Int> = dataStore.data.map { it[Keys.DAILY_CHALLENGE_STREAK] ?: 0 }
    val lastChallengeDay: Flow<Int> = dataStore.data.map { it[Keys.LAST_CHALLENGE_DAY] ?: 0 }
    val dailyChallengeTotalXp: Flow<Int> = dataStore.data.map { it[Keys.DAILY_CHALLENGE_TOTAL_XP] ?: 0 }
    val dailyChallengeCompletedIds: Flow<Set<String>> = dataStore.data.map { it[Keys.DAILY_CHALLENGE_COMPLETED_IDS] ?: emptySet() }
    val familyProtectionEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.FAMILY_PROTECTION_ENABLED] ?: false }
    val batteryOptimizedScan: Flow<Boolean> = dataStore.data.map { it[Keys.BATTERY_OPTIMIZED_SCAN] ?: true }
    val autoQuarantineOnThreat: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_QUARANTINE_ON_THREAT] ?: true }

    val unlockedAchievements: Flow<Set<String>> = dataStore.data.map { it[Keys.UNLOCKED_ACHIEVEMENTS] ?: emptySet() }
    val overlayBubbleEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.OVERLAY_BUBBLE_ENABLED] ?: true }

    suspend fun unlockAchievement(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.UNLOCKED_ACHIEVEMENTS] ?: emptySet()
            prefs[Keys.UNLOCKED_ACHIEVEMENTS] = current + id
        }
    }

    // Setters
    suspend fun setMasterProtection(enabled: Boolean) {
        dataStore.edit { it[Keys.MASTER_PROTECTION_ENABLED] = enabled }
    }

    suspend fun setVideoShield(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_SHIELD_ENABLED] = enabled }
    }

    suspend fun setMessageShield(enabled: Boolean) {
        dataStore.edit { it[Keys.MESSAGE_SHIELD_ENABLED] = enabled }
    }

    suspend fun setCallShield(enabled: Boolean) {
        dataStore.edit { it[Keys.CALL_SHIELD_ENABLED] = enabled }
    }

    suspend fun setProtectionLevel(level: String) {
        dataStore.edit { it[Keys.PROTECTION_LEVEL] = level }
    }

    suspend fun setAlertStyle(style: String) {
        dataStore.edit { it[Keys.ALERT_STYLE] = style }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setTorEnabled(enabled: Boolean) { dataStore.edit { it[Keys.TOR_ENABLED] = enabled } }
    suspend fun setTorProxyHost(host: String) { dataStore.edit { it[Keys.TOR_PROXY_HOST] = host } }
    suspend fun setTorProxyPort(port: Int) { dataStore.edit { it[Keys.TOR_PROXY_PORT] = port } }
    suspend fun setTorDisclosureAccepted(accepted: Boolean) { dataStore.edit { it[Keys.TOR_DISCLOSURE_ACCEPTED] = accepted } }
    suspend fun setTorLastTestResult(result: String) { dataStore.edit { it[Keys.TOR_LAST_TEST_RESULT] = result } }
    suspend fun setTorExitCountry(country: String) { dataStore.edit { it[Keys.TOR_EXIT_COUNTRY] = country } }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setSimpleMode(enabled: Boolean) {
        dataStore.edit { it[Keys.SIMPLE_MODE_ENABLED] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setClipboardScanning(enabled: Boolean) {
        dataStore.edit { it[Keys.CLIPBOARD_SCANNING_ENABLED] = enabled }
    }

    suspend fun setSpeakerphoneMode(enabled: Boolean) {
        dataStore.edit { it[Keys.SPEAKERPHONE_MODE_ENABLED] = enabled }
    }

    suspend fun setDataRetentionDays(days: Int) {
        dataStore.edit { it[Keys.DATA_RETENTION_DAYS] = days }
    }

    suspend fun setAutoDeleteHandled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_DELETE_HANDLED] = enabled }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ANALYTICS_ENABLED] = enabled }
    }

    suspend fun setCrashlyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CRASHLYTICS_ENABLED] = enabled }
    }

    suspend fun setQuietHours(enabled: Boolean, start: Int? = null, end: Int? = null) {
        dataStore.edit { prefs ->
            prefs[Keys.QUIET_HOURS_ENABLED] = enabled
            start?.let { prefs[Keys.QUIET_HOURS_START] = it }
            end?.let { prefs[Keys.QUIET_HOURS_END] = it }
        }
    }

    suspend fun setAlertSensitivity(sensitivity: String) {
        dataStore.edit { it[Keys.ALERT_SENSITIVITY] = sensitivity }
    }

    suspend fun setMinAlertSeverity(severity: String) {
        dataStore.edit { it[Keys.MIN_ALERT_SEVERITY] = severity }
    }

    suspend fun incrementVideoScans() {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_SCANS_COUNT] = (prefs[Keys.VIDEO_SCANS_COUNT] ?: 0) + 1
            prefs[Keys.LAST_SCAN_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun incrementMessageScans() {
        dataStore.edit { prefs ->
            prefs[Keys.MESSAGE_SCANS_COUNT] = (prefs[Keys.MESSAGE_SCANS_COUNT] ?: 0) + 1
            prefs[Keys.LAST_SCAN_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setDailyChallengeCompleted(dayId: Int, xpEarned: Int) {
        dataStore.edit { prefs ->
            val today = (System.currentTimeMillis() / (24 * 60 * 60 * 1000)).toInt()
            val lastDay = prefs[Keys.LAST_CHALLENGE_DAY] ?: 0
            val streak = prefs[Keys.DAILY_CHALLENGE_STREAK] ?: 0
            val newStreak = when {
                lastDay == today -> streak
                lastDay == today - 1 -> streak + 1
                else -> 1
            }
            val completedIds = (prefs[Keys.DAILY_CHALLENGE_COMPLETED_IDS] ?: emptySet()) + dayId.toString()
            prefs[Keys.LAST_CHALLENGE_DAY] = today
            prefs[Keys.DAILY_CHALLENGE_STREAK] = newStreak
            prefs[Keys.DAILY_CHALLENGE_TOTAL_XP] = (prefs[Keys.DAILY_CHALLENGE_TOTAL_XP] ?: 0) + xpEarned
            prefs[Keys.DAILY_CHALLENGE_COMPLETED_IDS] = completedIds
        }
    }

    suspend fun setFamilyProtection(enabled: Boolean) {
        dataStore.edit { it[Keys.FAMILY_PROTECTION_ENABLED] = enabled }
    }

    suspend fun setBatteryOptimizedScan(enabled: Boolean) {
        dataStore.edit { it[Keys.BATTERY_OPTIMIZED_SCAN] = enabled }
    }

    suspend fun setAutoQuarantineOnThreat(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_QUARANTINE_ON_THREAT] = enabled }
    }

    suspend fun setOverlayBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.OVERLAY_BUBBLE_ENABLED] = enabled }
    }

}
