package com.deepfakeshield.core.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Page content analysis - phishing keywords, credential harvesting patterns.
 * Used when page HTML/text is available (e.g. from WebView or fetched content).
 */
@Singleton
class PageContentAnalyzer @Inject constructor() {

    data class PageAnalysis(
        val riskScore: Int,
        val phishingKeywords: List<String>,
        val credentialForms: Boolean,
        val recommendation: String
    )

    private val phishingKeywords = listOf(
        "verify your account", "confirm your identity", "suspended account",
        "urgent action required", "click here to unlock", "your account has been locked",
        "verify your information", "update your payment", "confirm your details",
        "security alert", "unusual activity", "sign in to continue",
        "reset your password", "verify your email", "confirm your bank"
    )

    private val credentialFormPatterns = listOf(
        "password", "pin", "social security", "ssn", "card number",
        "cvv", "routing number", "account number", "otp", "verification code"
    )

    /** Analyze page text (HTML stripped or body text) for phishing indicators */
    fun analyze(text: String): PageAnalysis {
        val lower = text.lowercase()
        val found = phishingKeywords.filter { lower.contains(it) }
        val hasCredentialForm = credentialFormPatterns.any { lower.contains(it) }
        var riskScore = found.size * 15
        if (hasCredentialForm) riskScore += 25
        val finalScore = riskScore.coerceIn(0, 100)
        val recommendation = when {
            finalScore >= 60 -> "This page may be a phishing site. Do not enter credentials."
            finalScore >= 30 -> "Suspicious content detected. Verify the URL before proceeding."
            else -> "No significant phishing indicators found in page content."
        }
        return PageAnalysis(
            riskScore = finalScore,
            phishingKeywords = found,
            credentialForms = hasCredentialForm,
            recommendation = recommendation
        )
    }
}
