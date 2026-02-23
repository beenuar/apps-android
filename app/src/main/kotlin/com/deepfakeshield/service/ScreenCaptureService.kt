package com.deepfakeshield.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.deepfakeshield.DeepfakeShieldApplication
import com.deepfakeshield.R
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.ml.engine.DeepfakeMLEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Service that uses MediaProjection to capture the screen in real time,
 * extract frames, and run deepfake detection on them.
 * Works across any app: WhatsApp, Instagram, TikTok, etc.
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        const val NOTIFICATION_ID = 1005
        const val CHANNEL_ID = DeepfakeShieldApplication.CHANNEL_SERVICE
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_TARGET_APP = "target_app"
        const val ACTION_STOP = "com.deepfakeshield.STOP_SCREEN_CAPTURE"

        private const val CAPTURE_INTERVAL_MS = 1800L  // Faster capture for better temporal analysis
        private const val CAPTURE_SCALE = 0.55f       // Higher resolution for better artifact detection

        @Volatile
        var isRunning = false
            private set

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var deepfakeEngine: DeepfakeMLEngine

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureJob: Job? = null
    private var targetApp: String = "full_screen"

    private val frameBuffer = mutableListOf<Bitmap>()
    private val maxFrameBuffer = 10

    @Volatile private var deepfakesDetected = 0
    @Volatile private var framesAnalyzed = 0
    @Volatile private var lastDetectionScore = 0
    @Volatile private var lastDetectionReasons = ""
    @Volatile private var lastAlertPushTime = 0L
    private val alertCooldownMs = 30_000L // Don't push alerts more than once per 30 seconds

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        targetApp = intent?.getStringExtra(EXTRA_TARGET_APP) ?: "full_screen"

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "MediaProjection permission denied")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, createNotification("Initializing..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: run { stopSelf(); return }
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null")
            stopSelf()
            return
        }

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by system")
                scope.launch(Dispatchers.Main) {
                    stopCapture()
                    stopSelf()
                }
            }
        }, Handler(Looper.getMainLooper()))

        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val captureWidth = (metrics.widthPixels * CAPTURE_SCALE).toInt()
        val captureHeight = (metrics.heightPixels * CAPTURE_SCALE).toInt()
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Cyble_ScreenCapture",
            captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        Log.i(TAG, "Screen capture started: ${captureWidth}x${captureHeight}, target=$targetApp")
        updateNotification("Monitoring ${getAppLabel(targetApp)}...")
        pushStatusToBubble("\uD83D\uDEE1\uFE0F Monitoring ${getAppLabel(targetApp)} active", 0)

        captureJob = scope.launch {
            while (isActive) {
                delay(CAPTURE_INTERVAL_MS)
                try {
                    captureAndAnalyzeFrame()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Frame analysis error", e)
                }
            }
        }
    }

    private fun stopCapture() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null

        synchronized(frameBuffer) {
            frameBuffer.forEach { if (!it.isRecycled) it.recycle() }
            frameBuffer.clear()
        }

        pushStatusToBubble("Monitoring stopped", 0)
    }

    private suspend fun captureAndAnalyzeFrame() {
        val image = imageReader?.acquireLatestImage() ?: return

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            image.close()

            synchronized(frameBuffer) {
                frameBuffer.add(cropped)
                while (frameBuffer.size > maxFrameBuffer) {
                    val old = frameBuffer.removeAt(0)
                    if (!old.isRecycled) old.recycle()
                }
            }

            framesAnalyzed++
            withContext(Dispatchers.Default) { analyzeFrame(cropped) }
        } catch (e: Exception) {
            try { image.close() } catch (_: Exception) {}
            if (e is CancellationException) throw e
            Log.w(TAG, "Frame capture error: ${e.message}")
        }
    }

    private suspend fun analyzeFrame(frame: Bitmap) {
        // The DeepfakeMLEngine now runs a full multi-model pipeline:
        // 1. ML Kit Face Detection (real neural network) → face boxes, landmarks, classification
        // 2. ML Kit Face Mesh (real neural network) → 468 3D mesh points
        // 3. Temporal Face Tracker (ML-driven) → blink rate, jitter, coordination
        // 4. Pixel heuristics → ELA, frequency, boundary, warping, etc.
        val frameResult = deepfakeEngine.analyzeFrame(frame)

        val pixelTemporalScore = synchronized(frameBuffer) {
            if (frameBuffer.size >= 3) analyzeTemporalConsistency(frameBuffer.toList()) else 0f
        }

        // B2C-SAFE: Pixel temporal score alone MUST NOT trigger alerts.
        // The ML models (ML Kit face detection + face mesh + ML temporal tracker)
        // provide much more reliable signals than raw pixel differences.
        val hasMLEvidence = frameResult.isDeepfake ||
                            frameResult.manipulationScore > 0.18f ||
                            frameResult.mlFaceDetected && (frameResult.mlKitFaceScore > 0.15f || frameResult.faceMeshScore > 0.15f)

        val combinedScore = if (hasMLEvidence) {
            // ML models found something — combine all signals
            (frameResult.manipulationScore * 0.75f + pixelTemporalScore * 0.25f)
        } else {
            // No ML evidence — use only the ML pipeline score (which includes its own temporal)
            frameResult.manipulationScore
        }

        // Threshold for real-time detection — balanced for accuracy
        if (combinedScore > 0.50f) {
            deepfakesDetected++
            lastDetectionScore = (combinedScore * 100).toInt()
            lastDetectionReasons = buildString {
                if (frameResult.mlFaceDetected && frameResult.mlKitFaceScore > 0.2f)
                    append("ML face analysis flagged (${(frameResult.mlKitFaceScore * 100).toInt()}%). ")
                if (frameResult.faceMeshScore > 0.2f)
                    append("Face mesh anomalies (${(frameResult.faceMeshScore * 100).toInt()}%). ")
                if (frameResult.temporalScore > 0.2f)
                    append("Temporal anomalies (${(frameResult.temporalScore * 100).toInt()}%). ")
                if (frameResult.isDeepfake) append("AI-generated face detected. ")
                if (frameResult.manipulationScore > 0.5f)
                    append("Manipulation artifacts (${(frameResult.manipulationScore * 100).toInt()}%). ")
                if (hasMLEvidence && pixelTemporalScore > 0.3f) append("Unnatural frame transitions. ")
            }

            // B2C-SAFE: Only save alert to DB at most once per 30 seconds.
            // Without this, continuous detection floods the alerts list with
            // hundreds of identical entries per minute.
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastAlertPushTime >= alertCooldownMs - 1000) { // Slightly before the push cooldown
                withContext(Dispatchers.IO) {
                    try {
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        alertRepository.insertAlert(
                            AlertEntity(
                                threatType = ThreatType.DEEPFAKE_VIDEO,
                                source = ThreatSource.SCREEN_CAPTURE,
                                severity = when {
                                    combinedScore > 0.7f -> RiskSeverity.CRITICAL
                                    combinedScore > 0.5f -> RiskSeverity.HIGH
                                    else -> RiskSeverity.MEDIUM
                                },
                                score = (combinedScore * 100).toInt(),
                                confidence = combinedScore,
                                title = "Live Deepfake Detected",
                                summary = "Deepfake content detected while monitoring ${getAppLabel(targetApp)}",
                                content = "App: ${getAppLabel(targetApp)}\nScore: ${(combinedScore * 100).toInt()}%\nTime: $timeStr\n$lastDetectionReasons",
                                senderInfo = targetApp,
                                timestamp = nowMs
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save alert", e)
                    }
                }
            }

            // B2C-SAFE: Only push alert to bubble/notification at most once per 30 seconds.
            // The ML model fires on every frame — without cooldown, users get spammed
            // with non-stop unstoppable alerts (the #1 complaint).
            val now = System.currentTimeMillis()
            if (now - lastAlertPushTime >= alertCooldownMs) {
                lastAlertPushTime = now

                val alertMsg = "\uD83D\uDEA8 DEEPFAKE in ${getAppLabel(targetApp)} \u2014 ${lastDetectionScore}%"
                pushStatusToBubble(alertMsg, lastDetectionScore)
                pushDeepfakeAlert()

                FloatingBubbleService.pushThreatDetected(
                    context = this@ScreenCaptureService,
                    threatType = "deepfake",
                    source = getAppLabel(targetApp),
                    score = lastDetectionScore,
                    summary = "Deepfake in ${getAppLabel(targetApp)}",
                    reasons = lastDetectionReasons
                )

                updateNotification("\uD83D\uDEA8 DEEPFAKE in ${getAppLabel(targetApp)}! Score: ${lastDetectionScore}%")
            }
        } else if (framesAnalyzed % 10 == 0) {
            val msg = if (deepfakesDetected > 0) {
                "\uD83D\uDEE1\uFE0F Monitoring \u2022 $deepfakesDetected deepfake${if (deepfakesDetected == 1) "" else "s"} found"
            } else {
                "\uD83D\uDEE1\uFE0F Monitoring ${getAppLabel(targetApp)} \u2022 $framesAnalyzed frames"
            }
            pushStatusToBubble(msg, if (deepfakesDetected > 0) lastDetectionScore else 0)
            updateNotification(msg)
        }
    }

    /**
     * Analyze temporal consistency across buffered frames.
     *
     * Uses multiple temporal signals:
     * 1. Face region brightness stability (deepfakes flicker at boundaries)
     * 2. Edge persistence (deepfake edges shimmer/oscillate)
     * 3. Overall scene stability with face-specific tracking
     *
     * B2C-SAFE: High thresholds for global changes (scrolling, app switching are normal).
     * Lower thresholds for face-specific changes (face flickering is NOT normal).
     */
    private fun analyzeTemporalConsistency(frames: List<Bitmap>): Float {
        if (frames.size < 3) return 0f
        var totalAnomaly = 0f
        var faceFlickerCount = 0
        
        for (i in 1 until frames.size) {
            val prev = frames[i - 1]
            val curr = frames[i]
            if (prev.isRecycled || curr.isRecycled) continue
            
            // Global brightness change (high threshold — normal UI produces big changes)
            val prevAvg = computeRegionAverage(prev)
            val currAvg = computeRegionAverage(curr)
            val globalDiff = kotlin.math.abs(prevAvg - currAvg)
            if (globalDiff > 80f) totalAnomaly += 0.08f
            if (globalDiff > 120f) totalAnomaly += 0.12f
            
            // Face-region specific flicker detection (lower threshold)
            // Deepfakes produce small but rapid oscillations in the face region
            // while the background remains stable
            val prevFaceAvg = computeFaceRegionAverage(prev)
            val currFaceAvg = computeFaceRegionAverage(curr)
            val faceDiff = kotlin.math.abs(prevFaceAvg - currFaceAvg)
            val bgPrev = computeBackgroundAverage(prev)
            val bgCurr = computeBackgroundAverage(curr)
            val bgDiff = kotlin.math.abs(bgPrev - bgCurr)
            
            // Face changed significantly but background didn't = face flicker
            if (faceDiff > 12f && bgDiff < 25f) {
                faceFlickerCount++
                totalAnomaly += 0.12f
            }
            
            // Edge oscillation: face edges that change while scene is stable
            if (i >= 2 && i < frames.size) {
                val prevPrev = frames[i - 2]
                if (!prevPrev.isRecycled) {
                    val ppFaceAvg = computeFaceRegionAverage(prevPrev)
                    // Oscillation pattern: A→B→A (face brightness oscillates)
                    if (kotlin.math.abs(ppFaceAvg - currFaceAvg) < 5f &&
                        faceDiff > 10f) {
                        totalAnomaly += 0.15f // Strong deepfake oscillation signal
                    }
                }
            }
        }
        
        // Sustained face flickering across multiple frames is a strong signal
        if (faceFlickerCount >= 3) totalAnomaly += 0.20f
        
        return totalAnomaly.coerceIn(0f, 1f)
    }

    private fun computeRegionAverage(bitmap: Bitmap): Float {
        if (bitmap.isRecycled || bitmap.width < 10 || bitmap.height < 10) return 0f
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val r = minOf(bitmap.width, bitmap.height) / 6
        var sum = 0L; var count = 0
        val step = maxOf(1, r / 10)
        for (y in (cy - r).coerceAtLeast(0) until (cy + r).coerceAtMost(bitmap.height) step step) {
            for (x in (cx - r).coerceAtLeast(0) until (cx + r).coerceAtMost(bitmap.width) step step) {
                val pixel = bitmap.getPixel(x, y)
                sum += ((0.299 * ((pixel shr 16) and 0xFF)) + (0.587 * ((pixel shr 8) and 0xFF)) + (0.114 * (pixel and 0xFF))).toLong()
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 0f
    }
    
    /** Average brightness of central 40% of frame (likely face area in video calls/portraits) */
    private fun computeFaceRegionAverage(bitmap: Bitmap): Float {
        if (bitmap.isRecycled || bitmap.width < 20 || bitmap.height < 20) return 0f
        val x1 = bitmap.width * 3 / 10; val x2 = bitmap.width * 7 / 10
        val y1 = bitmap.height / 5; val y2 = bitmap.height * 3 / 5
        var sum = 0L; var count = 0
        val step = maxOf(2, (x2 - x1) / 15)
        for (y in y1 until y2 step step) {
            for (x in x1 until x2 step step) {
                val pixel = bitmap.getPixel(x, y)
                sum += ((0.299 * ((pixel shr 16) and 0xFF)) + (0.587 * ((pixel shr 8) and 0xFF)) + (0.114 * (pixel and 0xFF))).toLong()
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 0f
    }
    
    /** Average brightness of top and bottom strips (background in portrait/video content) */
    private fun computeBackgroundAverage(bitmap: Bitmap): Float {
        if (bitmap.isRecycled || bitmap.width < 20 || bitmap.height < 20) return 0f
        var sum = 0L; var count = 0
        val step = maxOf(2, bitmap.width / 15)
        // Top strip
        for (y in 0 until bitmap.height / 8 step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                sum += ((0.299 * ((pixel shr 16) and 0xFF)) + (0.587 * ((pixel shr 8) and 0xFF)) + (0.114 * (pixel and 0xFF))).toLong()
                count++
            }
        }
        // Bottom strip
        for (y in bitmap.height * 7 / 8 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                sum += ((0.299 * ((pixel shr 16) and 0xFF)) + (0.587 * ((pixel shr 8) and 0xFF)) + (0.114 * (pixel and 0xFF))).toLong()
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 0f
    }

    private fun getAppLabel(packageName: String): String {
        if (packageName == "full_screen") return "Full Screen"
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) { packageName.substringAfterLast(".") }
    }

    private fun pushStatusToBubble(message: String, score: Int) {
        FloatingBubbleService.pushScanResult(this, message, score)
    }

    private fun pushDeepfakeAlert() {
        try {
            val intent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_UPDATE_STATUS
                putExtra(FloatingBubbleService.EXTRA_THREATS_BLOCKED, deepfakesDetected)
                putExtra(FloatingBubbleService.EXTRA_IS_ACTIVE, true)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        // Channel is now created centrally in DeepfakeShieldApplication.createNotificationChannels()
        // using the shared CHANNEL_SERVICE. No-op here for backward compatibility.
    }

    private fun createNotification(status: String): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cyble \u2014 Live Monitoring")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop Monitoring", stopPi)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification(status))
        } catch (_: Exception) {}
    }
}
