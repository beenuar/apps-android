package com.deepfakeshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.deepfakeshield.data.preferences.UserPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DeepfakeShieldApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userPreferences: UserPreferences

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        try {
            // Initialize Firebase safely (only if configured)
            try {
                FirebaseApp.initializeApp(this)
                try {
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
                } catch (e: Exception) { android.util.Log.w("Cyble", "Init error: ${e.message}") }
                try {
                    FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
                } catch (e: Exception) { android.util.Log.w("Cyble", "Init error: ${e.message}") }
                appScope.launch {
                    try {
                        val crash = userPreferences.crashlyticsEnabled.first()
                        val analytics = userPreferences.analyticsEnabled.first()
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crash)
                        FirebaseAnalytics.getInstance(this@DeepfakeShieldApplication).setAnalyticsCollectionEnabled(analytics)
                    } catch (e: Exception) { android.util.Log.w("Cyble", "Init error: ${e.message}") }
                }
            } catch (e: Exception) {
                android.util.Log.w("Cyble", "Firebase not configured: ${e.message}")
            }

            createNotificationChannels()
            scheduleWorkers()
        } catch (t: Throwable) {
            android.util.Log.e("Cyble", "Application onCreate error", t)
            // Don't rethrow - allow app to open
        }
    }
    
    private fun scheduleWorkers() {
        try {
            com.deepfakeshield.worker.WeeklySafetyReportWorker.schedule(this)
            com.deepfakeshield.worker.DailyCleanupWorker.schedule(this)
            com.deepfakeshield.worker.DailyDigestWorker.schedule(this)
            com.deepfakeshield.worker.InactivityNudgeWorker.schedule(this)
            com.deepfakeshield.worker.MonthlySafetyReportWorker.schedule(this)
            appScope.launch {
                try {
                    val batteryOpt = userPreferences.batteryOptimizedScan.first()
                    com.deepfakeshield.worker.ScheduledScanWorker.schedule(this@DeepfakeShieldApplication, batteryOpt)
                    val torEnabled = userPreferences.torEnabled.first()
                    if (torEnabled) {
                        val exitCountry = userPreferences.torExitCountry.first()
                        com.deepfakeshield.service.EmbeddedTorManager.start(this@DeepfakeShieldApplication, exitCountry)
                    }
                } catch (e: Exception) { android.util.Log.w("Cyble", "Init error: ${e.message}") }
            }
            com.deepfakeshield.worker.ThreatDatabaseUpdateWorker.schedule(this)
            com.deepfakeshield.worker.ThreatDatabaseUpdateWorker.scheduleImmediateIfNeeded(this)
            com.deepfakeshield.worker.UrlThreatCacheRefreshWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.w("Cyble", "Failed to schedule workers: ${e.message}")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Critical alerts channel (HIGH importance)
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALERTS,
                "Critical Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important security warnings that need immediate attention"
                enableVibration(true)
                setShowBadge(true)
            }

            // Regular alerts channel (DEFAULT importance)
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Security alerts and warnings"
                setShowBadge(true)
            }

            // Service channel (LOW importance)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when protection is actively running"
                setShowBadge(false)
            }

            // Info channel (LOW importance)
            val infoChannel = NotificationChannel(
                CHANNEL_INFO,
                "Information",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tips and information about the app"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(criticalChannel, alertsChannel, serviceChannel, infoChannel)
            )
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("Cyble", "Uncaught exception: ${throwable.message}", throwable)
                try {
                    FirebaseCrashlytics.getInstance().log("Uncaught: ${throwable.javaClass.simpleName}: ${throwable.message}")
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                } catch (e: Exception) { android.util.Log.w("Cyble", "Crashlytics not available in crash handler: ${e.message}") }
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        const val CHANNEL_CRITICAL_ALERTS = "critical_alerts"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_SERVICE = "service"
        const val CHANNEL_INFO = "info"
    }
}
