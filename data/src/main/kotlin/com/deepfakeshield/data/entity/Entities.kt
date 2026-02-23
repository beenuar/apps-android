package com.deepfakeshield.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType

/**
 * Alert entity representing a detected threat
 */
@Entity(
    tableName = "alerts",
    indices = [
        Index("timestamp"),
        Index("isHandled", "timestamp"),
        Index("severity"),
        Index("source"),
        Index("threatType")
    ]
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threatType: ThreatType,
    val source: ThreatSource,
    val severity: RiskSeverity,
    val score: Int,
    val confidence: Float,
    val title: String,
    val summary: String,
    val content: String?, // Original content (encrypted)
    val senderInfo: String?, // Phone number, sender name, etc.
    val timestamp: Long,
    val isHandled: Boolean = false,
    val handledAt: Long? = null,
    val userMarkedAs: String? = null, // "safe", "scam", "deepfake", etc.
    val isExported: Boolean = false,
    val metadata: String? = null // JSON string of additional data
)

/**
 * Reputation entry for phone numbers
 */
@Entity(tableName = "phone_reputation", indices = [Index(value = ["isBlocked"])])
data class PhoneReputationEntity(
    @PrimaryKey
    val phoneNumber: String,
    val trustScore: Int, // -100 to +100
    val reportCount: Int,
    val scamReports: Int,
    val safeReports: Int,
    val lastReportedAt: Long,
    val isBlocked: Boolean = false,
    val notes: String? = null
)

/**
 * Reputation entry for domains/URLs
 */
@Entity(tableName = "domain_reputation", indices = [Index(value = ["isBlocked"])])
data class DomainReputationEntity(
    @PrimaryKey
    val domain: String,
    val trustScore: Int, // -100 to +100
    val reportCount: Int,
    val scamReports: Int,
    val safeReports: Int,
    val lastReportedAt: Long,
    val isBlocked: Boolean = false,
    val category: String? = null // "phishing", "malware", etc.
)

/**
 * User feedback on alerts for learning
 */
@Entity(tableName = "user_feedback", indices = [Index(value = ["alertId"])])
data class UserFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alertId: Long,
    val feedbackType: String, // "false_positive", "confirmed_scam", "helpful", "not_helpful"
    val userMarkedAs: String?, // "safe", "scam", "deepfake"
    val notes: String?,
    val timestamp: Long
)

/**
 * Incident vault entries for long-term evidence storage
 */
@Entity(tableName = "vault_entries", indices = [Index(value = ["entryType"])])
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alertId: Long?,
    val entryType: String, // "scam_message", "malicious_link", "scam_call", "deepfake_video"
    val title: String,
    val description: String,
    val severity: RiskSeverity,
    val evidenceData: String, // JSON or encrypted data
    val metadata: String?,
    val tags: String?, // Comma-separated
    val createdAt: Long,
    val isArchived: Boolean = false
)

/**
 * Scam patterns learned from user feedback
 */
@Entity(tableName = "scam_patterns", indices = [Index(value = ["patternType"])])
data class ScamPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patternType: String, // "text_phrase", "url_pattern", "phone_prefix"
    val pattern: String,
    val threatType: ThreatType,
    val weight: Float, // How much this pattern contributes to risk score
    val confidence: Float,
    val hitCount: Int = 0,
    val createdAt: Long,
    val lastUsedAt: Long?
)

/**
 * Export history for audit trail
 */
@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exportType: String, // "json", "csv", "encrypted_zip"
    val itemCount: Int,
    val fileHash: String?,
    val exportedAt: Long,
    val filePath: String?
)

/**
 * Community threat reports (cached locally)
 */
@Entity(tableName = "community_threats")
data class CommunityThreatEntity(
    @PrimaryKey
    val threatHash: String,
    val threatType: String,
    val severity: Int,
    val reportCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val geographicRegion: String?,
    val metadata: String? // JSON
)

/**
 * Behavioral profiles for senders
 */
@Entity(tableName = "behavior_profiles")
data class BehaviorProfileEntity(
    @PrimaryKey
    val senderId: String,
    val messageCount: Int,
    val callCount: Int,
    val avgResponseTime: Long,
    val avgMessageLength: Int,
    val activeHours: String,             // Comma-separated hours
    val typingSpeed: Double?,
    val suspiciousPatterns: String?,     // JSON array
    val trustScore: Int,
    val firstContact: Long,
    val lastContact: Long
)

/**
 * Scammer fingerprints
 */
@Entity(tableName = "scammer_fingerprints")
data class ScammerFingerprintEntity(
    @PrimaryKey
    val fingerprintId: String,
    val phoneNumbers: String,            // JSON array
    val deviceSignatures: String?,       // JSON array
    val writingStyleHash: String,
    val activeHours: String,             // Comma-separated
    val targetedVictims: Int,
    val campaignIds: String?,            // JSON array
    val confidence: Float,
    val firstSeen: Long,
    val lastSeen: Long
)

/**
 * Adaptive learning pattern weights
 */
@Entity(tableName = "pattern_weights")
data class PatternWeightEntity(
    @PrimaryKey
    val patternId: String,
    val pattern: String,
    val weight: Double,
    val accuracy: Double,
    val falsePositiveRate: Double,
    val truePositiveCount: Int,
    val falsePositiveCount: Int,
    val lastUpdated: Long
)

/**
 * Discovered patterns from learning
 */
@Entity(tableName = "learned_patterns")
data class LearnedPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val confidence: Double,
    val occurrences: Int,
    val threatType: String,
    val discoveredAt: Long,
    val language: String? = null
)

/**
 * Scam campaigns
 */
@Entity(tableName = "scam_campaigns")
data class ScamCampaignEntity(
    @PrimaryKey
    val campaignId: String,
    val campaignType: String,
    val affectedUsers: Int,
    val scammerCount: Int,
    val commonPatterns: String,          // JSON array
    val startDate: Long,
    val endDate: Long?,
    val isActive: Boolean
)

/**
 * Threat predictions
 */
@Entity(tableName = "threat_predictions")
data class ThreatPredictionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threatType: String,
    val probability: Float,
    val expectedTimeframe: String,
    val targetAudience: String,
    val reasoning: String,
    val createdAt: Long,
    val isActive: Boolean = true
)

/**
 * User risk profile
 */
@Entity(tableName = "user_risk_profile")
data class UserRiskProfileEntity(
    @PrimaryKey
    val id: Int = 1,                     // Single row
    val overallRiskScore: Int,
    val age: Int?,
    val occupation: String?,
    val techSavviness: String?,
    val hasSharedPersonalInfo: Boolean,
    val clickedSuspiciousLinks: Int,
    val lastUpdated: Long
)

