package com.deepfakeshield.ml.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.facemesh.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Face Mesh Deepfake Analyzer — uses Google ML Kit's Face Mesh Detection.
 *
 * This is a REAL ML model that provides 468 3D face mesh points.
 * The neural network is bundled inside the ML Kit AAR.
 *
 * Detection capabilities using the 468-point mesh:
 * 1. Mesh regularity analysis — real faces have consistent triangle proportions
 * 2. Depth (Z) consistency — real faces form smooth 3D surfaces
 * 3. Mesh symmetry — deepfakes often have asymmetric distortions
 * 4. Face oval regularity — the outer contour should be smooth
 * 5. Feature region analysis — eyes, nose, mouth mesh consistency
 */
@Singleton
class FaceMeshDeepfakeAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FaceMeshAnalyzer"

        // Key face mesh landmark indices (MediaPipe canonical — same numbering used by ML Kit)
        private val UPPER_LIP_INDICES = listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291)
        private val LOWER_LIP_INDICES = listOf(146, 91, 181, 84, 17, 314, 405, 321, 375, 291)
        private val LEFT_EYE_INDICES = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
        private val RIGHT_EYE_INDICES = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
        private val FACE_OVAL_INDICES = listOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
        )
        private val NOSE_RIDGE_INDICES = listOf(168, 6, 197, 195, 5, 4, 1, 19, 94, 2)
    }

    data class FaceMeshResult(
        val meshDetected: Boolean,
        val meshPointCount: Int,
        val meshRegularityScore: Float,
        val depthConsistencyScore: Float,
        val meshSymmetryScore: Float,
        val faceOvalSmoothnessScore: Float,
        val lipMeshScore: Float,
        val eyeMeshScore: Float,
        val deepfakeScore: Float,
        val findings: List<String>,
        val processingTimeMs: Long
    )

    @Volatile
    private var meshDetector: FaceMeshDetector? = null

    private fun getDetector(): FaceMeshDetector {
        if (meshDetector == null) {
            synchronized(this) {
                if (meshDetector == null) {
                    val options = FaceMeshDetectorOptions.Builder()
                        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                        .build()
                    meshDetector = FaceMeshDetection.getClient(options)
                }
            }
        }
        return checkNotNull(meshDetector) { "FaceMeshDetector not initialized" }
    }

    /**
     * Analyze bitmap using the 468-point face mesh ML model.
     */
    fun analyze(bitmap: Bitmap): FaceMeshResult {
        if (bitmap.isRecycled || bitmap.width < 10 || bitmap.height < 10) {
            return emptyResult(0)
        }

        val startTime = System.currentTimeMillis()
        val findings = mutableListOf<String>()

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val meshes = Tasks.await(getDetector().process(inputImage))
            val elapsed = System.currentTimeMillis() - startTime

            if (meshes.isEmpty()) {
                return FaceMeshResult(
                    false, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                    listOf("No face mesh detected"), elapsed
                )
            }

            // Analyze the primary (largest) face mesh
            val mesh = meshes.maxByOrNull {
                it.boundingBox.width() * it.boundingBox.height()
            } ?: return emptyResult(elapsed)

            val allPoints = mesh.allPoints
            if (allPoints.size < 100) return emptyResult(elapsed)

            // Build index→position map for fast lookup by canonical mesh index
            val pointMap = HashMap<Int, PointF3D>(allPoints.size)
            for (pt in allPoints) {
                pointMap[pt.index] = pt.position
            }

            // 1. Mesh triangle regularity
            val regularityScore = analyzeMeshRegularity(allPoints, findings)

            // 2. Depth (Z) consistency
            val depthScore = analyzeDepthConsistency(allPoints, findings)

            // 3. Left-right symmetry
            val symmetryScore = analyzeMeshSymmetry(allPoints, findings)

            // 4. Face oval smoothness
            val ovalScore = analyzeFaceOvalSmoothness(pointMap, findings)

            // 5. Lip mesh analysis
            val lipScore = analyzeLipMesh(pointMap, findings)

            // 6. Eye mesh analysis
            val eyeScore = analyzeEyeMesh(pointMap, findings)

            // Weighted composite
            val deepfakeScore = (
                regularityScore * 0.20f +
                depthScore * 0.20f +
                symmetryScore * 0.15f +
                ovalScore * 0.15f +
                lipScore * 0.15f +
                eyeScore * 0.15f
            ).coerceIn(0f, 1f)

            FaceMeshResult(
                meshDetected = true,
                meshPointCount = allPoints.size,
                meshRegularityScore = regularityScore,
                depthConsistencyScore = depthScore,
                meshSymmetryScore = symmetryScore,
                faceOvalSmoothnessScore = ovalScore,
                lipMeshScore = lipScore,
                eyeMeshScore = eyeScore,
                deepfakeScore = deepfakeScore,
                findings = findings,
                processingTimeMs = elapsed
            )
        } catch (e: Exception) {
            Log.w(TAG, "Face mesh analysis failed: ${e.message}")
            emptyResult(System.currentTimeMillis() - startTime)
        }
    }

    // ---- Analysis functions ----

    private fun analyzeMeshRegularity(
        points: List<FaceMeshPoint>,
        findings: MutableList<String>
    ): Float {
        if (points.size < 50) return 0f

        val triangleAreas = mutableListOf<Float>()
        val step = maxOf(1, points.size / 50)

        for (i in points.indices step step) {
            val i2 = minOf(i + 1, points.size - 1)
            val i3 = minOf(i + 2, points.size - 1)
            if (i == i2 || i2 == i3) continue

            val p1 = points[i].position
            val p2 = points[i2].position
            val p3 = points[i3].position

            val area = triangleArea(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            if (area > 0.01f) triangleAreas.add(area)
        }

        if (triangleAreas.size < 5) return 0f

        val mean = triangleAreas.average()
        val stdDev = sqrt(triangleAreas.map { (it - mean).pow(2) }.average())
        val cv = if (mean > 0) (stdDev / mean).toFloat() else 0f

        if (cv > 2.0f) {
            findings.add("Mesh: Highly irregular triangle mesh (CV=${String.format("%.2f", cv)})")
            return (cv / 4f).coerceIn(0f, 1f)
        }
        if (cv > 1.2f) {
            findings.add("Mesh: Moderately irregular triangle mesh")
            return ((cv - 1.2f) / 2f).coerceIn(0f, 0.5f)
        }
        return 0f
    }

    private fun analyzeDepthConsistency(
        points: List<FaceMeshPoint>,
        findings: MutableList<String>
    ): Float {
        if (points.size < 50) return 0f

        val zValues = points.map { it.position.z }
        val zMean = zValues.average()
        val zStdDev = sqrt(zValues.map { (it - zMean).pow(2) }.average())

        var discontinuities = 0
        val step = maxOf(1, points.size / 100)

        for (i in 0 until points.size - 1 step step) {
            val j = minOf(i + 1, points.size - 1)
            val p1 = points[i].position
            val p2 = points[j].position

            val spatialDist = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
            val zDiff = abs(p1.z - p2.z)

            if (spatialDist > 0 && zDiff / spatialDist > 0.5f) {
                discontinuities++
            }
        }

        val checkCount = maxOf(1, points.size / step)
        val discontinuityRatio = discontinuities.toFloat() / checkCount

        if (discontinuityRatio > 0.15f) {
            findings.add("Mesh: Depth discontinuities (${(discontinuityRatio * 100).toInt()}% of surface)")
            return (discontinuityRatio * 3f).coerceIn(0f, 1f)
        }

        if (zStdDev < 0.5f && points.size > 200) {
            findings.add("Mesh: Unnaturally flat face surface")
            return 0.4f
        }

        return (discontinuityRatio * 2f).coerceIn(0f, 1f)
    }

    private fun analyzeMeshSymmetry(
        points: List<FaceMeshPoint>,
        findings: MutableList<String>
    ): Float {
        if (points.size < 100) return 0f

        val centerX = points.map { it.position.x }.average().toFloat()
        val leftPoints = points.filter { it.position.x < centerX }
        val rightPoints = points.filter { it.position.x >= centerX }

        if (leftPoints.size < 20 || rightPoints.size < 20) return 0f

        // Compare depth profiles
        val leftDepths = leftPoints.map { it.position.z }.sorted()
        val rightDepths = rightPoints.map { it.position.z }.sorted()

        val minSize = minOf(leftDepths.size, rightDepths.size)
        val step = maxOf(1, minSize / 30)
        var totalZDiff = 0f
        var count = 0

        for (i in 0 until minSize step step) {
            totalZDiff += abs(leftDepths[i] - rightDepths[i])
            count++
        }

        val avgZDiff = if (count > 0) totalZDiff / count else 0f

        // Compare local noise
        val leftNoise = computeLocalNoise(leftPoints)
        val rightNoise = computeLocalNoise(rightPoints)
        val noiseDiff = abs(leftNoise - rightNoise)

        var score = 0f
        if (avgZDiff > 2.0f) {
            score += 0.3f
            findings.add("Mesh: Depth asymmetry between face halves")
        }
        if (noiseDiff > 0.5f) {
            score += 0.25f
            findings.add("Mesh: Asymmetric mesh noise pattern")
        }
        return score.coerceIn(0f, 1f)
    }

    private fun analyzeFaceOvalSmoothness(
        pointMap: Map<Int, PointF3D>,
        findings: MutableList<String>
    ): Float {
        val ovalPoints = FACE_OVAL_INDICES.mapNotNull { pointMap[it] }
        if (ovalPoints.size < 10) return 0f

        var totalAngleChange = 0f
        var sharpCorners = 0

        for (i in 1 until ovalPoints.size - 1) {
            val prev = ovalPoints[i - 1]
            val curr = ovalPoints[i]
            val next = ovalPoints[i + 1]

            val angle1 = atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble())
            val angle2 = atan2((next.y - curr.y).toDouble(), (next.x - curr.x).toDouble())

            var angleDiff = abs(angle2 - angle1)
            if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff

            totalAngleChange += angleDiff.toFloat()
            if (angleDiff > Math.PI / 6) sharpCorners++
        }

        val segments = (ovalPoints.size - 2).coerceAtLeast(1)
        val avgAngleChange = totalAngleChange / segments
        val sharpRatio = sharpCorners.toFloat() / segments

        var score = 0f
        if (sharpRatio > 0.3f) {
            score += 0.35f
            findings.add("Mesh: Jagged face oval (${(sharpRatio * 100).toInt()}% sharp corners)")
        }
        if (avgAngleChange > 0.4f) {
            score += 0.25f
            findings.add("Mesh: Irregular face boundary curvature")
        }
        return score.coerceIn(0f, 1f)
    }

    private fun analyzeLipMesh(
        pointMap: Map<Int, PointF3D>,
        findings: MutableList<String>
    ): Float {
        val upperLip = UPPER_LIP_INDICES.mapNotNull { pointMap[it] }
        val lowerLip = LOWER_LIP_INDICES.mapNotNull { pointMap[it] }
        if (upperLip.size < 5 || lowerLip.size < 5) return 0f

        var score = 0f

        // Lip depth consistency
        val lipZ = (upperLip + lowerLip).map { it.z }
        val lipZMean = lipZ.average()
        val lipZStdDev = sqrt(lipZ.map { (it - lipZMean).pow(2) }.average())

        if (lipZStdDev > 1.5f) {
            score += 0.3f
            findings.add("Mesh: Inconsistent lip depth (σ=${String.format("%.2f", lipZStdDev)})")
        }

        // Upper lip contour smoothness
        val smoothness = computeContourSmoothness(upperLip)
        if (smoothness > 0.35f) {
            score += 0.2f
            findings.add("Mesh: Jagged upper lip mesh")
        }

        // Lip depth symmetry
        val centerX = upperLip.map { it.x }.average().toFloat()
        val leftLip = upperLip.filter { it.x < centerX }
        val rightLip = upperLip.filter { it.x >= centerX }
        if (leftLip.isNotEmpty() && rightLip.isNotEmpty()) {
            val leftZ = leftLip.map { it.z }.average()
            val rightZ = rightLip.map { it.z }.average()
            if (abs(leftZ - rightZ) > 1.0f) {
                score += 0.2f
                findings.add("Mesh: Asymmetric lip depth")
            }
        }

        return score.coerceIn(0f, 1f)
    }

    private fun analyzeEyeMesh(
        pointMap: Map<Int, PointF3D>,
        findings: MutableList<String>
    ): Float {
        val leftEye = LEFT_EYE_INDICES.mapNotNull { pointMap[it] }
        val rightEye = RIGHT_EYE_INDICES.mapNotNull { pointMap[it] }
        if (leftEye.size < 5 || rightEye.size < 5) return 0f

        var score = 0f

        // Eye area comparison
        val leftArea = computeContourArea(leftEye)
        val rightArea = computeContourArea(rightEye)

        if (leftArea > 0 && rightArea > 0) {
            val ratio = minOf(leftArea, rightArea) / maxOf(leftArea, rightArea)
            if (ratio < 0.5f) {
                score += 0.3f
                findings.add("Mesh: Eye size mismatch (ratio=${String.format("%.2f", ratio)})")
            }
        }

        // Eye depth profiles
        val leftZMean = leftEye.map { it.z }.average()
        val rightZMean = rightEye.map { it.z }.average()
        if (abs(leftZMean - rightZMean) > 1.5f) {
            score += 0.25f
            findings.add("Mesh: Eye depth inconsistency")
        }

        // Eye contour smoothness comparison
        val leftSmooth = computeContourSmoothness(leftEye)
        val rightSmooth = computeContourSmoothness(rightEye)
        if (abs(leftSmooth - rightSmooth) > 0.2f) {
            score += 0.2f
            findings.add("Mesh: Asymmetric eye contour smoothness")
        }

        return score.coerceIn(0f, 1f)
    }

    // ---- Helpers ----

    private fun triangleArea(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        return abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / 2f)
    }

    private fun computeLocalNoise(points: List<FaceMeshPoint>): Float {
        if (points.size < 3) return 0f
        var total = 0f; var count = 0
        val step = maxOf(1, points.size / 30)
        for (i in 1 until points.size - 1 step step) {
            val prev = points[i - 1].position
            val curr = points[i].position
            val next = points[minOf(i + 1, points.size - 1)].position
            val ddx = prev.x - 2 * curr.x + next.x
            val ddy = prev.y - 2 * curr.y + next.y
            val ddz = prev.z - 2 * curr.z + next.z
            total += sqrt(ddx.pow(2) + ddy.pow(2) + ddz.pow(2))
            count++
        }
        return if (count > 0) total / count else 0f
    }

    private fun computeContourSmoothness(points: List<PointF3D>): Float {
        if (points.size < 3) return 0f
        var total = 0f; var count = 0
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]; val curr = points[i]; val next = points[i + 1]
            val a1 = atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble())
            val a2 = atan2((next.y - curr.y).toDouble(), (next.x - curr.x).toDouble())
            var diff = abs(a2 - a1)
            if (diff > Math.PI) diff = 2 * Math.PI - diff
            total += diff.toFloat()
            count++
        }
        return if (count > 0) (total / count / Math.PI.toFloat()).coerceIn(0f, 1f) else 0f
    }

    private fun computeContourArea(points: List<PointF3D>): Float {
        if (points.size < 3) return 0f
        var area = 0f
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area) / 2f
    }

    private fun emptyResult(elapsed: Long) = FaceMeshResult(
        false, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, emptyList(), elapsed
    )

    fun close() {
        try { meshDetector?.close() } catch (_: Exception) {}
    }
}
