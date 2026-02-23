package com.deepfakeshield.data.dao

import androidx.room.*
import com.deepfakeshield.data.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Community Threat DAO
 */
@Dao
interface CommunityThreatDao {
    @Query("SELECT * FROM community_threats ORDER BY lastSeen DESC")
    fun getAllThreats(): Flow<List<CommunityThreatEntity>>
    
    @Query("SELECT * FROM community_threats WHERE threatHash = :hash")
    suspend fun getThreatByHash(hash: String): CommunityThreatEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(threat: CommunityThreatEntity)
    
    @Query("SELECT * FROM community_threats WHERE threatType = :type ORDER BY reportCount DESC LIMIT :limit")
    suspend fun getThreatsByType(type: String, limit: Int): List<CommunityThreatEntity>
}

/**
 * Behavior Profile DAO
 */
@Dao
interface BehaviorProfileDao {
    @Query("SELECT * FROM behavior_profiles WHERE senderId = :senderId")
    suspend fun getProfile(senderId: String): BehaviorProfileEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BehaviorProfileEntity)
    
    @Query("SELECT * FROM behavior_profiles WHERE trustScore < :threshold ORDER BY lastContact DESC")
    fun getSuspiciousProfiles(threshold: Int): Flow<List<BehaviorProfileEntity>>
}

/**
 * Scammer Fingerprint DAO
 */
@Dao
interface ScammerFingerprintDao {
    @Query("SELECT * FROM scammer_fingerprints WHERE fingerprintId = :id")
    suspend fun getFingerprint(id: String): ScammerFingerprintEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fingerprint: ScammerFingerprintEntity)
    
    @Query("SELECT * FROM scammer_fingerprints ORDER BY lastSeen DESC LIMIT :limit")
    fun getRecentScammers(limit: Int): Flow<List<ScammerFingerprintEntity>>
    
    @Query("SELECT * FROM scammer_fingerprints WHERE phoneNumbers = :phone LIMIT 1")
    suspend fun findByPhoneNumber(phone: String): List<ScammerFingerprintEntity>
}

/**
 * Pattern Weight DAO
 */
@Dao
interface PatternWeightDao {
    @Query("SELECT * FROM pattern_weights WHERE patternId = :id")
    suspend fun getWeight(id: String): PatternWeightEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weight: PatternWeightEntity)
    
    @Query("SELECT * FROM pattern_weights ORDER BY weight DESC")
    fun getAllWeights(): Flow<List<PatternWeightEntity>>
    
    @Query("SELECT * FROM pattern_weights WHERE falsePositiveRate > :threshold")
    suspend fun getProblematicPatterns(threshold: Double): List<PatternWeightEntity>
}

/**
 * Learned Pattern DAO
 */
@Dao
interface LearnedPatternDao {
    @Query("SELECT * FROM learned_patterns WHERE confidence >= :minConfidence ORDER BY occurrences DESC")
    fun getHighConfidencePatterns(minConfidence: Double): Flow<List<LearnedPatternEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: LearnedPatternEntity)
    
    @Query("SELECT * FROM learned_patterns WHERE pattern = :pattern")
    suspend fun findPattern(pattern: String): LearnedPatternEntity?
    
    @Query("DELETE FROM learned_patterns WHERE confidence < :threshold AND occurrences < :minOccurrences")
    suspend fun cleanLowConfidencePatterns(threshold: Double, minOccurrences: Int)
}

/**
 * Scam Campaign DAO
 */
@Dao
interface ScamCampaignDao {
    @Query("SELECT * FROM scam_campaigns WHERE isActive = 1 ORDER BY startDate DESC")
    fun getActiveCampaigns(): Flow<List<ScamCampaignEntity>>
    
    @Query("SELECT * FROM scam_campaigns WHERE campaignId = :id")
    suspend fun getCampaign(id: String): ScamCampaignEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(campaign: ScamCampaignEntity)
    
    @Query("UPDATE scam_campaigns SET isActive = 0, endDate = :endDate WHERE campaignId = :id")
    suspend fun endCampaign(id: String, endDate: Long)
}

/**
 * Threat Prediction DAO
 */
@Dao
interface ThreatPredictionDao {
    @Query("SELECT * FROM threat_predictions WHERE isActive = 1 ORDER BY probability DESC")
    fun getActivePredictions(): Flow<List<ThreatPredictionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prediction: ThreatPredictionEntity)
    
    @Query("DELETE FROM threat_predictions WHERE createdAt < :expiry")
    suspend fun cleanOldPredictions(expiry: Long)
}

/**
 * Audit Log DAO - compliance, immutable action log
 */
@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    fun getLogsForEntity(entityType: String, entityId: String): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_log WHERE timestamp < :expiry")
    suspend fun deleteOlderThan(expiry: Long)
}

/**
 * User Risk Profile DAO
 */
@Dao
interface UserRiskProfileDao {
    @Query("SELECT * FROM user_risk_profile WHERE id = 1")
    suspend fun getProfile(): UserRiskProfileEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserRiskProfileEntity)
    
    @Query("SELECT * FROM user_risk_profile WHERE id = 1")
    fun getProfileFlow(): Flow<UserRiskProfileEntity?>
}
