package com.deepfakeshield.di

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.core.notification.AlertNotifier
import com.deepfakeshield.enterprise.QuietHoursHelper
import com.deepfakeshield.service.FloatingBubbleService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlertNotifier {

    override fun onAlertInserted(alertCount: Int, title: String, summary: String) {
        // Update floating bubble so it shows threat count and "X threats blocked"
        try {
            FloatingBubbleService.updateStatus(context, alertCount, true)
        } catch (e: Exception) { /* service may not be running */ }
        try {
            com.deepfakeshield.widget.ShieldWidgetProvider.updateWidget(context)
        } catch (e: Exception) { }

        // B2C: Respect user notification preferences, quiet hours, and system permission
        val suppress = try {
            runBlocking { QuietHoursHelper.shouldSuppressNotification(context) }
        } catch (e: Exception) { false }
        if (suppress) return

        // B2C-SAFE: Use regular ALERTS channel, not CRITICAL.
        // The bubble already handles real-time alerts — this notification is supplementary.
        // Using CRITICAL for every alert annoys users and causes uninstalls.
        try {
            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("open_route", "alerts")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, DeepfakeShieldApplication.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("\uD83D\uDEE1\uFE0F $title")
                .setContentText("Threat blocked — $summary")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Threat blocked! You're protected.\n\n$summary"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notificationId = ALERT_NOTIFICATION_BASE + nextNotificationSequence()
            nm.notify(notificationId, notification)
        } catch (e: Exception) {
            android.util.Log.e("AlertNotifier", "Failed to show threat notification", e)
        }
    }

    companion object {
        private const val ALERT_NOTIFICATION_BASE = 20000
        private val notificationCounter = java.util.concurrent.atomic.AtomicInteger(0)

        /** Thread-safe sequential IDs that won't collide within a session. */
        fun nextNotificationSequence(): Int = notificationCounter.getAndIncrement()
    }
}
