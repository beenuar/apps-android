package com.deepfakeshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.entity.AlertEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageUpdateReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdateEntryPoint {
        fun alertRepository(): com.deepfakeshield.data.repository.AlertRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_REPLACED) return
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.i("PackageUpdate", "App updated: $packageName — checking for permission changes")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = context.packageManager
                val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

                val dangerousPerms = setOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_CONTACTS", "android.permission.READ_SMS", "android.permission.READ_CALL_LOG", "android.permission.BODY_SENSORS")
                val requested = (pkg.requestedPermissions ?: emptyArray()).filter { it in dangerousPerms }

                val prefs = context.getSharedPreferences("perm_snapshots", Context.MODE_PRIVATE)
                val prevKey = "perms_$packageName"
                val prev = prefs.getStringSet(prevKey, emptySet()) ?: emptySet()
                val current = requested.toSet()
                val newPerms = current - prev

                prefs.edit().putStringSet(prevKey, current).apply()

                if (newPerms.isNotEmpty()) {
                    val appName = try { pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName } catch (_: Exception) { packageName }
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, UpdateEntryPoint::class.java)
                    entryPoint.alertRepository().insertAlert(AlertEntity(
                        threatType = ThreatType.MALWARE,
                        source = ThreatSource.REAL_TIME_SCAN,
                        severity = RiskSeverity.MEDIUM,
                        score = 40 + newPerms.size * 10,
                        confidence = 0.7f,
                        title = "App permission expanded: $appName",
                        summary = "$appName gained ${newPerms.size} new dangerous permission${if (newPerms.size > 1) "s" else ""} after update",
                        content = "New permissions: ${newPerms.joinToString(", ") { it.substringAfterLast(".") }}\nPackage: $packageName",
                        senderInfo = packageName,
                        timestamp = System.currentTimeMillis()
                    ))
                    Log.w("PackageUpdate", "$appName gained new permissions: $newPerms")
                }
            } catch (e: Exception) {
                Log.e("PackageUpdate", "Failed to diff permissions for $packageName", e)
            } finally {
                try { pendingResult.finish() } catch (_: Exception) {}
            }
        }
    }
}
