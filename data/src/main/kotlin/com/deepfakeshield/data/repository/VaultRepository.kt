package com.deepfakeshield.data.repository

import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.data.dao.VaultDao
import com.deepfakeshield.data.entity.VaultEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val vaultDao: VaultDao
) {
    fun getAllEntries(): Flow<List<VaultEntryEntity>> = vaultDao.getAllEntries()
    
    fun getEntriesByType(type: String): Flow<List<VaultEntryEntity>> =
        vaultDao.getEntriesByType(type)
    
    fun getEntryCount(): Flow<Int> = vaultDao.getEntryCount()
    
    suspend fun getEntryById(id: Long): VaultEntryEntity? = vaultDao.getEntryById(id)
    
    suspend fun addEntry(
        alertId: Long?,
        entryType: String,
        title: String,
        description: String,
        severity: RiskSeverity,
        evidenceData: String,
        metadata: String? = null,
        tags: String? = null
    ): Long {
        return try {
            val entry = VaultEntryEntity(
                alertId = alertId,
                entryType = entryType,
                title = title,
                description = description,
                severity = severity,
                evidenceData = evidenceData,
                metadata = metadata,
                tags = tags,
                createdAt = System.currentTimeMillis()
            )
            vaultDao.insertEntry(entry)
        } catch (e: Exception) {
            android.util.Log.e("VaultRepository", "Failed to add vault entry: $title", e)
            -1L
        }
    }
    
    suspend fun updateEntry(entry: VaultEntryEntity) = vaultDao.updateEntry(entry)
    
    suspend fun deleteEntry(entry: VaultEntryEntity) = vaultDao.deleteEntry(entry)
    
    suspend fun cleanupOldEntries(retentionDays: Int) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        vaultDao.deleteEntriesOlderThan(cutoffTime)
    }

    /** Threat hunting - search vault by query (IOC, hash, domain, etc.) */
    suspend fun searchEntries(query: String): List<VaultEntryEntity> =
        vaultDao.searchEntries(query)
}
