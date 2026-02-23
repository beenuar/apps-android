package com.deepfakeshield.intelligence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suricata-style rule engine - pattern matching for network/log data.
 * Supports: content, flow bits (to_server, to_client).
 * Lightweight in-process - no external Suricata dependency.
 */
@Singleton
class SuricataRuleEngine @Inject constructor() {

    data class Rule(
        val sid: Int,
        val msg: String,
        val content: String? = null,
        val flow: FlowBits = FlowBits.ANY
    )

    enum class FlowBits { ANY, TO_SERVER, TO_CLIENT }

    data class MatchResult(val sid: Int, val msg: String)

    private val rules = java.util.concurrent.CopyOnWriteArrayList<Rule>()

    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    fun clearRules() {
        rules.clear()
    }

    /** Match payload against rules. flowDirection: "toserver" | "toclient" | null */
    fun match(payload: ByteArray, flowDirection: String? = null): List<MatchResult> {
        val contentStr = payload.toString(Charsets.ISO_8859_1)
        return rules.filter { rule ->
            val flowOk = when (rule.flow) {
                FlowBits.ANY -> true
                FlowBits.TO_SERVER -> flowDirection == "toserver"
                FlowBits.TO_CLIENT -> flowDirection == "toclient"
            }
            if (!flowOk) return@filter false
            rule.content == null || contentStr.contains(rule.content, ignoreCase = true)
        }.map { MatchResult(it.sid, it.msg) }
    }

    fun matchText(text: String, flowDirection: String? = null): List<MatchResult> =
        match(text.toByteArray(Charsets.ISO_8859_1), flowDirection)
}
