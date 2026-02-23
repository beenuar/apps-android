package com.deepfakeshield.core.intelligence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * ADAPTIVE LEARNING ENGINE
 * 
 * Learns from user feedback to improve detection accuracy
 * - Pattern weight adjustment
 * - False positive reduction
 * - New pattern discovery
 * - Continuous improvement
 */

data class PatternWeight(
    val patternId: String,
    val pattern: String,
    val weight: Double,              // 0.0-1.0 importance
    val accuracy: Double,            // How often it's correct
    val falsePositiveRate: Double,  // How often it's wrong
    val truePositiveCount: Int,
    val falsePositiveCount: Int,
    val lastUpdated: Long
)

data class UserFeedback(
    val contentHash: String,
    val detectedThreat: Boolean,
    val userConfirmedThreat: Boolean,
    val detectionScore: Int,
    val matchedPatterns: List<String>,
    val timestamp: Long
)

data class LearnedPattern(
    val pattern: String,
    val confidence: Double,
    val occurrences: Int,
    val threatType: String,
    val discoveredAt: Long
)

data class LearningStats(
    val totalFeedback: Int,
    val accuracyRate: Double,
    val falsePositiveRate: Double,
    val newPatternsDiscovered: Int,
    val improvedPatterns: Int
)

@Singleton
class AdaptiveLearningEngine @Inject constructor() {
    
    // Pattern weights (starts with defaults, adjusts based on feedback) - thread-safe
    private val patternWeights = java.util.concurrent.ConcurrentHashMap<String, PatternWeight>()
    private val userFeedbackHistory = java.util.Collections.synchronizedList(mutableListOf<UserFeedback>())
    private val discoveredPatterns = java.util.Collections.synchronizedList(mutableListOf<LearnedPattern>())
    
    init {
        // Initialize with default patterns and weights
        initializeDefaultPatterns()
    }
    
    /**
     * Record user feedback on a detection
     */
    suspend fun recordFeedback(
        contentHash: String,
        detectedThreat: Boolean,
        userConfirmedThreat: Boolean,
        detectionScore: Int,
        matchedPatterns: List<String>
    ) {
        val feedback = UserFeedback(
            contentHash = contentHash,
            detectedThreat = detectedThreat,
            userConfirmedThreat = userConfirmedThreat,
            detectionScore = detectionScore,
            matchedPatterns = matchedPatterns,
            timestamp = System.currentTimeMillis()
        )
        
        userFeedbackHistory.add(feedback)
        synchronized(userFeedbackHistory) {
            while (userFeedbackHistory.size > 10000) {
                userFeedbackHistory.removeAt(0)
            }
        }
        
        // Update pattern weights based on feedback
        updatePatternWeights(feedback)
    }
    
    /**
     * Get adjusted weight for a pattern
     */
    fun getPatternWeight(patternId: String): Double {
        return patternWeights[patternId]?.weight ?: 1.0
    }
    
    /**
     * Get all pattern weights
     */
    fun getAllPatternWeights(): Map<String, Double> {
        return patternWeights.mapValues { it.value.weight }
    }
    
    /**
     * Discover new patterns from content
     */
    suspend fun discoverPatterns(
        content: String,
        isThreat: Boolean
    ): List<LearnedPattern> {
        if (!isThreat) return emptyList()
        
        val discovered = mutableListOf<LearnedPattern>()
        
        // Extract potential patterns (n-grams)
        val words = content.lowercase().split(Regex("\\s+"))
        
        // Look for 2-3 word phrases that appear frequently in threats
        for (i in 0 until words.size - 1) {
            val phrase = "${words[i]} ${words[i + 1]}"
            if (phrase.length >= 6 && !isCommonPhrase(phrase)) {
                val existingPattern = discoveredPatterns.find { it.pattern == phrase }
                if (existingPattern != null) {
                    // Increase confidence
                    val updated = existingPattern.copy(
                        occurrences = existingPattern.occurrences + 1,
                        confidence = calculatePatternConfidence(existingPattern.occurrences + 1)
                    )
                    synchronized(discoveredPatterns) {
                        val idx = discoveredPatterns.indexOf(existingPattern)
                        if (idx >= 0) {
                            discoveredPatterns[idx] = updated
                        }
                    }
                } else {
                    // New pattern
                    val newPattern = LearnedPattern(
                        pattern = phrase,
                        confidence = 0.3,
                        occurrences = 1,
                        threatType = "learned_pattern",
                        discoveredAt = System.currentTimeMillis()
                    )
                    discoveredPatterns.add(newPattern)
                    synchronized(discoveredPatterns) {
                        while (discoveredPatterns.size > 5000) {
                            discoveredPatterns.removeAt(0)
                        }
                    }
                    discovered.add(newPattern)
                }
            }
        }
        
        return discovered
    }
    
    /**
     * Get high-confidence learned patterns
     */
    fun getLearnedPatterns(minConfidence: Double = 0.7): List<LearnedPattern> {
        return discoveredPatterns.filter { it.confidence >= minConfidence }
    }
    
    /**
     * Calculate adjusted risk score using learned weights
     */
    fun calculateAdjustedScore(
        baseScore: Int,
        matchedPatterns: List<String>
    ): Int {
        if (matchedPatterns.isEmpty()) return baseScore
        
        // Apply learned weights
        val weightedScore = matchedPatterns.sumOf { pattern ->
            val weight = getPatternWeight(pattern)
            (baseScore.toDouble() / matchedPatterns.size) * weight
        }
        
        return weightedScore.toInt().coerceIn(0, 100)
    }
    
    /**
     * Get learning statistics
     */
    fun getLearningStats(): LearningStats {
        val snapshot = synchronized(userFeedbackHistory) { userFeedbackHistory.toList() }
        val totalFeedback = snapshot.size
        if (totalFeedback == 0) {
            return LearningStats(
                totalFeedback = 0,
                accuracyRate = 0.0,
                falsePositiveRate = 0.0,
                newPatternsDiscovered = 0,
                improvedPatterns = 0
            )
        }
        
        val correct = snapshot.count { 
            it.detectedThreat == it.userConfirmedThreat 
        }
        val falsePositives = snapshot.count { 
            it.detectedThreat && !it.userConfirmedThreat 
        }
        
        val improvedPatterns = patternWeights.values.count { 
            it.accuracy > 0.8 
        }
        
        return LearningStats(
            totalFeedback = totalFeedback,
            accuracyRate = correct.toDouble() / totalFeedback,
            falsePositiveRate = falsePositives.toDouble() / totalFeedback,
            newPatternsDiscovered = discoveredPatterns.size,
            improvedPatterns = improvedPatterns
        )
    }
    
    /**
     * Get patterns that need improvement (high false positive rate)
     */
    fun getProblematicPatterns(): List<PatternWeight> {
        return patternWeights.values
            .filter { it.falsePositiveRate > 0.3 && it.truePositiveCount + it.falsePositiveCount > 5 }
            .sortedByDescending { it.falsePositiveRate }
    }
    
    /**
     * Reset learning for a pattern (if consistently wrong)
     */
    suspend fun resetPattern(patternId: String) {
        val pattern = patternWeights[patternId]
        if (pattern != null) {
            patternWeights[patternId] = pattern.copy(
                weight = 1.0,
                accuracy = 0.5,
                falsePositiveRate = 0.0,
                truePositiveCount = 0,
                falsePositiveCount = 0,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    // === PRIVATE METHODS ===
    
    private fun updatePatternWeights(feedback: UserFeedback) {
        feedback.matchedPatterns.forEach { patternId ->
            val current = patternWeights[patternId] ?: return@forEach
            
            val isCorrect = feedback.detectedThreat == feedback.userConfirmedThreat
            val isFalsePositive = feedback.detectedThreat && !feedback.userConfirmedThreat
            
            val newTruePositives = if (isCorrect && feedback.userConfirmedThreat) {
                current.truePositiveCount + 1
            } else {
                current.truePositiveCount
            }
            
            val newFalsePositives = if (isFalsePositive) {
                current.falsePositiveCount + 1
            } else {
                current.falsePositiveCount
            }
            
            val total = newTruePositives + newFalsePositives
            val newAccuracy = if (total > 0) {
                newTruePositives.toDouble() / total
            } else {
                0.5
            }
            
            val newFPR = if (total > 0) {
                newFalsePositives.toDouble() / total
            } else {
                0.0
            }
            
            // Adjust weight using sigmoid function
            val newWeight = calculateOptimalWeight(newAccuracy, newFPR)
            
            patternWeights[patternId] = current.copy(
                weight = newWeight,
                accuracy = newAccuracy,
                falsePositiveRate = newFPR,
                truePositiveCount = newTruePositives,
                falsePositiveCount = newFalsePositives,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    private fun calculateOptimalWeight(accuracy: Double, falsePositiveRate: Double): Double {
        // Weight should be high when accuracy is high and FPR is low
        val score = accuracy - (falsePositiveRate * 2) // Penalize false positives heavily
        
        // Sigmoid function to map to 0.1-1.5 range
        val sigmoid = 1.0 / (1.0 + exp(-score * 5))
        return 0.1 + (sigmoid * 1.4) // Range: 0.1 to 1.5
    }
    
    private fun calculatePatternConfidence(occurrences: Int): Double {
        // Confidence increases with occurrences but plateaus
        return 1.0 - exp(-occurrences / 10.0)
    }
    
    private fun isCommonPhrase(phrase: String): Boolean {
        val commonPhrases = setOf(
            "the the", "and and", "is is", "of the", "to the",
            "in the", "for the", "on the", "at the", "from the"
        )
        return commonPhrases.contains(phrase)
    }
    
    private fun initializeDefaultPatterns() {
        // Initialize with common scam patterns
        val defaultPatterns = listOf(
            "urgent" to 1.2,
            "account suspended" to 1.5,
            "click here" to 1.3,
            "verify now" to 1.4,
            "winner" to 1.1,
            "congratulations" to 1.1,
            "tax refund" to 1.3,
            "bank alert" to 1.4,
            "otp" to 1.2,
            "password reset" to 1.0 // Less suspicious (could be legitimate)
        )
        
        defaultPatterns.forEach { (pattern, weight) ->
            patternWeights[pattern] = PatternWeight(
                patternId = pattern,
                pattern = pattern,
                weight = weight,
                accuracy = 0.7, // Start with moderate confidence
                falsePositiveRate = 0.1,
                truePositiveCount = 0,
                falsePositiveCount = 0,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}

/**
 * PATTERN EVOLUTION TRACKER
 * Tracks how patterns evolve over time
 */
@Singleton
class PatternEvolutionTracker @Inject constructor() {
    
    data class PatternEvolution(
        val patternId: String,
        val weightHistory: List<Pair<Long, Double>>, // timestamp to weight
        val accuracyHistory: List<Pair<Long, Double>>
    )
    
    private val evolutionHistory = java.util.concurrent.ConcurrentHashMap<String, PatternEvolution>()
    
    fun recordPatternState(
        patternId: String,
        weight: Double,
        accuracy: Double,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val current = evolutionHistory[patternId]
        
        if (current == null) {
            evolutionHistory[patternId] = PatternEvolution(
                patternId = patternId,
                weightHistory = listOf(timestamp to weight),
                accuracyHistory = listOf(timestamp to accuracy)
            )
        } else {
            evolutionHistory[patternId] = current.copy(
                weightHistory = current.weightHistory + (timestamp to weight),
                accuracyHistory = current.accuracyHistory + (timestamp to accuracy)
            )
        }
    }
    
    fun getPatternTrend(patternId: String): String {
        val evolution = evolutionHistory[patternId] ?: return "unknown"
        if (evolution.weightHistory.size < 2) return "insufficient_data"
        
        val recent = evolution.weightHistory.takeLast(10)
        val first = recent.first().second
        val last = recent.last().second
        
        return when {
            last > first * 1.1 -> "improving"
            last < first * 0.9 -> "declining"
            else -> "stable"
        }
    }
}
