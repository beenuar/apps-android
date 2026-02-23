package com.deepfakeshield.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Transparent activity that requests MediaProjection permission.
 * Launched by the FloatingBubbleService when the user taps "Monitor Screen".
 * After the user grants permission, it starts ScreenCaptureService and finishes.
 */
class ScreenCaptureActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        const val EXTRA_TARGET_APP = "target_app"

        fun launch(context: Context, targetApp: String = "full_screen") {
            val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TARGET_APP, targetApp)
            }
            context.startActivity(intent)
        }
    }

    private var targetApp: String = "full_screen"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetApp = intent?.getStringExtra(EXTRA_TARGET_APP) ?: "full_screen"

        if (ScreenCaptureService.isRunning) {
            // Already monitoring — stop it
            ScreenCaptureService.stop(this)
            Toast.makeText(this, "Screen monitoring stopped", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Request MediaProjection permission
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) { Toast.makeText(this, "Screen capture not available", Toast.LENGTH_SHORT).show(); finish(); return }
        try {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        } catch (e: Exception) {
            Log.e("ScreenCaptureActivity", "Failed to request screen capture", e)
            Toast.makeText(this, "Screen capture not available on this device", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Using for MediaProjection result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                // Start the capture service
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, data)
                    putExtra(ScreenCaptureService.EXTRA_TARGET_APP, targetApp)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Deepfake monitoring started for ${if (targetApp == "full_screen") "Full Screen" else targetApp}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
