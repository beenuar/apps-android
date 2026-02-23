package com.deepfakeshield.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log

/**
 * Transparent activity that requests VPN consent from the user.
 * After consent is granted, starts the TorVpnService.
 */
class TorVpnConsentActivity : Activity() {

    companion object {
        private const val REQUEST_VPN = 1001

        fun launch(context: Context) {
            val intent = Intent(context, TorVpnConsentActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN)
        } else {
            onVpnApproved()
        }
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                onVpnApproved()
            } else {
                Log.w("TorVPN", "VPN consent denied by user")
            }
            finish()
        }
    }

    private fun onVpnApproved() {
        Log.i("TorVPN", "VPN consent granted, starting TorVpnService")
        TorVpnService.start(applicationContext)
        finish()
    }
}
