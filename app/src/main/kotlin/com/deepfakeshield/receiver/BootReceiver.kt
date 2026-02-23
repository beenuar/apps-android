package com.deepfakeshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.deepfakeshield.service.AntivirusForegroundService
import com.deepfakeshield.service.FloatingBubbleService
import com.deepfakeshield.service.ProtectionForegroundService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.deepfakeshield.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts protection services after device boot / app update.
 * Uses goAsync() to keep the process alive while launching services in a coroutine.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun userPreferences(): UserPreferences
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in BOOT_ACTIONS) return

        // goAsync() keeps process alive while we launch services in a coroutine
        val pendingResult = goAsync()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val app = context.applicationContext
                val entryPoint = EntryPointAccessors.fromApplication(app, BootEntryPoint::class.java)
                val prefs = entryPoint.userPreferences()

                val masterOn = prefs.masterProtectionEnabled.first()
                val onboardingDone = prefs.onboardingCompleted.first()

                if (!onboardingDone || !masterOn) {
                    Log.d(TAG, "Protection off or onboarding incomplete, skipping boot start")
                    return@launch
                }

                Log.d(TAG, "Boot/update received ($action) - starting protection services")

                try {
                    val protectionIntent = Intent(app, ProtectionForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(protectionIntent)
                    } else {
                        app.startService(protectionIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ProtectionForegroundService", e)
                }

                try {
                    val bubbleEnabled = prefs.overlayBubbleEnabled.first()
                    if (Settings.canDrawOverlays(app) && bubbleEnabled) {
                        FloatingBubbleService.start(app)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start FloatingBubbleService", e)
                }

                try {
                    AntivirusForegroundService.start(app)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start AntivirusForegroundService", e)
                }

                Log.d(TAG, "Protection services started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start services on boot", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
