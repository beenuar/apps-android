package com.deepfakeshield.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import kotlin.math.abs

/**
 * FLOATING PROTECTION BUBBLE — THE APP'S COMMAND CENTER
 *
 * This is the most critical piece of UI in the entire app.
 * It floats over all other apps and provides:
 *
 * 1. REAL-TIME ALERT BANNERS — auto-popup when threats are detected
 *    (deepfake video, scam call, scam message, malicious file, voice clone)
 * 2. FULL COMMAND CENTER — tap the bubble to access ALL features:
 *    - Live deepfake monitor (select any app or full screen)
 *    - Quick scans (video, message, URL, file/AV)
 *    - Shield controls (call, message, clipboard)
 *    - Recent threats feed with analytics
 *    - Navigation to all app screens
 * 3. VISUAL STATE — color changes based on status:
 *    - GREEN = all shields active, no threats
 *    - BLUE = live monitoring active
 *    - RED = threat detected (pulses urgently)
 *    - ORANGE = protection paused
 * 4. HAPTIC FEEDBACK — vibrates on threat detection
 * 5. DRAG & SNAP — repositionable, snaps to screen edge
 */
class FloatingBubbleService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val ACTION_SHOW = "com.deepfakeshield.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.deepfakeshield.HIDE_BUBBLE"
        const val ACTION_UPDATE_STATUS = "com.deepfakeshield.UPDATE_BUBBLE_STATUS"
        const val ACTION_TOGGLE_FULLSCREEN = "com.deepfakeshield.TOGGLE_FULLSCREEN"
        const val ACTION_SCAN_RESULT = "com.deepfakeshield.SCAN_RESULT"
        const val ACTION_THREAT_PUSH = "com.deepfakeshield.THREAT_PUSH"
        const val EXTRA_THREATS_BLOCKED = "threats_blocked"
        const val EXTRA_IS_ACTIVE = "is_active"
        const val EXTRA_LAST_SCAN_RESULT = "last_scan_result"
        const val EXTRA_LAST_SCAN_SCORE = "last_scan_score"
        const val EXTRA_THREAT_TYPE = "threat_type"
        const val EXTRA_THREAT_SOURCE = "threat_source"
        const val EXTRA_THREAT_SCORE = "threat_score"
        const val EXTRA_THREAT_SUMMARY = "threat_summary"
        const val EXTRA_THREAT_REASONS = "threat_reasons"

        private const val BUBBLE_SIZE = 72
        private const val EXPANDED_WIDTH = 340
        private const val EXPANDED_HEIGHT = 520
        const val EXTRA_OPEN_ROUTE = "open_route"
        const val ROUTE_VIDEO_SCAN = "video_scan"
        const val ROUTE_MESSAGE_SCAN = "message_scan"
        const val ROUTE_URL_SCAN = "qr_scanner"

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply { action = ACTION_SHOW }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) { context.stopService(Intent(context, FloatingBubbleService::class.java)) }

        fun updateStatus(context: Context, threatsBlocked: Int, isActive: Boolean) {
            try {
                context.startService(Intent(context, FloatingBubbleService::class.java).apply {
                    action = ACTION_UPDATE_STATUS
                    putExtra(EXTRA_THREATS_BLOCKED, threatsBlocked)
                    putExtra(EXTRA_IS_ACTIVE, isActive)
                })
            } catch (_: Exception) {}
        }

        fun pushScanResult(context: Context, resultSummary: String, score: Int) {
            try {
                context.startService(Intent(context, FloatingBubbleService::class.java).apply {
                    action = ACTION_SCAN_RESULT
                    putExtra(EXTRA_LAST_SCAN_RESULT, resultSummary)
                    putExtra(EXTRA_LAST_SCAN_SCORE, score)
                })
            } catch (_: Exception) {}
        }

        fun pushThreatDetected(context: Context, threatType: String, source: String, score: Int, summary: String, reasons: String = "") {
            try {
                context.startService(Intent(context, FloatingBubbleService::class.java).apply {
                    action = ACTION_THREAT_PUSH
                    putExtra(EXTRA_THREAT_TYPE, threatType)
                    putExtra(EXTRA_THREAT_SOURCE, source)
                    putExtra(EXTRA_THREAT_SCORE, score)
                    putExtra(EXTRA_THREAT_SUMMARY, summary)
                    putExtra(EXTRA_THREAT_REASONS, reasons)
                })
            } catch (_: Exception) {}
        }
    }

    // ─── State ───
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var alertBannerView: View? = null
    private var fullScreenOverlayView: View? = null
    private var isExpanded = false
    private var isFullScreenMode = false

    @Volatile private var threatsBlocked = 0
    @Volatile private var isActive = true
    @Volatile private var lastScanResult: String? = null
    @Volatile private var lastScanScore: Int = -1
    @Volatile private var isMonitoringScreen: Boolean = false
    @Volatile private var monitoringTarget: String = "full_screen"
    @Volatile private var deepfakeDetectionCount: Int = 0
    @Volatile private var scamCallCount: Int = 0
    @Volatile private var scamMessageCount: Int = 0
    @Volatile private var malwareCount: Int = 0
    @Volatile private var hasActiveAlert = false

    data class ThreatInfo(val type: String, val source: String, val score: Int, val summary: String, val reasons: String, val time: Long = System.currentTimeMillis())
    private val recentThreats = mutableListOf<ThreatInfo>()
    private val maxRecentThreats = 15

    private val handler = Handler(Looper.getMainLooper())
    private val autoCollapseRunnable = Runnable { collapsePanel() }
    private val autoDismissAlertRunnable = Runnable { dismissAlertBanner() }
    private var pulseAnimator: ValueAnimator? = null
    private var redPulseAnimator: ValueAnimator? = null

    private var bubbleX = 0
    private var bubbleY = 400

    data class AppOption(val packageName: String, val label: String, val emoji: String)
    private val monitorableApps = listOf(
        AppOption("full_screen", "Full Screen", "\uD83D\uDDA5\uFE0F"),
        AppOption("com.whatsapp", "WhatsApp", "\uD83D\uDCAC"),
        AppOption("com.facebook.katana", "Facebook", "\uD83D\uDCD8"),
        AppOption("com.instagram.android", "Instagram", "\uD83D\uDCF7"),
        AppOption("com.zhiliaoapp.musically", "TikTok", "\uD83C\uDFB5"),
        AppOption("com.google.android.youtube", "YouTube", "\u25B6\uFE0F"),
        AppOption("org.telegram.messenger", "Telegram", "\u2708\uFE0F"),
        AppOption("com.snapchat.android", "Snapchat", "\uD83D\uDC7B"),
        AppOption("com.twitter.android", "X/Twitter", "\uD83D\uDC26"),
        AppOption("us.zoom.videomeetings", "Zoom", "\uD83C\uDF10"),
        AppOption("com.skype.raider", "Skype", "\uD83D\uDCDE"),
        AppOption("com.discord", "Discord", "\uD83C\uDFAE"),
        AppOption("com.linkedin.android", "LinkedIn", "\uD83D\uDCBC"),
    )

    // ─── Lifecycle ───

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingBubble", "Failed to start foreground", e)
            stopSelf(); return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW, null -> showBubble()
            ACTION_HIDE -> hideBubble()
            ACTION_TOGGLE_FULLSCREEN -> {
                if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
                if (bubbleView == null) showBubble()
                toggleFullScreenOverlay()
            }
            ACTION_UPDATE_STATUS -> {
                threatsBlocked = intent.getIntExtra(EXTRA_THREATS_BLOCKED, 0)
                isActive = intent.getBooleanExtra(EXTRA_IS_ACTIVE, true)
                updateBubbleAppearance()
                updateNotification()
            }
            ACTION_SCAN_RESULT -> {
                lastScanResult = intent.getStringExtra(EXTRA_LAST_SCAN_RESULT)
                lastScanScore = intent.getIntExtra(EXTRA_LAST_SCAN_SCORE, -1)
                isMonitoringScreen = ScreenCaptureService.isRunning
                // B2C-SAFE: Don't increment detection count or haptic alert here.
                // The ACTION_THREAT_PUSH handler already does this properly with
                // cooldown/dedup. This path is for status updates only (e.g. "Monitoring...").
                // Previously this was firing on every pushScanResult causing constant
                // red pulsing and detection count inflation.
                updateBubbleAppearance(); updateNotification()
            }
            ACTION_THREAT_PUSH -> handleThreatPush(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pulseAnimator?.cancel(); pulseAnimator = null
        redPulseAnimator?.cancel(); redPulseAnimator = null
        handler.removeCallbacksAndMessages(null)
        dismissAlertBanner(); hideFullScreenOverlay(); hideBubble()
        super.onDestroy()
    }

    // ─── Threat Handling ───

    private fun handleThreatPush(intent: Intent) {
        val type = intent.getStringExtra(EXTRA_THREAT_TYPE) ?: "unknown"
        val source = intent.getStringExtra(EXTRA_THREAT_SOURCE) ?: ""
        val score = intent.getIntExtra(EXTRA_THREAT_SCORE, 0)
        val summary = intent.getStringExtra(EXTRA_THREAT_SUMMARY) ?: ""
        val reasons = intent.getStringExtra(EXTRA_THREAT_REASONS) ?: ""

        // Always record the threat for the feed/stats (even if banner suppressed)
        synchronized(recentThreats) {
            recentThreats.add(0, ThreatInfo(type, source, score, summary, reasons))
            while (recentThreats.size > maxRecentThreats) recentThreats.removeAt(recentThreats.size - 1)
        }

        when (type) {
            "call" -> scamCallCount++
            "sms", "notification", "clipboard" -> scamMessageCount++
            "deepfake" -> deepfakeDetectionCount++
            "malware", "av" -> malwareCount++
        }

        val emoji = when (type) {
            "call" -> "\uD83D\uDCDE"; "sms" -> "\uD83D\uDCF1"; "notification" -> "\uD83D\uDD14"
            "clipboard" -> "\uD83D\uDCCB"; "deepfake" -> "\uD83C\uDFAD"; "malware", "av" -> "\uD83D\uDC1B"
            else -> "\u26A0\uFE0F"
        }
        lastScanResult = "$emoji $summary"
        lastScanScore = score
        hasActiveAlert = true

        // Update bubble appearance and notification (always, for badge/stats)
        updateBubbleAppearance()
        updateNotification()

        // B2C-SAFE: Only show alert banner + haptic if NOT snoozed, NOT on cooldown,
        // and NOT a duplicate of the same alert type within 30 seconds.
        // This prevents the infuriating experience of unstoppable alert popups.
        val shouldShowBanner = !isSnoozed() && !isOnCooldown() && !isDuplicateBanner(type)

        if (shouldShowBanner) {
            startRedPulse()
            hapticAlert()
            showAlertBanner(type, source, score, summary, reasons)
            recordBannerShown(type)
        }
    }

    // ─── Haptic Feedback ───

    private fun hapticAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val v = vm?.defaultVibrator
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 200), -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 200), -1))
            }
        } catch (_: Exception) {}
    }

    // ─── Snooze & Cooldown System (B2C-friendly: don't annoy users) ───

    @Volatile private var snoozeUntil: Long = 0L
    @Volatile private var bannerCooldownUntil: Long = 0L
    @Volatile private var lastBannerType: String = ""
    @Volatile private var lastBannerTime: Long = 0L

    private fun snoozeAlerts(durationMs: Long) {
        snoozeUntil = System.currentTimeMillis() + durationMs
    }

    private fun isSnoozed(): Boolean = System.currentTimeMillis() < snoozeUntil

    /**
     * After user dismisses a banner, suppress new banners for 60 seconds.
     * This prevents the infuriating experience where dismissing an alert
     * immediately pops up the same alert again (e.g., continuous deepfake detection).
     */
    private fun startDismissCooldown() {
        bannerCooldownUntil = System.currentTimeMillis() + 60_000L // 60 seconds
    }

    private fun isOnCooldown(): Boolean = System.currentTimeMillis() < bannerCooldownUntil

    /**
     * Deduplicate: don't show the same type of banner within 30 seconds.
     * The ScreenCaptureService fires every few seconds for the same deepfake.
     */
    private fun isDuplicateBanner(type: String): Boolean {
        val now = System.currentTimeMillis()
        return type == lastBannerType && now - lastBannerTime < 30_000L
    }

    private fun recordBannerShown(type: String) {
        lastBannerType = type
        lastBannerTime = System.currentTimeMillis()
    }

    // ─── Alert Banner (auto-popup on threat detection) ───

    /**
     * Show the alert banner with clear risk score, reasons, and dismissal options.
     * B2C-SAFE: users can dismiss, snooze, or view details.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun showAlertBanner(type: String, source: String, score: Int, summary: String, reasons: String) {
        if (!Settings.canDrawOverlays(this)) return
        // Respect snooze
        if (isSnoozed()) { hasActiveAlert = false; return }

        dismissAlertBanner()

        val dp = resources.displayMetrics.density
        val screenWidth = getScreenWidth()

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = 20f * dp
                val bgColor = when {
                    score > 70 -> Color.parseColor("#1C1B1F")
                    score > 40 -> Color.parseColor("#1C1B1F")
                    else -> Color.parseColor("#1C1B1F")
                }
                setColor(bgColor)
                setStroke((1.5f * dp).toInt(), when {
                    score > 70 -> Color.parseColor("#FF5252")
                    score > 40 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#FFD740")
                })
            }
            background = bg
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            elevation = 16f * dp
        }

        val typeLabel = when (type) {
            "call" -> "\uD83D\uDCDE Scam Call"; "sms" -> "\uD83D\uDCF1 Scam SMS"; "notification" -> "\uD83D\uDD14 Suspicious Notification"
            "clipboard" -> "\uD83D\uDCCB Clipboard Threat"; "deepfake" -> "\uD83C\uDFAD Deepfake Detected"
            "malware", "av" -> "\uD83D\uDC1B Malware Found"; else -> "\u26A0\uFE0F Threat Detected"
        }

        // ── Row 1: Title + Close X ──
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = typeLabel
            setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // Close X button (always visible, easy to tap)
        titleRow.addView(TextView(this).apply {
            text = "\u2715"; setTextColor(Color.WHITE); textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding((10 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener { dismissAlertBanner() }
        })
        banner.addView(titleRow)

        // ── Row 2: Risk Score Bar ──
        val scoreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        scoreRow.addView(TextView(this).apply {
            text = "Risk Score"
            setTextColor(Color.argb(200, 255, 255, 255)); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val scoreColor = when {
            score >= 70 -> "#FF1744"; score >= 40 -> "#FF9100"; else -> "#FFEA00"
        }
        scoreRow.addView(TextView(this).apply {
            text = "  ${score}%  "
            setTextColor(Color.WHITE); textSize = 18f; setTypeface(null, Typeface.BOLD)
            val scoreBg = GradientDrawable().apply {
                cornerRadius = 14f * dp
                setColor(Color.parseColor(scoreColor))
            }
            background = scoreBg
            setPadding((12 * dp).toInt(), (3 * dp).toInt(), (12 * dp).toInt(), (3 * dp).toInt())
        })
        banner.addView(scoreRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * dp).toInt() })

        // ── Row 3: Summary ──
        banner.addView(TextView(this).apply {
            text = summary; setTextColor(Color.argb(240, 255, 255, 255)); textSize = 13f
            setPadding(0, (6 * dp).toInt(), 0, 0); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })

        // ── Row 4: Reasons (why this was flagged) ──
        if (reasons.isNotBlank()) {
            banner.addView(TextView(this).apply {
                text = "\uD83D\uDD0D Why: $reasons"
                setTextColor(Color.argb(210, 255, 255, 255)); textSize = 11.5f
                setPadding(0, (5 * dp).toInt(), 0, 0); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                val reasonBg = GradientDrawable().apply { cornerRadius = 8f * dp; setColor(Color.argb(40, 0, 0, 0)) }
                background = reasonBg
                setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
            })
        }

        // ── Row 5: Action buttons: View Details | Snooze 30m | Dismiss ──
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        btnRow.addView(makeBannerBtn(dp, "\uD83D\uDD0E Details") {
            dismissAlertBanner()
            startActivity(Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_ROUTE, "alerts")
            })
        })
        btnRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), 1) })
        btnRow.addView(makeBannerBtn(dp, "\uD83D\uDD15 Snooze") {
            snoozeAlerts(30 * 60 * 1000L) // Snooze for 30 minutes
            dismissAlertBanner()
        })
        btnRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), 1) })
        btnRow.addView(makeBannerBtn(dp, "\u2715 Dismiss") { dismissAlertBanner() })
        banner.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (10 * dp).toInt() })

        val params = WindowManager.LayoutParams(
            screenWidth - (24 * dp).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (48 * dp).toInt() }

        try {
            windowManager?.addView(banner, params)
            alertBannerView = banner
            banner.translationY = -200f * dp; banner.alpha = 0f
            banner.animate().translationY(0f).alpha(1f).setDuration(350).setInterpolator(OvershootInterpolator(0.8f)).start()
            // Auto-dismiss after 10 seconds (not too long, B2C friendly)
            handler.removeCallbacks(autoDismissAlertRunnable)
            handler.postDelayed(autoDismissAlertRunnable, 10000)
        } catch (e: Exception) {
            android.util.Log.e("FloatingBubble", "Failed to show alert banner", e)
            alertBannerView = null
        }
    }

    private fun dismissAlertBanner() {
        handler.removeCallbacks(autoDismissAlertRunnable)
        val banner = alertBannerView ?: return
        banner.animate().translationY(-200f * resources.displayMetrics.density).alpha(0f).setDuration(200).withEndAction {
            try { windowManager?.removeView(banner) } catch (_: Exception) {}
            alertBannerView = null
        }.start()
        hasActiveAlert = false
        // B2C-SAFE: After dismissing, suppress new banners for 60 seconds
        startDismissCooldown()
    }

    private fun makeBannerBtn(dp: Float, label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 11f; isAllCaps = false
            val bg = GradientDrawable().apply { cornerRadius = 12f * dp; setColor(Color.argb(35, 255, 255, 255)) }
            background = bg; setTextColor(Color.WHITE)
            setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            minimumHeight = 0; minHeight = 0; stateListAnimator = null; elevation = 0f
            setOnClickListener { onClick() }
        }
    }

    // ─── Bubble View ───

    private fun showBubble() {
        if (bubbleView != null) return
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        val bubble = BubbleView(this)
        bubbleView = bubble

        val params = WindowManager.LayoutParams(
            dpToPx(BUBBLE_SIZE), dpToPx(BUBBLE_SIZE),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Position at bottom-right to avoid overlapping home screen content.
            val sw = getScreenWidth(); val sh = getScreenHeight()
            x = sw - dpToPx(BUBBLE_SIZE) - dpToPx(12)
            y = sh - dpToPx(BUBBLE_SIZE) - dpToPx(160) // Above nav bar
        }

        bubbleX = params.x; bubbleY = params.y

        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false; var touchStartTime = 0L

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false; touchStartTime = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx.toInt(); params.y = initialY + dy.toInt()
                        try { windowManager?.updateViewLayout(bubble, params) } catch (_: Exception) {}
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging && System.currentTimeMillis() - touchStartTime < 300) toggleExpanded()
                    else if (isDragging) snapToEdge(params, bubble)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { if (isDragging) snapToEdge(params, bubble); isDragging = false; true }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubble, params)
            startPulseAnimation(bubble)
        } catch (e: Exception) { bubbleView = null }
    }

    private fun hideBubble() {
        pulseAnimator?.cancel(); pulseAnimator = null
        try { bubbleView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        bubbleView = null
        try { expandedView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        expandedView = null; isExpanded = false
        hideFullScreenOverlay()
    }

    // ─── Expanded Panel ───

    private fun toggleExpanded() { if (isExpanded) collapsePanel() else expandPanel() }

    private fun expandPanel() {
        if (expandedView != null || bubbleView == null) return
        try { expandedView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        expandedView = null; isExpanded = true

        val threatsCopy = synchronized(recentThreats) { recentThreats.toList() }
        val panel = buildExpandedPanel(threatsCopy)
        expandedView = panel

        val w = dpToPx(EXPANDED_WIDTH); val h = dpToPx(EXPANDED_HEIGHT)
        val screenWidth = getScreenWidth(); val screenHeight = getScreenHeight()
        val panelX = if (bubbleX < screenWidth / 2) bubbleX + dpToPx(BUBBLE_SIZE) + dpToPx(8) else bubbleX - w - dpToPx(8)
        val panelY = bubbleY.coerceIn(0, maxOf(0, screenHeight - h))

        val params = WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = panelX.coerceIn(0, maxOf(0, screenWidth - w)); y = panelY }

        try {
            windowManager?.addView(panel, params)
            panel.scaleX = 0.5f; panel.scaleY = 0.5f; panel.alpha = 0f
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(OvershootInterpolator(1.5f)).start()
            handler.removeCallbacks(autoCollapseRunnable)
            handler.postDelayed(autoCollapseRunnable, 15000)
        } catch (_: Exception) { expandedView = null; isExpanded = false }
    }

    private fun collapsePanel() {
        handler.removeCallbacks(autoCollapseRunnable)
        val panel = expandedView ?: return
        panel.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(150).withEndAction {
            try { windowManager?.removeView(panel) } catch (_: Exception) {}
            expandedView = null; isExpanded = false
        }.start()
    }

    // ─── Build the Expanded Panel (Command Center) ───

    private fun buildExpandedPanel(threatsList: List<ThreatInfo>): FrameLayout {
        val dp = resources.displayMetrics.density
        val isAlert = lastScanScore > 40 || hasActiveAlert
        val totalThreats = scamCallCount + scamMessageCount + deepfakeDetectionCount + malwareCount

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.TRANSPARENT)

        val card = View(this)
        card.background = GradientDrawable().apply {
            cornerRadius = 24f * dp
            val bgColor = when {
                isAlert -> Color.parseColor("#1C1B1F")
                isMonitoringScreen -> Color.parseColor("#1A1C2E")
                totalThreats > 0 -> Color.parseColor("#1C1B1F")
                isActive -> Color.parseColor("#1A2E1C")
                else -> Color.parseColor("#2E2518")
            }
            setColor(bgColor)
        }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true; isSmoothScrollingEnabled = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // ━━━ HEADER ━━━
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val statusColor = when {
            isAlert -> Color.parseColor("#FF5252")
            isMonitoringScreen -> Color.parseColor("#448AFF")
            totalThreats > 0 -> Color.parseColor("#FF9800")
            isActive -> Color.parseColor("#69F0AE")
            else -> Color.parseColor("#FFD740")
        }
        headerRow.addView(View(this).apply {
            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(statusColor) }
            background = dot; layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt()).apply { marginEnd = (8 * dp).toInt() }
        })
        headerRow.addView(TextView(this).apply {
            text = when {
                isAlert -> "Threat Detected"
                isMonitoringScreen -> "Live Monitoring"
                totalThreats > 0 -> "$totalThreats Threat${if (totalThreats == 1) "" else "s"} Blocked"
                isActive -> "Cyble Active"
                else -> "Protection Paused"
            }
            setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text = "\u2715"; setTextColor(Color.argb(120, 255, 255, 255)); textSize = 16f
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { collapsePanel() }
        })
        content.addView(headerRow)

        if (lastScanResult != null) {
            content.addView(TextView(this).apply {
                text = lastScanResult; setTextColor(if (isAlert) Color.parseColor("#FFCDD2") else Color.parseColor("#C8E6C9"))
                textSize = 11f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, (2 * dp).toInt(), 0, (4 * dp).toInt())
            })
        }

        // ━━━ STATS DASHBOARD ━━━
        if (totalThreats > 0 || isMonitoringScreen) {
            val statsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply { cornerRadius = 10f * dp; setColor(Color.argb(50, 0, 0, 0)) }
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }
            if (scamCallCount > 0) statsRow.addView(statBadge(dp, "\uD83D\uDCDE", "$scamCallCount", "Calls", "#FF8A80"))
            if (scamMessageCount > 0) statsRow.addView(statBadge(dp, "\uD83D\uDCF1", "$scamMessageCount", "Msgs", "#FFAB91"))
            if (deepfakeDetectionCount > 0) statsRow.addView(statBadge(dp, "\uD83C\uDFAD", "$deepfakeDetectionCount", "Fakes", "#EF9A9A"))
            if (malwareCount > 0) statsRow.addView(statBadge(dp, "\uD83D\uDC1B", "$malwareCount", "Files", "#CE93D8"))
            if (totalThreats == 0 && isMonitoringScreen) statsRow.addView(statBadge(dp, "\uD83D\uDD35", "", "Scanning", "#90CAF9"))
            content.addView(statsRow, lp(top = 6))
        }

        // ━━━ RECENT THREATS FEED ━━━
        if (threatsList.isNotEmpty()) {
            content.addView(sectionLabel(dp, "\uD83D\uDEA8 RECENT THREATS"), lp(top = 8))
            val feedBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { cornerRadius = 10f * dp; setColor(Color.argb(40, 0, 0, 0)) }
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            }
            for (threat in threatsList.take(4)) {
                val emoji = when (threat.type) { "call" -> "\uD83D\uDCDE"; "sms" -> "\uD83D\uDCF1"; "notification" -> "\uD83D\uDD14"; "clipboard" -> "\uD83D\uDCCB"; "deepfake" -> "\uD83C\uDFAD"; "malware", "av" -> "\uD83D\uDC1B"; else -> "\u26A0\uFE0F" }
                val ageMin = ((System.currentTimeMillis() - threat.time) / 60000).toInt()
                val time = when { ageMin < 1 -> "now"; ageMin < 60 -> "${ageMin}m"; else -> "${ageMin / 60}h" }

                val threatItem = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply { cornerRadius = 6f * dp; setColor(Color.argb(30, 255, 255, 255)) }
                    setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
                    setOnClickListener {
                        startActivity(Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(EXTRA_OPEN_ROUTE, "alerts")
                        })
                        collapsePanel()
                    }
                }
                // Title row: emoji + summary + score badge + time
                val titleRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                titleRow2.addView(TextView(this).apply {
                    text = "$emoji ${threat.summary.take(28)}"
                    setTextColor(if (threat.score > 50) Color.parseColor("#FF8A80") else Color.parseColor("#FFCC80"))
                    textSize = 10.5f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                titleRow2.addView(TextView(this).apply {
                    val scoreColor2 = when { threat.score >= 70 -> "#FF1744"; threat.score >= 40 -> "#FF9100"; else -> "#FFEA00" }
                    text = " ${threat.score}% "
                    setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
                    val scoreBg2 = GradientDrawable().apply { cornerRadius = 8f * dp; setColor(Color.parseColor(scoreColor2)) }
                    background = scoreBg2
                    setPadding((5 * dp).toInt(), (1 * dp).toInt(), (5 * dp).toInt(), (1 * dp).toInt())
                })
                titleRow2.addView(TextView(this).apply {
                    text = " $time"; setTextColor(Color.argb(150, 255, 255, 255)); textSize = 9.5f
                })
                threatItem.addView(titleRow2)

                // Reason row (if available)
                if (threat.reasons.isNotBlank()) {
                    threatItem.addView(TextView(this).apply {
                        text = threat.reasons.take(60)
                        setTextColor(Color.argb(160, 255, 255, 255)); textSize = 9f; maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding((2 * dp).toInt(), (1 * dp).toInt(), 0, 0)
                    })
                }
                feedBox.addView(threatItem, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (3 * dp).toInt() })
            }
            if (threatsList.size > 4) {
                feedBox.addView(TextView(this).apply {
                    text = "View all ${threatsList.size} threats \u2192"
                    setTextColor(Color.parseColor("#82B1FF")); textSize = 11f; setTypeface(null, Typeface.BOLD)
                    setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
                    setOnClickListener { openRoute("alerts") }
                })
            }
            content.addView(feedBox, lp(top = 2))
        }

        // ━━━ PROTECT MY IP (simple toggle) ━━━
        val torRunning = EmbeddedTorManager.isRunning()
        val vpnActive = TorVpnService.isRunning
        val torStarting = com.deepfakeshield.core.network.TorNetworkModule.mode == "starting"
        val torError = com.deepfakeshield.core.network.TorNetworkModule.mode == "error"
        val allProtected = torRunning && vpnActive
        val ipToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            val bgColor = when {
                allProtected -> Color.argb(35, 124, 77, 255)
                torRunning -> Color.argb(30, 76, 175, 80)
                torStarting -> Color.argb(25, 255, 152, 0)
                torError -> Color.argb(25, 244, 67, 54)
                else -> Color.argb(15, 255, 255, 255)
            }
            background = GradientDrawable().apply { cornerRadius = 12f * dp; setColor(bgColor) }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        ipToggle.addView(TextView(this).apply {
            text = when {
                allProtected -> "\uD83D\uDEE1\uFE0F"
                torRunning -> "\uD83D\uDD12"
                torStarting -> "\u23F3"
                torError -> "\u26A0\uFE0F"
                else -> "\uD83C\uDF10"
            }
            textSize = 18f
        })
        ipToggle.addView(TextView(this).apply {
            text = when {
                allProtected -> "All Apps Protected"
                torRunning -> "This App Only"
                torStarting -> "Connecting..."
                torError -> "Failed"
                else -> "Protect My IP"
            }
            setTextColor(when {
                allProtected -> Color.parseColor("#B388FF")
                torRunning -> Color.parseColor("#A5D6A7")
                torStarting -> Color.parseColor("#FFE082")
                torError -> Color.parseColor("#FF8A80")
                else -> Color.WHITE
            })
            textSize = 13f; setTypeface(null, Typeface.BOLD)
            setPadding((8 * dp).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        ipToggle.addView(Button(this).apply {
            text = if (torRunning || torStarting) "OFF" else "ON"
            textSize = 10f; setTypeface(null, Typeface.BOLD)
            val bgD = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(if (torRunning || torStarting) Color.argb(80, 255, 255, 255) else Color.parseColor("#7C4DFF"))
            }
            background = bgD; setTextColor(Color.WHITE)
            minimumWidth = 0; minimumHeight = 0; setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                if (torRunning || torStarting) EmbeddedTorManager.stop(this@FloatingBubbleService)
                else EmbeddedTorManager.start(this@FloatingBubbleService)
                collapsePanel()
            }
        })
        content.addView(ipToggle, lp(top = 6))

        // ━━━ LIVE DEEPFAKE MONITOR ━━━
        content.addView(sectionLabel(dp, "\uD83D\uDCF9 LIVE DEEPFAKE MONITOR"), lp(top = 8))
        if (isMonitoringScreen) {
            content.addView(actionBtn(dp, "\u23F9 Stop Monitoring", "#D32F2F", Color.WHITE) {
                isMonitoringScreen = false; collapsePanel(); ScreenCaptureService.stop(this)
            }, lp(top = 2))
        } else {
            val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (app in monitorableApps) {
                chipRow.addView(chipView(dp, "${app.emoji} ${app.label}") {
                    monitoringTarget = app.packageName; isMonitoringScreen = true
                    collapsePanel(); ScreenCaptureActivity.launch(this, app.packageName)
                })
            }
            chipScroll.addView(chipRow)
            content.addView(chipScroll, lp(top = 2))
        }

        // ━━━ QUICK ACTIONS ━━━
        content.addView(divider(dp), lp(top = 8, bottom = 6))
        content.addView(sectionLabel(dp, "\u26A1 QUICK ACTIONS"))

        val actRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow1.addView(actionBtn(dp, "\uD83D\uDCF9 Video", "#2E7D32") { openRoute(ROUTE_VIDEO_SCAN) }, wt(1f, end = 3))
        actRow1.addView(actionBtn(dp, "\uD83D\uDCAC Message", "#2E7D32") { openRoute(ROUTE_MESSAGE_SCAN) }, wt(1f, start = 3))
        content.addView(actRow1, lp(top = 4))

        val actRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow2.addView(actionBtn(dp, "\uD83D\uDD17 URL/QR", "#1565C0") { openRoute(ROUTE_URL_SCAN) }, wt(1f, end = 3))
        actRow2.addView(actionBtn(dp, "\uD83D\uDC1B AV Scan", "#6A1B9A") { openRoute("diagnostics") }, wt(1f, start = 3))
        content.addView(actRow2, lp(top = 4))

        val actRow3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow3.addView(actionBtn(dp, "\uD83D\uDCDE Call Shield", "#0D47A1") { openRoute("call_protection") }, wt(1f, end = 3))
        actRow3.addView(actionBtn(dp, "\uD83C\uDF10 Safe Browser", "#00695C") { openRoute("safe_browser") }, wt(1f, start = 3))
        content.addView(actRow3, lp(top = 4))

        // ━━━ NAVIGATION ━━━
        content.addView(divider(dp), lp(top = 8, bottom = 6))

        val navRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow1.addView(navBtn(dp, "\uD83D\uDCCA Analytics") { openRoute("analytics") }, wt(1f, end = 3))
        navRow1.addView(navBtn(dp, "\uD83D\uDEA8 Alerts") { openRoute("alerts") }, wt(1f, start = 3))
        content.addView(navRow1, lp(top = 2))

        val navRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow2.addView(navBtn(dp, "\u2699\uFE0F Settings") { openRoute("settings") }, wt(1f, end = 3))
        navRow2.addView(navBtn(dp, "\uD83D\uDD10 Vault") { openRoute("vault") }, wt(1f, start = 3))
        content.addView(navRow2, lp(top = 4))

        val navRow3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow3.addView(navBtn(dp, "\uD83C\uDF93 Education") { openRoute("education") }, wt(1f, end = 3))
        navRow3.addView(navBtn(dp, "\u23F9 Stop Bubble") {
            collapsePanel(); stopSelf()
        }, wt(1f, start = 3))
        content.addView(navRow3, lp(top = 4))

        scroll.addView(content)
        root.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    // ─── Panel Builder Helpers ───

    private fun openRoute(route: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_ROUTE, route)
        })
        collapsePanel()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sectionLabel(dp: Float, text: String) = TextView(this).apply {
        this.text = text; setTextColor(Color.argb(180, 255, 255, 255)); textSize = 10f
        setTypeface(null, Typeface.BOLD); letterSpacing = 0.05f
    }

    @Suppress("UNUSED_PARAMETER")
    private fun divider(dp: Float) = View(this).apply {
        setBackgroundColor(Color.argb(50, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
    }

    private fun actionBtn(dp: Float, label: String, bgColor: String, textColor: Int = Color.WHITE, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = 14f * dp; setColor(Color.parseColor(bgColor)) }
        setTextColor(textColor); setTypeface(null, Typeface.BOLD)
        setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        minimumHeight = 0; minHeight = 0; stateListAnimator = null; elevation = 0f
        setOnClickListener { onClick() }
    }

    private fun navBtn(dp: Float, label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = 12f * dp; setColor(Color.argb(40, 255, 255, 255)) }
        setTextColor(Color.WHITE)
        setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
        minimumHeight = 0; minHeight = 0; setOnClickListener { onClick() }
    }

    private fun chipView(dp: Float, label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = 16f * dp; setColor(Color.argb(80, 255, 255, 255)); setStroke((1 * dp).toInt(), Color.argb(100, 255, 255, 255)) }
        setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
        val clp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = (6 * dp).toInt() }
        layoutParams = clp; setOnClickListener { onClick() }
    }

    private fun statBadge(dp: Float, emoji: String, count: String, label: String, color: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(context).apply { text = "$emoji $count"; setTextColor(Color.parseColor(color)); textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER })
        addView(TextView(context).apply { text = label; setTextColor(Color.argb(180, 255, 255, 255)); textSize = 9f; gravity = Gravity.CENTER })
    }

    private fun lp(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (top * resources.displayMetrics.density).toInt(); bottomMargin = (bottom * resources.displayMetrics.density).toInt()
    }

    private fun wt(weight: Float, start: Int = 0, end: Int = 0) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
        marginStart = (start * resources.displayMetrics.density).toInt(); marginEnd = (end * resources.displayMetrics.density).toInt()
    }

    // ─── Animation & Appearance ───

    private fun snapToEdge(params: WindowManager.LayoutParams, view: View) {
        val screenWidth = getScreenWidth()
        val targetX = if (params.x + dpToPx(BUBBLE_SIZE) / 2 < screenWidth / 2) 0 else screenWidth - dpToPx(BUBBLE_SIZE)
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 200
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int; bubbleX = params.x; bubbleY = params.y
                try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
            }; start()
        }
    }

    private fun startPulseAnimation(view: View) {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.0f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { view.scaleX = it.animatedValue as Float; view.scaleY = it.animatedValue as Float }
            start()
        }
    }

    private fun startRedPulse() {
        redPulseAnimator?.cancel()
        val bubble = bubbleView ?: return
        redPulseAnimator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 400; repeatCount = 5; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { bubble.scaleX = it.animatedValue as Float; bubble.scaleY = it.animatedValue as Float }
            start()
        }
    }

    private fun updateBubbleAppearance() {
        (bubbleView as? BubbleView)?.let {
            it.isProtectionActive = isActive; it.threatsBlocked = threatsBlocked
            it.isDeepfakeAlert = lastScanScore > 40 || hasActiveAlert
            it.isMonitoring = isMonitoringScreen
            it.totalDetections = scamCallCount + scamMessageCount + deepfakeDetectionCount + malwareCount
            it.invalidate()
        }
    }

    // ─── Full-Screen Overlay ───

    private fun toggleFullScreenOverlay() { if (isFullScreenMode) hideFullScreenOverlay() else showFullScreenOverlay() }

    private fun showFullScreenOverlay() {
        if (fullScreenOverlayView != null || !Settings.canDrawOverlays(this)) return
        isFullScreenMode = true

        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.argb(160, 0, 0, 0)); setOnClickListener { hideFullScreenOverlay() } }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, dpToPx(80)) }
        col.addView(TextView(this).apply { text = "\uD83D\uDEE1\uFE0F"; textSize = 52f; gravity = Gravity.CENTER })
        col.addView(TextView(this).apply { text = "DeepFake Shield Active"; setTextColor(Color.WHITE); textSize = 22f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, dpToPx(12), 0, 0) })
        val total = scamCallCount + scamMessageCount + deepfakeDetectionCount + malwareCount
        col.addView(TextView(this).apply {
            text = if (total > 0) "$total threats blocked this session" else "All shields active — monitoring threats"
            setTextColor(Color.argb(220, 255, 255, 255)); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, dpToPx(8), 0, 0)
        })
        col.addView(Button(this).apply {
            text = "Minimize"; setBackgroundColor(Color.argb(200, 255, 255, 255)); setTextColor(Color.parseColor("#1B5E20"))
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
            setOnClickListener { hideFullScreenOverlay() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(24); gravity = Gravity.CENTER_HORIZONTAL })

        overlay.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.CENTER })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try { windowManager?.addView(overlay, params); fullScreenOverlayView = overlay }
        catch (_: Exception) { isFullScreenMode = false }
    }

    private fun hideFullScreenOverlay() {
        fullScreenOverlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        fullScreenOverlayView = null; isFullScreenMode = false
    }

    // ─── Utilities ───

    private fun getScreenWidth(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) windowManager?.currentWindowMetrics?.bounds?.width() ?: resources.displayMetrics.widthPixels else resources.displayMetrics.widthPixels
    private fun getScreenHeight(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) windowManager?.currentWindowMetrics?.bounds?.height() ?: resources.displayMetrics.heightPixels else resources.displayMetrics.heightPixels
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun updateNotification() { try { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, createNotification()) } catch (_: Exception) {} }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val total = scamCallCount + scamMessageCount + deepfakeDetectionCount + malwareCount
        val statusText = if (isActive) { if (total > 0) "$total threats blocked" else "All shields active" } else "Protection paused"

        return NotificationCompat.Builder(this, DeepfakeShieldApplication.CHANNEL_SERVICE)
            .setContentTitle(if (isActive) "\uD83D\uDEE1\uFE0F Shield Active" else "\u26A0\uFE0F Shield Paused")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent).setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (isActive) "$statusText\n\n\u2705 Deepfake detection\n\u2705 SMS & message scanning\n\u2705 Call screening\n\u2705 AV & file monitoring\n\nTap to open" else "Tap to re-enable shields."))
            .build()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // BubbleView — Custom drawn floating shield
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private class BubbleView(context: Context) : View(context) {
        var isProtectionActive = true
        var threatsBlocked = 0
        var isDeepfakeAlert = false
        var isMonitoring = false
        var totalDetections = 0

        private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val monitorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD32F2F.toInt() }
        private val shieldPath = Path()
        private var cachedWidth = 0
        private var cachedGlowColors = intArrayOf(0, 0)
        private var cachedBgColors = intArrayOf(0, 0)

        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); shieldPath.reset() }

        private fun updateShaders(cx: Float, cy: Float, r: Float, glowColor: Int, hilite: Int, mainColor: Int) {
            glowPaint.shader = RadialGradient(cx, cy, r * 1.3f, intArrayOf(glowColor, Color.TRANSPARENT), floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
            bgPaint.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.5f, intArrayOf(hilite, mainColor), null, Shader.TileMode.CLAMP)
            cachedWidth = width
            cachedGlowColors = intArrayOf(glowColor, Color.TRANSPARENT)
            cachedBgColors = intArrayOf(hilite, mainColor)
        }

        override fun onDraw(canvas: Canvas) {
            val saveCount = canvas.save()
            try {
                val cx = width / 2f; val cy = height / 2f
                val r = width / 2f - 4f * resources.displayMetrics.density
                val d = resources.displayMetrics.density
                val hasThreats = threatsBlocked > 0 || isDeepfakeAlert || totalDetections > 0

                val mainColor = when { isDeepfakeAlert -> 0xFFB71C1C.toInt(); hasThreats -> 0xFFC62828.toInt(); isMonitoring -> 0xFF1565C0.toInt(); isProtectionActive -> 0xFF2E7D32.toInt(); else -> 0xFFF57F17.toInt() }
                val glowColor = when { isDeepfakeAlert -> 0xFFFF1744.toInt(); hasThreats -> 0xFFE53935.toInt(); isMonitoring -> 0xFF42A5F5.toInt(); isProtectionActive -> 0xFF4CAF50.toInt(); else -> 0xFFFFC107.toInt() }
                val hilite = when { isDeepfakeAlert -> 0xFFFF1744.toInt(); hasThreats -> 0xFFE53935.toInt(); isMonitoring -> 0xFF1E88E5.toInt(); isProtectionActive -> 0xFF43A047.toInt(); else -> 0xFFFFA000.toInt() }

                if (cachedWidth != width || cachedGlowColors[0] != glowColor || cachedBgColors[0] != hilite) {
                    updateShaders(cx, cy, r, glowColor, hilite, mainColor)
                }

                canvas.drawCircle(cx, cy, r * 1.3f, glowPaint)
                canvas.drawCircle(cx, cy, r, bgPaint)

                if (isMonitoring) { monitorRingPaint.color = if (isDeepfakeAlert) 0xFFFF1744.toInt() else 0xFF42A5F5.toInt(); monitorRingPaint.alpha = 180; canvas.drawCircle(cx, cy, r - 2f * d, monitorRingPaint) }

                val s = r * 0.55f
                shieldPath.reset()
                shieldPath.moveTo(cx, cy - s); shieldPath.lineTo(cx + s * 0.85f, cy - s * 0.5f)
                shieldPath.quadTo(cx + s * 0.85f, cy + s * 0.3f, cx, cy + s)
                shieldPath.quadTo(cx - s * 0.85f, cy + s * 0.3f, cx - s * 0.85f, cy - s * 0.5f); shieldPath.close()

                shieldPaint.color = Color.WHITE; shieldPaint.alpha = 60; shieldPaint.style = Paint.Style.FILL; canvas.drawPath(shieldPath, shieldPaint)
                shieldPaint.alpha = 200; shieldPaint.style = Paint.Style.STROKE; shieldPaint.strokeWidth = 2f * d; canvas.drawPath(shieldPath, shieldPaint)

                if (isDeepfakeAlert) {
                    val xs = s * 0.3f; checkPaint.color = 0xFFFF8A80.toInt()
                    canvas.drawLine(cx - xs, cy - xs * 0.3f, cx + xs, cy + xs * 0.7f, checkPaint)
                    canvas.drawLine(cx + xs, cy - xs * 0.3f, cx - xs, cy + xs * 0.7f, checkPaint)
                    checkPaint.color = Color.WHITE
                } else if (isProtectionActive) {
                    val cs = s * 0.35f
                    canvas.drawLine(cx - cs * 0.5f, cy + cs * 0.1f, cx - cs * 0.05f, cy + cs * 0.5f, checkPaint)
                    canvas.drawLine(cx - cs * 0.05f, cy + cs * 0.5f, cx + cs * 0.6f, cy - cs * 0.4f, checkPaint)
                }

                val badgeCount = totalDetections.coerceAtLeast(threatsBlocked)
                if (badgeCount > 0) {
                    val br = 9f * d; val bx = cx + r * 0.6f; val by = cy - r * 0.6f
                    canvas.drawCircle(bx, by, br, badgePaint)
                    canvas.drawText(if (badgeCount > 9) "9+" else badgeCount.toString(), bx, by + textPaint.textSize * 0.35f, textPaint)
                }
            } finally { canvas.restoreToCount(saveCount) }
        }
    }
}
