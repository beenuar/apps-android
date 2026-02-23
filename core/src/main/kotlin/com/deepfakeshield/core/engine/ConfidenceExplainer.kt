@file:Suppress("UNUSED_PARAMETER")
package com.deepfakeshield.core.engine

import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates technical risk scores into human-readable,
 * contextual explanations that anyone can understand.
 */
@Singleton
class ConfidenceExplainer @Inject constructor() {

    data class HumanExplanation(
        val headline: String,
        val explanation: String,
        val context: String,
        val actionRequired: String,
        val analogies: List<String>
    )

    fun explain(result: RiskResult): HumanExplanation {
        return when (result.threatType) {
            ThreatType.OTP_TRAP -> explainOTP(result)
            ThreatType.PAYMENT_SCAM -> explainPayment(result)
            ThreatType.IMPERSONATION -> explainImpersonation(result)
            ThreatType.MALICIOUS_LINK -> explainMaliciousLink(result)
            ThreatType.PHISHING_ATTEMPT -> explainPhishing(result)
            ThreatType.REMOTE_ACCESS_SCAM -> explainRemoteAccess(result)
            ThreatType.DEEPFAKE_VIDEO -> explainDeepfake(result)
            ThreatType.SUSPICIOUS_CALL -> explainSuspiciousCall(result)
            ThreatType.ROMANCE_SCAM -> explainRomance(result)
            ThreatType.CRYPTO_SCAM -> explainCrypto(result)
            ThreatType.JOB_SCAM -> explainJob(result)
            else -> explainGeneric(result)
        }
    }

    private fun explainOTP(_result: RiskResult) = HumanExplanation(
        headline = "Someone wants your one-time password",
        explanation = "This message is asking you to share a verification code. Real banks, Google, Amazon, and every legitimate company will NEVER ask you to share an OTP. This is like someone asking for the key to your house.",
        context = "OTP scams are the #1 fraud method globally. Victims lose an average of \$2,400.",
        actionRequired = "NEVER share your OTP with anyone. If in doubt, call your bank directly.",
        analogies = listOf("Sharing your OTP is like giving a stranger your house key", "Would you read your ATM PIN out loud to a stranger calling you?")
    )

    private fun explainPayment(_result: RiskResult) = HumanExplanation(
        headline = "This is trying to get your money",
        explanation = "This message is pressuring you to send money or share payment details. Scammers create false urgency to make you act without thinking.",
        context = "Payment scams cause billions in losses annually. The money is usually unrecoverable.",
        actionRequired = "Do NOT send money or share card details. Verify through official channels.",
        analogies = listOf("It's like someone on the street asking you to hand over your wallet because 'it's an emergency'")
    )

    private fun explainImpersonation(_result: RiskResult) = HumanExplanation(
        headline = "Someone is pretending to be an organization",
        explanation = "This message claims to be from a trusted company or government agency, but it's not. Real organizations communicate through their official apps and websites, not random messages.",
        context = "Impersonation scams are up 300% since 2020. Scammers use fear and authority to manipulate.",
        actionRequired = "Do NOT reply. Look up the real organization's contact info yourself.",
        analogies = listOf("This is like someone in a fake police uniform asking for your ID")
    )

    private fun explainMaliciousLink(_result: RiskResult) = HumanExplanation(
        headline = "This link is not what it seems",
        explanation = "The URL in this message leads to a fake website designed to steal your information. It may look identical to a real website, but it's controlled by criminals.",
        context = "Clicking malicious links can install malware or steal your passwords instantly.",
        actionRequired = "Do NOT click. If you need to visit the website, type the address manually in your browser.",
        analogies = listOf("It's like a door that looks like your bank but opens into a thief's den")
    )

    private fun explainPhishing(_result: RiskResult) = HumanExplanation(
        headline = "This is a phishing attempt",
        explanation = "Someone is 'fishing' for your personal information by creating a convincing fake message. They want your login details, personal data, or financial information.",
        context = "95% of data breaches start with a phishing message. You're the last line of defense.",
        actionRequired = "Delete this message. If worried, contact the real company directly.",
        analogies = listOf("It's like a fisherman using bait - the message is the bait, your data is the catch")
    )

    private fun explainRemoteAccess(_result: RiskResult) = HumanExplanation(
        headline = "Someone wants to control your device",
        explanation = "This message asks you to install remote access software. Once installed, a scammer can see everything on your screen, access your apps, and steal your money in real-time.",
        context = "Remote access scams cause average losses of \$15,000. The scammer watches you enter passwords.",
        actionRequired = "NEVER install remote access apps when asked by a stranger. Hang up immediately.",
        analogies = listOf("It's like handing a stranger the remote control to your entire digital life")
    )

    private fun explainDeepfake(_result: RiskResult) = HumanExplanation(
        headline = "This video may be AI-generated",
        explanation = "Our analysis detected signs that this video was created or manipulated using artificial intelligence. The person in the video may not have actually said or done what's shown.",
        context = "Deepfake technology can now create convincing fake videos of anyone in minutes.",
        actionRequired = "Do not trust or share this video. Look for the original source to verify.",
        analogies = listOf("Think of it as a digital mask - someone else's face is being puppeted by AI")
    )

    private fun explainSuspiciousCall(_result: RiskResult) = HumanExplanation(
        headline = "This call looks suspicious",
        explanation = "The caller's number shows signs of being spoofed or is from a region known for scam operations. Be very careful about sharing any information.",
        context = "Phone scammers can make any number appear on your caller ID.",
        actionRequired = "Don't share personal info. If they claim to be from a company, hang up and call the company directly.",
        analogies = listOf("It's like someone wearing a disguise and claiming to be your friend")
    )

    private fun explainRomance(_result: RiskResult) = HumanExplanation(
        headline = "This may be a romance scam",
        explanation = "Messages with emotional manipulation, quick declarations of love, and eventual money requests are classic romance scam patterns.",
        context = "Romance scam victims lose an average of \$10,000 and suffer severe emotional trauma.",
        actionRequired = "Never send money to someone you've only met online. Share with a trusted friend.",
        analogies = listOf("Real love doesn't come with wire transfer requests")
    )

    private fun explainCrypto(_result: RiskResult) = HumanExplanation(
        headline = "Cryptocurrency scam detected",
        explanation = "Promises of guaranteed returns, pressure to invest quickly, and requests for crypto transfers are hallmarks of investment fraud.",
        context = "Crypto scams stole over \$14 billion in 2023 alone. No investment is 'guaranteed'.",
        actionRequired = "Never invest based on a message from a stranger. Consult a licensed financial advisor.",
        analogies = listOf("If someone on the street promised to double your money, would you hand it over?")
    )

    private fun explainJob(_result: RiskResult) = HumanExplanation(
        headline = "Fake job offer detected",
        explanation = "This job offer shows signs of being fraudulent. Legitimate employers never ask for upfront payments, personal banking info, or cryptocurrency as part of hiring.",
        context = "Job scams target unemployed people. Victims pay 'training fees' or share bank details for 'direct deposit setup'.",
        actionRequired = "Verify the company independently. Never pay to get a job.",
        analogies = listOf("Real employers pay you, not the other way around")
    )

    private fun explainGeneric(result: RiskResult) = HumanExplanation(
        headline = when (result.severity) {
            RiskSeverity.CRITICAL -> "This is very dangerous"
            RiskSeverity.HIGH -> "This looks suspicious"
            RiskSeverity.MEDIUM -> "Something doesn't seem right"
            RiskSeverity.LOW -> "Minor concerns detected"
        },
        explanation = "Our analysis found ${result.reasons.size} warning sign${if (result.reasons.size != 1) "s" else ""}. ${result.explainLikeImFive}",
        context = "Trust your instincts. If something feels wrong, it probably is.",
        actionRequired = when (result.severity) {
            RiskSeverity.CRITICAL -> "Do not engage. Delete and block."
            RiskSeverity.HIGH -> "Proceed with extreme caution. Verify independently."
            RiskSeverity.MEDIUM -> "Double-check before taking any action."
            RiskSeverity.LOW -> "Likely safe, but stay alert."
        },
        analogies = emptyList()
    )
}
