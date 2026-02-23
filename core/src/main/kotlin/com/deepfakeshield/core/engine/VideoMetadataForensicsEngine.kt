package com.deepfakeshield.core.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video Metadata Forensics Engine
 * 
 * Analyzes video file metadata for deepfake indicators:
 * - Encoding inconsistencies
 * - Software signatures (AI generation tools)
 * - Temporal metadata anomalies
 * - Frame rate irregularities
 * - Compression artifacts
 */
@Singleton
class VideoMetadataForensicsEngine @Inject constructor() {

    data class MetadataForensicResult(
        val isManipulated: Boolean,
        val confidence: Float,
        val findings: List<ForensicFinding>,
        val riskScore: Int,
        val summary: String
    )
    
    data class ForensicFinding(
        val category: String,
        val finding: String,
        val severity: String,
        val details: String
    )

    /**
     * Analyze video metadata for manipulation signs
     */
    @Suppress("UNUSED_PARAMETER")
    fun analyzeMetadata(
        duration: Long,
        width: Int,
        height: Int,
        frameRate: Float,
        bitrate: Long,
        _mimeType: String?,
        encoder: String?,
        creationTime: Long?,
        modificationTime: Long?,
        _location: String?,
        _rotation: Int,
        hasAudio: Boolean
    ): MetadataForensicResult {
        val findings = mutableListOf<ForensicFinding>()
        var riskScore = 0
        
        // 1. Check for AI generation tool signatures
        encoder?.let { enc ->
            val aiEncoders = listOf("deepfake", "faceswap", "faceapp", "reface", 
                "deepfacelab", "synthesia", "runway", "d-id", "heygen")
            val foundAiTool = aiEncoders.find { enc.lowercase().contains(it) }
            if (foundAiTool != null) {
                riskScore += 50
                findings.add(ForensicFinding(
                    "AI_TOOL_SIGNATURE", 
                    "AI generation tool detected",
                    "CRITICAL",
                    "Encoder signature matches known AI tool: $foundAiTool"
                ))
            }
            
            // Check for unusual encoders
            val normalEncoders = listOf("libx264", "libx265", "h264", "hevc", "avc", 
                "apple", "google", "samsung", "qualcomm", "mediatek", "exynos")
            if (normalEncoders.none { enc.lowercase().contains(it) } && enc.isNotEmpty()) {
                riskScore += 15
                findings.add(ForensicFinding(
                    "UNUSUAL_ENCODER",
                    "Non-standard encoder detected",
                    "LOW",
                    "Encoder: $enc (not a common mobile/desktop encoder)"
                ))
            }
        }
        
        // 2. Frame rate analysis
        if (frameRate > 0f) {
            val standardRates = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
            val isStandard = standardRates.any { kotlin.math.abs(frameRate - it) < 0.5f }
            if (!isStandard) {
                riskScore += 20
                findings.add(ForensicFinding(
                    "UNUSUAL_FRAMERATE",
                    "Non-standard frame rate: ${String.format("%.2f", frameRate)} fps",
                    "MEDIUM",
                    "AI-generated videos often have unusual frame rates"
                ))
            }
        }
        
        // 3. Resolution analysis
        val standardResolutions = listOf(
            320 to 240, 640 to 480, 720 to 480, 854 to 480,
            1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160,
            1080 to 1920, 720 to 1280, 1080 to 1350 // portrait
        )
        val isStandardRes = standardResolutions.any { (w, h) -> 
            (width == w && height == h) || (width == h && height == w)
        }
        if (!isStandardRes && width > 0 && height > 0) {
            riskScore += 10
            findings.add(ForensicFinding(
                "UNUSUAL_RESOLUTION",
                "Non-standard resolution: ${width}x${height}",
                "LOW",
                "Unusual resolution may indicate post-processing or generation"
            ))
        }
        
        // 4. Bitrate analysis (very low or very high for resolution)
        if (bitrate > 0 && width > 0 && height > 0) {
            val pixelCount = width.toLong() * height
            val bitsPerPixel = bitrate.toFloat() / (pixelCount * (if (frameRate > 0) frameRate else 30f))
            
            if (bitsPerPixel < 0.01f) {
                riskScore += 15
                findings.add(ForensicFinding(
                    "LOW_BITRATE",
                    "Unusually low bitrate for resolution",
                    "MEDIUM",
                    "Heavy compression may hide manipulation artifacts"
                ))
            } else if (bitsPerPixel > 2f) {
                riskScore += 5
                findings.add(ForensicFinding(
                    "HIGH_BITRATE",
                    "Unusually high bitrate",
                    "LOW",
                    "Very high quality encoding, possibly re-encoded"
                ))
            }
        }
        
        // 5. Temporal metadata analysis
        if (creationTime != null && modificationTime != null) {
            if (modificationTime < creationTime) {
                riskScore += 25
                findings.add(ForensicFinding(
                    "TEMPORAL_ANOMALY",
                    "Modification time precedes creation time",
                    "HIGH",
                    "This is physically impossible and indicates metadata manipulation"
                ))
            }
            
            val timeDiff = modificationTime - creationTime
            if (timeDiff > 365L * 24 * 60 * 60 * 1000) {
                riskScore += 10
                findings.add(ForensicFinding(
                    "OLD_MODIFICATION",
                    "Large gap between creation and modification",
                    "LOW",
                    "Video was modified long after creation"
                ))
            }
        }
        
        // 6. Audio presence check
        if (!hasAudio && duration > 5000) {
            riskScore += 10
            findings.add(ForensicFinding(
                "NO_AUDIO",
                "Video has no audio track",
                "LOW",
                "Audio may have been removed to hide voice manipulation artifacts"
            ))
        }
        
        // 7. Duration analysis
        if (duration in 1..2000) {
            riskScore += 5
            findings.add(ForensicFinding(
                "VERY_SHORT",
                "Very short video (${duration}ms)",
                "LOW",
                "Very short clips are easier to manipulate"
            ))
        }
        
        val finalScore = riskScore.coerceAtMost(100)
        val isManipulated = finalScore >= 40
        val confidence = when {
            finalScore >= 70 -> 0.9f
            finalScore >= 50 -> 0.75f
            finalScore >= 30 -> 0.6f
            finalScore >= 15 -> 0.45f
            else -> 0.3f
        }
        
        val summary = when {
            finalScore >= 70 -> "HIGH RISK: Multiple manipulation indicators detected. This video is very likely AI-generated or tampered with."
            finalScore >= 40 -> "MODERATE RISK: Some suspicious metadata found. Exercise caution with this video."
            finalScore >= 15 -> "LOW RISK: Minor anomalies detected but no strong manipulation evidence."
            else -> "CLEAN: No significant manipulation indicators found in metadata."
        }
        
        return MetadataForensicResult(
            isManipulated = isManipulated,
            confidence = confidence,
            findings = findings,
            riskScore = finalScore,
            summary = summary
        )
    }
}
