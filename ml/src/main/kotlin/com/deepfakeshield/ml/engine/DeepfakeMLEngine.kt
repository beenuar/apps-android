package com.deepfakeshield.ml.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.deepfakeshield.ml.face.FaceMeshDeepfakeAnalyzer
import com.deepfakeshield.ml.face.MLKitFaceAnalyzer
import com.deepfakeshield.ml.face.TemporalFaceTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Production ML Engine for Deepfake Detection — Multi-Model Pipeline
 *
 * Integrates THREE real ML models + advanced heuristics:
 *
 * MODEL 1: Google ML Kit Face Detection (Neural Network)
 *   - Precise face bounding boxes
 *   - 133 facial landmarks (eyes, nose, mouth, ears, cheeks)
 *   - Face contour detection (jawline, eyebrows, lips — 133 contour points)
 *   - Eye open/close classification, smile classification
 *   - Head pose (Euler angles: pitch, yaw, roll)
 *   - Face tracking ID for temporal analysis
 *
 * MODEL 2: Google ML Kit Face Mesh Detection (Neural Network)
 *   - 468 3D face mesh points with X, Y, Z coordinates
 *   - Mesh regularity analysis (triangle proportions)
 *   - Depth consistency (smooth 3D surface vs discontinuous)
 *   - Mesh symmetry analysis
 *   - Face oval, lip, eye mesh detailed analysis
 *
 * MODEL 3: Temporal Face Tracker (ML-driven multi-frame)
 *   - Blink rate analysis using ML-detected eye probabilities
 *   - Landmark jitter detection using ML landmark positions
 *   - Head pose consistency using ML Euler angles
 *   - Eye-mouth coordination analysis
 *   - Face shape stability tracking
 *
 * HEURISTICS: Advanced pixel-level analysis
 *   - Error Level Analysis (ELA)
 *   - Frequency domain / GAN artifact detection
 *   - Face-background boundary transition
 *   - Warping/geometric distortion
 *   - Specular highlight inconsistency
 *   - Facial symmetry analysis
 *   - Compression artifact detection
 *   - Color channel entropy analysis
 *   - Noise pattern analysis
 *   - Diffusion model heuristics
 *   - Inpainting boundary detection
 */
@Singleton
class DeepfakeMLEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mlKitFaceAnalyzer: MLKitFaceAnalyzer,
    private val faceMeshAnalyzer: FaceMeshDeepfakeAnalyzer,
    private val temporalTracker: TemporalFaceTracker
) {
    companion object {
        private const val TAG = "DeepfakeMLEngine"
    }

    data class DeepfakeResult(
        val isDeepfake: Boolean,
        val confidence: Float,
        val manipulationScore: Float,
        val elaScore: Float,
        val frequencyScore: Float,
        val faceConsistencyScore: Float,
        val compressionScore: Float,
        val findings: List<String>,
        val technicalDetails: Map<String, String>,
        // ML model scores
        val mlKitFaceScore: Float = 0f,
        val faceMeshScore: Float = 0f,
        val temporalScore: Float = 0f,
        val mlFaceDetected: Boolean = false
    )

    /**
     * Analyze a single frame for deepfake indicators using ALL models.
     *
     * Pipeline:
     * 1. ML Kit Face Detection → face boxes, landmarks, classifications
     * 2. ML Kit Face Mesh → 468 3D mesh points
     * 3. Temporal tracking → multi-frame analysis
     * 4. Heuristic analysis → pixel-level signals
     * 5. Ensemble scoring → weighted combination
     */
    fun analyzeFrame(bitmap: Bitmap): DeepfakeResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) {
            return emptyResult()
        }

        val findings = mutableListOf<String>()
        val details = mutableMapOf<String, String>()

        // ============ STAGE 1: ML Kit Face Detection (Real Neural Network) ============
        val mlKitResult = try {
            mlKitFaceAnalyzer.analyzeFast(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit face detection error: ${e.message}")
            null
        }

        val mlFaceDetected = mlKitResult != null && mlKitResult.facesDetected > 0
        val mlKitScore = mlKitResult?.deepfakeScore ?: 0f
        details["mlkit_faces"] = "${mlKitResult?.facesDetected ?: 0}"
        details["mlkit_score"] = String.format("%.3f", mlKitScore)
        details["mlkit_time_ms"] = "${mlKitResult?.processingTimeMs ?: -1}"

        if (mlKitResult != null) {
            findings.addAll(mlKitResult.findings)
        }

        // Record face for temporal tracking
        if (mlFaceDetected && mlKitResult != null && mlKitResult.faces.isNotEmpty()) {
            try {
                temporalTracker.recordFrame(mlKitResult.faces[0])
            } catch (e: Exception) {
                Log.w(TAG, "Temporal tracker error: ${e.message}")
            }
        }

        // ============ STAGE 2: ML Kit Face Mesh (468-point 3D mesh) ============
        val meshResult = try {
            faceMeshAnalyzer.analyze(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Face mesh analysis error: ${e.message}")
            null
        }

        val meshScore = meshResult?.deepfakeScore ?: 0f
        details["mesh_detected"] = "${meshResult?.meshDetected ?: false}"
        details["mesh_points"] = "${meshResult?.meshPointCount ?: 0}"
        details["mesh_score"] = String.format("%.3f", meshScore)
        details["mesh_time_ms"] = "${meshResult?.processingTimeMs ?: -1}"

        if (meshResult != null) {
            findings.addAll(meshResult.findings)
        }

        // ============ STAGE 3: Temporal Analysis (ML-driven multi-frame) ============
        val temporalResult = try {
            temporalTracker.analyze()
        } catch (e: Exception) {
            Log.w(TAG, "Temporal analysis error: ${e.message}")
            null
        }

        val temporalScore = temporalResult?.temporalDeepfakeScore ?: 0f
        details["temporal_score"] = String.format("%.3f", temporalScore)
        details["temporal_frames"] = "${temporalResult?.framesAnalyzed ?: 0}"

        if (temporalResult != null) {
            findings.addAll(temporalResult.findings)
        }

        // ============ STAGE 4: Heuristic Analysis (pixel-level) ============
        // Use ML-detected face region if available, fall back to skin-color heuristic
        val faceRegion = if (mlFaceDetected && mlKitResult != null) {
            val box = mlKitResult.faces[0].boundingBox
            FaceRect(box.left, box.top, box.width(), box.height())
        } else {
            detectFaceRegion(bitmap)
        }

        val hasFace = faceRegion != null || mlFaceDetected
        details["face_detected"] = if (hasFace) "yes (${if (mlFaceDetected) "ML" else "heuristic"})" else "no"

        // Heuristic scores
        val elaScore = performELA(bitmap, findings)
        details["ela_score"] = String.format("%.3f", elaScore)

        val frequencyScore = analyzeFrequencyDomain(bitmap, findings)
        details["frequency_score"] = String.format("%.3f", frequencyScore)

        val faceScore = if (hasFace && faceRegion != null) {
            analyzeFaceConsistency(bitmap, findings, faceRegion)
        } else 0f
        details["face_consistency"] = String.format("%.3f", faceScore)

        val compressionScore = analyzeCompressionArtifacts(bitmap, findings)
        details["compression_score"] = String.format("%.3f", compressionScore)

        val colorScore = analyzeColorChannels(bitmap, findings)
        details["color_score"] = String.format("%.3f", colorScore)

        val edgeScore = analyzeEdgeConsistency(bitmap, findings)
        details["edge_score"] = String.format("%.3f", edgeScore)

        val noiseScore = analyzeNoisePatterns(bitmap, findings)
        details["noise_score"] = String.format("%.3f", noiseScore)

        val diffusionScore = analyzeDiffusionHeuristic(bitmap, findings)
        details["diffusion_score"] = String.format("%.3f", diffusionScore)

        val inpaintingScore = detectInpaintingBoundary(bitmap, findings)
        details["inpainting_score"] = String.format("%.3f", inpaintingScore)

        val boundaryScore = if (hasFace && faceRegion != null) {
            analyzeFaceBoundaryTransition(bitmap, faceRegion, findings)
        } else 0f
        details["boundary_score"] = String.format("%.3f", boundaryScore)

        val warpScore = if (hasFace && faceRegion != null) {
            detectWarpingDistortion(bitmap, faceRegion, findings)
        } else 0f
        details["warp_score"] = String.format("%.3f", warpScore)

        val specularScore = if (hasFace && faceRegion != null) {
            analyzeSpecularHighlights(bitmap, faceRegion, findings)
        } else 0f
        details["specular_score"] = String.format("%.3f", specularScore)

        val symmetryScore = if (hasFace && faceRegion != null) {
            analyzeFacialSymmetry(bitmap, faceRegion, findings)
        } else 0f
        details["symmetry_score"] = String.format("%.3f", symmetryScore)

        // ============ STAGE 5: Ensemble Scoring ============
        // Combine all model scores with weights
        val heuristicScore = if (hasFace) {
            (elaScore * 0.08f +
            frequencyScore * 0.07f +
            faceScore * 0.10f +
            compressionScore * 0.04f +
            colorScore * 0.04f +
            edgeScore * 0.04f +
            noiseScore * 0.02f +
            diffusionScore * 0.03f +
            inpaintingScore * 0.02f +
            boundaryScore * 0.10f +
            warpScore * 0.07f +
            specularScore * 0.05f +
            symmetryScore * 0.04f)
        } else {
            (elaScore * 0.20f +
            frequencyScore * 0.18f +
            compressionScore * 0.12f +
            colorScore * 0.12f +
            edgeScore * 0.12f +
            noiseScore * 0.08f +
            diffusionScore * 0.09f +
            inpaintingScore * 0.09f)
        }
        details["heuristic_total"] = String.format("%.3f", heuristicScore)

        // Final manipulation score: ML models get higher weight than heuristics
        val manipulationScore = if (mlFaceDetected) {
            // ML models available — they get primary weight
            (mlKitScore * 0.25f +           // ML Kit face analysis
             meshScore * 0.20f +             // Face mesh analysis
             temporalScore * 0.20f +         // Temporal tracking
             heuristicScore * 0.35f)         // Pixel heuristics
        } else {
            // No ML face — rely more on heuristics
            (heuristicScore * 0.70f +
             meshScore * 0.15f +             // Mesh may still contribute
             temporalScore * 0.15f)
        }
        details["final_score"] = String.format("%.3f", manipulationScore)

        // Confidence based on evidence strength
        val mlFindings = (mlKitResult?.findings?.size ?: 0) +
                         (meshResult?.findings?.size ?: 0) +
                         (temporalResult?.findings?.size ?: 0)
        val totalFindings = findings.size

        val confidence = when {
            totalFindings >= 8 && mlFindings >= 3 -> 0.95f
            totalFindings >= 6 && mlFindings >= 2 -> 0.90f
            totalFindings >= 5 && mlFindings >= 1 -> 0.82f
            totalFindings >= 4 -> 0.70f
            totalFindings >= 3 -> 0.58f
            totalFindings >= 2 -> 0.45f
            totalFindings >= 1 -> 0.30f
            else -> 0.15f
        }

        return DeepfakeResult(
            isDeepfake = manipulationScore > 0.48f,
            confidence = confidence,
            manipulationScore = manipulationScore,
            elaScore = elaScore,
            frequencyScore = frequencyScore,
            faceConsistencyScore = faceScore,
            compressionScore = compressionScore,
            findings = findings,
            technicalDetails = details,
            mlKitFaceScore = mlKitScore,
            faceMeshScore = meshScore,
            temporalScore = temporalScore,
            mlFaceDetected = mlFaceDetected
        )
    }

    private fun emptyResult() = DeepfakeResult(
        false, 0f, 0f, 0f, 0f, 0f, 0f, emptyList(), emptyMap()
    )

    /**
     * Reset temporal tracking (call when changing monitoring target).
     */
    fun resetTemporalTracking() {
        temporalTracker.reset()
    }

    // ================== HEURISTIC ANALYSIS FUNCTIONS ==================
    // (These complement the ML models with pixel-level forensics)

    private data class FaceRect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun detectFaceRegion(bitmap: Bitmap): FaceRect? {
        val w = bitmap.width; val h = bitmap.height
        val step = maxOf(2, minOf(w, h) / 80)
        var skinCount = 0; var totalPixels = 0
        var sumX = 0L; var sumY = 0L; var skinPixels = 0

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = bitmap.getPixel(x, y)
                totalPixels++
                if (isSkinColor(pixel)) {
                    skinCount++
                    sumX += x; sumY += y; skinPixels++
                }
            }
        }

        val skinRatio = skinCount.toFloat() / totalPixels
        if (skinRatio < 0.05f || skinPixels < 10) return null

        val cx = (sumX / skinPixels).toInt()
        val cy = (sumY / skinPixels).toInt()

        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                if (isSkinColor(bitmap.getPixel(x, y))) {
                    val dx = abs(x - cx); val dy = abs(y - cy)
                    if (dx < w / 3 && dy < h / 3) {
                        minX = minOf(minX, x); maxX = maxOf(maxX, x)
                        minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    }
                }
            }
        }

        val faceW = maxX - minX
        val faceH = maxY - minY
        if (faceW < w / 10 || faceH < h / 10) return null
        return FaceRect(minX, minY, faceW, faceH)
    }

    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
        return r > 60 && g > 40 && b > 20 && r > g && r > b &&
               abs(r - g) > 10 && r - b > 15 && r < 250 && g < 230 && b < 210
    }

    private fun performELA(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = bitmap.width; val h = bitmap.height
        val blockSize = 8
        val blockEnergies = mutableListOf<Double>()
        var blockCount = 0

        for (y in 0 until h - blockSize step blockSize) {
            for (x in 0 until w - blockSize step blockSize) {
                var blockEnergy = 0.0
                for (by in 0 until blockSize) { for (bx in 0 until blockSize) {
                    val px = x + bx; val py = y + by
                    if (px < w && py < h && px > 0) {
                        val pixel = bitmap.getPixel(px, py)
                        val prevPixel = bitmap.getPixel(px - 1, py)
                        blockEnergy += abs(Color.red(pixel) - Color.red(prevPixel)) +
                                       abs(Color.green(pixel) - Color.green(prevPixel)) +
                                       abs(Color.blue(pixel) - Color.blue(prevPixel))
                    }
                }}
                blockEnergies.add(blockEnergy)
                blockCount++
            }
        }

        if (blockEnergies.isEmpty()) return 0f
        val mean = blockEnergies.average()
        val stdDev = sqrt(blockEnergies.map { (it - mean).pow(2) }.average())
        val outliers = blockEnergies.count { abs(it - mean) > 2 * stdDev }
        val outlierRatio = outliers.toFloat() / blockCount

        if (outlierRatio > 0.15f) {
            findings.add("ELA: Inconsistent compression levels (${(outlierRatio * 100).toInt()}% anomalous)")
        }
        return (outlierRatio * 3f).coerceIn(0f, 1f)
    }

    private fun analyzeFrequencyDomain(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = bitmap.width; val h = bitmap.height
        val sample = minOf(w, h, 256)
        var hGradSum = 0.0; var vGradSum = 0.0; var dGradSum = 0.0; var pixelCount = 0

        for (y in 1 until minOf(h, sample) - 1) { for (x in 1 until minOf(w, sample) - 1) {
            val center = luminance(bitmap.getPixel(x, y))
            hGradSum += abs(luminance(bitmap.getPixel(x + 1, y)) - luminance(bitmap.getPixel(x - 1, y)))
            vGradSum += abs(luminance(bitmap.getPixel(x, y + 1)) - luminance(bitmap.getPixel(x, y - 1)))
            dGradSum += abs(luminance(bitmap.getPixel(x + 1, y + 1)) - center)
            pixelCount++
        }}

        if (pixelCount == 0) return 0f
        val hGrad = hGradSum / pixelCount; val vGrad = vGradSum / pixelCount
        val gradRatio = if (maxOf(hGrad, vGrad) > 0) minOf(hGrad, vGrad) / maxOf(hGrad, vGrad) else 1.0

        if (gradRatio > 0.97) findings.add("Frequency: Uniform gradient distribution (GAN indicator)")

        val periodicScore = detectPeriodicPatterns(bitmap)
        if (periodicScore > 0.5f) findings.add("Frequency: Periodic patterns (GAN artifacts)")

        return ((1.0 - gradRatio).toFloat() * 5f + periodicScore * 0.5f).coerceIn(0f, 1f)
    }

    private fun detectPeriodicPatterns(bitmap: Bitmap): Float {
        val size = minOf(bitmap.width, bitmap.height, 128)
        var periodicCount = 0; var totalChecks = 0
        for (y in 2 until size - 2 step 4) { for (x in 2 until size - 2 step 4) {
            val center = luminance(bitmap.getPixel(x, y))
            val p2 = luminance(bitmap.getPixel(x + 2, y))
            val p4 = if (x + 4 < size) luminance(bitmap.getPixel(x + 4, y)) else center
            if (abs(center - p4) < 3 && abs(center - p2) > 5) periodicCount++
            totalChecks++
        }}
        return if (totalChecks > 0) periodicCount.toFloat() / totalChecks else 0f
    }

    private fun analyzeFaceConsistency(bitmap: Bitmap, findings: MutableList<String>, face: FaceRect): Float {
        val w = bitmap.width; val h = bitmap.height
        val facePixels = extractRegion(bitmap, face.x, face.y, face.x + face.w, face.y + face.h)
        val bgPixels = mutableListOf<Int>()
        val step = maxOf(2, minOf(w, h) / 50)
        for (y in 0 until h step step) { for (x in 0 until w step step) {
            if (x < face.x || x > face.x + face.w || y < face.y || y > face.y + face.h) {
                bgPixels.add(bitmap.getPixel(x, y))
            }
        }}
        if (bgPixels.isEmpty()) return 0f

        val faceNoise = calculateNoiseLevelPixels(facePixels)
        val bgNoise = calculateNoiseLevelPixels(bgPixels)
        val noiseDiff = abs(faceNoise - bgNoise)
        if (noiseDiff > 12) findings.add("Face: Noise inconsistency (diff: ${noiseDiff.toInt()})")

        val faceTexture = computeTextureGranularity(bitmap, face.x, face.y, face.x + face.w, face.y + face.h)
        val bgTexture = computeTextureGranularity(bitmap, 0, 0, w, face.y.coerceAtLeast(10))
        val textureDiff = abs(faceTexture - bgTexture)
        if (textureDiff > 8f) findings.add("Face: Texture granularity mismatch")

        val blendingScore = detectBlendingArtifacts(bitmap, face)
        if (blendingScore > 0.3f) findings.add("Face: Blending artifacts at boundary")

        return ((noiseDiff / 25f) + (textureDiff / 20f) + blendingScore * 0.4f).coerceIn(0f, 1f)
    }

    private fun computeTextureGranularity(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): Float {
        var totalGrad = 0.0; var count = 0
        val step = maxOf(2, (x2 - x1) / 30)
        for (y in (y1 + 1).coerceAtLeast(1) until (y2 - 1).coerceAtMost(bitmap.height - 1) step step) {
            for (x in (x1 + 1).coerceAtLeast(1) until (x2 - 1).coerceAtMost(bitmap.width - 1) step step) {
                val c = luminance(bitmap.getPixel(x, y))
                totalGrad += abs(c - luminance(bitmap.getPixel(x + 1, y))) +
                             abs(c - luminance(bitmap.getPixel(x, y + 1)))
                count++
            }
        }
        return if (count > 0) (totalGrad / count).toFloat() else 0f
    }

    private fun extractRegion(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): List<Int> {
        val pixels = mutableListOf<Int>()
        for (y in y1 until minOf(y2, bitmap.height)) {
            for (x in x1 until minOf(x2, bitmap.width)) { pixels.add(bitmap.getPixel(x, y)) }
        }
        return pixels
    }

    private fun calculateNoiseLevelPixels(pixels: List<Int>): Float {
        if (pixels.size < 2) return 0f
        var totalDiff = 0.0
        for (i in 1 until pixels.size) {
            totalDiff += abs(Color.red(pixels[i]) - Color.red(pixels[i - 1])) +
                         abs(Color.green(pixels[i]) - Color.green(pixels[i - 1])) +
                         abs(Color.blue(pixels[i]) - Color.blue(pixels[i - 1]))
        }
        return (totalDiff / pixels.size).toFloat()
    }

    private fun detectBlendingArtifacts(bitmap: Bitmap, face: FaceRect): Float {
        val w = bitmap.width; val h = bitmap.height
        if (w < 3 || h < 3) return 0f
        val cx = face.x + face.w / 2; val cy = face.y + face.h / 2
        val radius = minOf(face.w, face.h) / 2
        if (radius < 5) return 0f
        var artifacts = 0; var checks = 0

        for (angle in 0 until 360 step 3) {
            val rad = Math.toRadians(angle.toDouble())
            val x = (cx + radius * cos(rad)).toInt().coerceIn(1, w - 2)
            val y = (cy + radius * sin(rad)).toInt().coerceIn(1, h - 2)
            val center = luminance(bitmap.getPixel(x, y))
            val innerX = (cx + (radius - 4) * cos(rad)).toInt().coerceIn(0, w - 1)
            val innerY = (cy + (radius - 4) * sin(rad)).toInt().coerceIn(0, h - 1)
            val outerX = (cx + (radius + 4) * cos(rad)).toInt().coerceIn(0, w - 1)
            val outerY = (cy + (radius + 4) * sin(rad)).toInt().coerceIn(0, h - 1)
            val inner = luminance(bitmap.getPixel(innerX, innerY))
            val outer = luminance(bitmap.getPixel(outerX, outerY))
            if (abs(center - inner) > 15 && abs(center - outer) > 15) artifacts++
            checks++
        }
        return if (checks > 0) artifacts.toFloat() / checks else 0f
    }

    private fun detectInpaintingBoundary(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = bitmap.width; val h = bitmap.height; val step = 16
        var seamCount = 0; var checks = 0
        for (y in step until h - step step step) { for (x in 1 until w - 1) {
            if (x % step != 0) continue
            if (abs(luminance(bitmap.getPixel(x - 1, y)) - luminance(bitmap.getPixel(x + 1, y))) > 25) seamCount++
            checks++
        }}
        for (x in step until w - step step step) { for (y in 1 until h - 1) {
            if (y % step != 0) continue
            if (abs(luminance(bitmap.getPixel(x, y - 1)) - luminance(bitmap.getPixel(x, y + 1))) > 25) seamCount++
            checks++
        }}
        val ratio = if (checks > 0) seamCount.toFloat() / checks else 0f
        if (ratio > 0.08f) findings.add("Inpainting: Sharp linear boundaries detected")
        return (ratio * 6f).coerceIn(0f, 1f)
    }

    private fun analyzeCompressionArtifacts(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = bitmap.width; val h = bitmap.height
        var boundaryDiscontinuity = 0; var checks = 0
        for (y in 8 until h - 8 step 8) { for (x in 8 until w - 8 step 8) {
            val inside = luminance(bitmap.getPixel(x - 1, y))
            val boundary = luminance(bitmap.getPixel(x, y))
            val outside = luminance(bitmap.getPixel(x + 1, y))
            if (abs(inside - boundary) > 10 && abs(boundary - outside) > 10 && abs(inside - outside) < 5) boundaryDiscontinuity++
            checks++
        }}
        val ratio = if (checks > 0) boundaryDiscontinuity.toFloat() / checks else 0f
        if (ratio > 0.1f) findings.add("Compression: Double-compression artifacts detected")
        return (ratio * 5f).coerceIn(0f, 1f)
    }

    private fun analyzeColorChannels(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        val rHist = IntArray(256); val gHist = IntArray(256); val bHist = IntArray(256)
        for (y in 0 until h) { for (x in 0 until w) {
            val p = bitmap.getPixel(x, y)
            rHist[Color.red(p)]++; gHist[Color.green(p)]++; bHist[Color.blue(p)]++
        }}
        val rE = calcEntropy(rHist); val gE = calcEntropy(gHist); val bE = calcEntropy(bHist)
        val range = maxOf(rE, gE, bE) - minOf(rE, gE, bE)
        if (range > 1.5) findings.add("Color: Entropy imbalance across channels")
        return (range / 3f).toFloat().coerceIn(0f, 1f)
    }

    private fun calcEntropy(histogram: IntArray): Double {
        val total = histogram.sum().toDouble()
        if (total == 0.0) return 0.0
        return -histogram.filter { it > 0 }.sumOf { count ->
            val p = count / total; p * ln(p)
        }
    }

    private fun analyzeEdgeConsistency(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        val edges = mutableListOf<Float>()
        for (y in 1 until h - 1) { for (x in 1 until w - 1) {
            val gx = luminance(bitmap.getPixel(x + 1, y)) - luminance(bitmap.getPixel(x - 1, y))
            val gy = luminance(bitmap.getPixel(x, y + 1)) - luminance(bitmap.getPixel(x, y - 1))
            edges.add(sqrt((gx * gx + gy * gy).toFloat()))
        }}
        if (edges.isEmpty()) return 0f
        val mean = edges.average()
        val stdDev = sqrt(edges.map { (it - mean).pow(2) }.average())
        if (stdDev == 0.0) return 0f
        val kurtosis = edges.map { ((it - mean) / stdDev).pow(4) }.average() - 3.0
        if (kurtosis < -1.0) findings.add("Edge: Over-smoothed edge distribution (AI generation)")
        return (abs(kurtosis) / 5.0).toFloat().coerceIn(0f, 1f)
    }

    private fun analyzeNoisePatterns(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = minOf(bitmap.width, 128); val h = minOf(bitmap.height, 128)
        val noiseMap = mutableListOf<Float>()
        for (y in 1 until h - 1 step 2) { for (x in 1 until w - 1 step 2) {
            val center = luminance(bitmap.getPixel(x, y)).toFloat()
            val neighbors = listOf(
                luminance(bitmap.getPixel(x-1, y)), luminance(bitmap.getPixel(x+1, y)),
                luminance(bitmap.getPixel(x, y-1)), luminance(bitmap.getPixel(x, y+1))
            ).average().toFloat()
            noiseMap.add(abs(center - neighbors))
        }}
        if (noiseMap.isEmpty()) return 0f
        val noiseStd = sqrt(noiseMap.map { (it - noiseMap.average()).pow(2) }.average()).toFloat()
        if (noiseStd < 1.5f) findings.add("Noise: Uniform noise pattern (AI generation)")
        return (1f - noiseStd / 10f).coerceIn(0f, 1f)
    }

    private fun analyzeDiffusionHeuristic(bitmap: Bitmap, findings: MutableList<String>): Float {
        val w = minOf(bitmap.width, 128); val h = minOf(bitmap.height, 128)
        val patchVariances = mutableListOf<Double>()
        val patchSize = 16
        for (y in 0 until h - patchSize step patchSize) { for (x in 0 until w - patchSize step patchSize) {
            var sum = 0.0; var sumSq = 0.0; var count = 0
            for (py in y until minOf(y + patchSize, h)) { for (px in x until minOf(x + patchSize, w)) {
                val l = luminance(bitmap.getPixel(px, py)).toDouble()
                sum += l; sumSq += l * l; count++
            }}
            if (count > 0) {
                val mean = sum / count
                patchVariances.add(((sumSq / count) - (mean * mean)).coerceAtLeast(0.0))
            }
        }}
        if (patchVariances.size < 4) return 0f
        val vov = run {
            val m = patchVariances.average()
            sqrt(patchVariances.map { (it - m).pow(2) }.average())
        }
        if (vov < 50 && patchVariances.average() < 200)
            findings.add("Diffusion: Uniform patch statistics (Stable Diffusion/DALL-E)")
        return (1f - (vov / 150).toFloat().coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun analyzeFaceBoundaryTransition(bitmap: Bitmap, face: FaceRect, findings: MutableList<String>): Float {
        val w = bitmap.width; val h = bitmap.height
        val cx = face.x + face.w / 2; val cy = face.y + face.h / 2
        val radius = minOf(face.w, face.h) / 2
        if (radius < 8) return 0f

        val colorJumps = mutableListOf<Float>()
        val transitionWidths = mutableListOf<Float>()

        for (angle in 0 until 360 step 4) {
            val rad = Math.toRadians(angle.toDouble())
            val values = mutableListOf<Int>()
            for (r in (radius - 10).coerceAtLeast(0)..radius + 10) {
                val px = (cx + r * cos(rad)).toInt().coerceIn(0, w - 1)
                val py = (cy + r * sin(rad)).toInt().coerceIn(0, h - 1)
                values.add(luminance(bitmap.getPixel(px, py)))
            }
            var maxGrad = 0f
            for (i in 1 until values.size) { maxGrad = maxOf(maxGrad, abs(values[i] - values[i - 1]).toFloat()) }
            colorJumps.add(maxGrad)
            val threshold = maxGrad * 0.5f
            transitionWidths.add(values.zipWithNext { a, b -> if (abs(a - b) > threshold) 1f else 0f }.sum())
        }

        if (colorJumps.isEmpty()) return 0f
        val avgJump = colorJumps.average()
        val avgTransWidth = transitionWidths.average()
        val jumpStdDev = sqrt(colorJumps.map { (it - avgJump).pow(2) }.average())

        var score = 0f
        if (avgJump > 18) { score += 0.3f; findings.add("Boundary: Sharp color transition (${avgJump.toInt()} avg)") }
        if (avgTransWidth < 2.5f && avgJump > 10) { score += 0.3f; findings.add("Boundary: Abrupt face-background seam") }
        if (jumpStdDev > 12) { score += 0.2f; findings.add("Boundary: Non-uniform face boundary") }
        return score.coerceIn(0f, 1f)
    }

    private fun detectWarpingDistortion(bitmap: Bitmap, face: FaceRect, findings: MutableList<String>): Float {
        val step = maxOf(2, minOf(face.w, face.h) / 20)
        if (step < 2) return 0f
        val distortions = mutableListOf<Float>()

        for (y in (face.y + step).coerceAtLeast(step) until (face.y + face.h - step).coerceAtMost(bitmap.height - step) step step) {
            for (x in (face.x + step).coerceAtLeast(step) until (face.x + face.w - step).coerceAtMost(bitmap.width - step) step step) {
                val gx = luminance(bitmap.getPixel(x + 1, y)) - luminance(bitmap.getPixel(x - 1, y))
                val gy = luminance(bitmap.getPixel(x, y + 1)) - luminance(bitmap.getPixel(x, y - 1))
                val a1 = atan2(gy.toDouble(), gx.toDouble())

                val nx = (x + step).coerceAtMost(bitmap.width - 2)
                val gxR = luminance(bitmap.getPixel(nx + 1, y)) - luminance(bitmap.getPixel(nx - 1, y))
                val gyR = luminance(bitmap.getPixel(nx, y + 1)) - luminance(bitmap.getPixel(nx, y - 1))
                val a2 = atan2(gyR.toDouble(), gxR.toDouble())

                var diff = abs(a1 - a2)
                if (diff > Math.PI) diff = 2 * Math.PI - diff
                distortions.add(diff.toFloat())
            }
        }

        if (distortions.size < 10) return 0f
        val mean = distortions.average()
        val variance = distortions.map { (it - mean).pow(2) }.average()
        val score = (variance * 3f).toFloat().coerceIn(0f, 1f)
        if (score > 0.35f) findings.add("Warp: Geometric distortion in face region")
        return score
    }

    private fun analyzeSpecularHighlights(bitmap: Bitmap, face: FaceRect, findings: MutableList<String>): Float {
        val w = face.w; val h = face.h
        if (w < 20 || h < 20) return 0f

        val quadrants = arrayOf(
            intArrayOf(face.x, face.y, face.x + w / 2, face.y + h / 2),
            intArrayOf(face.x + w / 2, face.y, face.x + w, face.y + h / 2),
            intArrayOf(face.x, face.y + h / 2, face.x + w / 2, face.y + h),
            intArrayOf(face.x + w / 2, face.y + h / 2, face.x + w, face.y + h)
        )
        val qBright = FloatArray(4); val qHighlights = IntArray(4)
        val step = maxOf(2, minOf(w, h) / 15)

        for (q in 0..3) {
            val qx1 = quadrants[q][0].coerceIn(0, bitmap.width - 1)
            val qy1 = quadrants[q][1].coerceIn(0, bitmap.height - 1)
            val qx2 = quadrants[q][2].coerceIn(0, bitmap.width - 1)
            val qy2 = quadrants[q][3].coerceIn(0, bitmap.height - 1)
            var sum = 0f; var cnt = 0; var hl = 0
            for (y in qy1 until qy2 step step) { for (x in qx1 until qx2 step step) {
                val lum = luminance(bitmap.getPixel(x, y)).toFloat()
                sum += lum; cnt++; if (lum > 200) hl++
            }}
            qBright[q] = if (cnt > 0) sum / cnt else 0f
            qHighlights[q] = hl
        }

        val leftB = (qBright[0] + qBright[2]) / 2; val rightB = (qBright[1] + qBright[3]) / 2
        val topB = (qBright[0] + qBright[1]) / 2; val bottomB = (qBright[2] + qBright[3]) / 2
        val totalHL = qHighlights.sum()
        val hlSpread = if (totalHL > 3) 1f - (qHighlights.max().toFloat() / totalHL) else 0f

        var score = 0f
        if (abs(leftB - rightB) > 30 && abs(topB - bottomB) > 25) {
            score += 0.3f; findings.add("Specular: Inconsistent lighting direction")
        }
        if (hlSpread > 0.6f && totalHL > 5) {
            score += 0.3f; findings.add("Specular: Scattered highlight pattern")
        }
        return score.coerceIn(0f, 1f)
    }

    private fun analyzeFacialSymmetry(bitmap: Bitmap, face: FaceRect, findings: MutableList<String>): Float {
        val halfW = face.w / 2; val step = maxOf(2, halfW / 15)
        if (halfW < 10) return 0f
        var colorAsym = 0f; var noiseAsym = 0f; var count = 0

        for (y in face.y until (face.y + face.h).coerceAtMost(bitmap.height) step step) {
            for (dx in 0 until halfW step step) {
                val lx = (face.x + halfW - dx).coerceIn(0, bitmap.width - 1)
                val rx = (face.x + halfW + dx).coerceIn(0, bitmap.width - 1)
                if (lx >= bitmap.width || rx >= bitmap.width || y >= bitmap.height) continue
                val lp = bitmap.getPixel(lx, y); val rp = bitmap.getPixel(rx, y)
                colorAsym += abs(Color.red(lp) - Color.red(rp)) +
                             abs(Color.green(lp) - Color.green(rp)) +
                             abs(Color.blue(lp) - Color.blue(rp))
                if (lx > 0 && rx < bitmap.width - 1) {
                    noiseAsym += abs(
                        abs(luminance(lp) - luminance(bitmap.getPixel(lx - 1, y))) -
                        abs(luminance(rp) - luminance(bitmap.getPixel(rx + 1, y)))
                    ).toFloat()
                }
                count++
            }
        }

        if (count < 10) return 0f
        val avgColor = colorAsym / count; val avgNoise = noiseAsym / count
        var score = 0f
        if (avgNoise > 8f && avgColor < 15f) {
            score += 0.4f; findings.add("Symmetry: Noise pattern asymmetry")
        }
        if (avgColor > 40f) { score += 0.3f; findings.add("Symmetry: Color asymmetry across face") }
        return score.coerceIn(0f, 1f)
    }

    private fun luminance(pixel: Int): Int {
        return (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
    }
}
