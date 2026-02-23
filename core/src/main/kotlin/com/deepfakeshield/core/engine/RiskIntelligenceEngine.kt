package com.deepfakeshield.core.engine

import com.deepfakeshield.core.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Unified Risk Intelligence Engine
 * 
 * Central intelligence layer used by all detection features.
 * Provides consistent risk scoring, severity assessment, and actionable recommendations.
 */
@Singleton
class RiskIntelligenceEngine @Inject constructor() {

    /**
     * Analyze text content for scam indicators
     */
    fun analyzeText(
        text: String,
        source: ThreatSource,
        senderInfo: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): RiskResult {
        if (text.isBlank()) return RiskResult(
            score = 0,
            severity = RiskSeverity.LOW,
            confidence = 0.1f,
            threatType = ThreatType.SCAM_MESSAGE,
            reasons = emptyList(),
            recommendedActions = emptyList(),
            explainLikeImFive = "No content to analyze."
        )
        val indicators = mutableListOf<Reason>()
        var totalScore = 0

        // Urgency detection
        val urgencyScore = detectUrgency(text)
        if (urgencyScore > 0) {
            totalScore += urgencyScore
            indicators.add(
                Reason(
                    type = ReasonType.LANGUAGE,
                    title = "Urgent Language",
                    explanation = "This message uses pressure tactics to make you act quickly without thinking.",
                    evidence = extractUrgentPhrases(text).take(2).joinToString(", ")
                )
            )
        }

        // OTP trap detection
        val otpScore = detectOtpRequest(text)
        if (otpScore > 0) {
            totalScore += otpScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Requests One-Time Password",
                    explanation = "Legitimate services never ask you to share OTP codes via message or call.",
                    evidence = "OTP/verification code request detected"
                )
            )
        }

        // Impersonation detection
        val impersonationScore = detectImpersonation(text, senderInfo)
        if (impersonationScore > 0) {
            totalScore += impersonationScore
            indicators.add(
                Reason(
                    type = ReasonType.IDENTITY,
                    title = "Possible Impersonation",
                    explanation = "This message claims to be from an organization but may be fake.",
                    evidence = extractImpersonationClaims(text)
                )
            )
        }

        // Payment/financial pressure
        val paymentScore = detectPaymentPressure(text)
        if (paymentScore > 0) {
            totalScore += paymentScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Payment Pressure",
                    explanation = "This message pressures you to send money or share payment information.",
                    evidence = "Financial request detected"
                )
            )
        }

        // Remote access request
        val remoteAccessScore = detectRemoteAccessRequest(text)
        if (remoteAccessScore > 0) {
            totalScore += remoteAccessScore
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "Remote Access Request",
                    explanation = "Scammers often ask victims to install remote control apps like AnyDesk or TeamViewer.",
                    evidence = extractRemoteAccessApps(text)
                )
            )
        }

        // Link analysis — only flag links that are actually suspicious.
        // B2C-SAFE: Do NOT add 35 points just for having a link. Most legitimate
        // notifications contain links (Spotify, WhatsApp, Netflix, banks, etc.).
        // Only add score if the link analysis finds genuine issues (shortened URLs,
        // suspicious TLDs, homograph attacks, etc.)
        val links = extractLinks(text)
        if (links.isNotEmpty()) {
            val linkScore = analyzeLinkSafety(links)
            if (linkScore > 0) {
                totalScore += linkScore
                indicators.add(
                    Reason(
                        type = ReasonType.URL,
                        title = "Suspicious Link",
                        explanation = "This message contains a link with suspicious characteristics.",
                        evidence = links.first()
                    )
                )
            }
            // Don't add points just for having a link — that's normal in 2026
        }

        // Generic scam / phishing phrases (prize, account locked, verify, etc.)
        val scamPhraseScore = detectGenericScamPhrases(text)
        if (scamPhraseScore > 0) {
            totalScore += scamPhraseScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Common Scam Wording",
                    explanation = "This message uses phrases commonly found in scams and phishing.",
                    evidence = extractScamPhraseEvidence(text)
                )
            )
        }

        // Delivery / parcel scam
        val deliveryScore = detectDeliveryScam(text)
        if (deliveryScore > 0) {
            totalScore += deliveryScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Delivery/Parcel Scam",
                    explanation = "Fake delivery messages often ask for payment or personal details to release a package.",
                    evidence = "Delivery/parcel wording detected"
                )
            )
        }

        // Tech support / refund scam
        val techSupportScore = detectTechSupportScam(text)
        if (techSupportScore > 0) {
            totalScore += techSupportScore
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "Tech Support / Refund Scam",
                    explanation = "Scammers pretend to be from tech companies or offer fake refunds to get access or payment.",
                    evidence = "Tech support / refund wording"
                )
            )
        }

        // Romance / advance-fee
        val romanceScore = detectRomanceOrAdvanceFee(text)
        if (romanceScore > 0) {
            totalScore += romanceScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Romance or Advance-Fee Scam",
                    explanation = "Messages asking for money for travel, emergencies, or 'gifts' are common scams.",
                    evidence = "Romance/advance-fee wording"
                )
            )
        }

        // Investment / crypto scam
        val cryptoScore = detectInvestmentOrCryptoScam(text)
        if (cryptoScore > 0) {
            totalScore += cryptoScore
            indicators.add(
                Reason(
                    type = ReasonType.PATTERN,
                    title = "Investment or Crypto Scam",
                    explanation = "Unsolicited investment or cryptocurrency offers are often fraudulent.",
                    evidence = "Investment/crypto wording"
                )
            )
        }

        // Government / tax / legal threat
        val govScore = detectGovernmentOrTaxScam(text)
        if (govScore > 0) {
            totalScore += govScore
            indicators.add(
                Reason(
                    type = ReasonType.IDENTITY,
                    title = "Government/Tax Impersonation",
                    explanation = "Real agencies do not threaten arrest or demand payment via text or phone.",
                    evidence = "Government/tax/legal wording"
                )
            )
        }

        // Credential-harvesting link (login, verify, secure in URL path)
        if (links.isNotEmpty()) {
            val credentialLinkScore = analyzeLinkPathForPhishing(links)
            if (credentialLinkScore > 0) {
                totalScore += credentialLinkScore
                indicators.add(
                    Reason(
                        type = ReasonType.URL,
                        title = "Login/Verify Link",
                        explanation = "Links that look like login or verification pages are often used to steal passwords.",
                        evidence = links.first().take(60) + if (links.first().length > 60) "…" else ""
                    )
                )
            }
        }

        // Emoji obfuscation - scammers use emoji to evade filters or hide keywords
        val emojiObfuscationScore = detectEmojiObfuscation(text)
        if (emojiObfuscationScore > 0) {
            totalScore += emojiObfuscationScore
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "Emoji Obfuscation",
                    explanation = "Emoji may be used to hide or disguise scam-related text from filters.",
                    evidence = "High emoji density or emoji-surrounded keywords"
                )
            )
        }

        // Multi-signal boost: multiple scam categories = much higher confidence
        if (indicators.size >= 2) {
            totalScore += min(indicators.size * 8, 25)
        }
        if (indicators.size >= 3) {
            totalScore += 15
        }

        // Conversation context - prior messages mentioning bank/account + current OTP/payment = higher risk
        @Suppress("UNCHECKED_CAST")
        val priorMessages = metadata["conversationContext"] as? List<String> ?: emptyList()
        if (priorMessages.isNotEmpty()) {
            val contextText = priorMessages.joinToString(" ").lowercase()
            val hasBankingContext = listOf("bank", "account", "card", "payment", "verify").any { contextText.contains(it) }
            val currentAsksSensitive = listOf("otp", "code", "password", "pin", "pay", "transfer").any { text.lowercase().contains(it) }
            if (hasBankingContext && currentAsksSensitive) {
                totalScore += 15
                indicators.add(
                    Reason(
                        type = ReasonType.PATTERN,
                        title = "Context-Aware Risk",
                        explanation = "Previous messages discussed banking or accounts; this message requests sensitive information.",
                        evidence = "Conversation context analysis"
                    )
                )
            }
        }

        val finalScore = min(totalScore, 100)
        val severity = calculateSeverity(finalScore, indicators.size)
        val confidence = calculateConfidence(indicators.size, text.length)
        val threatType = determineThreatType(indicators)

        return RiskResult(
            score = finalScore,
            severity = severity,
            confidence = confidence,
            threatType = threatType,
            reasons = indicators,
            recommendedActions = generateTextActions(severity, threatType, source),
            explainLikeImFive = generateSimpleExplanation(severity, threatType, indicators.size),
            suggestedReplies = generateSuggestedReplies(severity, threatType)
        )
    }

    private fun generateSuggestedReplies(severity: RiskSeverity, threatType: ThreatType): List<String> {
        if (severity == RiskSeverity.LOW) return emptyList()
        return when (threatType) {
            ThreatType.OTP_TRAP -> listOf("I never share OTP codes.", "Please contact me through official channels.")
            ThreatType.PAYMENT_SCAM, ThreatType.ROMANCE_SCAM -> listOf("I don't send money to people I haven't met.", "Please verify through official channels.")
            ThreatType.PHISHING_ATTEMPT, ThreatType.MALICIOUS_LINK -> listOf("I don't click links in messages.", "I'll visit the website directly.")
            ThreatType.REMOTE_ACCESS_SCAM -> listOf("I don't install remote access software.", "I'll contact support directly.")
            ThreatType.SCAM_MESSAGE -> listOf("I've reported this to the authorities.", "Please stop contacting me.")
            else -> listOf("I've reported this. Please stop.", "I don't respond to unsolicited messages.")
        }
    }

    /**
     * Analyze URL for safety
     * B2C-SAFE: Only flag genuinely suspicious URLs. Don't penalize normal web browsing.
     */
    fun analyzeUrl(url: String): RiskResult {
        val indicators = mutableListOf<Reason>()
        var totalScore = 0

        // Extract domain once for all checks
        val domain = url.lowercase().removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringAfterLast("@").substringBefore(":")
            .removePrefix("www.")

        // Lookalike domain detection (matches domain only, not path)
        if (isLookAlikeDomain(url)) {
            totalScore += 40
            indicators.add(
                Reason(
                    type = ReasonType.URL,
                    title = "Lookalike Domain",
                    explanation = "This URL looks similar to a well-known website but isn't the real one.",
                    evidence = domain
                )
            )
        }

        // Suspicious TLD (only truly high-risk disposable TLDs)
        if (hasSuspiciousTld(url)) {
            totalScore += 20
            indicators.add(
                Reason(
                    type = ReasonType.URL,
                    title = "Suspicious Domain Extension",
                    explanation = "This website uses a domain extension commonly used in scams.",
                    evidence = extractTld(url)
                )
            )
        }

        // Punycode detection — check DOMAIN only, not the full URL
        if (domain.contains("xn--")) {
            totalScore += 25
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "Internationalized Domain",
                    explanation = "This URL uses special characters that may be trying to disguise its real address.",
                    evidence = "Punycode detected in domain"
                )
            )
        }

        // Excessive subdomains — raised threshold
        if (hasExcessiveSubdomains(url)) {
            totalScore += 15
            indicators.add(
                Reason(
                    type = ReasonType.URL,
                    title = "Suspicious URL Structure",
                    explanation = "This URL has an unusually complex structure often used to deceive users.",
                    evidence = domain
                )
            )
        }

        val finalScore = min(totalScore, 100)
        val severity = calculateSeverity(finalScore, indicators.size)
        val confidence = calculateConfidence(indicators.size, url.length)

        return RiskResult(
            score = finalScore,
            severity = severity,
            confidence = confidence,
            threatType = ThreatType.MALICIOUS_LINK,
            reasons = indicators,
            recommendedActions = generateUrlActions(severity),
            explainLikeImFive = if (indicators.isEmpty()) {
                "This link looks okay, but always be careful before clicking."
            } else {
                "This link looks suspicious. Don't click it unless you're absolutely sure it's safe."
            }
        )
    }

    /**
     * Analyze call metadata for scam indicators
     * EXPANDED: Catches fake calls, deepfake voice scams, spoofed numbers, international fraud
     */
    fun analyzeCall(
        phoneNumber: String,
        isIncoming: Boolean,
        callDuration: Long = 0,
        metadata: Map<String, Any> = emptyMap()
    ): RiskResult {
        val indicators = mutableListOf<Reason>()
        var totalScore = 0

        // International number check — EXPANDED country coverage
        if (isInternationalNumber(phoneNumber)) {
            totalScore += 20
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "International Number",
                    explanation = "This call is from an international number. Be cautious if you weren't expecting it.",
                    evidence = extractCountryCode(phoneNumber)
                )
            )
        }

        // Spoofed number pattern — EXPANDED detection
        if (looksLikeSpoofedNumber(phoneNumber)) {
            totalScore += 35
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "Possible Number Spoofing",
                    explanation = "This number's pattern suggests it may be fake or manipulated.",
                    evidence = "Pattern analysis"
                )
            )
        }

        // VoIP / disposable / burner indicators
        if (looksLikeVoipOrBurner(phoneNumber)) {
            totalScore += 25
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "VoIP / Burner Number",
                    explanation = "This type of number is commonly used for scam calls and can be easily disposed.",
                    evidence = "Number format analysis"
                )
            )
        }

        // Same-digit patterns (e.g. 555-5555, 111-1111)
        if (hasSuspiciousRepeatingDigits(phoneNumber)) {
            totalScore += 30
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "Fake Call Pattern",
                    explanation = "This number pattern is often used for fake or spoofed caller ID.",
                    evidence = "Repeating digit pattern"
                )
            )
        }

        // Check reputation database
        val reputationScore = checkPhoneReputation(phoneNumber)
        if (reputationScore > 0) {
            totalScore += reputationScore
            indicators.add(
                Reason(
                    type = ReasonType.REPUTATION,
                    title = "Reported by Others",
                    explanation = "This number has been flagged as suspicious by other users.",
                    evidence = "Community reports"
                )
            )
        }

        // Unknown / private / restricted
        if (isUnknownOrPrivate(phoneNumber)) {
            totalScore += 15
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "Unknown / Private Caller",
                    explanation = "Caller ID is hidden or unknown. Legitimate callers rarely hide their number.",
                    evidence = "Unknown/Private"
                )
            )
        }

        // Short number (potential spoofing)
        if (phoneNumber.replace(Regex("[^0-9]"), "").length in 4..6) {
            totalScore += 20
            indicators.add(
                Reason(
                    type = ReasonType.TECHNICAL,
                    title = "Unusually Short Number",
                    explanation = "This number format is uncommon and may indicate spoofing.",
                    evidence = "Short number format"
                )
            )
        }

        // Silent call / one-ring scam: call answered very briefly or hang-up immediately
        if (callDuration in 1..3000 && isIncoming) {
            totalScore += 25
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "Silent/One-Ring Scam Pattern",
                    explanation = "Very short call duration may indicate a one-ring scam to generate callback charges.",
                    evidence = "Call duration ${callDuration}ms"
                )
            )
        }

        // Duress indicator from metadata (when voice stress analysis is available)
        val voiceStressScore = (metadata["voice_stress_score"] as? Number)?.toFloat() ?: 0f
        if (voiceStressScore > 0.7f) {
            totalScore += 40
            indicators.add(
                Reason(
                    type = ReasonType.BEHAVIOR,
                    title = "Possible Duress/Coercion",
                    explanation = "Voice analysis suggests the caller may be under stress or coercion.",
                    evidence = "Voice stress score: $voiceStressScore"
                )
            )
        }

        val finalScore = min(totalScore, 100)
        val severity = calculateSeverity(finalScore, indicators.size)
        val confidence = calculateConfidence(indicators.size, phoneNumber.length)

        return RiskResult(
            score = finalScore,
            severity = severity,
            confidence = confidence,
            threatType = ThreatType.SUSPICIOUS_CALL,
            reasons = indicators,
            recommendedActions = generateCallActions(severity, isIncoming),
            explainLikeImFive = if (indicators.isEmpty()) {
                "This call doesn't show obvious red flags, but stay alert."
            } else {
                "This call looks suspicious. Be very careful about sharing any information."
            }
        )
    }

    /**
     * Analyze video for deepfake indicators
     */
    @Suppress("UNUSED_PARAMETER")
    fun analyzeVideo(
        faceConsistencyScore: Float,
        temporalAnomalies: Int,
        audioVisualMismatch: Float,
        _metadata: Map<String, Any> = emptyMap()
    ): RiskResult {
        val indicators = mutableListOf<Reason>()
        var totalScore = 0

        // Face inconsistency — lower threshold to catch more deepfakes
        if (faceConsistencyScore < 0.88f) {
            val inconsistencyScore = ((1.0f - faceConsistencyScore) * 80).toInt()
            totalScore += inconsistencyScore
            indicators.add(
                Reason(
                    type = ReasonType.MEDIA,
                    title = "Facial Inconsistencies",
                    explanation = "The faces in this video show unnatural variations that may indicate AI manipulation.",
                    evidence = "Consistency score: ${String.format("%.2f", faceConsistencyScore)}"
                )
            )
        }

        // Temporal anomalies (>=1 for actual anomalies; 0 = no penalty)
        if (temporalAnomalies >= 1) {
            val anomalyScore = min(temporalAnomalies * 12, 50)
            totalScore += anomalyScore
            indicators.add(
                Reason(
                    type = ReasonType.MEDIA,
                    title = "Unnatural Motion",
                    explanation = "This video contains motion patterns that don't look natural.",
                    evidence = "$temporalAnomalies anomalies detected"
                )
            )
        }

        // Audio-visual mismatch — lower threshold for lip-sync deepfakes
        if (audioVisualMismatch > 0.10f) {
            val mismatchScore = (audioVisualMismatch * 60).toInt()
            totalScore += mismatchScore
            indicators.add(
                Reason(
                    type = ReasonType.MEDIA,
                    title = "Audio-Visual Mismatch",
                    explanation = "The audio and video in this clip don't sync naturally, which is common in deepfakes.",
                    evidence = "Mismatch score: ${String.format("%.2f", audioVisualMismatch)}"
                )
            )
        }

        val finalScore = min(totalScore, 100)
        val severity = calculateSeverity(finalScore, indicators.size)
        val confidence = calculateConfidence(indicators.size, 100)

        return RiskResult(
            score = finalScore,
            severity = severity,
            confidence = confidence,
            threatType = ThreatType.DEEPFAKE_VIDEO,
            reasons = indicators,
            recommendedActions = generateVideoActions(severity),
            explainLikeImFive = if (indicators.isEmpty()) {
                "This video looks genuine based on our analysis."
            } else {
                "This video shows signs of AI manipulation. The person in the video may not have actually said or done these things."
            }
        )
    }

    // ========== Private Helper Methods ==========

    private fun detectUrgency(text: String): Int {
        val urgentPhrases = listOf(
            "urgent", "immediately", "act now", "limited time", "expires", "expiring",
            "account suspended", "verify now", "confirm now", "click here now", "click now",
            "last chance", "final notice", "action required", "respond immediately", "act immediately",
            "verify your account", "confirm your identity", "account locked", "account blocked",
            "reactivate", "suspended", "expire in", "expires in", "deadline", "time sensitive",
            "within 24 hours", "within 48 hours", "do not ignore", "don't ignore", "attention required",
            "click below", "click link", "tap here", "reply now", "text back", "call now",
            "asap", "as soon as possible", "right now", "urgent message", "important message",
            "your attention", "immediate action", "take action", "verify immediately",
            "confirm immediately", "update now", "update your account", "secure your account",
            "unusual activity", "suspicious activity", "we detected", "we've detected",
            "sign in now", "log in now", "password reset", "reset your password",
            "verify it's you", "is this you", "confirm it's you", "lock your account",
            "unlock your account", "restore access", "regain access", "confirm your details",
            "update your details", "validate your account", "reverify", "re-verify"
        )
        val lowerText = text.lowercase()
        val matches = urgentPhrases.count { lowerText.contains(it) }
        return min(matches * 8, 50)
    }

    private fun extractUrgentPhrases(text: String): List<String> {
        val urgentPhrases = listOf(
            "urgent", "immediately", "act now", "limited time", "expires",
            "account suspended", "verify now", "confirm now"
        )
        return urgentPhrases.filter { text.lowercase().contains(it) }
    }

    private fun detectOtpRequest(text: String): Int {
        val otpKeywords = listOf(
            "otp", "one time password", "one-time password", "verification code", "verify code",
            "share code", "send code", "do not share this code", "never share this code",
            "your code is", "enter this code", "use this code", "type this code",
            "one-time code", "security code", "login code", "confirm code", "auth code",
            "pin code", "activation code", "your verification", "code to verify",
            "text you a code", "sent you a code", "requested code", "temporary code",
            "one time code", "single use code", "sms code", "text code", "6 digit",
            "6-digit", "4 digit", "4-digit", "enter the code", "submit the code",
            "provide the code", "give us the code", "reply with the code", "send us the code"
        )
        val lowerText = text.lowercase()
        val matchCount = otpKeywords.count { lowerText.contains(it) }
        return if (matchCount > 0) min(40 + matchCount * 5, 55) else 0
    }

    /**
     * Detect organization impersonation in messages.
     *
     * B2C-SAFE: Only flag impersonation when message is from an UNKNOWN sender
     * (phone number) AND mentions organizations. Notifications from known apps
     * (senderInfo = package name like "com.spotify.music") should NOT be flagged
     * because the notification IS from that organization.
     */
    private fun detectImpersonation(text: String, senderInfo: String?): Int {
        val organizations = listOf(
            "bank", "banks", "banking", "paypal", "amazon",
            "irs", "inland revenue", "police", "government", "social security", "customs",
            "fedex", "ups", "dhl", "usps", "royal mail", "hmrc", "revenue", "tax office",
            "customer service", "support team", "security department", "fraud team", "fraud prevention",
            "hmrc", "fca", "fbi", "cia", "interpol", "nhs", "dvla", "dvsa",
            "barclays", "hsbc", "lloyds", "natwest", "santander", "chase", "wells fargo",
            "your bank", "your account", "our records", "our system", "our security"
        )
        // REMOVED: "netflix", "spotify", "google", "microsoft", "apple", "apple id",
        // "google account", "microsoft account" — these trigger false positives when
        // those companies send legitimate notifications through their own apps.

        val lowerText = text.lowercase()
        val mentionCount = organizations.count { lowerText.contains(it) }
        if (mentionCount == 0) return 0

        // Only consider it impersonation from unknown senders (phone numbers).
        // App package names (com.xxx.yyy) are verified by Play Store — they ARE the real org.
        val fromPhoneNumber = senderInfo?.matches(Regex("\\+?\\d+")) ?: true
        val fromApp = senderInfo?.contains(".") == true  // Package names have dots

        return when {
            fromApp -> 0 // Notification from a real app — not impersonation
            fromPhoneNumber -> min(25 + mentionCount * 5, 45)  // SMS from phone number — suspicious
            else -> 0
        }
    }

    private fun extractImpersonationClaims(text: String): String {
        val organizations = listOf("bank", "paypal", "amazon", "google", "irs", "police")
        val found = organizations.find { text.lowercase().contains(it) }
        return found?.let { "Claims to be from $it" } ?: "Organization impersonation"
    }

    /**
     * Detect payment/financial pressure in messages.
     *
     * B2C-SAFE: Removed common legitimate payment words like "payment", "subscription renewal",
     * "invoice", "billing address", "membership fee" which appear in every Spotify, Netflix,
     * bank, or SaaS notification. Only flag when there's actual PRESSURE to pay urgently.
     */
    private fun detectPaymentPressure(text: String): Int {
        val paymentKeywords = listOf(
            // Truly suspicious payment pressure:
            "pay now", "send money", "bank details", "card number", "card details",
            "account number", "wire", "western union", "gift card", "gift cards",
            "overcharge", "unrecognized transaction", "unauthorized transaction",
            "bitcoin", "btc", "crypto", "cryptocurrency",
            "bank transfer", "wire transfer", "send payment",
            "click to pay", "pay here",
            "cvv", "expiry"
            // REMOVED common legitimate terms: "payment", "paypal", "venmo", "cashapp", "zelle",
            // "refund", "outstanding balance", "overdue", "invoice", "billing address",
            // "subscription renewal", "renewal fee", "membership fee", "processing fee",
            // "debit card", "credit card", "secure payment", "make a payment",
            // "pay your bill", "settle your account", "past due"
        )
        val lowerText = text.lowercase()
        val matches = paymentKeywords.count { lowerText.contains(it) }
        return min(matches * 10, 50)
    }

    private fun detectRemoteAccessRequest(text: String): Int {
        val remoteApps = listOf(
            "anydesk", "teamviewer", "quicksupport", "remote access", "screen share",
            "remote desktop", "logmein", "join.me", "zoom", "go to meeting",
            "let me in", "access your screen", "control your pc", "fix your computer"
        )
        val lowerText = text.lowercase()
        return if (remoteApps.any { lowerText.contains(it) }) 55 else 0
    }

    private fun detectGenericScamPhrases(text: String): Int {
        val scamPhrases = listOf(
            // Prize/lottery
            "congratulations", "congrats", "you have won", "you've won", "you won", "winner", "claim your", "claim your prize",
            "free gift", "free prize", "free money", "claim now", "claim today", "click to claim", "claim before",
            "lottery", "draw winner", "lucky winner", "won a prize", "you are the winner", "cash prize", "instant win",
            // Account lock/verify
            "account locked", "account blocked", "verify your account", "confirm your identity", "confirm your account",
            "unusual sign-in", "suspicious activity", "we noticed", "we've noticed", "we have noticed",
            "your account has been", "your card has been", "we've locked", "we have locked", "account suspended",
            "verify below", "confirm below", "update below", "click below to", "tap below to",
            "suspended until", "blocked until", "locked until", "restricted until",
            "someone tried", "attempt to access", "attempted login", "failed login",
            "unusual login", "new device", "new sign-in", "from new location",
            // Dear/greeting
            "dear customer", "dear member", "dear account holder", "dear friend", "dear sir", "dear madam",
            "valued customer", "honored customer", "esteemed customer",
            // Urgency
            "action required", "immediate action", "required action", "attention required",
            "urgent matter", "asap", "confidential", "don't miss", "do not miss", "last day", "final day", "expires today",
            "limited offer", "exclusive offer", "one time offer", "special offer", "act fast",
            // Links/clicks
            "click here", "follow this link", "open this link", "visit this link", "go to this link",
            "reply with", "text back with", "send us your", "provide your", "enter your",
            "to avoid", "to prevent", "to secure", "to unlock", "to restore", "to reactivate",
            // Delivery/package
            "your package", "delivery failed", "parcel held", "pay to release", "customs fee", "delivery fee",
            "track your order", "order status", "shipment", "redelivery", "reschedule delivery",
            // Money/inheritance
            "tax refund", "you are owed", "you're owed", "inheritance", "wire transfer", "nigerian", "prince",
            // Common SMS scam triggers
            // REMOVED brand names: "paypal", "amazon", "netflix", "spotify", "apple",
            // "microsoft", "google" — these caused massive false positives on LEGITIMATE
            // notifications from those companies. A Spotify notification mentioning "Spotify"
            // is NOT a scam. Brand impersonation is handled by detectImpersonation() which
            // already checks if the sender is a phone number vs a real app.
            "hi this is", "hello this is", "hey its", "hi its",
            "refund available", "refund pending", "overcharged", "double charged",
            "verify payment", "confirm payment", "update payment",
            "unusual activity", "fraud alert", "identity theft", "suspicious transaction"
        )
        val lowerText = text.lowercase()
        val matches = scamPhrases.count { lowerText.contains(it) }
        return min(matches * 5, 60)
    }

    private fun extractScamPhraseEvidence(text: String): String {
        val scamPhrases = listOf(
            "congratulations", "you have won", "claim", "free gift", "account locked",
            "verify your account", "dear customer", "action required", "urgent"
        )
        val found = scamPhrases.find { text.lowercase().contains(it) }
        return found ?: "Scam wording detected"
    }

    private fun detectDeliveryScam(text: String): Int {
        val phrases = listOf(
            "your package", "your parcel", "delivery failed", "delivery attempt", "couldn't deliver",
            "parcel held", "package held", "customs fee", "customs charge", "release fee",
            "pay to release", "pay for delivery", "redelivery", "re-delivery",
            "track your package", "tracking number", "delivery update", "shipping update",
            "dhl", "fedex", "ups", "usps", "royal mail", "courier", "post office", "postal",
            "final delivery attempt", "awaiting collection", "pick up your package",
            "missed delivery", "delivery pending", "order shipped", "out for delivery",
            "package waiting", "delivery fee", "shipping fee", "handling fee",
            "receiver fee", "import fee", "duty fee", "clearance fee"
        )
        val lowerText = text.lowercase()
        val matches = phrases.count { lowerText.contains(it) }
        return min(matches * 10, 50)
    }

    /**
     * Detect tech support scam patterns.
     *
     * B2C-SAFE: Removed brand names ("microsoft", "apple", "windows", "mac") that trigger
     * false positives on real notifications from those companies. Only flag when combined
     * with actual tech support scam language (remote access, call this number, etc.)
     */
    private fun detectTechSupportScam(text: String): Int {
        val phrases = listOf(
            // Genuine tech support scam indicators (not brand names)
            "tech support", "fix your computer", "fix your device",
            "remote access", "screen share", "call us to", "call this number",
            "we've detected a problem", "virus detected on your",
            "your computer has been compromised", "your device is infected"
            // REMOVED: "microsoft", "windows", "apple", "mac", "virus", "malware",
            // "infected", "support team", "customer support", "refund", "overcharge",
            // "billing issue", "billing error", "unauthorized charge", "cancel subscription",
            // "renew your subscription", "security alert", "suspicious activity on your account"
        )
        val lowerText = text.lowercase()
        val matches = phrases.count { lowerText.contains(it) }
        return min(matches * 10, 50)
    }

    private fun detectRomanceOrAdvanceFee(text: String): Int {
        val phrases = listOf(
            "i need your help", "can you help me", "send me money", "need money for",
            "travel to see you", "visa fee", "flight ticket", "medical emergency",
            "stuck abroad", "stranded", "need funds", "urgent help", "love you",
            "sweetheart", "darling", "my dear", "future together", "gift for you",
            "western union", "moneygram", "transfer me", "send bitcoin", "send crypto",
            "inheritance", "lottery", "need to pay fee", "release funds", "clearance fee"
        )
        val lowerText = text.lowercase()
        val matches = phrases.count { lowerText.contains(it) }
        return min(matches * 12, 45)
    }

    private fun detectInvestmentOrCryptoScam(text: String): Int {
        val phrases = listOf(
            "bitcoin", "crypto", "cryptocurrency", "btc", "eth", "ethereum",
            "investment opportunity", "guaranteed returns", "double your money",
            "trading platform", "mining", "wallet", "send crypto", "transfer btc",
            "no risk", "limited spots", "exclusive opportunity", "insider tip",
            "forex", "trading signal", "pump and dump", "get rich", "passive income"
        )
        val lowerText = text.lowercase()
        val matches = phrases.count { lowerText.contains(it) }
        return min(matches * 12, 50)
    }

    private fun detectGovernmentOrTaxScam(text: String): Int {
        val phrases = listOf(
            "irs", "inland revenue", "hmrc", "tax office", "revenue service",
            "arrest warrant", "arrest order", "warrant for your arrest",
            "social security", "benefits suspended", "national insurance",
            "court order", "legal action", "lawsuit", "sue you",
            "pay your tax", "outstanding tax", "tax debt", "tax refund",
            "government grant", "stimulus", "relief payment", "benefit payment",
            "customs", "border", "immigration", "visa", "deportation"
        )
        val lowerText = text.lowercase()
        val matches = phrases.count { lowerText.contains(it) }
        return min(matches * 12, 50)
    }

    private fun detectEmojiObfuscation(text: String): Int {
        val emojiRanges = listOf(
            0x1F300..0x1F9FF, 0x2600..0x26FF, 0x2700..0x27BF,
            0x1F600..0x1F64F, 0x1F000..0x1F02F
        )
        // Use codePoints() to correctly handle supplementary plane emoji (above U+FFFF)
        val emojiCount = text.codePoints().filter { cp ->
            emojiRanges.any { cp in it }
        }.count().toInt()
        val charCount = text.count { it.isLetterOrDigit() || it.isWhitespace() }
        if (charCount == 0) return 0
        val emojiRatio = emojiCount.toFloat() / (emojiCount + charCount)
        return when {
            emojiRatio > 0.3f -> 20
            emojiRatio > 0.15f && text.lowercase().let { it.contains("click") || it.contains("link") || it.contains("verify") } -> 25
            emojiCount >= 5 && text.length < 200 -> 15
            else -> 0
        }
    }

    private fun analyzeLinkPathForPhishing(links: List<String>): Int {
        val phishingPathTerms = listOf(
            "login", "signin", "sign-in", "log-in", "verify", "verification",
            "secure", "security", "account", "update", "confirm", "validation",
            "password", "reset", "recover", "unlock", "suspended", "locked",
            "bank", "paypal", "amazon", "apple", "microsoft", "google",
            "credential", "authenticate", "2fa", "otp", "auth", "sign"
        )
        for (link in links) {
            val lower = link.lowercase()
            val afterScheme = lower.substringAfter("//", "")
            val path = afterScheme.substringAfter("/", "")
            val matchCount = phishingPathTerms.count { path.contains(it) }
            if (matchCount > 0) return min(25 + matchCount * 5, 45)
        }
        return 0
    }

    private fun extractRemoteAccessApps(text: String): String {
        val remoteApps = listOf("anydesk", "teamviewer", "quicksupport")
        return remoteApps.find { text.lowercase().contains(it) } ?: "Remote access app"
    }

    private fun extractLinks(text: String): List<String> {
        val urlPattern = Regex("""https?://[^\s]+""")
        return urlPattern.findAll(text).map { it.value }.toList()
    }

    private fun analyzeLinkSafety(links: List<String>): Int {
        var score = 0
        links.forEach { link ->
            if (isLookAlikeDomain(link)) score += 25
            if (hasSuspiciousTld(link)) score += 15
            // Punycode: check DOMAIN only, not the full URL path
            val linkDomain = link.lowercase().removePrefix("https://").removePrefix("http://")
                .substringBefore("/").substringBefore("?").substringAfterLast("@").substringBefore(":")
            if (linkDomain.contains("xn--")) score += 20
        }
        return min(score, 40)
    }

    // Legitimate brand-affiliated domains that contain brand keywords but are NOT phishing.
    // Comprehensive list to prevent false positives from CDNs, APIs, and official sub-brands.
    private val knownSafeBrandDomains = setOf(
        // Google ecosystem
        "googleapis.com", "googleusercontent.com", "googlevideo.com",
        "googleadservices.com", "googlesyndication.com", "gstatic.com",
        "googletagmanager.com", "google-analytics.com", "googlesource.com",
        "googlecode.com", "googlegroups.com", "googlemail.com",
        "google.com", "google.co.uk", "google.co.in", "google.de", "google.fr",
        "google.ca", "google.com.au", "google.co.jp", "google.com.br",
        // Amazon ecosystem
        "amazonaws.com", "amazonws.com", "cloudfront.net", "amazon.com",
        "amazon.co.uk", "amazon.de", "amazon.co.jp", "amazon.in",
        "amazonpay.com", "primevideo.com",
        // Microsoft ecosystem
        "microsoftonline.com", "microsoft.com", "azure.com", "live.com",
        "windows.com", "windowsupdate.com", "office.com", "office365.com",
        "outlook.com", "outlook.live.com", "onedrive.com", "bing.com",
        "msn.com", "skype.com", "linkedin.com", "github.com",
        // Apple ecosystem
        "apple.com", "apple-dns.net", "mzstatic.com", "apple.news",
        "icloud.com", "icloud-content.com", "itunes.com",
        // Meta/Facebook ecosystem
        "facebook.com", "fbcdn.net", "facebook.net", "fb.com",
        "instagram.com", "whatsapp.com", "whatsapp.net", "messenger.com",
        // Other major platforms
        "netflix.com", "nflxext.com", "nflximg.net", "nflxvideo.net",
        "paypal.com", "paypalobjects.com",
        "twitter.com", "twimg.com", "x.com",
        "ebay.com", "ebaystatic.com",
        "yahoo.com", "yimg.com",
        "dropbox.com", "dropboxusercontent.com",
        "telegram.org", "t.me",
        "alibaba.com", "alicdn.com", "aliexpress.com",
        "githubusercontent.com", "github.io",
        "osint.digitalside.it",
        // CDNs & infrastructure
        "akamaized.net", "akamai.net", "fastly.net", "cdnjs.cloudflare.com",
        "cloudflare.com", "unpkg.com", "jsdelivr.net"
    )

    private fun isLookAlikeDomain(url: String): Boolean {
        // B2C-SAFE: Only match brand keywords against the DOMAIN portion, NEVER path/query.
        // "bank" removed — flags every banking site. "irs"/"hmrc" removed — too generic.
        // Intentionally kept list focused on brand impersonation via domain name only.
        val knownBrands = listOf(
            "paypal", "amazon", "google", "microsoft", "netflix",
            "icloud", "facebook", "instagram", "whatsapp",
            "paypa1", "amaz0n", "g00gle", "micros0ft"
        )
        val lowerUrl = url.lowercase()
        // Extract just the domain from the URL
        val domain = lowerUrl.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringAfterLast("@").substringBefore(":")
            .removePrefix("www.")
        // Known safe brand-affiliated domains must never be flagged as lookalikes
        if (knownSafeBrandDomains.any { domain == it || domain.endsWith(".$it") }) return false
        val intlTlds = listOf(".com", ".co.uk", ".co.in", ".co.jp", ".de", ".fr",
            ".com.au", ".com.br", ".ca", ".org", ".me", ".in", ".net", ".io")
        val baseDomain = domain.substringBeforeLast(".")
        return knownBrands.any { brand ->
            // Match against DOMAIN only (not full URL path)
            baseDomain.contains(brand) &&
                intlTlds.none { tld -> domain == "$brand${tld.removePrefix(".")}" || domain.endsWith(".$brand${tld.removePrefix(".")}") }
        }
    }

    private fun hasSuspiciousTld(url: String): Boolean {
        // B2C-SAFE: Removed mainstream TLDs that legitimate businesses use:
        // .info (Wikipedia alternatives, EU businesses), .news (news sites), .live (MS, gaming),
        // .online (banks, businesses), .store/.shop (e-commerce), .link, .site, .space
        val suspiciousTlds = listOf(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".top", ".work", ".click",
            ".buzz", ".stream", ".download", ".win", ".racing", ".bid", ".icu",
            ".loan", ".date"
        )
        val domain = url.lowercase()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringAfterLast("@").substringBefore(":")
            .removePrefix("www.")
        return suspiciousTlds.any { domain.endsWith(it) }
    }

    private fun extractTld(url: String): String {
        val domain = url.lowercase()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringAfterLast("@").substringBefore(":")
            .removePrefix("www.")
        return ".${domain.substringAfterLast('.', "unknown")}"
    }

    private fun hasExcessiveSubdomains(url: String): Boolean {
        // Raised from 3 to 4 — CDNs and legitimate services routinely have 4 levels
        val domain = url.substringAfter("://").substringBefore("/")
            .substringAfterLast("@").substringBefore(":")
        return domain.count { it == '.' } > 4
    }

    private fun isInternationalNumber(phoneNumber: String): Boolean {
        return phoneNumber.startsWith("+") && !phoneNumber.startsWith("+1")
    }

    private fun extractCountryCode(phoneNumber: String): String {
        return if (phoneNumber.startsWith("+")) {
            phoneNumber.substring(0, min(3, phoneNumber.length))
        } else "Unknown"
    }

    private fun looksLikeSpoofedNumber(phoneNumber: String): Boolean {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        if (digitsOnly.length < 7) return false
        if (digitsOnly.length > 30) return false
        
        val sequential = Regex("""(012|123|234|345|456|567|678|789|890|321|432|543|654|765|876|987)""")
        val repeated = Regex("""(\d)\1{4,}""")
        val palindrome = digitsOnly == digitsOnly.reversed() && digitsOnly.length >= 6
        val mostlySame = digitsOnly.toSet().size <= 2 && digitsOnly.length >= 6
        return sequential.containsMatchIn(digitsOnly) || repeated.containsMatchIn(digitsOnly) || palindrome || mostlySame
    }

    private fun looksLikeVoipOrBurner(phoneNumber: String): Boolean {
        val digits = phoneNumber.replace(Regex("[^0-9+]"), "")
        // Toll-free lookalike, 1-800 style spoofing (often abused)
        if (digits.startsWith("1800") || digits.startsWith("1888") || digits.startsWith("1877") || digits.startsWith("1866")) {
            if (digits.length > 10) return true
        }
        // Very repetitive digits after country code (common in VoIP/burner)
        if (digits.length >= 10) {
            val last6 = digits.takeLast(6)
            if (last6.toSet().size <= 2) return true
        }
        return false
    }

    private fun hasSuspiciousRepeatingDigits(phoneNumber: String): Boolean {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 6) return false
        // All same digit
        if (digits.toSet().size == 1) return true
        // Alternating (e.g. 121212)
        val half = digits.length / 2
        if (half >= 3 && digits.take(half) == digits.drop(half).take(half)) return true
        return false
    }

    private fun isUnknownOrPrivate(phoneNumber: String): Boolean {
        val p = phoneNumber.lowercase().trim()
        return p == "unknown" || p == "private" || p == "restricted" || p == "anonymous" ||
            p == "blocked" || p.isEmpty() || p == "null"
    }

    /**
     * Check phone number reputation using real heuristic analysis:
     * - Known scam country code prefixes
     * - VoIP/burner phone indicators
     * - Formatting anomalies
     * - User-reported numbers (session-level)
     * Returns a risk score: 0 = neutral, positive = suspicious, negative = trusted
     */
    private fun checkPhoneReputation(phoneNumber: String): Int {
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleaned.length < 4) return 0
        
        var score = 0
        
        // 1. EXPANDED: Known high-risk international prefixes (scam call origins worldwide)
        val dialPrefixes = listOf(
            "234", "233", "225", "221", "880", "92", "91", "855", "856",
            "95", "66", "63", "7", "380", "86", "84", "62", "60", "81",
            "82", "98", "966", "971", "20", "27", "254", "237", "212",
            "351", "34", "39", "33", "49", "31", "32", "48", "358",
            "353", "44", "61", "64", "65", "55", "52", "57", "58",
            "54", "51", "56", "46", "47", "421"
        )
        val digitsOnly = cleaned.filter { it.isDigit() }
        for (prefix in dialPrefixes) {
            if (digitsOnly.startsWith(prefix) || cleaned.startsWith("+$prefix")) {
                score += 18
                break
            }
        }
        // Extra risk for very common scam-origin prefixes
        val veryHighRisk = listOf("234", "92", "91", "63", "855", "880", "86", "351")
        if (veryHighRisk.any { digitsOnly.startsWith(it) || cleaned.startsWith("+$it") }) score += 12
        
        // 2. Premium rate numbers — match US 1-900/1-976 prefixes only, not substrings
        val usPremiumPattern = Regex("^\\+?1(900|976)")
        if (usPremiumPattern.containsMatchIn(cleaned)) {
            score += 25
        }
        
        // 3. Very short numbers (potential spoofed caller ID)
        if (cleaned.length in 4..6 && !cleaned.startsWith("+")) {
            score += 15
        }
        
        // 4. Auto-generated look (sequential/repetitive digits)
        val digits = cleaned.filter { it.isDigit() }
        if (digits.length >= 6) {
            val last6 = digits.takeLast(6)
            if (last6.toSet().size <= 2) score += 18
            var sequential = 0
            for (i in 1 until last6.length) {
                if (last6[i].digitToInt() == last6[i-1].digitToInt() + 1) sequential++
            }
            if (sequential >= 4) score += 12
        }
        
        // 5. User-reported numbers (session-level cache)
        if (userReportedScamNumbers.contains(cleaned)) {
            score += 35
        }
        
        // 6. Blocked list
        if (blockedNumbers.contains(cleaned)) {
            score += 45
        }
        
        return score
    }
    
    // Session-level caches for user-reported data
    private val userReportedScamNumbers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val blockedNumbers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    /**
     * Report a phone number as scam (adds to session-level reputation cache)
     */
    fun reportScamNumber(phoneNumber: String) {
        userReportedScamNumbers.add(phoneNumber.replace(Regex("[^0-9+]"), ""))
    }
    
    /**
     * Block a phone number (session-level cache)
     */
    fun blockNumber(phoneNumber: String) {
        blockedNumbers.add(phoneNumber.replace(Regex("[^0-9+]"), ""))
    }

    /**
     * Load blocked numbers from database into the session cache.
     * Call this on service startup to sync with persisted data.
     */
    fun syncBlockedNumbers(numbers: List<String>) {
        numbers.forEach { number ->
            blockedNumbers.add(number.replace(Regex("[^0-9+]"), ""))
        }
    }

    /**
     * Check if a number is blocked in the session cache
     */
    fun isNumberBlocked(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        return blockedNumbers.contains(cleaned)
    }

    private fun calculateSeverity(score: Int, indicatorCount: Int): RiskSeverity {
        // B2C-SAFE: Raised thresholds significantly to avoid false positives.
        // Users WILL uninstall if every Spotify/Netflix notification triggers a HIGH alert.
        // Require both a high score AND multiple indicators for severe classifications.
        return when {
            score >= 65 && indicatorCount >= 4 -> RiskSeverity.CRITICAL
            score >= 45 && indicatorCount >= 3 -> RiskSeverity.HIGH
            score >= 25 && indicatorCount >= 2 -> RiskSeverity.MEDIUM
            score >= 15 && indicatorCount >= 1 -> RiskSeverity.LOW
            else -> RiskSeverity.LOW
        }
    }

    private fun calculateConfidence(indicatorCount: Int, contentLength: Int): Float {
        // B2C-SAFE: Confidence should reflect how certain we are this is a REAL threat.
        // A single indicator (e.g. "unusual activity" in a bank notification) is NOT
        // enough for high confidence. Require multiple indicators.
        val baseConfidence = when (indicatorCount) {
            0 -> 0.1f
            1 -> 0.3f   // Single indicator = low confidence (was 0.78!)
            2 -> 0.5f   // Two indicators = moderate
            3 -> 0.7f   // Three = good confidence
            4 -> 0.85f  // Four+ = high confidence
            else -> 0.92f
        }
        val lengthFactor = if (contentLength < 15) 0.85f else 1.0f
        return min(baseConfidence * lengthFactor, 1.0f)
    }

    private fun determineThreatType(indicators: List<Reason>): ThreatType {
        val reasonTypes = indicators.map { it.type }
        return when {
            indicators.any { it.title.contains("OTP", ignoreCase = true) } -> ThreatType.OTP_TRAP
            indicators.any { it.title.contains("Payment", ignoreCase = true) } -> ThreatType.PAYMENT_SCAM
            indicators.any { it.title.contains("Remote", ignoreCase = true) } -> ThreatType.REMOTE_ACCESS_SCAM
            indicators.any { it.title.contains("Impersonation", ignoreCase = true) } -> ThreatType.IMPERSONATION
            indicators.any { it.title.contains("Government", ignoreCase = true) } -> ThreatType.IMPERSONATION
            indicators.any { it.title.contains("Delivery", ignoreCase = true) } -> ThreatType.SCAM_MESSAGE
            indicators.any { it.title.contains("Tech Support", ignoreCase = true) } -> ThreatType.SCAM_MESSAGE
            indicators.any { it.title.contains("Romance", ignoreCase = true) } -> ThreatType.ROMANCE_SCAM
            indicators.any { it.title.contains("Investment", ignoreCase = true) } -> ThreatType.CRYPTO_SCAM
            indicators.any { it.title.contains("Login/Verify Link", ignoreCase = true) } -> ThreatType.PHISHING_ATTEMPT
            reasonTypes.contains(ReasonType.URL) -> ThreatType.MALICIOUS_LINK
            else -> ThreatType.SCAM_MESSAGE
        }
    }

    private fun generateTextActions(severity: RiskSeverity, threatType: ThreatType, source: ThreatSource): List<Action> {
        val actions = mutableListOf<Action>()
        
        if (severity >= RiskSeverity.MEDIUM) {
            actions.add(
                Action(
                    type = RecommendedAction.DELETE_MESSAGE,
                    title = "Delete This Message",
                    description = "Remove this suspicious message from your device",
                    isPrimary = true
                )
            )
            
            if (source == ThreatSource.SMS) {
                actions.add(
                    Action(
                        type = RecommendedAction.BLOCK_NUMBER,
                        title = "Block This Number",
                        description = "Prevent future messages from this sender",
                        isPrimary = false
                    )
                )
            }
        }
        
        if (threatType == ThreatType.OTP_TRAP) {
            actions.add(
                Action(
                    type = RecommendedAction.DO_NOT_SHARE_OTP,
                    title = "Never Share OTP",
                    description = "Do not respond or share any codes",
                    isPrimary = true
                )
            )
        }
        
        actions.add(
            Action(
                type = RecommendedAction.REPORT_SCAM,
                title = "Report as Scam",
                description = "Help protect others by reporting this",
                isPrimary = false
            )
        )
        
        if (severity >= RiskSeverity.HIGH) {
            actions.add(
                Action(
                    type = RecommendedAction.EXPORT_EVIDENCE,
                    title = "Export Evidence",
                    description = "Save this for reporting to authorities",
                    isPrimary = false
                )
            )
        }
        
        return actions
    }

    private fun generateUrlActions(severity: RiskSeverity): List<Action> {
        val actions = mutableListOf<Action>()
        
        if (severity >= RiskSeverity.MEDIUM) {
            actions.add(
                Action(
                    type = RecommendedAction.DO_NOT_CLICK_LINK,
                    title = "Don't Click",
                    description = "Avoid opening this link",
                    isPrimary = true
                )
            )
        }
        
        actions.add(
            Action(
                type = RecommendedAction.VERIFY_OFFICIAL_CHANNEL,
                title = "Verify Through Official Channel",
                description = "Check the organization's official website or app",
                isPrimary = severity < RiskSeverity.MEDIUM
            )
        )
        
        actions.add(
            Action(
                type = RecommendedAction.REPORT_SCAM,
                title = "Report This Link",
                description = "Help protect others",
                isPrimary = false
            )
        )
        
        return actions
    }

    private fun generateCallActions(severity: RiskSeverity, isIncoming: Boolean): List<Action> {
        val actions = mutableListOf<Action>()
        
        if (isIncoming && severity >= RiskSeverity.MEDIUM) {
            actions.add(
                Action(
                    type = RecommendedAction.HANG_UP_IMMEDIATELY,
                    title = "Hang Up",
                    description = "End this call immediately",
                    isPrimary = true
                )
            )
            
            actions.add(
                Action(
                    type = RecommendedAction.BLOCK_NUMBER,
                    title = "Block This Number",
                    description = "Prevent future calls",
                    isPrimary = false
                )
            )
        }
        
        actions.add(
            Action(
                type = RecommendedAction.DO_NOT_SHARE_OTP,
                title = "Never Share OTP or Personal Info",
                description = "Legitimate organizations won't ask for this over the phone",
                isPrimary = !isIncoming || severity < RiskSeverity.MEDIUM
            )
        )
        
        actions.add(
            Action(
                type = RecommendedAction.VERIFY_OFFICIAL_CHANNEL,
                title = "Call Back Using Official Number",
                description = "Look up the official number yourself",
                isPrimary = false
            )
        )
        
        return actions
    }

    private fun generateVideoActions(severity: RiskSeverity): List<Action> {
        val actions = mutableListOf<Action>()
        
        if (severity >= RiskSeverity.HIGH) {
            actions.add(
                Action(
                    type = RecommendedAction.DELETE_VIDEO,
                    title = "Delete This Video",
                    description = "Consider removing if it's misleading",
                    isPrimary = true
                )
            )
            
            actions.add(
                Action(
                    type = RecommendedAction.EXPORT_EVIDENCE,
                    title = "Export Analysis",
                    description = "Save the analysis report",
                    isPrimary = false
                )
            )
        }
        
        actions.add(
            Action(
                type = RecommendedAction.INVESTIGATE_FURTHER,
                title = "Verify Content",
                description = "Look for the original source or fact-checks",
                isPrimary = severity < RiskSeverity.HIGH
            )
        )
        
        actions.add(
            Action(
                type = RecommendedAction.REPORT_SCAM,
                title = "Report Deepfake",
                description = "Help flag manipulated content",
                isPrimary = false
            )
        )
        
        return actions
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generateSimpleExplanation(severity: RiskSeverity, _threatType: ThreatType, _indicatorCount: Int): String {
        return when (severity) {
            RiskSeverity.CRITICAL -> {
                "This looks very suspicious and dangerous. Multiple warning signs suggest this is a scam. Do not trust it."
            }
            RiskSeverity.HIGH -> {
                "This looks suspicious. Several things don't seem right. Be very careful and verify before taking any action."
            }
            RiskSeverity.MEDIUM -> {
                "This looks a bit suspicious. Some things seem unusual. Double-check before you trust it or take any action."
            }
            RiskSeverity.LOW -> {
                "This doesn't show major red flags, but it's always good to be careful. If something feels wrong, trust your instincts."
            }
        }
    }
}
