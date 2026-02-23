package com.deepfakeshield.core.engine

import com.deepfakeshield.core.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiskIntelligenceEngineTest {

    private lateinit var engine: RiskIntelligenceEngine

    @Before
    fun setup() { engine = RiskIntelligenceEngine() }

    // === TEXT ANALYSIS TESTS ===
    @Test
    fun `clean text returns low risk`() {
        val result = engine.analyzeText("Hey, want to grab coffee tomorrow?", ThreatSource.MANUAL_SCAN)
        assertEquals(RiskSeverity.LOW, result.severity)
        assertTrue(result.score < 25)
    }

    @Test
    fun `OTP request detected as high risk`() {
        val result = engine.analyzeText("Please share your OTP code immediately to verify your account", ThreatSource.SMS)
        assertTrue(result.score >= 40)
        assertTrue(result.reasons.any { it.title.contains("One-Time Password", ignoreCase = true) || it.title.contains("OTP", ignoreCase = true) })
    }

    @Test
    fun `urgency language increases risk score`() {
        val result = engine.analyzeText("URGENT: Act now or your account will be suspended immediately! Last chance!", ThreatSource.SMS)
        assertTrue(result.score >= 25)
        assertTrue(result.reasons.any { it.title.contains("Urgent", ignoreCase = true) })
    }

    @Test
    fun `impersonation detected when org mentioned from unknown sender`() {
        val result = engine.analyzeText("This is from your bank. Your account is locked.", ThreatSource.SMS, senderInfo = "+1234567890")
        assertTrue(result.reasons.any { it.title.contains("Impersonation", ignoreCase = true) })
    }

    @Test
    fun `payment pressure detected`() {
        val result = engine.analyzeText("Send money now via wire transfer or gift card to avoid arrest", ThreatSource.SMS)
        assertTrue(result.score >= 40)
        assertTrue(result.reasons.any { it.title.contains("Payment", ignoreCase = true) })
    }

    @Test
    fun `remote access request detected`() {
        val result = engine.analyzeText("Please install AnyDesk so our technician can fix your computer", ThreatSource.NOTIFICATION)
        assertTrue(result.score >= 50)
        assertTrue(result.reasons.any { it.title.contains("Remote", ignoreCase = true) })
    }

    @Test
    fun `suspicious link in text detected`() {
        val result = engine.analyzeText("Click here: https://paypal-verify.tk/login", ThreatSource.SMS)
        assertTrue(result.reasons.any { it.type == ReasonType.URL })
    }

    @Test
    fun `multiple red flags produce critical severity`() {
        val result = engine.analyzeText(
            "URGENT from Google: Your account is suspended! Share your OTP now and send payment via gift card to restore access: https://google-verify.tk/restore",
            ThreatSource.SMS, senderInfo = "+999"
        )
        assertTrue(result.severity >= RiskSeverity.HIGH)
        assertTrue(result.reasons.size >= 3)
    }

    // === URL ANALYSIS TESTS ===
    @Test
    fun `safe URL returns low risk`() {
        val result = engine.analyzeUrl("https://google.com")
        assertEquals(RiskSeverity.LOW, result.severity)
    }

    @Test
    fun `lookalike domain detected`() {
        val result = engine.analyzeUrl("https://paypal-secure.example.com/login")
        assertTrue(result.score >= 30)
    }

    @Test
    fun `suspicious TLD detected`() {
        val result = engine.analyzeUrl("https://example.tk")
        assertTrue(result.reasons.any { it.title.contains("Domain Extension", ignoreCase = true) || it.title.contains("TLD", ignoreCase = true) })
    }

    @Test
    fun `punycode URL detected`() {
        val result = engine.analyzeUrl("https://xn--ppal-poa.com")
        assertTrue(result.score >= 25)
    }

    // === CALL ANALYSIS TESTS ===
    @Test
    fun `domestic number returns low risk`() {
        val result = engine.analyzeCall("+12125551234", isIncoming = true)
        assertTrue(result.score < 50)
    }

    @Test
    fun `international number flagged`() {
        val result = engine.analyzeCall("+44123456789", isIncoming = true)
        assertTrue(result.reasons.any { it.title.contains("International", ignoreCase = true) })
    }

    // === VIDEO ANALYSIS TESTS ===
    @Test
    fun `normal video returns low risk`() {
        val result = engine.analyzeVideo(0.9f, 1, 0.1f)
        assertTrue(result.score < 30)
    }

    @Test
    fun `deepfake indicators produce high risk`() {
        val result = engine.analyzeVideo(0.3f, 10, 0.8f)
        assertTrue(result.severity >= RiskSeverity.HIGH)
        assertTrue(result.reasons.size >= 2)
    }

    @Test
    fun `confidence increases with more indicators`() {
        val lowResult = engine.analyzeVideo(0.8f, 2, 0.2f)
        val highResult = engine.analyzeVideo(0.3f, 10, 0.8f)
        assertTrue(highResult.confidence >= lowResult.confidence)
    }

    // === GENERAL TESTS ===
    @Test
    fun `score never exceeds 100`() {
        val result = engine.analyzeText(
            "URGENT! Your bank PayPal account is suspended. Share OTP immediately. Send payment via gift card. Install AnyDesk. Click https://paypal.tk/hack",
            ThreatSource.SMS, senderInfo = "+999"
        )
        assertTrue(result.score <= 100)
    }

    @Test
    fun `confidence between 0 and 1`() {
        val result = engine.analyzeText("test", ThreatSource.MANUAL_SCAN)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `recommended actions provided for threats`() {
        val result = engine.analyzeText("Share your OTP now!", ThreatSource.SMS)
        assertTrue(result.recommendedActions.isNotEmpty())
    }

    @Test
    fun `explain like im five is not empty`() {
        val result = engine.analyzeText("Normal message", ThreatSource.MANUAL_SCAN)
        assertTrue(result.explainLikeImFive.isNotEmpty())
    }
}
