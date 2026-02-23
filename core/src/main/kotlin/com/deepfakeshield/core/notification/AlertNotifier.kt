package com.deepfakeshield.core.notification

/**
 * Called when a new threat alert is saved so the app can show a notification
 * and update the floating bubble (threat count, "Threat detected").
 */
interface AlertNotifier {
    fun onAlertInserted(alertCount: Int, title: String, summary: String)
}
