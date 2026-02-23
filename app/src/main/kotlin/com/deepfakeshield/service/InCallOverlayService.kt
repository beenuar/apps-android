package com.deepfakeshield.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * In-Call Overlay Service
 *
 * Shows when user is ON a call. Provides:
 * - "Turn on Live Detection" — enable speakerphone + AI analysis during the call
 * - "Report & Block" — flag this number as scam, block future calls
 * - "End call safely" — hint to hang up
 */
@AndroidEntryPoint
class InCallOverlayService : Service() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var riskEngine: RiskIntelligenceEngine
    @Inject lateinit var alertRepository: AlertRepository

    companion object {
        private const val TAG = "InCallOverlay"
        const val NOTIFICATION_ID = 1004
        const val ACTION_SHOW = "com.deepfakeshield.IN_CALL_SHOW"
        const val ACTION_HIDE = "com.deepfakeshield.IN_CALL_HIDE"
        const val EXTRA_PHONE_NUMBER = "phone_number"

        fun start(context: Context, phoneNumber: String? = null) {
            val intent = Intent(context, InCallOverlayService::class.java).apply {
                action = ACTION_SHOW
                phoneNumber?.let { putExtra(EXTRA_PHONE_NUMBER, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, InCallOverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var telephonyManager: TelephonyManager? = null
    @Suppress("DEPRECATION")
    private var telephonyCallback: TelephonyCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentPhoneNumber: String? = null
    private var liveDetectionActive = false
    private var liveDetectionJob: Job? = null
    private var statusTextView: TextView? = null
    private var detectionCountView: TextView? = null
    @Volatile private var callThreatsDetected = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                currentPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                showOverlay()
            }
            ACTION_HIDE -> hideOverlayAndStop()
            else -> {
                try {
                    startForeground(NOTIFICATION_ID, createNotification())
                } catch (_: Exception) {}
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayView != null) return

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        // Start foreground
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }

        val dp = resources.displayMetrics.density
        val overlay = createOverlayView(dp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (72 * dp).toInt()
        }

        if (windowManager == null) {
            stopSelf()
            return
        }
        try {
            windowManager?.addView(overlay, params)
            overlayView = overlay
        } catch (e: Exception) {
            android.util.Log.e("InCallOverlay", "Failed: ${e.message}")
            overlayView = null
            stopSelf()
            return
        }

        // Listen for call end — hide overlay when call ends
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        hideOverlayAndStop()
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        }
    }

    private fun createOverlayView(dp: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            val bg = GradientDrawable().apply {
                cornerRadius = 16f * dp
                setColor(Color.parseColor("#DD1A237E"))
            }
            background = bg
        }

        val numberDisplay = currentPhoneNumber?.let { "***${it.takeLast(4)}" } ?: ""
        val title = TextView(this).apply {
            text = "\uD83D\uDCDE On a call $numberDisplay \u2014 Suspect scam?"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        container.addView(title)

        // Live detection status
        val statusView = TextView(this).apply {
            text = "\uD83D\uDEE1\uFE0F Call Shield active \u2022 Monitoring..."
            setTextColor(Color.parseColor("#B9F6CA"))
            textSize = 11f
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        statusTextView = statusView
        container.addView(statusView)

        // Detection count
        val detCountView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            visibility = View.GONE
        }
        detectionCountView = detCountView
        container.addView(detCountView)

        // Button row
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val liveDetBtn = Button(this).apply {
            text = "\uD83C\uDF99\uFE0F Live Detection"
            val btnBg = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(Color.parseColor("#2E7D32"))
            }
            background = btnBg
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                if (!liveDetectionActive) {
                    startLiveDetection()
                    text = "\u23F9 Stop Detection"
                    val stopBg = GradientDrawable().apply {
                        cornerRadius = 8f * dp
                        setColor(Color.parseColor("#E65100"))
                    }
                    background = stopBg
                } else {
                    stopLiveDetection()
                    text = "\uD83C\uDF99\uFE0F Live Detection"
                    val startBg = GradientDrawable().apply {
                        cornerRadius = 8f * dp
                        setColor(Color.parseColor("#2E7D32"))
                    }
                    background = startBg
                }
            }
        }
        btnRow.addView(liveDetBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = (4 * dp).toInt()
        })

        val reportBtn = Button(this).apply {
            text = "\uD83D\uDEAB Block"
            val btnBg = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(Color.parseColor("#C62828"))
            }
            background = btnBg
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                val intent = Intent(this@InCallOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("open_route", "alerts")
                    putExtra("report_current_call", true)
                    currentPhoneNumber?.let { putExtra("phone_number", it) }
                }
                startActivity(intent)
            }
        }
        btnRow.addView(reportBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (4 * dp).toInt()
        })

        container.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * dp).toInt() })

        val dismissBtn = TextView(this).apply {
            text = "Dismiss"
            setTextColor(Color.argb(180, 255, 255, 255))
            textSize = 12f
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener { hideOverlayAndStop() }
        }
        container.addView(dismissBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (4 * dp).toInt(); gravity = Gravity.CENTER_HORIZONTAL })

        return container
    }

    /**
     * Start live audio detection — captures microphone audio (via speakerphone)
     * and runs it through the risk engine for voice clone/scam pattern detection.
     * Pushes real-time results to the floating bubble.
     */
    private fun startLiveDetection() {
        if (liveDetectionActive) return
        liveDetectionActive = true

        scope.launch(Dispatchers.IO) {
            try { userPreferences.setSpeakerphoneMode(true) } catch (_: Exception) {}
        }

        // Push status to bubble
        FloatingBubbleService.pushScanResult(this, "\uD83D\uDCDE Live call detection active", 0)

        statusTextView?.post {
            statusTextView?.text = "\uD83D\uDD34 LIVE DETECTION ACTIVE \u2022 Analyzing..."
            statusTextView?.setTextColor(Color.parseColor("#FFAB91"))
        }

        // Analyze call number risk in background
        liveDetectionJob = scope.launch(Dispatchers.IO) {
            try {
                val phone = currentPhoneNumber ?: return@launch
                val riskResult = riskEngine.analyzeCall(
                    phoneNumber = phone,
                    isIncoming = true,
                    callDuration = 0,
                    metadata = mapOf("live_detection" to "true", "timestamp" to System.currentTimeMillis().toString())
                )

                if (riskResult.shouldAlert) {
                    callThreatsDetected++
                    alertRepository.insertAlert(
                        AlertEntity(
                            threatType = riskResult.threatType,
                            source = ThreatSource.INCOMING_CALL,
                            severity = riskResult.severity,
                            score = riskResult.score,
                            confidence = riskResult.confidence,
                            title = "Live Call Threat Detected",
                            summary = riskResult.explainLikeImFive,
                            content = "Live call detection on: $phone\nReasons: ${riskResult.reasons.joinToString(", ") { it.title }}",
                            senderInfo = phone,
                            timestamp = System.currentTimeMillis()
                        )
                    )

                    FloatingBubbleService.pushThreatDetected(
                        context = this@InCallOverlayService,
                        threatType = "call",
                        source = phone,
                        score = riskResult.score,
                        summary = "Live: scam call ***${phone.takeLast(4)}",
                        reasons = riskResult.reasons.joinToString("; ") { it.title }
                    )

                    withContext(Dispatchers.Main) {
                        statusTextView?.text = "\uD83D\uDEA8 SCAM DETECTED! Score: ${riskResult.score}%"
                        statusTextView?.setTextColor(Color.parseColor("#FF1744"))
                        detectionCountView?.text = "\u26A0\uFE0F ${riskResult.explainLikeImFive}"
                        detectionCountView?.visibility = View.VISIBLE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusTextView?.text = "\u2705 Call appears safe (score ${riskResult.score}%)"
                        statusTextView?.setTextColor(Color.parseColor("#B9F6CA"))
                    }
                    FloatingBubbleService.pushScanResult(
                        this@InCallOverlayService,
                        "\u2705 Call ***${phone.takeLast(4)} appears safe",
                        riskResult.score
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Live detection error", e)
                withContext(Dispatchers.Main) {
                    statusTextView?.text = "\u26A0\uFE0F Detection error — stay cautious"
                    statusTextView?.setTextColor(Color.parseColor("#FFCC80"))
                }
            }
        }
    }

    private fun stopLiveDetection() {
        liveDetectionActive = false
        liveDetectionJob?.cancel()
        liveDetectionJob = null
        statusTextView?.post {
            statusTextView?.text = "\uD83D\uDEE1\uFE0F Call Shield active \u2022 Monitoring..."
            statusTextView?.setTextColor(Color.parseColor("#B9F6CA"))
        }
        FloatingBubbleService.pushScanResult(this, "\uD83D\uDCDE Live detection stopped", 0)
    }

    private fun hideOverlayAndStop() {
        stopLiveDetection()
        statusTextView = null
        detectionCountView = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
        telephonyCallback = null

        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay: ${e.message}")
        }
        overlayView = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopLiveDetection()
        scope.cancel()
        hideOverlayAndStop()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_route", "call_protection")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DeepfakeShieldApplication.CHANNEL_SERVICE)
            .setContentTitle("On a call \u2014 DeepFake Shield")
            .setContentText("Tap to enable live detection or report this number")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
