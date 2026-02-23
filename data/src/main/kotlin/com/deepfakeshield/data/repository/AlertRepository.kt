package com.deepfakeshield.data.repository

import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.dao.AlertDao
import com.deepfakeshield.data.dao.UserFeedbackDao
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.entity.UserFeedbackEntity
import android.util.Log
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val feedbackDao: UserFeedbackDao
) {
    fun getAllAlerts(): Flow<List<AlertEntity>> = alertDao.getAllAlerts()

    /** Returns ALL alerts without LIMIT — use only for data export, never for UI. */
    fun getAllAlertsUnbounded(): Flow<List<AlertEntity>> = alertDao.getAllAlertsUnbounded()
    
    fun getUnhandledAlerts(): Flow<List<AlertEntity>> = alertDao.getUnhandledAlerts()
    
    fun getAlertsBySeverity(severity: RiskSeverity): Flow<List<AlertEntity>> =
        alertDao.getAlertsBySeverity(severity)
    
    fun getAlertsByType(type: ThreatType): Flow<List<AlertEntity>> =
        alertDao.getAlertsByType(type)
    
    fun getAlertsBySource(source: ThreatSource): Flow<List<AlertEntity>> =
        alertDao.getAlertsBySource(source)
    
    fun getUnhandledCount(): Flow<Int> = alertDao.getUnhandledCount()
    
    suspend fun getAlertById(id: Long): AlertEntity? = alertDao.getAlertById(id)
    
    suspend fun getAlertCount(): Int = alertDao.getAlertCount()
    
    fun getAlertCountFlow(): Flow<Int> = alertDao.getAlertCountFlow()
    
    suspend fun insertAlert(alert: AlertEntity): Long = alertDao.insertAlert(alert)
    
    suspend fun updateAlert(alert: AlertEntity) = alertDao.updateAlert(alert)
    
    suspend fun deleteAlert(alert: AlertEntity) = alertDao.deleteAlert(alert)
    
    suspend fun markAsHandled(id: Long) {
        alertDao.markAsHandled(id, System.currentTimeMillis())
    }
    
    suspend fun updateUserMarking(id: Long, markedAs: String) {
        alertDao.updateUserMarking(id, markedAs)
    }
    
    suspend fun submitFeedback(
        alertId: Long,
        feedbackType: String,
        userMarkedAs: String?,
        notes: String?
    ) {
        val feedback = UserFeedbackEntity(
            alertId = alertId,
            feedbackType = feedbackType,
            userMarkedAs = userMarkedAs,
            notes = notes,
            timestamp = System.currentTimeMillis()
        )
        feedbackDao.insertFeedback(feedback)
    }
    
    suspend fun cleanupOldAlerts(retentionDays: Int) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        // Delete feedback first, then alerts. Wrapped in try-catch so a partial
        // failure is logged instead of silently leaving orphan rows.
        try {
            feedbackDao.deleteFeedbackForOldAlerts(cutoffTime)
            alertDao.deleteAlertsOlderThan(cutoffTime)
        } catch (e: Exception) {
            Log.e("AlertRepository", "Partial failure during cleanup: ${e.message}", e)
            throw e
        }
    }
}
