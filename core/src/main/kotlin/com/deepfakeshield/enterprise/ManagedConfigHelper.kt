package com.deepfakeshield.enterprise

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * Enterprise Managed Configuration support.
 * When deployed via EMM/MDM, IT admins can push policies that override user preferences.
 * See: https://developer.android.com/work/managed-configurations
 */
object ManagedConfigHelper {

    /**
     * Returns true if this app is under managed configuration (work profile / EMM).
     */
    fun isManaged(context: Context): Boolean {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return false
        val bundle = restrictionsManager.applicationRestrictions
        return bundle != null && !bundle.isEmpty
    }

    /**
     * Get the current managed configuration bundle.
     * Empty bundle if not managed or pre-Lollipop.
     */
    fun getRestrictions(context: Context): Bundle {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return Bundle()
        return restrictionsManager.applicationRestrictions ?: Bundle()
    }

    /**
     * Get effective master protection setting.
     * Managed config overrides user preference when present.
     */
    fun getMasterProtectionOverride(context: Context): Boolean? {
        val bundle = getRestrictions(context)
        return if (bundle.containsKey("master_protection_enabled")) {
            bundle.getBoolean("master_protection_enabled")
        } else null
    }

    /**
     * Get effective protection level (gentle/balanced/strict).
     */
    fun getProtectionLevelOverride(context: Context): String? {
        val bundle = getRestrictions(context)
        return if (bundle.containsKey("protection_level")) {
            bundle.getString("protection_level") ?: "balanced"
        } else null
    }

    /**
     * Get effective data retention in days.
     */
    fun getDataRetentionOverride(context: Context): Int? {
        val bundle = getRestrictions(context)
        return if (bundle.containsKey("data_retention_days")) {
            bundle.getInt("data_retention_days", 90).coerceIn(7, 365)
        } else null
    }

    /**
     * Whether data export is allowed by admin.
     */
    fun isExportAllowed(context: Context): Boolean {
        val bundle = getRestrictions(context)
        return if (bundle.containsKey("allow_export")) {
            bundle.getBoolean("allow_export", true)
        } else true
    }

    /**
     * Whether biometric is required for vault access.
     */
    fun isBiometricVaultRequired(context: Context): Boolean {
        val bundle = getRestrictions(context)
        return if (bundle.containsKey("require_biometric_vault")) {
            bundle.getBoolean("require_biometric_vault", false)
        } else false
    }
}
