package com.deepfakeshield.ml.face

import android.graphics.PointF
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Temporal Face Tracker — analyzes face landmarks across multiple frames
 * to detect deepfake-specific temporal anomalies.
 *
 * Uses ML Kit face detection results (not raw pixels) for precise tracking:
 *
 * 1. **Blink Rate Analysis**: Natural humans blink 15-20 times/minute.
 *    Deepfakes often don't blink, blink too fast, or blink both eyes
 *    at slightly different times.
 *
 * 2. **Landmark Jitter**: Natural head movements produce smooth landmark
 *    trajectories. Deepfakes have micro-jitter — landmarks jump between
 *    frames due to imperfect face reconstruction.
 *
 * 3. **Head Pose Consistency**: Smooth head movement vs jerky transitions.
 *
 * 4. **Eye-Mouth Coordination**: In real faces, eye blinks and mouth
 *    movements are coordinated. Deepfakes can decouple them.
 *
 * 5. **Face Shape Stability**: The face bounding box aspect ratio should
 *    remain consistent unless the person is actively moving.
 */
@Singleton
class TemporalFaceTracker @Inject constructor() {

    companion object {
        private const val TAG = "TemporalFaceTracker"
        private const val MAX_HISTORY = 30          // Keep last 30 frames
        private const val MIN_FRAMES_FOR_ANALYSIS = 5
        private const val BLINK_THRESHOLD = 0.4f    // Eye open probability < this = closed
        private const val EXPECTED_BLINKS_PER_MINUTE = 17f  // Average human blink rate
    }

    data class TemporalResult(
        val blinkRateScore: Float,          // 0-1: how abnormal the blink pattern is
        val landmarkJitterScore: Float,      // 0-1: amount of landmark jitter
        val headPoseScore: Float,            // 0-1: head pose consistency
        val eyeMouthCoordScore: Float,       // 0-1: eye-mouth coordination anomaly
        val faceShapeStabilityScore: Float,  // 0-1: face shape consistency
        val temporalDeepfakeScore: Float,    // 0-1: composite temporal score
        val findings: List<String>,
        val framesAnalyzed: Int
    )

    // Per-frame snapshot for temporal analysis
    data class FrameSnapshot(
        val timestamp: Long,
        val leftEyeOpen: Float,     // -1 if unavailable
        val rightEyeOpen: Float,    // -1 if unavailable
        val smileProb: Float,
        val headEulerX: Float,      // pitch
        val headEulerY: Float,      // yaw
        val headEulerZ: Float,      // roll
        val landmarks: Map<Int, PointF>,
        val faceWidth: Float,
        val faceHeight: Float,
        val faceCenterX: Float,
        val faceCenterY: Float
    )

    private val frameHistory = ArrayDeque<FrameSnapshot>(MAX_HISTORY + 1)

    /**
     * Record a new frame's face detection results.
     */
    fun recordFrame(faceInfo: MLKitFaceAnalyzer.DetectedFaceInfo) {
        val snapshot = FrameSnapshot(
            timestamp = System.currentTimeMillis(),
            leftEyeOpen = faceInfo.leftEyeOpenProb,
            rightEyeOpen = faceInfo.rightEyeOpenProb,
            smileProb = faceInfo.smileProb,
            headEulerX = faceInfo.headEulerX,
            headEulerY = faceInfo.headEulerY,
            headEulerZ = faceInfo.headEulerZ,
            landmarks = faceInfo.landmarks,
            faceWidth = faceInfo.boundingBox.width().toFloat(),
            faceHeight = faceInfo.boundingBox.height().toFloat(),
            faceCenterX = faceInfo.boundingBox.centerX().toFloat(),
            faceCenterY = faceInfo.boundingBox.centerY().toFloat()
        )

        synchronized(frameHistory) {
            frameHistory.addLast(snapshot)
            while (frameHistory.size > MAX_HISTORY) {
                frameHistory.removeFirst()
            }
        }
    }

    /**
     * Analyze temporal patterns from recorded frame history.
     * Call this after recording at least MIN_FRAMES_FOR_ANALYSIS frames.
     */
    fun analyze(): TemporalResult {
        val history = synchronized(frameHistory) { frameHistory.toList() }

        if (history.size < MIN_FRAMES_FOR_ANALYSIS) {
            return TemporalResult(0f, 0f, 0f, 0f, 0f, 0f,
                listOf("Insufficient frames (${history.size}/$MIN_FRAMES_FOR_ANALYSIS)"),
                history.size)
        }

        val findings = mutableListOf<String>()

        // 1. Blink rate analysis
        val blinkScore = analyzeBlinkRate(history, findings)

        // 2. Landmark jitter
        val jitterScore = analyzeLandmarkJitter(history, findings)

        // 3. Head pose consistency
        val poseScore = analyzeHeadPoseConsistency(history, findings)

        // 4. Eye-mouth coordination
        val coordScore = analyzeEyeMouthCoordination(history, findings)

        // 5. Face shape stability
        val shapeScore = analyzeFaceShapeStability(history, findings)

        // Weighted composite
        val temporalScore = (
            blinkScore * 0.25f +
            jitterScore * 0.25f +
            poseScore * 0.20f +
            coordScore * 0.15f +
            shapeScore * 0.15f
        ).coerceIn(0f, 1f)

        return TemporalResult(
            blinkRateScore = blinkScore,
            landmarkJitterScore = jitterScore,
            headPoseScore = poseScore,
            eyeMouthCoordScore = coordScore,
            faceShapeStabilityScore = shapeScore,
            temporalDeepfakeScore = temporalScore,
            findings = findings,
            framesAnalyzed = history.size
        )
    }

    /**
     * Clear frame history (e.g., when monitoring a new video/app).
     */
    fun reset() {
        synchronized(frameHistory) { frameHistory.clear() }
    }

    // ---- Analysis functions ----

    /**
     * Analyze blink patterns.
     * Deepfake indicators:
     * - No blinks at all (common in older deepfakes)
     * - Unnaturally fast blinks (< 100ms)
     * - Asymmetric blinks (one eye closes, other stays open)
     * - Blinks that are too regular (metronomic)
     */
    private fun analyzeBlinkRate(history: List<FrameSnapshot>, findings: MutableList<String>): Float {
        // Filter frames with valid eye data
        val eyeFrames = history.filter { it.leftEyeOpen >= 0 && it.rightEyeOpen >= 0 }
        if (eyeFrames.size < MIN_FRAMES_FOR_ANALYSIS) return 0f

        var score = 0f

        // Detect blink events (transition from open → closed → open)
        val blinks = mutableListOf<Long>() // timestamps of blinks
        var wasEyesClosed = false

        for (frame in eyeFrames) {
            val avgEyeOpen = (frame.leftEyeOpen + frame.rightEyeOpen) / 2
            val eyesClosed = avgEyeOpen < BLINK_THRESHOLD
            if (eyesClosed && !wasEyesClosed) {
                blinks.add(frame.timestamp)
            }
            wasEyesClosed = eyesClosed
        }

        // Time span of analysis
        val timeSpanMs = eyeFrames.last().timestamp - eyeFrames.first().timestamp
        val timeSpanMinutes = timeSpanMs / 60_000f

        if (timeSpanMinutes > 0.05f) { // At least 3 seconds of data
            val blinksPerMinute = blinks.size / timeSpanMinutes

            // No blinks in >5 seconds is suspicious for a human face
            if (blinks.isEmpty() && timeSpanMs > 5000) {
                score += 0.4f
                findings.add("Temporal: No blinks detected in ${timeSpanMs / 1000}s (deepfakes often don't blink)")
            }

            // Abnormally high blink rate (>40/min)
            if (blinksPerMinute > 40 && blinks.size >= 3) {
                score += 0.3f
                findings.add("Temporal: Abnormal blink rate (${blinksPerMinute.toInt()}/min, normal: ~17/min)")
            }
        }

        // Check for asymmetric blinks — deepfakes may blink one eye at a time
        var asymmetricBlinks = 0
        for (frame in eyeFrames) {
            val leftOpen = frame.leftEyeOpen > 0.6f
            val rightOpen = frame.rightEyeOpen > 0.6f
            val leftClosed = frame.leftEyeOpen < 0.3f
            val rightClosed = frame.rightEyeOpen < 0.3f

            // One eye clearly open, other clearly closed
            if ((leftOpen && rightClosed) || (leftClosed && rightOpen)) {
                asymmetricBlinks++
            }
        }

        val asymmetricRatio = asymmetricBlinks.toFloat() / eyeFrames.size
        if (asymmetricRatio > 0.15f) {
            score += 0.3f
            findings.add("Temporal: Frequent asymmetric blinks (${(asymmetricRatio * 100).toInt()}% of frames)")
        }

        // Check blink regularity (metronomic blinks = artificial)
        if (blinks.size >= 3) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until blinks.size) {
                intervals.add(blinks[i] - blinks[i - 1])
            }
            val mean = intervals.average()
            val stdDev = sqrt(intervals.map { (it - mean).pow(2) }.average())
            val cv = if (mean > 0) stdDev / mean else 0.0

            // Very regular blinks (CV < 0.15) are suspicious
            if (cv < 0.15 && intervals.size >= 2) {
                score += 0.2f
                findings.add("Temporal: Metronomic blink pattern (CV=${String.format("%.2f", cv)})")
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Analyze landmark jitter — how much landmarks jump between frames.
     * Real faces have smooth landmark trajectories.
     * Deepfakes have micro-jitter from per-frame reconstruction.
     */
    private fun analyzeLandmarkJitter(history: List<FrameSnapshot>, findings: MutableList<String>): Float {
        if (history.size < 3) return 0f

        val jitterValues = mutableListOf<Float>()

        for (i in 1 until history.size) {
            val prev = history[i - 1]
            val curr = history[i]

            // Find common landmarks
            val commonKeys = prev.landmarks.keys.intersect(curr.landmarks.keys)
            if (commonKeys.isEmpty()) continue

            // Compute normalized displacement for each landmark
            val faceSize = maxOf(curr.faceWidth, curr.faceHeight, 1f)
            var totalDisplacement = 0f

            for (key in commonKeys) {
                val prevPt = prev.landmarks[key] ?: continue
                val currPt = curr.landmarks[key] ?: continue
                val dx = currPt.x - prevPt.x
                val dy = currPt.y - prevPt.y
                val displacement = sqrt(dx * dx + dy * dy) / faceSize
                totalDisplacement += displacement
            }

            val avgDisplacement = totalDisplacement / commonKeys.size
            jitterValues.add(avgDisplacement)
        }

        if (jitterValues.size < 2) return 0f

        // Compute jitter statistics
        val meanJitter = jitterValues.average()
        val jitterStdDev = sqrt(jitterValues.map { (it - meanJitter).pow(2) }.average())

        var score = 0f

        // High-frequency jitter (large stdDev relative to mean)
        if (jitterStdDev > 0.02f && meanJitter < 0.05f) {
            score += 0.3f
            findings.add("Temporal: Landmark micro-jitter detected (σ=${String.format("%.4f", jitterStdDev)})")
        }

        // Analyze 2nd derivative (acceleration) — natural movements are smooth
        if (jitterValues.size >= 3) {
            val accelerations = mutableListOf<Float>()
            for (i in 1 until jitterValues.size) {
                accelerations.add(abs(jitterValues[i] - jitterValues[i - 1]))
            }
            val meanAccel = accelerations.average()

            if (meanAccel > 0.015f) {
                score += 0.25f
                findings.add("Temporal: Jerky landmark trajectories (non-smooth motion)")
            }
        }

        // Check for oscillating landmarks (A→B→A pattern)
        var oscillations = 0
        for (i in 2 until jitterValues.size) {
            val j0 = jitterValues[i - 2]
            val j1 = jitterValues[i - 1]
            val j2 = jitterValues[i]

            // Value goes up then down (or vice versa) by similar amounts
            val d1 = j1 - j0
            val d2 = j2 - j1
            if (d1 * d2 < 0 && abs(abs(d1) - abs(d2)) < 0.005f) {
                oscillations++
            }
        }

        val oscillationRatio = oscillations.toFloat() / (jitterValues.size - 2).coerceAtLeast(1)
        if (oscillationRatio > 0.4f) {
            score += 0.2f
            findings.add("Temporal: Oscillating landmarks (${(oscillationRatio * 100).toInt()}% of frames)")
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Analyze head pose consistency.
     * Real head movements are smooth and physically constrained.
     * Deepfakes can have jerky or physically impossible pose transitions.
     */
    private fun analyzeHeadPoseConsistency(history: List<FrameSnapshot>, findings: MutableList<String>): Float {
        if (history.size < 3) return 0f

        val yawChanges = mutableListOf<Float>()
        val pitchChanges = mutableListOf<Float>()
        val rollChanges = mutableListOf<Float>()

        for (i in 1 until history.size) {
            yawChanges.add(abs(history[i].headEulerY - history[i - 1].headEulerY))
            pitchChanges.add(abs(history[i].headEulerX - history[i - 1].headEulerX))
            rollChanges.add(abs(history[i].headEulerZ - history[i - 1].headEulerZ))
        }

        var score = 0f

        // Large sudden pose changes (>15 degrees between frames)
        val suddenYaw = yawChanges.count { it > 15f }
        val suddenPitch = pitchChanges.count { it > 12f }
        val suddenRoll = rollChanges.count { it > 10f }
        val totalSudden = suddenYaw + suddenPitch + suddenRoll
        val suddenRatio = totalSudden.toFloat() / (yawChanges.size * 3)

        if (suddenRatio > 0.1f) {
            score += 0.3f
            findings.add("Temporal: Sudden head pose jumps (${totalSudden} sudden changes)")
        }

        // Pose instability: high-frequency jitter in pose angles
        val yawJitter = computeJitter(yawChanges)
        val pitchJitter = computeJitter(pitchChanges)

        if (yawJitter > 3f || pitchJitter > 3f) {
            score += 0.25f
            findings.add("Temporal: Head pose jitter (yaw σ=${String.format("%.1f", yawJitter)}°)")
        }

        // Check for unnaturally stable head (no movement at all)
        val yawRange = yawChanges.maxOrNull()?.minus(yawChanges.minOrNull() ?: 0f) ?: 0f
        if (yawRange < 0.5f && history.size > 10) {
            score += 0.15f
            findings.add("Temporal: Unnaturally stable head pose (no micro-movements)")
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Analyze eye-mouth coordination.
     * In natural faces, mouth movements and eye states are correlated.
     * Deepfakes (especially lip-sync) can decouple them.
     */
    private fun analyzeEyeMouthCoordination(history: List<FrameSnapshot>, findings: MutableList<String>): Float {
        val validFrames = history.filter { it.leftEyeOpen >= 0 && it.smileProb >= 0 }
        if (validFrames.size < MIN_FRAMES_FOR_ANALYSIS) return 0f

        // In natural faces, smile probability and eye "squinting" are correlated
        // When people smile, their eyes narrow slightly
        val eyeOpenValues = validFrames.map { (it.leftEyeOpen + it.rightEyeOpen) / 2 }
        val smileValues = validFrames.map { it.smileProb }

        // Compute correlation
        val correlation = computeCorrelation(eyeOpenValues, smileValues)

        var score = 0f

        // Natural faces: when smiling, eyes narrow (negative correlation expected)
        // If smiling changes but eyes don't respond at all, it's suspicious
        val smileRange = (smileValues.maxOrNull() ?: 0f) - (smileValues.minOrNull() ?: 0f)
        val eyeRange = (eyeOpenValues.maxOrNull() ?: 0f) - (eyeOpenValues.minOrNull() ?: 0f)

        if (smileRange > 0.3f && eyeRange < 0.05f) {
            // Smile changes significantly but eyes don't react
            score += 0.4f
            findings.add("Temporal: Eyes don't respond to smile changes (decoupled)")
        }

        // Very high positive correlation when smile changes significantly
        // (eyes open MORE when smiling = unnatural)
        if (correlation > 0.7f && smileRange > 0.2f) {
            score += 0.2f
            findings.add("Temporal: Unnatural eye-smile correlation (${String.format("%.2f", correlation)})")
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Analyze face bounding box stability.
     * Real faces maintain consistent aspect ratios.
     * Deepfakes can have fluctuating face shapes.
     */
    private fun analyzeFaceShapeStability(history: List<FrameSnapshot>, findings: MutableList<String>): Float {
        if (history.size < 3) return 0f

        val aspectRatios = history.map {
            if (it.faceHeight > 0) it.faceWidth / it.faceHeight else 0f
        }.filter { it > 0 }

        if (aspectRatios.size < 3) return 0f

        val mean = aspectRatios.average()
        val stdDev = sqrt(aspectRatios.map { (it - mean).pow(2) }.average())
        val cv = if (mean > 0) (stdDev / mean).toFloat() else 0f

        var score = 0f

        // High variability in aspect ratio
        if (cv > 0.08f) {
            score += 0.3f
            findings.add("Temporal: Unstable face aspect ratio (CV=${String.format("%.3f", cv)})")
        }

        // Face size stability (sudden size changes without smooth transition)
        val sizes = history.map { it.faceWidth * it.faceHeight }
        val sizeChanges = mutableListOf<Float>()
        for (i in 1 until sizes.size) {
            if (sizes[i - 1] > 0) {
                sizeChanges.add(abs(sizes[i] - sizes[i - 1]) / sizes[i - 1])
            }
        }

        val suddenSizeChanges = sizeChanges.count { it > 0.2f }
        if (suddenSizeChanges > 2) {
            score += 0.25f
            findings.add("Temporal: Sudden face size changes ($suddenSizeChanges occurrences)")
        }

        return score.coerceIn(0f, 1f)
    }

    // ---- Helpers ----

    private fun computeJitter(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        return sqrt(values.map { (it - mean).pow(2) }.average()).toFloat()
    }

    private fun computeCorrelation(xs: List<Float>, ys: List<Float>): Float {
        if (xs.size != ys.size || xs.size < 3) return 0f
        val xMean = xs.average(); val yMean = ys.average()
        var cov = 0.0; var xVar = 0.0; var yVar = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - xMean; val dy = ys[i] - yMean
            cov += dx * dy
            xVar += dx * dx
            yVar += dy * dy
        }
        val denom = sqrt(xVar * yVar)
        return if (denom > 0) (cov / denom).toFloat() else 0f
    }
}
