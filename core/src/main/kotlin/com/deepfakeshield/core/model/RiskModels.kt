package com.deepfakeshield.core.model

/**
 * Severity levels for risk assessment
 */
enum class RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Types of threats that can be detected
 */
enum class ThreatType {
    DEEPFAKE_VIDEO,
    SCAM_MESSAGE,
    MALICIOUS_LINK,
    SUSPICIOUS_CALL,
    PHISHING_ATTEMPT,
    IMPERSONATION,
    OTP_TRAP,
    PAYMENT_SCAM,
    REMOTE_ACCESS_SCAM,
    ROMANCE_SCAM,
    JOB_SCAM,
    CRYPTO_SCAM,
    MALWARE,
    PUA,
    RANSOMWARE,
    TROJAN,
    ADWARE,
    SPYWARE,
    UNKNOWN
}

/**
 * Reason categories for risk detection
 */
enum class ReasonType {
    URL,
    LANGUAGE,
    IDENTITY,
    BEHAVIOR,
    MEDIA,
    REPUTATION,
    TECHNICAL,
    PATTERN
}

/**
 * Source of the detected threat
 */
enum class ThreatSource {
    SMS,
    NOTIFICATION,
    CLIPBOARD,
    MANUAL_SCAN,
    VIDEO_FILE,
    SYSTEM_WIDE_SCAN,
    INCOMING_CALL,
    OUTGOING_CALL,
    GALLERY,
    FILE_DOWNLOAD,
    APP_INSTALL,
    REAL_TIME_SCAN,
    FULL_SCAN,
    SCHEDULED_SCAN,
    SCREEN_CAPTURE
}

/**
 * Recommended actions for the user
 */
enum class RecommendedAction {
    DELETE_MESSAGE,
    BLOCK_NUMBER,
    BLOCK_SENDER,
    REPORT_SCAM,
    VERIFY_OFFICIAL_CHANNEL,
    DO_NOT_CLICK_LINK,
    DO_NOT_SHARE_OTP,
    DO_NOT_SHARE_PAYMENT_INFO,
    HANG_UP_IMMEDIATELY,
    MARK_AS_SAFE,
    EXPORT_EVIDENCE,
    CONTACT_BANK,
    CHANGE_PASSWORD,
    ENABLE_2FA,
    DELETE_VIDEO,
    INVESTIGATE_FURTHER,
    IGNORE,
    NO_ACTION_NEEDED,
    QUARANTINE,
    UNINSTALL_APP,
    DELETE_FILE
}

/**
 * Detailed reason for flagging content
 */
data class Reason(
    val type: ReasonType,
    val title: String,
    val explanation: String,
    val evidence: String? = null
)

/**
 * Action that user can take
 */
data class Action(
    val type: RecommendedAction,
    val title: String,
    val description: String,
    val isPrimary: Boolean = false
)

/**
 * Unified risk assessment result used across all detection types
 */
data class RiskResult(
    val score: Int, // 0-100
    val severity: RiskSeverity,
    val confidence: Float, // 0.0-1.0
    val threatType: ThreatType,
    val reasons: List<Reason>,
    val recommendedActions: List<Action>,
    val explainLikeImFive: String,
    val technicalDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestedReplies: List<String> = emptyList()
) {
    /**
     * Human-readable severity label
     */
    val severityLabel: String
        get() = when (severity) {
            RiskSeverity.LOW -> "Low Risk"
            RiskSeverity.MEDIUM -> "Medium Risk"
            RiskSeverity.HIGH -> "High Risk"
            RiskSeverity.CRITICAL -> "Critical Risk"
        }

    /**
     * Should this trigger an immediate alert? (notification + bubble update)
     *
     * B2C-SAFE: Require MEDIUM+ severity with HIGH confidence to avoid
     * annoying false positives on legitimate app notifications, payment
     * confirmations, etc. Users WILL uninstall if spammed with false alerts.
     */
    val shouldAlert: Boolean
        get() = severity >= RiskSeverity.MEDIUM &&
                confidence >= 0.55f &&
                score >= 35 &&
                reasons.isNotEmpty()
}
