package com.deepfakeshield.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Home screen widget showing real protection status and threat count.
 * Uses goAsync() + coroutine to avoid ANR from blocking main thread.
 */
class ShieldWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun userPreferences(): UserPreferences
        fun alertRepository(): AlertRepository
    }

    companion object {
        const val ACTION_QUICK_SCAN = "com.deepfakeshield.widget.QUICK_SCAN"
        const val ACTION_UPDATE = "com.deepfakeshield.widget.UPDATE"
        
        fun updateWidget(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ShieldWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            ids.forEach { id ->
                updateAppWidgetAsync(context, manager, id)
            }
        }
        
        private fun updateAppWidgetAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_shield)
            
            // Set up click intents immediately (don't need async data)
            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            
            val scanIntent = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).apply { putExtra("open_scan", true) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_quick_scan, scanIntent)
            
            // Show a safe default state (survives process death if coroutine is killed)
            views.setTextViewText(R.id.tv_status, "Protected")
            views.setTextViewText(R.id.tv_subtitle, "Tap to check status")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Load real data on background thread
            CoroutineScope(Dispatchers.IO).launch {
                val (status, subtitle) = try {
                    val app = context.applicationContext
                    val entryPoint = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java)
                    val protected = withTimeoutOrNull(3000L) {
                        entryPoint.userPreferences().masterProtectionEnabled.first()
                    } ?: true
                    val count = withTimeoutOrNull(3000L) {
                        entryPoint.alertRepository().getAlertCount()
                    } ?: 0
                    val videoShield = withTimeoutOrNull(2000L) { entryPoint.userPreferences().videoShieldEnabled.first() } ?: false
                    val msgShield = withTimeoutOrNull(2000L) { entryPoint.userPreferences().messageShieldEnabled.first() } ?: false
                    val callShield = withTimeoutOrNull(2000L) { entryPoint.userPreferences().callShieldEnabled.first() } ?: false
                    val score = (listOf(protected, videoShield, msgShield, callShield).count { it } * 25).coerceIn(0, 100)
                    views.setTextViewText(R.id.tv_score, "Score: $score%")
                    if (protected) {
                        "Protected" to if (count > 0) "$count threat${if (count == 1) "" else "s"} blocked" else "All shields active"
                    } else {
                        "Protection Off" to "Tap to enable"
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ShieldWidget", "Failed to read status: ${e.message}")
                    "Status Unknown" to "Tap to open app"
                }
                
                views.setTextViewText(R.id.tv_status, status)
                views.setTextViewText(R.id.tv_subtitle, subtitle)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidgetAsync(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // goAsync() keeps the process alive for background coroutines in updateAppWidgetAsync
        val pendingResult = goAsync()
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_QUICK_SCAN -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("open_scan", true)
                }
                context.startActivity(launchIntent)
            }
            ACTION_UPDATE -> {
                updateWidget(context)
            }
        }
        // Finish after a delay to keep process alive for widget update coroutines.
        // updateAppWidgetAsync has two sequential withTimeoutOrNull(3000L) calls = up to 6s.
        // Allow 8s total for safety margin.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(8000)
            } finally {
                try { pendingResult.finish() } catch (_: Exception) { }
            }
        }
    }
}
