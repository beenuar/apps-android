package com.deepfakeshield.core.intelligence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * CONTEXTUAL AI ASSISTANT
 * 
 * Helps users understand and respond to threats
 * - Threat explanation in simple terms
 * - Safe response suggestions
 * - Evidence collection guidance
 * - Reporting automation
 */

data class AssistantResponse(
    val explanation: String,
    val safeResponses: List<SuggestedResponse>,
    val actionSteps: List<ActionStep>,
    val educationalTip: String?
)

data class SuggestedResponse(
    val text: String,
    val type: ResponseType,
    val reasoning: String
)

enum class ResponseType {
    IGNORE,           // Don't respond, block
    POLITE_DECLINE,   // Politely refuse
    REQUEST_INFO,     // Ask for clarification
    REPORT_ONLY,      // Just report, don't respond
    SCAM_BAIT         // Advanced: waste scammer's time
}

data class ActionStep(
    val step: Int,
    val action: String,
    val details: String,
    val isOptional: Boolean = false
)

@Singleton
class ContextualAIAssistant @Inject constructor() {
    
    /**
     * Get assistance for a detected threat
     */
    @Suppress("UNUSED_PARAMETER")
    fun getAssistance(
        threatType: String,
        severity: Int,
        _content: String,
        reasons: List<String>
    ): AssistantResponse {
        val safeReasons = reasons.map { it.take(500) }
        val explanation = explainThreat(threatType, severity, safeReasons)
        val responses = suggestResponses(threatType, severity)
        val actions = suggestActions(threatType, severity)
        val tip = getEducationalTip(threatType)
        
        return AssistantResponse(
            explanation = explanation,
            safeResponses = responses,
            actionSteps = actions,
            educationalTip = tip
        )
    }
    
    /**
     * Explain threat in simple, non-technical terms
     */
    private fun explainThreat(
        threatType: String,
        severity: Int,
        reasons: List<String>
    ): String {
        val severityText = when {
            severity >= 80 -> "very dangerous"
            severity >= 60 -> "suspicious"
            else -> "potentially risky"
        }
        
        return when (threatType) {
            "SCAM_SMS" -> """
                This is a $severityText scam message. Someone is trying to trick you into:
                ${reasons.joinToString("\n") { "• $it" }}
                
                🚫 Do NOT click any links
                🚫 Do NOT reply with personal information
                🚫 Do NOT send money
            """.trimIndent()
            
            "PHISHING_URL" -> """
                This link leads to a $severityText fake website. It's designed to:
                ${reasons.joinToString("\n") { "• $it" }}
                
                🚫 Do NOT visit this website
                🚫 Do NOT enter passwords or card details
                🚫 Do NOT download anything
            """.trimIndent()
            
            "SCAM_CALL" -> """
                This call shows $severityText characteristics:
                ${reasons.joinToString("\n") { "• $it" }}
                
                🚫 Do NOT share OTPs or passwords
                🚫 Do NOT give remote access
                🚫 Do NOT send money immediately
            """.trimIndent()
            
            "DEEPFAKE_VIDEO" -> """
                This video has $severityText signs of being manipulated:
                ${reasons.joinToString("\n") { "• $it" }}
                
                ⚠️ This may not be the real person
                ⚠️ Verify through official channels
                ⚠️ Don't make decisions based solely on this video
            """.trimIndent()
            
            else -> """
                We detected $severityText content because:
                ${reasons.joinToString("\n") { "• $it" }}
                
                Stay cautious and verify before taking action.
            """.trimIndent()
        }
    }
    
    /**
     * Suggest safe responses
     */
    @Suppress("UNUSED_PARAMETER")
    private fun suggestResponses(_threatType: String, severity: Int): List<SuggestedResponse> {
        val responses = mutableListOf<SuggestedResponse>()
        
        when {
            severity >= 80 -> {
                // High risk - recommend no response
                responses.add(
                    SuggestedResponse(
                        text = "[Don't Respond - Just Block]",
                        type = ResponseType.REPORT_ONLY,
                        reasoning = "This is clearly a scam. Responding could confirm your number is active."
                    )
                )
            }
            
            severity >= 60 -> {
                // Medium risk - polite decline
                responses.add(
                    SuggestedResponse(
                        text = "I don't recognize this request. I'll contact the official organization directly.",
                        type = ResponseType.POLITE_DECLINE,
                        reasoning = "This lets them know you're aware without engaging further."
                    )
                )
                
                responses.add(
                    SuggestedResponse(
                        text = "Can you provide an official reference number? I'll verify through official channels.",
                        type = ResponseType.REQUEST_INFO,
                        reasoning = "Real organizations can provide verification. Scammers can't."
                    )
                )
            }
            
            else -> {
                // Lower risk - verify
                responses.add(
                    SuggestedResponse(
                        text = "Could you please send this request through the official app/website?",
                        type = ResponseType.REQUEST_INFO,
                        reasoning = "Legitimate requests can be made through official channels."
                    )
                )
            }
        }
        
        // Advanced users: scam baiting
        if (severity >= 70) {
            responses.add(
                SuggestedResponse(
                    text = "[Advanced] Waste scammer's time with fake information",
                    type = ResponseType.SCAM_BAIT,
                    reasoning = "For advanced users only. Keeps scammer busy, protecting others."
                )
            )
        }
        
        return responses
    }
    
    /**
     * Suggest action steps
     */
    private fun suggestActions(threatType: String, severity: Int): List<ActionStep> {
        val actions = mutableListOf<ActionStep>()
        
        // Always recommend blocking
        actions.add(
            ActionStep(
                step = 1,
                action = "Block this sender",
                details = "Prevents future contact from this number/address"
            )
        )
        
        // Report to authorities
        if (severity >= 70) {
            actions.add(
                ActionStep(
                    step = 2,
                    action = "Report to authorities",
                    details = when (threatType) {
                        "SCAM_SMS" -> "Forward to 7726 (SPAM) for your carrier"
                        "PHISHING_URL" -> "Report to Anti-Phishing Working Group (reportphishing@apwg.org)"
                        "SCAM_CALL" -> "Report to FTC at ReportFraud.ftc.gov"
                        else -> "Report to appropriate authorities"
                    }
                )
            )
        }
        
        // Save evidence
        actions.add(
            ActionStep(
                step = 3,
                action = "Save evidence",
                details = "Screenshot the message/call log before blocking",
                isOptional = true
            )
        )
        
        // Warn others
        if (severity >= 60) {
            actions.add(
                ActionStep(
                    step = 4,
                    action = "Warn friends and family",
                    details = "Share this scam pattern so others can avoid it",
                    isOptional = true
                )
            )
        }
        
        // Check accounts
        if (threatType == "PHISHING_URL" || (threatType == "SCAM_SMS" && severity >= 70)) {
            actions.add(
                ActionStep(
                    step = 5,
                    action = "Check your accounts",
                    details = "Login directly (not through links) and verify no unauthorized access"
                )
            )
        }
        
        return actions
    }
    
    /**
     * Provide educational tip
     */
    private fun getEducationalTip(threatType: String): String? {
        return when (threatType) {
            "SCAM_SMS" -> """
                💡 Tip: Real companies never ask for passwords, OTPs, or card CVVs via text.
                If it seems urgent, contact the company directly using their official number.
            """.trimIndent()
            
            "PHISHING_URL" -> """
                💡 Tip: Before clicking links, long-press to preview the URL.
                Check for misspellings or suspicious domains (e.g., "paypa1.com" instead of "paypal.com").
            """.trimIndent()
            
            "SCAM_CALL" -> """
                💡 Tip: Legitimate companies won't pressure you for immediate action.
                If someone claims to be from a bank/government, hang up and call their official number.
            """.trimIndent()
            
            "DEEPFAKE_VIDEO" -> """
                💡 Tip: Even if it looks like someone you know, verify through a different channel.
                Call them directly or use a known verified account.
            """.trimIndent()
            
            else -> null
        }
    }
    
    /**
     * Generate scam bait responses (advanced feature)
     */
    fun generateScamBaitResponse(iteration: Int): String {
        // Waste scammer's time with believable but fake responses
        return when (iteration % 5) {
            0 -> "I'm interested but I'm at work right now. Can you explain more?"
            1 -> "That sounds good. What information do you need from me?"
            2 -> "I'm having trouble with the link. Can you resend it?"
            3 -> "My bank is asking for verification. What's your company registration number?"
            4 -> "I need to discuss this with my spouse. Can we continue tomorrow?"
            else -> "Could you clarify one more thing for me?"
        }
    }
}
