package com.deepfakeshield.feature.shield

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.engine.RiskIntelligenceEngine
import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.core.notification.AlertNotifier
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.data.repository.VaultRepository
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.ml.deepfake.GovernmentGradeDeepfakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

data class VideoShieldUiState(
    val systemWideEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val scanResult: RiskResult? = null,
    val scanProgress: Float = 0f,
    val statusMessage: String = "",
    val error: String? = null
)

@HiltViewModel
class VideoShieldViewModel @Inject constructor(
    private val riskEngine: RiskIntelligenceEngine,
    private val deepfakeDetector: GovernmentGradeDeepfakeDetector,
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    private val vaultRepository: VaultRepository,
    private val alertNotifier: AlertNotifier,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoShieldUiState())
    val uiState: StateFlow<VideoShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.videoShieldEnabled.collect { enabled ->
                _uiState.update { it.copy(systemWideEnabled = enabled) }
            }
        }
    }

    private var currentScanJob: kotlinx.coroutines.Job? = null

    fun scanVideo(uri: Uri) {
        // Cancel any previous scan
        currentScanJob?.cancel()
        currentScanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanResult = null, scanProgress = 0f, error = null, statusMessage = "Extracting video frames...") }

            try {
                // Run BOTH the lightweight frame analysis AND the GovernmentGrade ML pipeline
                val analysisResults = withContext(Dispatchers.IO) {
                    extractAndAnalyzeFrames(uri)
                }

                _uiState.update { it.copy(scanProgress = 0.7f, statusMessage = "Running ML deepfake models...") }

                // Run GovernmentGrade detector (EfficientNet, Xception, CNNDetection, audio deepfake)
                val mlResult = withContext(Dispatchers.IO) {
                    try {
                        deepfakeDetector.analyzeVideo(uri)
                    } catch (e: Exception) {
                        Log.w("VideoShieldVM", "GovernmentGrade analysis failed, using heuristic only", e)
                        null
                    }
                }

                _uiState.update { it.copy(scanProgress = 0.9f, statusMessage = "Computing risk score...") }

                // Blend heuristic + ML results: use the MORE suspicious of the two
                val mlConfidence = mlResult?.confidence ?: 0f
                val mlIsDeepfake = mlResult?.isDeepfake ?: false
                
                // Blend heuristic + ML: boost scores when ML confirms heuristic suspicion
                val effectiveFaceConsistency = if (mlIsDeepfake && mlConfidence > 0.4f) {
                    minOf(analysisResults.faceConsistency, 1f - mlConfidence * 0.6f)
                } else {
                    analysisResults.faceConsistency
                }
                val effectiveAnomalies = if (mlIsDeepfake && mlConfidence > 0.4f) {
                    maxOf(analysisResults.temporalAnomalies, (mlConfidence * 15).toInt())
                } else {
                    analysisResults.temporalAnomalies
                }
                val effectiveAvMismatch = if (mlIsDeepfake && mlConfidence > 0.4f) {
                    maxOf(analysisResults.avMismatch, mlConfidence * 0.6f)
                } else {
                    analysisResults.avMismatch
                }

                val result = riskEngine.analyzeVideo(
                    faceConsistencyScore = effectiveFaceConsistency,
                    temporalAnomalies = effectiveAnomalies,
                    audioVisualMismatch = effectiveAvMismatch
                )

                _uiState.update { it.copy(isScanning = false, scanResult = result, scanProgress = 1f, statusMessage = "Analysis complete") }
                userPreferences.incrementVideoScans()

                // Always push scan result to bubble so user sees detection info
                val scanSummary = if (result.score >= 30) {
                    "\uD83D\uDEA8 Deepfake risk: ${result.score}% \u2014 ${result.severity.name}"
                } else {
                    "\u2705 Last scan: Clean (score ${result.score}%)"
                }
                try {
                    val bubbleIntent = Intent("com.deepfakeshield.SCAN_RESULT").apply {
                        setClassName(context.packageName, "com.deepfakeshield.service.FloatingBubbleService")
                        putExtra("last_scan_result", scanSummary)
                        putExtra("last_scan_score", result.score)
                    }
                    context.startService(bubbleIntent)
                } catch (e: Exception) {
                    Log.w("VideoShieldVM", "Could not update bubble", e)
                }

                // Save alert if threat detected — then notify user and update bubble
                if (result.shouldAlert) {
                    val mlInfo = if (mlResult != null) "\nML confidence: ${(mlResult.confidence * 100).toInt()}%\nManipulation: ${mlResult.manipulationType.name}" else ""
                    val content = "Source: ${uri.lastPathSegment ?: "video"}\nFace consistency: ${(effectiveFaceConsistency * 100).toInt()}%\nTemporal anomalies: $effectiveAnomalies\nAV mismatch: ${(effectiveAvMismatch * 100).toInt()}%$mlInfo"
                    val alertId = alertRepository.insertAlert(
                        AlertEntity(
                            threatType = result.threatType,
                            source = ThreatSource.VIDEO_FILE,
                            severity = result.severity,
                            score = result.score,
                            confidence = result.confidence,
                            title = "Video Analysis: ${result.severity.name} Risk",
                            summary = result.explainLikeImFive,
                            content = content,
                            senderInfo = null,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    vaultRepository.addEntry(
                        alertId = alertId,
                        entryType = "deepfake_video",
                        title = "Video Analysis: ${result.severity.name} Risk",
                        description = result.explainLikeImFive,
                        severity = result.severity,
                        evidenceData = content
                    )
                    val count = alertRepository.getAlertCount()
                    alertNotifier.onAlertInserted(count, "Possible deepfake detected", result.explainLikeImFive)
                }
            } catch (e: SecurityException) {
                Log.e("VideoShieldVM", "Video access denied", e)
                _uiState.update { it.copy(isScanning = false, scanProgress = 0f, error = "Unable to read video. Grant storage permission or try a different file.") }
            } catch (e: Exception) {
                // CancellationException must be rethrown to preserve structured concurrency.
                // Swallowing it corrupts UI state when the user starts a new scan while one is running.
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("VideoShieldVM", "Scan failed", e)
                val msg = when {
                    e.message?.contains("Unable to create", ignoreCase = true) == true -> "Unsupported video format. Try MP4 or another common format."
                    e.message?.contains("DataSource", ignoreCase = true) == true -> "Video file could not be opened. It may be corrupted or in an unsupported format."
                    else -> "Scan failed: ${e.message ?: "Unknown error"}"
                }
                _uiState.update { it.copy(isScanning = false, scanProgress = 0f, error = msg) }
            }
        }
    }

    /**
     * Extract frames from a real video and analyze them for deepfake indicators.
     * Uses MediaMetadataRetriever for actual frame extraction.
     */
    /**
     * Downscale a bitmap to prevent OOM on large videos.
     * Keeps it under 540p for analysis (sufficient for deepfake detection).
     */
    private fun downscaleBitmap(bitmap: Bitmap, maxDimension: Int = 540): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (scaled !== bitmap) bitmap.recycle() // Recycle original if a new one was created
        return scaled
    }

    private suspend fun extractAndAnalyzeFrames(uri: Uri): VideoAnalysisResult {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 5000L

            // Extract frames for deepfake detection (10-20 frames, downscaled to prevent OOM)
            val frameCount = 15.coerceAtMost((durationMs / 500).toInt().coerceAtLeast(5))
            val interval = if (frameCount > 0) durationMs / frameCount else durationMs
            if (interval <= 0) return VideoAnalysisResult(0.70f, 3, 0.35f)

            for (i in 0 until frameCount) {
                currentCoroutineContext().ensureActive()
                val timeUs = (i.toLong() * interval * 1000) // Convert ms to microseconds
                try {
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        // Downscale to prevent OOM (540p is sufficient for analysis)
                        frames.add(downscaleBitmap(frame))
                    }
                } catch (e: Exception) {
                    Log.w("VideoShieldVM", "Failed to extract frame at ${i * interval}ms: ${e.message}")
                }
                // Update progress
                _uiState.update {
                    it.copy(
                        scanProgress = 0.1f + (0.6f * (i + 1) / frameCount),
                        statusMessage = "Analyzing frame ${i + 1}/$frameCount..."
                    )
                }
            }

            if (frames.isEmpty()) {
                return VideoAnalysisResult(0.70f, 3, 0.35f) // No frames = uncertain, err cautious
            }

            // Analyze face consistency across frames
            val faceConsistency = analyzeFaceConsistency(frames)

            // Analyze temporal coherence between consecutive frames
            val temporalAnomalies = analyzeTemporalCoherence(frames)

            // Check audio-visual sync indicators from metadata
            val avMismatch = analyzeAudioVisualSync(retriever, durationMs)

            // === DEEPFAKE-SPECIFIC HEURISTIC CHECKS ===
            // These detect artifacts that are characteristic of AI-generated/manipulated faces
            val deepfakeArtifactScore = analyzeDeepfakeArtifacts(frames)

            // Blend deepfake artifact analysis into the face consistency score
            // Lower face consistency = more suspicious
            val adjustedFaceConsistency = faceConsistency * (1f - deepfakeArtifactScore * 0.5f)

            // Add deepfake-specific temporal anomalies
            val adjustedAnomalies = temporalAnomalies + (deepfakeArtifactScore * 10).toInt()

            // If artifacts detected, increase AV mismatch suspicion
            val adjustedAvMismatch = maxOf(avMismatch, deepfakeArtifactScore * 0.6f)

            return VideoAnalysisResult(
                adjustedFaceConsistency.coerceIn(0f, 1f),
                adjustedAnomalies,
                adjustedAvMismatch.coerceIn(0f, 1f)
            )
        } finally {
            // Always clean up frames and retriever, even on exception
            frames.forEach { 
                try { if (!it.isRecycled) it.recycle() } catch (_: Exception) {}
            }
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Analyze face region consistency across extracted frames.
     * Compares color histograms and luminance distribution of face regions.
     */
    private fun analyzeFaceConsistency(frames: List<Bitmap>): Float {
        if (frames.size < 2) return 0.80f  // Conservative: fewer frames = more suspicious

        var totalSimilarity = 0f
        var comparisons = 0

        for (i in 0 until frames.size - 1) {
            val current = frames[i]
            val next = frames[i + 1]

            // Compare center region (likely face area) color histograms
            val currentHist = computeCenterHistogram(current)
            val nextHist = computeCenterHistogram(next)

            val similarity = computeHistogramSimilarity(currentHist, nextHist)
            totalSimilarity += similarity
            comparisons++
        }

        return if (comparisons > 0) (totalSimilarity / comparisons).coerceIn(0f, 1f) else 0.80f
    }

    /**
     * Detect temporal anomalies (sudden jumps, flickers) between consecutive frames.
     */
    private fun analyzeTemporalCoherence(frames: List<Bitmap>): Int {
        if (frames.size < 2) return 0

        var anomalies = 0
        var previousAvgLuminance = computeAverageLuminance(frames[0])

        for (i in 1 until frames.size) {
            val currentAvgLuminance = computeAverageLuminance(frames[i])
            val diff = abs(currentAvgLuminance - previousAvgLuminance)

            // Sudden luminance shift = potential splice/edit (15 for higher sensitivity)
            if (diff > 15f) {
                anomalies++
            }
            previousAvgLuminance = currentAvgLuminance
        }

        return anomalies
    }

    /**
     * Analyze audio-visual sync indicators from video metadata.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun analyzeAudioVisualSync(retriever: MediaMetadataRetriever, durationMs: Long): Float {
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"

        if (!hasAudio || !hasVideo) return 0.15f // No audio = mild suspicion

        var suspicionScore = 0f

        // 1. Check bitrate for signs of re-encoding
        val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val bitrate = bitrateStr?.toLongOrNull() ?: 0L
        val expectedMinBitrate = 500_000L
        if (bitrate in 1 until expectedMinBitrate) suspicionScore += 0.25f

        // 2. Check for unusual frame rate (deepfakes often have non-standard frame rates)
        val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        val frameRate = frameRateStr?.toFloatOrNull()
        if (frameRate != null && (frameRate < 20f || frameRate > 61f)) suspicionScore += 0.15f

        // 3. Check resolution — very low resolution with audio = likely re-encoded
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val width = widthStr?.toIntOrNull() ?: 0
        val height = heightStr?.toIntOrNull() ?: 0
        if (width in 1..319 || height in 1..239) suspicionScore += 0.15f

        // 4. Very short videos with audio are more likely manipulated
        if (durationMs in 1..3000 && hasAudio) suspicionScore += 0.10f

        return suspicionScore.coerceIn(0f, 0.8f)
    }

    /**
     * Analyze frames for deepfake-specific artifacts.
     * Uses multiple heuristic checks based on known deepfake generation artifacts:
     * 1. Face boundary smoothness (blending seam detection)
     * 2. Skin texture uniformity (deepfakes have unnaturally smooth textures)
     * 3. Color channel inconsistency (RGB channels differ in synthetic faces)
     * 4. High-frequency noise analysis (deepfakes have different noise patterns)
     * 5. Edge sharpness at face-to-background boundary
     *
     * Returns 0.0 (no artifacts) to 1.0 (strong deepfake artifacts detected)
     */
    private fun analyzeDeepfakeArtifacts(frames: List<Bitmap>): Float {
        if (frames.isEmpty()) return 0f

        var totalArtifactScore = 0f
        var frameCount = 0

        for (frame in frames) {
            val w = frame.width
            val h = frame.height
            if (w < 20 || h < 20) continue

            var frameScore = 0f

            // 1. Face boundary smoothness analysis
            // Deepfakes often have an unnatural smoothing at the face boundary (blending seam)
            // Check for unusually smooth transitions in the center-to-edge gradient
            val centerX = w / 2
            val centerY = h / 2
            val faceRegionW = w / 3
            val faceRegionH = h / 3
            val boundarySharpness = computeBoundarySharpness(
                frame, centerX - faceRegionW / 2, centerY - faceRegionH / 2,
                faceRegionW, faceRegionH
            )
            // Balanced thresholds — detect real deepfakes without flagging compression
            if (boundarySharpness < 4.5f || boundarySharpness > 50.0f) {
                frameScore += 0.15f
            }

            // 2. Skin texture uniformity
            val textureVariance = computeTextureVariance(frame, centerX, centerY, faceRegionW / 2)
            if (textureVariance < 22.0f) {
                frameScore += 0.17f
            }

            // 3. Color channel inconsistency
            val channelInconsistency = computeChannelInconsistency(frame, centerX, centerY, faceRegionW / 2)
            if (channelInconsistency > 0.25f) {
                frameScore += 0.13f
            }

            // 4. High-frequency noise analysis
            val noisePattern = analyzeNoisePattern(frame)
            if (noisePattern > 0.35f) {
                frameScore += 0.15f
            }

            // 5. Compression artifact inconsistency
            val compressionInconsistency = detectCompressionInconsistency(frame)
            if (compressionInconsistency > 0.22f) {
                frameScore += 0.13f
            }

            // 6. Symmetry analysis
            val symmetryScore = analyzeSymmetry(frame, centerX, centerY, faceRegionW / 2)
            if (symmetryScore > 0.92f || symmetryScore < 0.35f) {
                frameScore += 0.13f
            }

            totalArtifactScore += frameScore.coerceAtMost(1f)
            frameCount++
        }

        return if (frameCount > 0) (totalArtifactScore / frameCount).coerceIn(0f, 1f) else 0f
    }

    /**
     * Compute gradient sharpness at the boundary of a region (face edge detection).
     */
    private fun computeBoundarySharpness(
        bitmap: Bitmap, startX: Int, startY: Int, regionW: Int, regionH: Int
    ): Float {
        var totalGradient = 0f
        var count = 0
        val safeStartX = startX.coerceIn(1, bitmap.width - 2)
        val safeStartY = startY.coerceIn(1, bitmap.height - 2)
        val safeEndX = (startX + regionW).coerceIn(2, bitmap.width - 1)
        val safeEndY = (startY + regionH).coerceIn(2, bitmap.height - 1)

        // Check pixels along the boundary of the face region
        for (x in safeStartX until safeEndX step 4) {
            // Top and bottom edges
            for (edgeY in listOf(safeStartY, safeEndY - 1)) {
                if (edgeY < 1 || edgeY >= bitmap.height - 1) continue
                val above = luminance(bitmap.getPixel(x, edgeY - 1))
                val below = luminance(bitmap.getPixel(x, edgeY + 1))
                totalGradient += abs(above - below)
                count++
            }
        }
        for (y in safeStartY until safeEndY step 4) {
            for (edgeX in listOf(safeStartX, safeEndX - 1)) {
                if (edgeX < 1 || edgeX >= bitmap.width - 1) continue
                val left = luminance(bitmap.getPixel(edgeX - 1, y))
                val right = luminance(bitmap.getPixel(edgeX + 1, y))
                totalGradient += abs(left - right)
                count++
            }
        }

        return if (count > 0) totalGradient / count else 20f
    }

    /**
     * Compute texture variance in the face region (low variance = suspiciously smooth skin).
     */
    private fun computeTextureVariance(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        var sum = 0f
        var sumSq = 0f
        var count = 0
        val step = 3

        for (y in (cy - radius).coerceAtLeast(1) until (cy + radius).coerceAtMost(bitmap.height - 1) step step) {
            for (x in (cx - radius).coerceAtLeast(1) until (cx + radius).coerceAtMost(bitmap.width - 1) step step) {
                val center = luminance(bitmap.getPixel(x, y))
                val right = luminance(bitmap.getPixel(x + 1, y))
                val below = luminance(bitmap.getPixel(x, y + 1))
                val localGrad = abs(center - right) + abs(center - below)
                sum += localGrad
                sumSq += localGrad * localGrad
                count++
            }
        }

        if (count < 2) return 30f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    /**
     * Check RGB channel correlation in the face region.
     * Deepfakes can introduce independent channel artifacts.
     */
    private fun computeChannelInconsistency(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        var rSum = 0L; var gSum = 0L; var bSum = 0L
        var rSumSq = 0L; var gSumSq = 0L; var bSumSq = 0L
        var count = 0

        for (y in (cy - radius).coerceAtLeast(0) until (cy + radius).coerceAtMost(bitmap.height) step 5) {
            for (x in (cx - radius).coerceAtLeast(0) until (cx + radius).coerceAtMost(bitmap.width) step 5) {
                val pixel = bitmap.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF).toLong()
                val g = ((pixel shr 8) and 0xFF).toLong()
                val b = (pixel and 0xFF).toLong()
                rSum += r; gSum += g; bSum += b
                rSumSq += r * r; gSumSq += g * g; bSumSq += b * b
                count++
            }
        }

        if (count < 10) return 0f
        val rVar = (rSumSq.toFloat() / count) - (rSum.toFloat() / count).let { it * it }
        val gVar = (gSumSq.toFloat() / count) - (gSum.toFloat() / count).let { it * it }
        val bVar = (bSumSq.toFloat() / count) - (bSum.toFloat() / count).let { it * it }

        // If one channel has very different variance than others, it's suspicious
        val avgVar = (rVar + gVar + bVar) / 3f
        if (avgVar < 1f) return 0f
        val maxDeviation = maxOf(
            abs(rVar - avgVar) / avgVar,
            abs(gVar - avgVar) / avgVar,
            abs(bVar - avgVar) / avgVar
        )
        return maxDeviation.coerceIn(0f, 1f)
    }

    /**
     * Analyze high-frequency noise patterns.
     * Real camera noise is random; GAN artifacts create structured noise.
     */
    private fun analyzeNoisePattern(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return 0f

        // Compute Laplacian-like filter response (detects noise/edges)
        var laplacianSum = 0f
        var laplacianSumSq = 0f
        var count = 0

        for (y in 2 until h - 2 step 4) {
            for (x in 2 until w - 2 step 4) {
                val center = luminance(bitmap.getPixel(x, y))
                val up = luminance(bitmap.getPixel(x, y - 1))
                val down = luminance(bitmap.getPixel(x, y + 1))
                val left = luminance(bitmap.getPixel(x - 1, y))
                val right = luminance(bitmap.getPixel(x + 1, y))
                val laplacian = abs(4 * center - up - down - left - right)
                laplacianSum += laplacian
                laplacianSumSq += laplacian * laplacian
                count++
            }
        }

        if (count < 10) return 0f
        val mean = laplacianSum / count
        val variance = laplacianSumSq / count - mean * mean

        // Very low variance in Laplacian = unnaturally smooth (GAN artifact)
        // Very high but regular = also suspicious
        return when {
            variance < 2f -> 0.6f    // Too smooth - likely GAN
            variance < 5f -> 0.3f    // Suspiciously smooth
            mean > 40f -> 0.4f       // Unusual high-frequency content
            else -> 0f
        }
    }

    /**
     * Detect inconsistent JPEG/compression artifacts between face and background.
     */
    private fun detectCompressionInconsistency(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) return 0f

        // Compare block-level variance in center (face) vs periphery (background)
        val centerVar = regionBlockVariance(bitmap, w / 4, h / 4, w / 2, h / 2)
        val topVar = regionBlockVariance(bitmap, 0, 0, w, h / 6)
        val bottomVar = regionBlockVariance(bitmap, 0, h * 5 / 6, w, h / 6)

        if (topVar < 1f && bottomVar < 1f) return 0f

        val avgBgVar = (topVar + bottomVar) / 2f
        if (avgBgVar < 1f) return 0f
        val ratio = abs(centerVar - avgBgVar) / avgBgVar
        return ratio.coerceIn(0f, 1f)
    }

    private fun regionBlockVariance(bitmap: Bitmap, startX: Int, startY: Int, regionW: Int, regionH: Int): Float {
        val blockSize = 8
        var sum = 0f
        var sumSq = 0f
        var count = 0
        val sx = startX.coerceAtLeast(0)
        val sy = startY.coerceAtLeast(0)
        val ex = (startX + regionW).coerceAtMost(bitmap.width)
        val ey = (startY + regionH).coerceAtMost(bitmap.height)

        for (by2 in sy until ey step blockSize) {
            for (bx in sx until ex step blockSize) {
                var blockSum = 0
                var blockCount = 0
                for (py in by2 until (by2 + blockSize).coerceAtMost(ey)) {
                    for (px in bx until (bx + blockSize).coerceAtMost(ex)) {
                        blockSum += luminance(bitmap.getPixel(px, py)).toInt()
                        blockCount++
                    }
                }
                if (blockCount > 0) {
                    val avg = blockSum.toFloat() / blockCount
                    sum += avg
                    sumSq += avg * avg
                    count++
                }
            }
        }

        if (count < 2) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).coerceAtLeast(0f)
    }

    /**
     * Analyze left-right symmetry of the face region.
     */
    private fun analyzeSymmetry(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        var matchCount = 0
        var totalCount = 0

        for (y in (cy - radius).coerceAtLeast(0) until (cy + radius).coerceAtMost(bitmap.height) step 4) {
            for (dx in 1 until radius step 4) {
                val lx = cx - dx
                val rx = cx + dx
                if (lx < 0 || rx >= bitmap.width) continue

                val leftLum = luminance(bitmap.getPixel(lx, y))
                val rightLum = luminance(bitmap.getPixel(rx, y))
                if (abs(leftLum - rightLum) < 20f) matchCount++
                totalCount++
            }
        }

        return if (totalCount > 0) matchCount.toFloat() / totalCount else 0.5f
    }

    private fun luminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b)
    }

    /**
     * Compute a simple color histogram of the center 50% of the frame.
     */
    private fun computeCenterHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256)
        val startX = bitmap.width / 4
        val endX = startX + bitmap.width / 2
        val startY = bitmap.height / 4
        val endY = startY + bitmap.height / 2

        val safeEndX = endX.coerceAtMost(bitmap.width)
        val safeEndY = endY.coerceAtMost(bitmap.height)

        for (y in startY until safeEndY step 3) {
            for (x in startX until safeEndX step 3) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt().coerceIn(0, 255)
                histogram[luminance]++
            }
        }
        return histogram
    }

    /**
     * Compare two histograms using correlation coefficient.
     */
    private fun computeHistogramSimilarity(hist1: IntArray, hist2: IntArray): Float {
        val mean1 = hist1.average()
        val mean2 = hist2.average()
        var numerator = 0.0
        var denom1 = 0.0
        var denom2 = 0.0

        for (i in hist1.indices) {
            val diff1 = hist1[i] - mean1
            val diff2 = hist2[i] - mean2
            numerator += diff1 * diff2
            denom1 += diff1 * diff1
            denom2 += diff2 * diff2
        }

        val denominator = kotlin.math.sqrt(denom1 * denom2)
        return if (denominator > 0) {
            val r = (numerator / denominator).toFloat()
            when { r.isNaN() -> 1f; r < 0f -> 0f; r > 1f -> 1f; else -> r }
        } else 1f // Zero variance means identical frames = maximum similarity
    }

    /**
     * Compute average luminance of a frame for temporal analysis.
     */
    private fun computeAverageLuminance(bitmap: Bitmap): Float {
        var totalLuminance = 0L
        var pixelCount = 0
        val step = 5 // Sample every 5th pixel for performance

        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuminance += ((0.299 * r) + (0.587 * g) + (0.114 * b)).toLong()
                pixelCount++
            }
        }

        return if (pixelCount > 0) totalLuminance.toFloat() / pixelCount else 128f
    }

    @Suppress("UNUSED_PARAMETER")
    fun startSystemWideScanning(data: Intent?) {
        viewModelScope.launch {
            userPreferences.setVideoShield(true)
            _uiState.update { it.copy(systemWideEnabled = true) }
        }
    }

    fun stopSystemWideScanning() {
        viewModelScope.launch {
            userPreferences.setVideoShield(false)
            _uiState.update { it.copy(systemWideEnabled = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(scanResult = null, error = null) }
    }
}

private data class VideoAnalysisResult(
    val faceConsistency: Float,
    val temporalAnomalies: Int,
    val avMismatch: Float
)
