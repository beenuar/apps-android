package com.deepfakeshield.data.repository

import com.deepfakeshield.data.dao.AuditLogDao
import com.deepfakeshield.data.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for immutable audit logs - compliance.
 */
@Singleton
class AuditLogRepository @Inject constructor(
    private val auditLogDao: AuditLogDao
) {
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>> =
        auditLogDao.getRecentLogs(limit)

    suspend fun logAction(action: String, entityType: String, entityId: String?, metadata: String? = null) {
        auditLogDao.insert(
            AuditLogEntity(
                action = action,
                entityType = entityType,
                entityId = entityId,
                userId = "local",
                timestamp = System.currentTimeMillis(),
                metadata = metadata
            )
        )
    }

    suspend fun deleteOlderThan(retentionDays: Int) {
        require(retentionDays > 0) { "Retention days must be positive, was $retentionDays" }
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        auditLogDao.deleteOlderThan(cutoff)
    }
}
