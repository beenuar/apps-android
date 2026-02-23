package com.deepfakeshield.data.dao

import androidx.room.*
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 500")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    /** Unbounded query for export — only use in background workers. */
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlertsUnbounded(): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM alerts WHERE isHandled = 0 ORDER BY timestamp DESC LIMIT 200")
    fun getUnhandledAlerts(): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM alerts WHERE severity = :severity ORDER BY timestamp DESC LIMIT 200")
    fun getAlertsBySeverity(severity: RiskSeverity): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM alerts WHERE threatType = :type ORDER BY timestamp DESC LIMIT 200")
    fun getAlertsByType(type: ThreatType): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM alerts WHERE source = :source ORDER BY timestamp DESC LIMIT 200")
    fun getAlertsBySource(source: ThreatSource): Flow<List<AlertEntity>>
    
    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): AlertEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: AlertEntity): Long
    
    @Update
    suspend fun updateAlert(alert: AlertEntity)
    
    @Delete
    suspend fun deleteAlert(alert: AlertEntity)
    
    @Query("DELETE FROM alerts WHERE timestamp < :timestamp")
    suspend fun deleteAlertsOlderThan(timestamp: Long)
    
    @Query("UPDATE alerts SET isHandled = 1, handledAt = :handledAt WHERE id = :id")
    suspend fun markAsHandled(id: Long, handledAt: Long)
    
    @Query("UPDATE alerts SET userMarkedAs = :markedAs WHERE id = :id")
    suspend fun updateUserMarking(id: Long, markedAs: String)
    
    @Query("SELECT COUNT(*) FROM alerts WHERE isHandled = 0")
    fun getUnhandledCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM alerts")
    suspend fun getAlertCount(): Int
    
    @Query("SELECT COUNT(*) FROM alerts")
    fun getAlertCountFlow(): Flow<Int>
}

@Dao
interface PhoneReputationDao {
    @Query("SELECT * FROM phone_reputation WHERE phoneNumber = :number")
    suspend fun getReputation(number: String): PhoneReputationEntity?
    
    @Query("SELECT * FROM phone_reputation WHERE isBlocked = 1")
    fun getBlockedNumbers(): Flow<List<PhoneReputationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reputation: PhoneReputationEntity)
    
    @Query("UPDATE phone_reputation SET scamReports = scamReports + 1, reportCount = reportCount + 1, trustScore = CAST((safeReports * 100.0 / (scamReports + 1 + safeReports)) AS INTEGER), lastReportedAt = :timestamp WHERE phoneNumber = :number")
    suspend fun incrementScamReports(number: String, timestamp: Long)
    
    @Query("UPDATE phone_reputation SET safeReports = safeReports + 1, reportCount = reportCount + 1, trustScore = CAST(((safeReports + 1) * 100.0 / (scamReports + safeReports + 1)) AS INTEGER), lastReportedAt = :timestamp WHERE phoneNumber = :number")
    suspend fun incrementSafeReports(number: String, timestamp: Long)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(reputation: PhoneReputationEntity): Long
    
    @Query("UPDATE phone_reputation SET isBlocked = :blocked WHERE phoneNumber = :number")
    suspend fun setBlocked(number: String, blocked: Boolean)
    
    @Delete
    suspend fun delete(reputation: PhoneReputationEntity)
}

@Dao
interface DomainReputationDao {
    @Query("SELECT * FROM domain_reputation WHERE domain = :domain")
    suspend fun getReputation(domain: String): DomainReputationEntity?
    
    @Query("SELECT * FROM domain_reputation WHERE isBlocked = 1")
    fun getBlockedDomains(): Flow<List<DomainReputationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reputation: DomainReputationEntity)
    
    @Query("UPDATE domain_reputation SET scamReports = scamReports + 1, reportCount = reportCount + 1, trustScore = CAST((safeReports * 100.0 / (scamReports + 1 + safeReports)) AS INTEGER), lastReportedAt = :timestamp WHERE domain = :domain")
    suspend fun incrementScamReports(domain: String, timestamp: Long)
    
    @Query("UPDATE domain_reputation SET safeReports = safeReports + 1, reportCount = reportCount + 1, trustScore = CAST(((safeReports + 1) * 100.0 / (scamReports + safeReports + 1)) AS INTEGER), lastReportedAt = :timestamp WHERE domain = :domain")
    suspend fun incrementSafeReports(domain: String, timestamp: Long)
    
    @Query("UPDATE domain_reputation SET isBlocked = :blocked WHERE domain = :domain")
    suspend fun setBlocked(domain: String, blocked: Boolean)
    
    @Delete
    suspend fun delete(reputation: DomainReputationEntity)
    
    @Query("DELETE FROM domain_reputation WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
}

@Dao
interface UserFeedbackDao {
    @Query("SELECT * FROM user_feedback WHERE alertId = :alertId")
    suspend fun getFeedbackForAlert(alertId: Long): List<UserFeedbackEntity>
    
    @Insert
    suspend fun insertFeedback(feedback: UserFeedbackEntity)
    
    @Query("DELETE FROM user_feedback WHERE timestamp < :timestamp")
    suspend fun deleteFeedbackOlderThan(timestamp: Long)

    @Query("DELETE FROM user_feedback WHERE alertId IN (SELECT id FROM alerts WHERE timestamp < :cutoffTimestamp)")
    suspend fun deleteFeedbackForOldAlerts(cutoffTimestamp: Long)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<VaultEntryEntity>>
    
    @Query("SELECT * FROM vault_entries WHERE entryType = :type AND isArchived = 0 ORDER BY createdAt DESC")
    fun getEntriesByType(type: String): Flow<List<VaultEntryEntity>>
    
    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): VaultEntryEntity?
    
    @Insert
    suspend fun insertEntry(entry: VaultEntryEntity): Long
    
    @Update
    suspend fun updateEntry(entry: VaultEntryEntity)
    
    @Delete
    suspend fun deleteEntry(entry: VaultEntryEntity)
    
    @Query("DELETE FROM vault_entries WHERE createdAt < :timestamp AND isArchived = 0")
    suspend fun deleteEntriesOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM vault_entries WHERE isArchived = 0")
    fun getEntryCount(): Flow<Int>

    @Query("SELECT * FROM vault_entries WHERE isArchived = 0 AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR evidenceData LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR metadata LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    suspend fun searchEntries(query: String): List<VaultEntryEntity>
}

@Dao
interface ScamPatternDao {
    @Query("SELECT * FROM scam_patterns WHERE patternType = :type")
    suspend fun getPatternsByType(type: String): List<ScamPatternEntity>
    
    @Query("SELECT * FROM scam_patterns")
    suspend fun getAllPatterns(): List<ScamPatternEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: ScamPatternEntity)
    
    @Query("UPDATE scam_patterns SET hitCount = hitCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementHitCount(id: Long, timestamp: Long)
    
    @Delete
    suspend fun deletePattern(pattern: ScamPatternEntity)
}

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY exportedAt DESC LIMIT 20")
    fun getRecentExports(): Flow<List<ExportHistoryEntity>>
    
    @Insert
    suspend fun insertExport(export: ExportHistoryEntity)
    
    @Query("DELETE FROM export_history WHERE exportedAt < :timestamp")
    suspend fun deleteExportsOlderThan(timestamp: Long)
}
