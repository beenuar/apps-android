package com.deepfakeshield.data.repository

import com.deepfakeshield.data.dao.PhoneReputationDao
import com.deepfakeshield.data.entity.PhoneReputationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneReputationRepository @Inject constructor(
    private val phoneReputationDao: PhoneReputationDao
) {
    suspend fun getReputation(phoneNumber: String): PhoneReputationEntity? =
        phoneReputationDao.getReputation(phoneNumber)
    
    fun getBlockedNumbers(): Flow<List<PhoneReputationEntity>> =
        phoneReputationDao.getBlockedNumbers()
    
    suspend fun reportAsScam(phoneNumber: String) {
        val now = System.currentTimeMillis()
        // INSERT OR IGNORE ensures the row exists; no-op if it already does.
        // This eliminates the read-then-write race in the old check-then-insert pattern.
        phoneReputationDao.insertIgnore(
            PhoneReputationEntity(
                phoneNumber = phoneNumber,
                trustScore = 0,
                reportCount = 0,
                scamReports = 0,
                safeReports = 0,
                lastReportedAt = now
            )
        )
        // Atomic SQL UPDATE — safe even under concurrent calls
        phoneReputationDao.incrementScamReports(phoneNumber, now)
    }
    
    suspend fun reportAsSafe(phoneNumber: String) {
        val now = System.currentTimeMillis()
        // INSERT OR IGNORE ensures the row exists; no-op if it already does.
        phoneReputationDao.insertIgnore(
            PhoneReputationEntity(
                phoneNumber = phoneNumber,
                trustScore = 0,
                reportCount = 0,
                scamReports = 0,
                safeReports = 0,
                lastReportedAt = now
            )
        )
        // Atomic SQL UPDATE — safe even under concurrent calls
        phoneReputationDao.incrementSafeReports(phoneNumber, now)
    }
    
    suspend fun addToWhitelist(phoneNumber: String) = reportAsSafe(phoneNumber)

    suspend fun isWhitelisted(phoneNumber: String): Boolean {
        val rep = phoneReputationDao.getReputation(phoneNumber) ?: return false
        return rep.trustScore > 0
    }

    suspend fun blockNumber(phoneNumber: String) {
        val existing = phoneReputationDao.getReputation(phoneNumber)
        if (existing != null) {
            phoneReputationDao.setBlocked(phoneNumber, true)
        } else {
            phoneReputationDao.insertOrUpdate(
                PhoneReputationEntity(
                    phoneNumber = phoneNumber,
                    trustScore = -100,
                    reportCount = 1,
                    scamReports = 1,
                    safeReports = 0,
                    lastReportedAt = System.currentTimeMillis(),
                    isBlocked = true
                )
            )
        }
    }
    
    suspend fun unblockNumber(phoneNumber: String) {
        phoneReputationDao.setBlocked(phoneNumber, false)
    }
    
    suspend fun deleteReputation(reputation: PhoneReputationEntity) {
        phoneReputationDao.delete(reputation)
    }
}
