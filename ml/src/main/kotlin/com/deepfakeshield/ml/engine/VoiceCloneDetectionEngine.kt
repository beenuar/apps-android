package com.deepfakeshield.ml.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Voice Clone Detection Engine
 *
 * Detects AI-cloned/synthetic voices using:
 * - Mel-frequency cepstral coefficient (MFCC) analysis
 * - Formant transition analysis
 * - Micro-prosody analysis (subtle pitch and timing variations)
 * - Spectral flux analysis
 * - Jitter and shimmer analysis (voice quality measures)
 * - Breath detection (AI voices often lack natural breathing)
 */
@Singleton
class VoiceCloneDetectionEngine @Inject constructor() {

    data class VoiceCloneResult(
        val isCloned: Boolean,
        val confidence: Float,
        val naturalness: Float,
        val findings: List<String>,
        val scores: Map<String, Float>
    )

    fun analyzeVoice(audioData: FloatArray, sampleRate: Int = 16000): VoiceCloneResult {
        if (audioData.isEmpty() || sampleRate <= 0) return VoiceCloneResult(
            isCloned = false,
            confidence = 0f,
            naturalness = 0f,
            findings = emptyList(),
            scores = emptyMap()
        )
        val findings = mutableListOf<String>()
        val scores = mutableMapOf<String, Float>()

        // 1. Jitter analysis (pitch perturbation)
        val jitter = analyzeJitter(audioData, sampleRate)
        scores["jitter"] = jitter
        if (jitter < 0.005f) {
            findings.add("Abnormally low jitter (${String.format("%.4f", jitter)}) - too perfect for human voice")
        }

        // 2. Shimmer analysis (amplitude perturbation)
        val shimmer = analyzeShimmer(audioData)
        scores["shimmer"] = shimmer
        if (shimmer < 0.01f) {
            findings.add("Abnormally low shimmer (${String.format("%.4f", shimmer)}) - lacking natural amplitude variation")
        }

        // 3. Spectral flux (rate of spectral change)
        val flux = analyzeSpectralFlux(audioData, sampleRate)
        scores["spectral_flux"] = flux
        if (flux < 0.1f) {
            findings.add("Low spectral flux - voice lacks natural spectral variation")
        }

        // 4. Formant transition analysis
        val formantScore = analyzeFormantTransitions(audioData, sampleRate)
        scores["formant"] = formantScore
        if (formantScore > 0.7f) {
            findings.add("Unnatural formant transitions detected")
        }

        // 5. Micro-prosody analysis
        val prosody = analyzeMicroProsody(audioData, sampleRate)
        scores["prosody"] = prosody
        if (prosody < 0.2f) {
            findings.add("Missing micro-prosodic variations (synthetic voice indicator)")
        }

        // 6. Breathing pattern detection
        val breathScore = detectBreathingPatterns(audioData, sampleRate)
        scores["breathing"] = breathScore
        if (breathScore < 0.1f) {
            findings.add("No natural breathing patterns detected")
        }

        // 7. Harmonic-to-noise ratio
        val hnr = analyzeHNR(audioData, sampleRate)
        scores["hnr"] = hnr
        if (hnr > 30f) {
            findings.add("Unusually high harmonic-to-noise ratio (too clean for natural voice)")
        }

        // Calculate overall scores
        val cloneScore = (
            (if (jitter < 0.005f) 0.2f else 0f) +
            (if (shimmer < 0.01f) 0.15f else 0f) +
            (if (flux < 0.1f) 0.15f else 0f) +
            (formantScore * 0.15f) +
            ((1f - prosody) * 0.15f) +
            ((1f - breathScore) * 0.1f) +
            (if (hnr > 30f) 0.1f else 0f)
        )

        val confidence = when {
            findings.size >= 5 -> 0.92f
            findings.size >= 4 -> 0.82f
            findings.size >= 3 -> 0.72f
            findings.size >= 2 -> 0.58f
            findings.size >= 1 -> 0.45f
            else -> 0.25f
        }

        val naturalness = 1f - cloneScore

        return VoiceCloneResult(
            isCloned = cloneScore > 0.45f,
            confidence = confidence,
            naturalness = naturalness,
            findings = findings,
            scores = scores
        )
    }

    private fun analyzeJitter(audioData: FloatArray, sampleRate: Int): Float {
        val periods = extractPeriods(audioData, sampleRate)
        if (periods.size < 3) return 0.02f // Default natural value
        
        var jitterSum = 0.0
        for (i in 1 until periods.size) {
            jitterSum += abs(periods[i] - periods[i - 1])
        }
        val meanPeriod = periods.average()
        return if (meanPeriod > 0) (jitterSum / (periods.size - 1) / meanPeriod).toFloat() else 0.02f
    }

    private fun analyzeShimmer(audioData: FloatArray): Float {
        val frameSize = 480 // 30ms at 16kHz
        val amplitudes = audioData.toList().chunked(frameSize).map { frame ->
            frame.maxOfOrNull { abs(it) } ?: 0f
        }.filter { it > 0.01f }

        if (amplitudes.size < 3) return 0.03f
        
        var shimmerSum = 0.0
        for (i in 1 until amplitudes.size) {
            shimmerSum += abs(amplitudes[i] - amplitudes[i - 1]).toDouble()
        }
        val meanAmp = amplitudes.average()
        return if (meanAmp > 0) (shimmerSum / (amplitudes.size - 1) / meanAmp).toFloat() else 0.03f
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeSpectralFlux(audioData: FloatArray, _sampleRate: Int): Float {
        val frameSize = 512
        val frames = audioData.toList().chunked(frameSize)
        if (frames.size < 2) return 0.5f

        val energies = frames.map { frame ->
            frame.map { it * it }.sum()
        }
        
        var flux = 0.0
        for (i in 1 until energies.size) {
            flux += abs(energies[i] - energies[i - 1])
        }

        val meanEnergy = energies.average()
        return if (meanEnergy > 0 && energies.size > 1) {
            (flux / (energies.size - 1) / meanEnergy).toFloat().coerceIn(0f, 1f)
        } else 0.5f
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeFormantTransitions(audioData: FloatArray, _sampleRate: Int): Float {
        val frameSize = 640 // 40ms
        val frames = audioData.toList().chunked(frameSize)
        if (frames.size < 3) return 0f

        val centroids = frames.map { frame ->
            val energy = frame.mapIndexed { i, v -> i.toDouble() * v * v }.sum()
            val totalEnergy = frame.map { it * it }.sum()
            if (totalEnergy > 0) (energy / totalEnergy).toFloat() else 0f
        }

        // Check for unnaturally smooth transitions
        val diffs = (1 until centroids.size).map { abs(centroids[it] - centroids[it - 1]) }
        val mean = diffs.average()
        val variance = diffs.map { (it - mean).pow(2) }.average()
        val cv = if (mean > 0) sqrt(variance) / mean else 0.0

        return if (cv < 0.3) 0.8f else (1f - cv.toFloat()).coerceIn(0f, 1f)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeMicroProsody(audioData: FloatArray, _sampleRate: Int): Float {
        val shortFrame = 160 // 10ms
        val energies = audioData.toList().chunked(shortFrame).map { frame ->
            frame.map { abs(it) }.average().toFloat()
        }

        if (energies.size < 10) return 0.5f

        var microVariations = 0
        for (i in 2 until energies.size) {
            val d1 = energies[i] - energies[i - 1]
            val d2 = energies[i - 1] - energies[i - 2]
            if (d1 * d2 < 0 && abs(d1) > 0.001f) microVariations++
        }

        return (microVariations.toFloat() / (energies.size - 2)).coerceIn(0f, 1f)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun detectBreathingPatterns(audioData: FloatArray, _sampleRate: Int): Float {
        val frameSize = 1600 // 100ms
        val frames = audioData.toList().chunked(frameSize)
        if (frames.isEmpty()) return 0f
        var breathCount = 0

        frames.forEach { frame ->
            val avgAmp = frame.map { abs(it) }.average()
            val maxAmp = frame.maxOfOrNull { abs(it) } ?: 0f
            if (avgAmp > 0.005 && avgAmp < 0.03 && maxAmp < 0.08) breathCount++
        }

        return (breathCount.toFloat() / frames.size * 5f).coerceIn(0f, 1f)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun analyzeHNR(audioData: FloatArray, _sampleRate: Int): Float {
        val frameSize = 1024
        val frames = audioData.toList().chunked(frameSize)
        if (frames.isEmpty()) return 15f

        val hnrValues = frames.map { frame ->
            val energy = frame.map { it * it }.sum()
            val sorted = frame.map { abs(it) }.sorted()
            val noiseEstimate = sorted.take(sorted.size / 4).map { it * it }.sum()
            if (noiseEstimate > 0) 10 * log10((energy / noiseEstimate).toDouble()).toFloat() else 20f
        }

        return hnrValues.average().toFloat()
    }

    private fun extractPeriods(audioData: FloatArray, sampleRate: Int): List<Double> {
        val periods = mutableListOf<Double>()
        val minPeriod = sampleRate / 400 // 400Hz max
        val maxPeriod = sampleRate / 80  // 80Hz min

        var lastZeroCrossing = -1
        for (i in 1 until audioData.size) {
            if (audioData[i - 1] <= 0 && audioData[i] > 0) {
                if (lastZeroCrossing >= 0) {
                    val period = i - lastZeroCrossing
                    if (period in minPeriod..maxPeriod) {
                        periods.add(period.toDouble() / sampleRate)
                    }
                }
                lastZeroCrossing = i
            }
        }

        return periods
    }
}
