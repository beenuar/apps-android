package com.deepfakeshield.intelligence

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YARA-like rule engine - pattern matching for file content.
 * Supports: hex strings, text strings, and conditions.
 * No external YARA dependency - lightweight in-process matching.
 */
@Singleton
class YaraLikeRuleEngine @Inject constructor() {

    data class Rule(
        val name: String,
        val meta: Map<String, String> = emptyMap(),
        val strings: List<RuleString> = emptyList(),
        val condition: String = "any of them"
    )

    data class RuleString(
        val id: String,
        val type: String, // "hex" or "text"
        val value: String,
        val modifiers: List<String> = emptyList() // "nocase", "wide", etc.
    )

    data class MatchResult(
        val ruleName: String,
        val matchedStrings: List<String>,
        val offset: Long
    )

    private val rules = java.util.concurrent.CopyOnWriteArrayList<Rule>()

    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    fun clearRules() {
        rules.clear()
    }

    fun matchFile(file: File): List<MatchResult> {
        if (!file.exists() || !file.isFile) return emptyList()
        if (file.length() > 10 * 1024 * 1024) return emptyList() // Skip files > 10MB
        val content = file.readBytes()
        return matchContent(content)
    }

    fun matchContent(content: ByteArray): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        for (rule in rules) {
            val matched = mutableListOf<String>()
            for (s in rule.strings) {
                val pattern = when (s.type) {
                    "hex" -> hexToBytes(s.value.replace(" ", ""))
                    "text" -> s.value.toByteArray(Charsets.UTF_8)
                    else -> continue
                }
                if (pattern.isEmpty()) continue
                if (contains(content, pattern, s.modifiers.contains("nocase"))) {
                    matched.add(s.id)
                }
            }
            if (matched.isNotEmpty() && (rule.condition == "any of them" || matched.size >= rule.strings.size)) {
                results.add(MatchResult(rule.name, matched, 0))
            }
        }
        return results
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.length % 2 != 0) return ByteArray(0)
        return hex.chunked(2).map { chunk ->
            chunk.toIntOrNull(16)?.toByte() ?: return ByteArray(0) // Reject entire pattern on invalid hex
        }.toByteArray()
    }

    private fun contains(haystack: ByteArray, needle: ByteArray, nocase: Boolean): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        val n = if (nocase) needle.map { lowerByte(it) }.toByteArray() else needle
        val h = if (nocase) haystack.map { lowerByte(it) }.toByteArray() else haystack
        for (i in 0..h.size - n.size) {
            var match = true
            for (j in n.indices) {
                if (h[i + j] != n[j]) { match = false; break }
            }
            if (match) return true
        }
        return false
    }

    private fun lowerByte(b: Byte): Byte = when {
        b.toInt() in 65..90 -> (b.toInt() + 32).toByte()
        else -> b
    }
}
