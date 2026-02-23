package com.deepfakeshield.core.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UrlSafetyEngineTest {

    private lateinit var engine: UrlSafetyEngine

    @Before
    fun setup() { engine = UrlSafetyEngine() }

    @Test
    fun `HTTPS URL gets lower score than HTTP`() {
        val https = engine.analyzeUrl("https://example.com")
        val http = engine.analyzeUrl("http://example.com")
        assertTrue(http.riskScore >= https.riskScore)
    }

    @Test
    fun `known brand lookalike detected`() {
        val result = engine.analyzeUrl("https://paypal-login.malicious.com")
        assertTrue(result.threats.any { it.type == "LOOKALIKE" })
    }

    @Test
    fun `suspicious TLD flagged`() {
        val result = engine.analyzeUrl("https://something.tk")
        assertTrue(result.threats.any { it.type == "SUSPICIOUS_TLD" })
    }

    @Test
    fun `IP address domain flagged`() {
        val result = engine.analyzeUrl("http://192.168.1.1/login")
        assertTrue(result.threats.any { it.type == "IP_DOMAIN" })
    }

    @Test
    fun `URL shortener detected`() {
        val result = engine.analyzeUrl("https://bit.ly/abc123")
        assertTrue(result.threats.any { it.type == "URL_SHORTENER" })
    }

    @Test
    fun `safe URL has low risk score`() {
        val result = engine.analyzeUrl("https://google.com/search?q=hello")
        assertTrue(result.riskScore < 20)
    }

    @Test
    fun `excessive subdomains flagged`() {
        val result = engine.analyzeUrl("https://login.verify.account.bank.evil.com/secure")
        assertTrue(result.threats.any { it.type == "EXCESSIVE_SUBDOMAINS" })
    }

    @Test
    fun `risk score capped at 100`() {
        val result = engine.analyzeUrl("http://xn--pypal-4ve.tk/login/verify/account/secure")
        assertTrue(result.riskScore <= 100)
    }

    @Test
    fun `analysis returns domain info`() {
        val result = engine.analyzeUrl("https://example.com/path")
        assertEquals("example.com", result.domain)
        assertTrue(result.isHttps)
    }

    @Test
    fun `recommendation provided for all results`() {
        val safe = engine.analyzeUrl("https://google.com")
        val dangerous = engine.analyzeUrl("http://paypal-login.tk/verify")
        assertTrue(safe.recommendation.isNotEmpty())
        assertTrue(dangerous.recommendation.isNotEmpty())
    }
}
