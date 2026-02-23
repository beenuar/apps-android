package com.deepfakeshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable audit log for compliance - actions taken in the app.
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val userId: String = "local",
    val timestamp: Long,
    val metadata: String? = null,
    val hash: String? = null
)
