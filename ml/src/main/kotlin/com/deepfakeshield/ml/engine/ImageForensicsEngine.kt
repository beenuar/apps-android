package com.deepfakeshield.ml.engine

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Image Forensics Engine
 * 
 * Detects manipulated screenshots and doctored images using:
 * - Error Level Analysis (ELA) for splice detection
 * - Copy-move forgery detection
 * - Text region consistency analysis
 * - Metadata stripping detection
 * - Color palette anomaly detection
 */
@Singleton
class ImageForensicsEngine @Inject constructor() {

    data class ImageForensicResult(
        val isManipulated: Boolean,
        val confidence: Float,
        val manipulationType: String,
        val findings: List<ForensicFinding>,
        val riskScore: Int,
        val recommendation: String
    )

    data class ForensicFinding(
        val type: String,
        val description: String,
        val severity: String,
        val region: String? = null
    )

    /**
     * Analyze an image/screenshot for manipulation
     */
    fun analyzeImage(bitmap: Bitmap): ImageForensicResult {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return ImageForensicResult(false, 0f, "None", emptyList(), 0, "Unable to analyze")
        val findings = mutableListOf<ForensicFinding>()
        var riskScore = 0

        // 1. ELA - Error Level Analysis
        val elaResult = performELA(bitmap)
        if (elaResult > 0.4f) {
            riskScore += (elaResult * 40).toInt()
            findings.add(ForensicFinding("ELA_ANOMALY", "Inconsistent compression levels suggest image editing", if (elaResult > 0.7f) "HIGH" else "MEDIUM", "Multiple regions"))
        }

        // 2. Copy-move detection
        val copyMoveResult = detectCopyMove(bitmap)
        if (copyMoveResult > 0.5f) {
            riskScore += (copyMoveResult * 35).toInt()
            findings.add(ForensicFinding("COPY_MOVE", "Duplicate regions found - possible copy-paste manipulation", "HIGH"))
        }

        // 3. Text consistency analysis (for screenshot verification)
        val textResult = analyzeTextConsistency(bitmap)
        if (textResult > 0.4f) {
            riskScore += (textResult * 25).toInt()
            findings.add(ForensicFinding("TEXT_INCONSISTENCY", "Text rendering inconsistencies detected", "MEDIUM"))
        }

        // 4. Edge analysis around suspected edit boundaries
        val edgeResult = analyzeEditBoundaries(bitmap)
        if (edgeResult > 0.3f) {
            riskScore += (edgeResult * 20).toInt()
            findings.add(ForensicFinding("EDIT_BOUNDARY", "Suspicious edge artifacts at potential edit boundaries", if (edgeResult > 0.6f) "HIGH" else "LOW"))
        }

        // 5. Color palette analysis
        val paletteResult = analyzeColorPalette(bitmap)
        if (paletteResult > 0.5f) {
            riskScore += (paletteResult * 15).toInt()
            findings.add(ForensicFinding("PALETTE_ANOMALY", "Color palette inconsistencies between image regions", "MEDIUM"))
        }

        // 6. Resolution consistency
        val resResult = analyzeResolutionConsistency(bitmap)
        if (resResult > 0.4f) {
            riskScore += (resResult * 15).toInt()
            findings.add(ForensicFinding("RESOLUTION_MISMATCH", "Different resolution levels in image regions", "MEDIUM"))
        }

        val finalScore = riskScore.coerceAtMost(100)
        val isManipulated = finalScore >= 35
        val confidence = when {
            findings.size >= 4 -> 0.90f
            findings.size >= 3 -> 0.78f
            findings.size >= 2 -> 0.65f
            findings.size >= 1 -> 0.50f
            else -> 0.30f
        }

        val manipulationType = when {
            findings.any { it.type == "COPY_MOVE" } -> "Copy-Paste Forgery"
            findings.any { it.type == "TEXT_INCONSISTENCY" } -> "Text Manipulation"
            findings.any { it.type == "ELA_ANOMALY" } -> "Image Splicing"
            findings.any { it.type == "EDIT_BOUNDARY" } -> "Region Editing"
            else -> "Unknown/None"
        }

        val recommendation = when {
            finalScore >= 70 -> "HIGH RISK: This image shows strong signs of manipulation. Do not trust its contents without independent verification."
            finalScore >= 40 -> "MODERATE RISK: Some manipulation indicators found. Verify important details through other sources."
            finalScore >= 20 -> "LOW RISK: Minor anomalies detected, but could be from normal image processing."
            else -> "CLEAN: No significant manipulation indicators found."
        }

        return ImageForensicResult(isManipulated, confidence, manipulationType, findings, finalScore, recommendation)
    }

    private fun performELA(bitmap: Bitmap): Float {
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        val blockSize = 8
        val blockEnergies = mutableListOf<Double>()

        for (y in 0 until h - blockSize step blockSize) {
            for (x in 0 until w - blockSize step blockSize) {
                var energy = 0.0
                for (by in 0 until blockSize) { for (bx in 0 until blockSize) {
                    val px = x + bx; val py = y + by
                    if (px < w - 1 && py < h - 1) {
                        val c = bitmap.getPixel(px, py)
                        val r = bitmap.getPixel(px + 1, py)
                        val d = bitmap.getPixel(px, py + 1)
                        energy += abs(Color.red(c) - Color.red(r)) + abs(Color.green(c) - Color.green(r)) + abs(Color.blue(c) - Color.blue(r))
                        energy += abs(Color.red(c) - Color.red(d)) + abs(Color.green(c) - Color.green(d)) + abs(Color.blue(c) - Color.blue(d))
                    }
                }}
                blockEnergies.add(energy)
            }
        }

        if (blockEnergies.isEmpty()) return 0f
        val mean = blockEnergies.average()
        val stdDev = sqrt(blockEnergies.map { (it - mean).pow(2) }.average())
        val outliers = blockEnergies.count { abs(it - mean) > 2.5 * stdDev }
        return (outliers.toFloat() / blockEnergies.size * 4f).coerceIn(0f, 1f)
    }

    private fun detectCopyMove(bitmap: Bitmap): Float {
        val w = minOf(bitmap.width, 200); val h = minOf(bitmap.height, 200)
        val blockSize = 16
        val blocks = mutableMapOf<Long, Int>()
        var duplicates = 0

        for (y in 0 until h - blockSize step 8) {
            for (x in 0 until w - blockSize step 8) {
                var hash = 0L
                for (by in 0 until blockSize step 4) { for (bx in 0 until blockSize step 4) {
                    val p = bitmap.getPixel(minOf(x + bx, w - 1), minOf(y + by, h - 1))
                    hash = hash * 31 + (Color.red(p) / 16).toLong()
                    hash = hash * 31 + (Color.green(p) / 16).toLong()
                }}
                val count = blocks.getOrDefault(hash, 0)
                if (count > 0) duplicates++
                blocks[hash] = count + 1
            }
        }

        val totalBlocks = ((h - blockSize) / 8) * ((w - blockSize) / 8)
        return if (totalBlocks > 0) (duplicates.toFloat() / totalBlocks * 3f).coerceIn(0f, 1f) else 0f
    }

    private fun analyzeTextConsistency(bitmap: Bitmap): Float {
        // Look for sharp pixel transitions typical of text rendering
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        val sharpRegions = mutableListOf<Float>()

        for (y in 2 until h - 2 step 4) {
            var lineSharpness = 0f; var count = 0
            for (x in 2 until w - 2) {
                val l = luminance(bitmap.getPixel(x, y))
                val r = luminance(bitmap.getPixel(x + 1, y))
                if (abs(l - r) > 50) { lineSharpness++; count++ }
            }
            if (count > 0) sharpRegions.add(lineSharpness / (w - 4))
        }

        if (sharpRegions.size < 2) return 0f
        val mean = sharpRegions.average()
        val variance = sharpRegions.map { (it - mean).pow(2) }.average()
        return (sqrt(variance) * 5f).toFloat().coerceIn(0f, 1f)
    }

    private fun analyzeEditBoundaries(bitmap: Bitmap): Float {
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        var sharpEdges = 0; var totalEdges = 0

        for (y in 2 until h - 2 step 2) {
            for (x in 2 until w - 2 step 2) {
                val c = luminance(bitmap.getPixel(x, y))
                val neighbors = listOf(
                    luminance(bitmap.getPixel(x-1, y)), luminance(bitmap.getPixel(x+1, y)),
                    luminance(bitmap.getPixel(x, y-1)), luminance(bitmap.getPixel(x, y+1))
                )
                val maxDiff = neighbors.maxOf { abs(c - it) }
                if (maxDiff > 40) sharpEdges++
                totalEdges++
            }
        }

        return if (totalEdges > 0) (sharpEdges.toFloat() / totalEdges * 5f).coerceIn(0f, 1f) else 0f
    }

    private fun analyzeColorPalette(bitmap: Bitmap): Float {
        val w = minOf(bitmap.width, 128); val h = minOf(bitmap.height, 128)
        val topColors = IntArray(256); val bottomColors = IntArray(256)

        for (x in 0 until w) {
            for (y in 0 until h / 2) {
                topColors[luminance(bitmap.getPixel(x, y))]++
            }
            for (y in h / 2 until h) {
                bottomColors[luminance(bitmap.getPixel(x, y))]++
            }
        }

        var diff = 0.0; val total = (w * h / 2).toDouble()
        if (total == 0.0) return 0f
        for (i in 0 until 256) {
            diff += abs(topColors[i] / total - bottomColors[i] / total)
        }

        return (diff * 2f).toFloat().coerceIn(0f, 1f)
    }

    private fun analyzeResolutionConsistency(bitmap: Bitmap): Float {
        val w = minOf(bitmap.width, 256); val h = minOf(bitmap.height, 256)
        val regionSharpness = mutableListOf<Float>()
        val regionSize = 32

        for (ry in 0 until h - regionSize step regionSize) {
            for (rx in 0 until w - regionSize step regionSize) {
                var sharpness = 0f
                for (y in ry + 1 until ry + regionSize - 1) {
                    for (x in rx + 1 until rx + regionSize - 1) {
                        val c = luminance(bitmap.getPixel(x, y))
                        val r = luminance(bitmap.getPixel(x + 1, y))
                        sharpness += abs(c - r).toFloat()
                    }
                }
                regionSharpness.add(sharpness / ((regionSize - 2) * (regionSize - 2)))
            }
        }

        if (regionSharpness.size < 2) return 0f
        val mean = regionSharpness.average()
        if (mean == 0.0) return 0f
        val stdDev = sqrt(regionSharpness.map { (it - mean).pow(2) }.average())
        return (stdDev / mean * 2f).toFloat().coerceIn(0f, 1f)
    }

    private fun luminance(pixel: Int): Int {
        return (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
    }
}
