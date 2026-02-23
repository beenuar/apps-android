package com.deepfakeshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deepfakeshield.service.EmbeddedTorManager

class TorToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = intent.getBooleanExtra("enabled", false)
        val exitCountry = intent.getStringExtra("exit_country") ?: "auto"
        Log.i("TorToggle", "Tor toggle: enabled=$enabled, exit=$exitCountry")
        if (enabled) {
            EmbeddedTorManager.start(context.applicationContext, exitCountry)
        } else {
            EmbeddedTorManager.stop(context.applicationContext)
        }
    }
}
