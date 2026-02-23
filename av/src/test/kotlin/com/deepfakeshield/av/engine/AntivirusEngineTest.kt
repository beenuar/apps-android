package com.deepfakeshield.av.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AntivirusEngineTest {

    private lateinit var engine: AntivirusEngine
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val signatureDb = MalwareSignatureDatabase(context)
        val heuristicAnalyzer = HeuristicMalwareAnalyzer(context)
        engine = AntivirusEngine(context, signatureDb, heuristicAnalyzer, CloudHashChecker())
    }

    @Test
    fun `scanFile returns clean for safe file`() = runTest {
        val file = File.createTempFile("safe", ".txt").apply {
            writeText("Clean content - no malware")
        }
        val result = engine.scanFile(file.absolutePath, null, AntivirusEngine.SCAN_TYPE_ONDEMAND)
        assertTrue(result.isClean)
        assertEquals(ThreatLevel.CLEAN, result.threatLevel)
        file.delete()
    }

    @Test
    fun `scanFile detects malware pattern`() = runTest {
        val file = File.createTempFile("mal", ".bin").apply {
            writeText("metasploit exploit payload stealer keylogger backdoor reverse botnet substrate stealth trojan ransomware phishing dropper rootkit")
        }
        val result = engine.scanFile(file.absolutePath, null, AntivirusEngine.SCAN_TYPE_ONDEMAND)
        assertFalse(result.isClean)
        assertTrue(result.indicators.isNotEmpty())
        file.delete()
    }

    @Test
    fun `scanFile for non-existent returns clean`() = runTest {
        val result = engine.scanFile("/nonexistent/path/file.xyz", null)
        assertTrue(result.isClean)
    }

    @Test
    fun `scanFile produces duration`() = runTest {
        val file = File.createTempFile("timed", ".txt").apply { writeText("x") }
        val result = engine.scanFile(file.absolutePath)
        assertTrue(result.durationMs >= 0)
        file.delete()
    }

    @Test
    fun `scanFile includes file hash`() = runTest {
        val file = File.createTempFile("hash", ".txt").apply { writeText("abc") }
        val result = engine.scanFile(file.absolutePath)
        assertNotNull(result.fileHash)
        assertEquals(64, result.fileHash!!.length)
        assertTrue(result.fileHash!!.all { it in '0'..'9' || it in 'a'..'f' })
        file.delete()
    }

    @Test
    fun `toRiskResult returns null for clean`() {
        val cleanResult = AvScanResult(
            path = "/safe.txt",
            scanType = "test",
            displayName = "safe.txt",
            durationMs = 0,
            threatLevel = ThreatLevel.CLEAN
        )
        assertNull(engine.toRiskResult(cleanResult))
    }

    @Test
    fun `toRiskResult returns RiskResult for infected`() {
        val infectedResult = AvScanResult(
            path = "/mal.exe",
            scanType = "test",
            displayName = "mal.exe",
            durationMs = 10,
            threatLevel = ThreatLevel.INFECTED,
            threatName = "Trojan",
            indicators = listOf(
                AvIndicator(
                    AvIndicatorType.SIGNATURE,
                    AvSeverity.CRITICAL,
                    "Malware",
                    "Detected",
                    "evidence"
                )
            )
        )
        val risk = engine.toRiskResult(infectedResult)
        assertNotNull(risk)
        assertEquals(95, risk!!.score)
        assertTrue(risk.recommendedActions.any { it.type.name.contains("QUARANTINE") })
    }
}
