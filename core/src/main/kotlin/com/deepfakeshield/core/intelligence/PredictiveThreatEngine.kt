package com.deepfakeshield.core.intelligence

import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

/**
 * PREDICTIVE THREAT MODELING ENGINE
 * 
 * Anticipates threats before they arrive
 * - Seasonal scam predictions
 * - Event-based forecasting
 * - Personal risk profiling
 * - Early warning system
 */

data class ThreatPrediction(
    val threatType: String,
    val probability: Float,              // 0.0-1.0
    val expectedTimeframe: String,
    val targetAudience: String,
    val reasoning: String,
    val preventionTips: List<String>
)

data class UserRiskProfile(
    val overallRiskScore: Int,          // 0-100
    val vulnerabilities: List<Vulnerability>,
    val protectiveFactors: List<String>,
    val recommendations: List<String>
)

data class Vulnerability(
    val type: String,
    val severity: Int,
    val description: String
)

data class ThreatForecast(
    val period: String,                  // "next_week", "next_month"
    val predictions: List<ThreatPrediction>,
    val alertLevel: AlertLevel
)

enum class AlertLevel {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

@Singleton
class PredictiveThreatEngine @Inject constructor() {
    
    /**
     * Generate threat forecast
     */
    fun generateForecast(period: String = "next_week"): ThreatForecast {
        val predictions = mutableListOf<ThreatPrediction>()
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        
        // Seasonal predictions
        predictions.addAll(getSeasonalPredictions(month))
        
        // Event-based predictions
        predictions.addAll(getEventBasedPredictions(calendar))
        
        // Calculate alert level
        val alertLevel = calculateAlertLevel(predictions)
        
        return ThreatForecast(
            period = period,
            predictions = predictions.sortedByDescending { it.probability },
            alertLevel = alertLevel
        )
    }
    
    /**
     * Generate personal risk profile
     */
    @Suppress("UNUSED_PARAMETER")
    fun generateRiskProfile(
        age: Int?,
        _occupation: String?,
        techSavviness: String?, // "low", "medium", "high"
        hasSharedPersonalInfo: Boolean,
        clickedSuspiciousLinks: Int = 0
    ): UserRiskProfile {
        val vulnerabilities = mutableListOf<Vulnerability>()
        val protectiveFactors = mutableListOf<String>()
        var riskScore = 50 // Base score
        
        // Age-based risk
        if (age != null && age > 60) {
            vulnerabilities.add(
                Vulnerability(
                    type = "age_targeting",
                    severity = 30,
                    description = "Seniors are 3x more likely to be targeted by scams"
                )
            )
            riskScore += 20
        } else if (age != null && age < 25) {
            vulnerabilities.add(
                Vulnerability(
                    type = "tech_overconfidence",
                    severity = 20,
                    description = "Younger users may be overconfident in detecting scams"
                )
            )
            riskScore += 10
        }
        
        // Tech savviness
        when (techSavviness) {
            "low" -> {
                vulnerabilities.add(
                    Vulnerability(
                        type = "low_digital_literacy",
                        severity = 40,
                        description = "May not recognize sophisticated phishing attempts"
                    )
                )
                riskScore += 30
            }
            "high" -> {
                protectiveFactors.add("High digital literacy helps recognize threats")
                riskScore -= 15
            }
        }
        
        // Behavioral risk
        if (hasSharedPersonalInfo) {
            vulnerabilities.add(
                Vulnerability(
                    type = "data_exposure",
                    severity = 35,
                    description = "Personal info online increases targeted scam risk"
                )
            )
            riskScore += 25
        }
        
        if (clickedSuspiciousLinks > 0) {
            vulnerabilities.add(
                Vulnerability(
                    type = "risky_behavior",
                    severity = clickedSuspiciousLinks * 15,
                    description = "History of clicking suspicious links"
                )
            )
            riskScore += clickedSuspiciousLinks * 10
        }
        
        // Protective factors
        if (clickedSuspiciousLinks == 0) {
            protectiveFactors.add("No history of clicking suspicious links")
            riskScore -= 10
        }
        
        // Generate recommendations
        val recommendations = generateRecommendations(vulnerabilities)
        
        return UserRiskProfile(
            overallRiskScore = riskScore.coerceIn(0, 100),
            vulnerabilities = vulnerabilities,
            protectiveFactors = protectiveFactors,
            recommendations = recommendations
        )
    }
    
    /**
     * Predict likelihood of specific threat type
     */
    fun predictThreatLikelihood(
        threatType: String,
        userProfile: UserRiskProfile
    ): Float {
        var probability = 0.3f // Base probability
        
        // Adjust based on vulnerabilities
        userProfile.vulnerabilities.forEach { vuln ->
            probability += vuln.severity / 1000f
        }
        
        // Seasonal adjustment
        val seasonal = getSeasonalMultiplier(threatType)
        probability *= seasonal
        
        return probability.coerceIn(0f, 1f)
    }
    
    /**
     * Get early warning for emerging threats
     */
    fun getEarlyWarning(): List<ThreatPrediction> {
        val warnings = mutableListOf<ThreatPrediction>()
        val calendar = Calendar.getInstance()
        
        // Check for immediate threats
        val upcoming = getUpcomingEvents(calendar)
        upcoming.forEach { event ->
            warnings.add(
                ThreatPrediction(
                    threatType = event.first,
                    probability = 0.8f,
                    expectedTimeframe = event.second,
                    targetAudience = "General public",
                    reasoning = event.third,
                    preventionTips = getPreventionTips(event.first)
                )
            )
        }
        
        return warnings
    }
    
    // === PRIVATE METHODS ===
    
    private fun getSeasonalPredictions(month: Int): List<ThreatPrediction> {
        val predictions = mutableListOf<ThreatPrediction>()
        
        when (month) {
            Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH -> {
                // Tax season scams
                predictions.add(
                    ThreatPrediction(
                        threatType = "tax_scam",
                        probability = 0.85f,
                        expectedTimeframe = "Next 2-3 months",
                        targetAudience = "Taxpayers",
                        reasoning = "Tax season is prime time for IRS impersonation scams",
                        preventionTips = listOf(
                            "IRS never initiates contact via email or text",
                            "File taxes early to prevent fraudulent filings",
                            "Use official IRS.gov website only"
                        )
                    )
                )
            }
            
            Calendar.NOVEMBER, Calendar.DECEMBER -> {
                // Holiday scams
                predictions.add(
                    ThreatPrediction(
                        threatType = "package_delivery_scam",
                        probability = 0.9f,
                        expectedTimeframe = "Next month",
                        targetAudience = "Online shoppers",
                        reasoning = "Holiday shopping season sees 3x increase in fake delivery notifications",
                        preventionTips = listOf(
                            "Track packages through official shipper apps",
                            "Never click links in unexpected delivery texts",
                            "Verify sender address carefully"
                        )
                    )
                )
                
                predictions.add(
                    ThreatPrediction(
                        threatType = "charity_scam",
                        probability = 0.75f,
                        expectedTimeframe = "Holiday season",
                        targetAudience = "Charitable donors",
                        reasoning = "Fake charity scams increase by 50% during holidays",
                        preventionTips = listOf(
                            "Research charities before donating",
                            "Use official charity websites",
                            "Avoid cash or gift card donations"
                        )
                    )
                )
            }
            
            Calendar.JULY, Calendar.AUGUST -> {
                // Back-to-school scams
                predictions.add(
                    ThreatPrediction(
                        threatType = "scholarship_scam",
                        probability = 0.65f,
                        expectedTimeframe = "Next 2 months",
                        targetAudience = "Students and parents",
                        reasoning = "Back-to-school season sees increase in fake scholarship offers",
                        preventionTips = listOf(
                            "Never pay to apply for scholarships",
                            "Verify with school's financial aid office",
                            "Check scholarship database legitimacy"
                        )
                    )
                )
            }
        }
        
        return predictions
    }
    
    private fun getEventBasedPredictions(calendar: Calendar): List<ThreatPrediction> {
        val predictions = mutableListOf<ThreatPrediction>()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        
        // First of month - rent/bill scams
        if (dayOfMonth <= 5) {
            predictions.add(
                ThreatPrediction(
                    threatType = "payment_urgency_scam",
                    probability = 0.7f,
                    expectedTimeframe = "This week",
                    targetAudience = "Renters and bill payers",
                    reasoning = "Beginning of month sees spike in fake payment urgent messages",
                    preventionTips = listOf(
                        "Login directly to pay bills, don't use links",
                        "Verify payment requests through official channels"
                    )
                )
            )
        }
        
        return predictions
    }
    
    private fun getUpcomingEvents(calendar: Calendar): List<Triple<String, String, String>> {
        // Returns: (threat_type, timeframe, reasoning)
        val events = mutableListOf<Triple<String, String, String>>()
        
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        // Check for major holidays
        if (month == Calendar.DECEMBER && day > 15) {
            events.add(
                Triple(
                    "gift_card_scam",
                    "Next 2 weeks",
                    "Holiday season spike in gift card scams"
                )
            )
        }
        
        return events
    }
    
    private fun getSeasonalMultiplier(threatType: String): Float {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        
        return when (threatType) {
            "tax_scam" -> if (month in Calendar.JANUARY..Calendar.APRIL) 2.0f else 0.5f
            "package_delivery_scam" -> if (month in Calendar.NOVEMBER..Calendar.DECEMBER) 2.5f else 1.0f
            "charity_scam" -> if (month == Calendar.DECEMBER) 2.0f else 0.8f
            else -> 1.0f
        }
    }
    
    private fun calculateAlertLevel(predictions: List<ThreatPrediction>): AlertLevel {
        val maxProbability = predictions.maxOfOrNull { it.probability } ?: 0f
        val highProbCount = predictions.count { it.probability > 0.7f }
        
        return when {
            maxProbability > 0.9f || highProbCount >= 3 -> AlertLevel.CRITICAL
            maxProbability > 0.7f || highProbCount >= 2 -> AlertLevel.HIGH
            maxProbability > 0.5f -> AlertLevel.MODERATE
            else -> AlertLevel.LOW
        }
    }
    
    private fun generateRecommendations(vulnerabilities: List<Vulnerability>): List<String> {
        val recommendations = mutableListOf<String>()
        
        vulnerabilities.forEach { vuln ->
            when (vuln.type) {
                "age_targeting" -> {
                    recommendations.add("Enable extra verification for financial transactions")
                    recommendations.add("Share this app with trusted family members for advice")
                }
                "low_digital_literacy" -> {
                    recommendations.add("Complete the in-app security education course")
                    recommendations.add("Enable 'Ask Me First' mode for all links")
                }
                "data_exposure" -> {
                    recommendations.add("Review your privacy settings on social media")
                    recommendations.add("Consider removing birthdates and phone numbers from profiles")
                }
                "risky_behavior" -> {
                    recommendations.add("Enable click confirmation for all links")
                    recommendations.add("Review recent account activity")
                }
            }
        }
        
        return recommendations.distinct()
    }
    
    private fun getPreventionTips(threatType: String): List<String> {
        return when (threatType) {
            "tax_scam" -> listOf(
                "File early to prevent fraud",
                "Never share SSN via email/text",
                "Use IRS.gov directly"
            )
            "package_delivery_scam" -> listOf(
                "Use official shipper apps",
                "Verify tracking numbers",
                "Don't click text message links"
            )
            "gift_card_scam" -> listOf(
                "No legitimate company demands gift cards",
                "Call back using official number",
                "Report to FTC"
            )
            else -> listOf(
                "Verify through official channels",
                "Don't rush decisions",
                "Report suspicious contacts"
            )
        }
    }
}
