package com.deepfakeshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.deepfakeshield.worker.InstallScanWorker

class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        Log.i("PackageInstall", "New app installed: $packageName — queueing scan")

        val request = OneTimeWorkRequestBuilder<InstallScanWorker>()
            .setInputData(workDataOf("package_name" to packageName))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
