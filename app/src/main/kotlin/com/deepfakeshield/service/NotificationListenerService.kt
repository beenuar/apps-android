package com.deepfakeshield.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.enterprise.QuietHoursHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * PRODUCTION NOTIFICATION LISTENER
 *
 * B2C-SAFE: Only flags truly suspicious notifications.
 * Whitelists known legitimate apps to prevent false positives that
 * would annoy users and cause uninstalls.
 */
private const val LISTENER_NOTIFICATION_BASE = 50000

class DeepfakeNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        fun riskEngine(): RiskIntelligenceEngine
        fun alertRepository(): AlertRepository
        fun userPreferences(): UserPreferences
    }

    companion object {
        /**
         * Known legitimate apps that should NEVER be flagged.
         * These are major, trusted brands whose notifications contain words
         * like "payment", "subscription", "verify" that the risk engine
         * would otherwise flag as scam indicators.
         */
        private val WHITELISTED_PACKAGES = setOf(
            // Google
            "com.google.android.gm", "com.google.android.apps.messaging",
            "com.google.android.apps.maps", "com.google.android.youtube",
            "com.google.android.apps.photos", "com.google.android.calendar",
            "com.google.android.dialer", "com.google.android.contacts",
            "com.google.android.apps.docs", "com.google.android.apps.fitness",
            "com.google.android.apps.walletnfcrel", "com.google.android.keep",
            "com.google.android.apps.translate", "com.google.android.apps.googleassistant",
            "com.google.android.googlequicksearchbox",
            // Social / messaging
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
            "com.twitter.android", "com.snapchat.android", "com.discord",
            "com.linkedin.android", "com.pinterest", "com.reddit.frontpage",
            "com.tumblr", "com.zhiliaoapp.musically", "com.viber.voip",
            "jp.naver.line.android", "com.imo.android.imoim",
            // Streaming / media
            "com.spotify.music", "com.netflix.mediaclient", "com.amazon.avod",
            "com.disney.disneyplus", "com.hbo.hbonow", "com.google.android.apps.youtube.music",
            "com.apple.android.music", "tv.twitch.android.app",
            "com.pandora.android", "com.soundcloud.android",
            // Shopping / delivery
            "com.amazon.mShop.android.shopping", "com.ebay.mobile",
            "com.ubercab", "com.ubercab.eats", "com.grubhub.android",
            "com.dd.doordash", "com.instacart.client",
            // Productivity
            "com.microsoft.office.outlook", "com.microsoft.teams",
            "com.slack", "com.Slack", "us.zoom.videomeetings",
            "com.dropbox.android", "com.evernote",
            // Finance / banking (these trigger many false positives with payment words)
            "com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash",
            // OS / system
            "com.android.vending", "com.android.chrome", "com.android.settings",
            "com.samsung.android.messaging", "com.samsung.android.email.provider",
            "com.sec.android.app.samsungapps", "com.oneplus.mms",
            // Travel
            "com.booking", "com.airbnb.android",
        )

        /**
         * Package prefixes that are always safe (system/OEM apps).
         */
        private val SAFE_PACKAGE_PREFIXES = listOf(
            "com.google.", "com.android.", "com.samsung.", "com.sec.android.",
            "com.huawei.", "com.xiaomi.", "com.oneplus.", "com.oppo.",
            "com.vivo.", "com.motorola.", "com.lge.", "com.sony.",
            "com.asus.", "com.nokia.", "com.realme.",
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val entryPoint: NotificationListenerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, NotificationListenerEntryPoint::class.java)
    }

    /**
     * Rate limiter: don't create more than 3 alerts per 5 minutes
     * to prevent notification spam that annoys B2C users.
     */
    private val recentAlertTimestamps = mutableListOf<Long>()
    private val maxAlertsPerWindow = 3
    private val alertWindowMs = 5 * 60 * 1000L // 5 minutes

    private fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentAlertTimestamps) {
            recentAlertTimestamps.removeAll { now - it > alertWindowMs }
            return recentAlertTimestamps.size >= maxAlertsPerWindow
        }
    }

    private fun recordAlert() {
        synchronized(recentAlertTimestamps) {
            recentAlertTimestamps.add(System.currentTimeMillis())
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { statusBarNotification ->
            try {
                val extras = statusBarNotification.notification.extras ?: return
                val packageName = statusBarNotification.packageName ?: return
                val title = extras.getString("android.title") ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""
                val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

                val fullContent = "$title $text $bigText".trim()
                if (fullContent.isBlank()) return

                // Skip our own notifications (prevents infinite loop)
                if (packageName == applicationContext.packageName) return

                // Skip WHITELISTED legitimate apps — these are trusted brands
                if (packageName in WHITELISTED_PACKAGES) return

                // Skip known OEM/system package prefixes
                if (SAFE_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return

                // Rate limit: don't spam the user
                if (isRateLimited()) {
                    Log.d("NotificationListener", "Rate limited, skipping notification from $packageName")
                    return
                }

                Log.d("NotificationListener", "Scanning notification from $packageName (${fullContent.length} chars)")

                scope.launch {
                    try {
                        val userPreferences = entryPoint.userPreferences()
                        if (!userPreferences.messageShieldEnabled.first()) return@launch

                        val riskEngine = entryPoint.riskEngine()
                        val alertRepository = entryPoint.alertRepository()

                        // F2: Extract and log URLs found in notifications
                        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", RegexOption.IGNORE_CASE)
                        val foundUrls = urlPattern.findAll(fullContent).map { it.value }.toList()
                        if (foundUrls.isNotEmpty()) {
                            Log.d("NotificationListener", "URLs found in notification from $packageName: ${foundUrls.size}")
                        }

                        val riskResult = riskEngine.analyzeText(
                            text = fullContent,
                            source = ThreatSource.NOTIFICATION,
                            metadata = mapOf("package" to packageName, "timestamp" to System.currentTimeMillis().toString(), "urls_found" to foundUrls.size.toString())
                        )

                        // B2C-SAFE: Only alert on genuinely high-risk notifications
                        // Require score >= 45 for notifications (higher than SMS)
                        if (riskResult.shouldAlert && riskResult.score >= 45) {
                            recordAlert()

                            alertRepository.insertAlert(
                                AlertEntity(
                                    threatType = riskResult.threatType,
                                    source = ThreatSource.NOTIFICATION,
                                    severity = riskResult.severity,
                                    score = riskResult.score,
                                    confidence = riskResult.confidence,
                                    title = "Suspicious Notification",
                                    summary = riskResult.explainLikeImFive,
                                    content = fullContent,
                                    senderInfo = packageName,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            val count = alertRepository.getAlertCount()
                            FloatingBubbleService.updateStatus(applicationContext, count, true)

                            val appLabel = try {
                                val pm = applicationContext.packageManager
                                val appInfo = pm.getApplicationInfo(packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) { packageName.substringAfterLast(".") }

                            FloatingBubbleService.pushThreatDetected(
                                context = applicationContext,
                                threatType = "notification",
                                source = appLabel,
                                score = riskResult.score,
                                summary = "Suspicious notification from $appLabel",
                                reasons = riskResult.reasons.joinToString("; ") { it.title }
                            )

                            if (!QuietHoursHelper.shouldSuppressNotification(applicationContext)) {
                                showWarningNotification(appLabel, riskResult.severity.name, riskResult.score)
                            }
                            Log.w("NotificationListener", "THREAT in notification from $packageName (score: ${riskResult.score})")
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("NotificationListener", "Analysis failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error processing notification", e)
            }
        }
    }

    private fun showWarningNotification(app: String, severity: String, score: Int) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val notification = NotificationCompat.Builder(this, DeepfakeShieldApplication.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Suspicious Notification")
                .setContentText("$severity risk from $app (Score: $score%)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Not HIGH — avoid being intrusive
                .setAutoCancel(true)
                .build()

            manager.notify(LISTENER_NOTIFICATION_BASE + com.deepfakeshield.di.AlertNotifierImpl.nextNotificationSequence(), notification)
        } catch (e: Exception) {
            Log.e("NotificationListener", "Failed to show notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
