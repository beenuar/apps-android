package com.deepfakeshield.av.engine

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YARA-like rule engine for pattern matching against file contents.
 *
 * Rules consist of:
 *   - name: unique identifier
 *   - strings: named patterns (text, hex, or regex) to search for
 *   - condition: how many strings must match (all, any, N of them)
 *   - severity: CRITICAL, HIGH, MEDIUM, LOW
 *   - family: threat family name
 *
 * This is a simplified but functional YARA implementation that runs on-device.
 */
@Singleton
class YaraRuleEngine @Inject constructor() {

    data class YaraString(val id: String, val pattern: String, val type: StringType)
    enum class StringType { TEXT, HEX, REGEX }
    enum class Condition { ALL, ANY, TWO_OF, THREE_OF }

    data class YaraRule(
        val name: String, val family: String, val severity: String,
        val description: String, val strings: List<YaraString>,
        val condition: Condition
    )

    data class YaraMatch(
        val rule: YaraRule, val matchedStrings: List<String>, val offset: Long = 0
    )

    private val rules = mutableListOf<YaraRule>()

    init { loadBuiltinRules() }

    fun scanFile(file: File, maxBytes: Long = 1024 * 1024): List<YaraMatch> {
        if (!file.exists() || !file.canRead() || file.length() == 0L) return emptyList()
        val matches = mutableListOf<YaraMatch>()

        try {
            val readLen = minOf(file.length(), maxBytes).toInt()
            val content = ByteArray(readLen)
            RandomAccessFile(file, "r").use { raf -> raf.readFully(content) }
            val textContent = content.decodeToString(throwOnInvalidSequence = false)
            val hexContent = content.joinToString("") { "%02x".format(it) }

            for (rule in rules) {
                val matched = mutableListOf<String>()
                for (s in rule.strings) {
                    val found = when (s.type) {
                        StringType.TEXT -> textContent.contains(s.pattern, ignoreCase = true)
                        StringType.HEX -> hexContent.contains(s.pattern.lowercase().replace(" ", ""))
                        StringType.REGEX -> try { Regex(s.pattern, RegexOption.IGNORE_CASE).containsMatchIn(textContent) } catch (_: Exception) { false }
                    }
                    if (found) matched.add(s.id)
                }

                val conditionMet = when (rule.condition) {
                    Condition.ALL -> matched.size == rule.strings.size
                    Condition.ANY -> matched.isNotEmpty()
                    Condition.TWO_OF -> matched.size >= 2
                    Condition.THREE_OF -> matched.size >= 3
                }
                if (conditionMet) matches.add(YaraMatch(rule, matched))
            }
        } catch (e: Exception) {
            Log.w("YARA", "Scan error ${file.name}: ${e.message}")
        }
        return matches
    }

    fun scanBytes(content: ByteArray, fileName: String = ""): List<YaraMatch> {
        if (content.isEmpty()) return emptyList()
        val textContent = content.decodeToString(throwOnInvalidSequence = false)
        val hexContent = content.joinToString("") { "%02x".format(it) }
        val matches = mutableListOf<YaraMatch>()

        for (rule in rules) {
            val matched = mutableListOf<String>()
            for (s in rule.strings) {
                val found = when (s.type) {
                    StringType.TEXT -> textContent.contains(s.pattern, ignoreCase = true)
                    StringType.HEX -> hexContent.contains(s.pattern.lowercase().replace(" ", ""))
                    StringType.REGEX -> try { Regex(s.pattern, RegexOption.IGNORE_CASE).containsMatchIn(textContent) } catch (_: Exception) { false }
                }
                if (found) matched.add(s.id)
            }
            val conditionMet = when (rule.condition) { Condition.ALL -> matched.size == rule.strings.size; Condition.ANY -> matched.isNotEmpty(); Condition.TWO_OF -> matched.size >= 2; Condition.THREE_OF -> matched.size >= 3 }
            if (conditionMet) matches.add(YaraMatch(rule, matched))
        }
        return matches
    }

    fun addRule(rule: YaraRule) { rules.add(rule) }
    fun getRuleCount(): Int = rules.size

    private fun loadBuiltinRules() {
        // ── Ransomware ───────────────────────────────────────
        rules += YaraRule("ransomware_note", "Ransomware", "CRITICAL", "Ransomware ransom note detected",
            listOf(YaraString("s1", "your files have been encrypted", StringType.TEXT), YaraString("s2", "bitcoin", StringType.TEXT), YaraString("s3", "decrypt", StringType.TEXT)),
            Condition.TWO_OF)

        rules += YaraRule("ransomware_crypto", "Ransomware", "CRITICAL", "File encryption routine detected",
            listOf(YaraString("s1", "javax.crypto.Cipher", StringType.TEXT), YaraString("s2", "AES/CBC/PKCS5Padding", StringType.TEXT), YaraString("s3", ".encrypted", StringType.TEXT), YaraString("s4", "ransom", StringType.TEXT)),
            Condition.THREE_OF)

        // ── Banking Trojans ──────────────────────────────────
        rules += YaraRule("banker_overlay", "BankingTrojan", "CRITICAL", "Banking overlay attack pattern",
            listOf(YaraString("s1", "SYSTEM_ALERT_WINDOW", StringType.TEXT), YaraString("s2", "getRunningTasks", StringType.TEXT), YaraString("s3", "card_number", StringType.TEXT), YaraString("s4", "AccessibilityService", StringType.TEXT)),
            Condition.THREE_OF)

        rules += YaraRule("banker_sms", "BankingTrojan", "CRITICAL", "SMS OTP interception pattern",
            listOf(YaraString("s1", "SMS_RECEIVED", StringType.TEXT), YaraString("s2", "abortBroadcast", StringType.TEXT), YaraString("s3", "getMessageBody", StringType.TEXT)),
            Condition.ALL)

        // ── Spyware ──────────────────────────────────────────
        rules += YaraRule("spyware_location", "Spyware", "HIGH", "Continuous location tracking",
            listOf(YaraString("s1", "requestLocationUpdates", StringType.TEXT), YaraString("s2", "getLastKnownLocation", StringType.TEXT), YaraString("s3", "http", StringType.TEXT)),
            Condition.ALL)

        rules += YaraRule("spyware_recorder", "Spyware", "CRITICAL", "Audio/screen recording spyware",
            listOf(YaraString("s1", "MediaRecorder", StringType.TEXT), YaraString("s2", "setAudioSource", StringType.TEXT), YaraString("s3", "startRecording", StringType.TEXT), YaraString("s4", "upload", StringType.TEXT)),
            Condition.THREE_OF)

        rules += YaraRule("spyware_keylogger", "Spyware", "CRITICAL", "Keylogger pattern",
            listOf(YaraString("s1", "AccessibilityEvent", StringType.TEXT), YaraString("s2", "TYPE_VIEW_TEXT_CHANGED", StringType.TEXT), YaraString("s3", "getText", StringType.TEXT)),
            Condition.ALL)

        // ── RAT (Remote Access Trojan) ───────────────────────
        rules += YaraRule("rat_c2", "RAT", "CRITICAL", "Remote access trojan with C2 communication",
            listOf(YaraString("s1", "socket", StringType.TEXT), YaraString("s2", "connect", StringType.TEXT), YaraString("s3", "getRuntime", StringType.TEXT), YaraString("s4", "exec", StringType.TEXT)),
            Condition.ALL)

        rules += YaraRule("rat_reverse_shell", "RAT", "CRITICAL", "Reverse shell payload",
            listOf(YaraString("s1", "/bin/sh", StringType.TEXT), YaraString("s2", "getInputStream", StringType.TEXT), YaraString("s3", "getOutputStream", StringType.TEXT)),
            Condition.ALL)

        // ── Dropper ──────────────────────────────────────────
        rules += YaraRule("dropper_dex", "Dropper", "HIGH", "Dynamic DEX loading (second stage payload)",
            listOf(YaraString("s1", "DexClassLoader", StringType.TEXT), YaraString("s2", "loadClass", StringType.TEXT), YaraString("s3", "InMemoryDexClassLoader", StringType.TEXT)),
            Condition.TWO_OF)

        rules += YaraRule("dropper_download", "Dropper", "HIGH", "Downloads and executes code",
            listOf(YaraString("s1", "URLConnection", StringType.TEXT), YaraString("s2", ".apk", StringType.TEXT), YaraString("s3", "PackageInstaller", StringType.TEXT)),
            Condition.ALL)

        // ── Adware / PUA ─────────────────────────────────────
        rules += YaraRule("adware_aggressive", "Adware", "MEDIUM", "Aggressive advertising SDK",
            listOf(YaraString("s1", "interstitial", StringType.TEXT), YaraString("s2", "rewardedAd", StringType.TEXT), YaraString("s3", "loadAd", StringType.TEXT), YaraString("s4", "fullscreen", StringType.TEXT)),
            Condition.THREE_OF)

        // ── Crypto Mining ────────────────────────────────────
        rules += YaraRule("cryptominer", "Cryptominer", "HIGH", "Cryptocurrency mining code",
            listOf(YaraString("s1", "stratum+tcp", StringType.TEXT), YaraString("s2", "hashrate", StringType.TEXT), YaraString("s3", "mining_pool", StringType.TEXT)),
            Condition.TWO_OF)

        rules += YaraRule("cryptominer_wasm", "Cryptominer", "HIGH", "WebAssembly-based crypto miner",
            listOf(YaraString("s1", "coinhive", StringType.TEXT), YaraString("s2", "CryptoNight", StringType.TEXT)),
            Condition.ANY)

        // ── Exploit / Packer ─────────────────────────────────
        rules += YaraRule("packed_apk", "Packer", "MEDIUM", "Packed/obfuscated APK (common in malware)",
            listOf(YaraString("s1", "62616964752e", StringType.HEX), YaraString("s2", "7061636b65722e", StringType.HEX), YaraString("s3", "4a696167752e", StringType.HEX)),
            Condition.ANY)

        rules += YaraRule("root_exploit", "Exploit", "CRITICAL", "Root exploit payload",
            listOf(YaraString("s1", "CVE-20", StringType.TEXT), YaraString("s2", "exploit", StringType.TEXT), YaraString("s3", "escalat", StringType.TEXT), YaraString("s4", "privilege", StringType.TEXT)),
            Condition.THREE_OF)

        // ── Phishing ─────────────────────────────────────────
        rules += YaraRule("phishing_webview", "Phishing", "HIGH", "WebView-based phishing page",
            listOf(YaraString("s1", "evaluateJavascript", StringType.TEXT), YaraString("s2", "password", StringType.TEXT), YaraString("s3", "document.getElementById", StringType.TEXT), YaraString("s4", "submit", StringType.TEXT)),
            Condition.THREE_OF)

        // ── Stalkerware ──────────────────────────────────────
        rules += YaraRule("stalkerware", "Stalkerware", "CRITICAL", "Stalkerware/monitoring app",
            listOf(YaraString("s1", "getCallLog", StringType.TEXT), YaraString("s2", "getSmsMessages", StringType.TEXT), YaraString("s3", "getContacts", StringType.TEXT), YaraString("s4", "uploadToServer", StringType.TEXT)),
            Condition.THREE_OF)

        // ── SIM Swap / Toll Fraud ────────────────────────────
        rules += YaraRule("toll_fraud", "TollFraud", "HIGH", "Premium SMS/toll fraud",
            listOf(YaraString("s1", "sendTextMessage", StringType.TEXT), YaraString("s2", "SEND_SMS", StringType.TEXT), YaraString("s3", "900", StringType.TEXT)),
            Condition.ALL)

        // ── Clipboard Hijacker ───────────────────────────────
        rules += YaraRule("clipboard_hijack", "ClipboardHijacker", "HIGH", "Cryptocurrency address replacement",
            listOf(YaraString("s1", "ClipboardManager", StringType.TEXT), YaraString("s2", "setPrimaryClip", StringType.TEXT), YaraString("s3", "bc1q", StringType.TEXT)),
            Condition.ALL)

        rules += YaraRule("clipboard_hijack_eth", "ClipboardHijacker", "HIGH", "ETH address clipboard swap",
            listOf(YaraString("s1", "ClipboardManager", StringType.TEXT), YaraString("s2", "setPrimaryClip", StringType.TEXT), YaraString("s3", "0x[a-fA-F0-9]{40}", StringType.REGEX)),
            Condition.ALL)

        Log.i("YARA", "Loaded ${rules.size} built-in rules")
    }
}
