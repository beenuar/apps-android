package com.deepfakeshield.core.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

/**
 * Accessibility utilities for DeepFake Shield
 * 
 * Provides content descriptions and semantic properties
 * for TalkBack and other accessibility services.
 */
object Accessibility {
    
    /**
     * Set meaningful content descriptions for screen readers
     */
    fun Modifier.shieldStatus(isActive: Boolean): Modifier = this.semantics {
        contentDescription = if (isActive) {
            "Protection is active. All shields are monitoring for threats."
        } else {
            "Protection is disabled. Tap to enable all shields."
        }
        stateDescription = if (isActive) "Active" else "Inactive"
    }
    
    fun Modifier.threatLevel(severity: String, score: Int): Modifier = this.semantics {
        contentDescription = "Threat level: $severity. Risk score: $score out of 100."
    }
    
    fun Modifier.scanButton(scanType: String): Modifier = this.semantics {
        contentDescription = "Scan $scanType for threats. Double tap to start scan."
        role = Role.Button
    }
    
    fun Modifier.alertBadge(count: Int): Modifier = this.semantics {
        contentDescription = if (count > 0) {
            "$count unhandled alert${if (count != 1) "s" else ""}. Double tap to review."
        } else {
            "No unhandled alerts."
        }
    }
    
    fun Modifier.protectionToggle(name: String, isEnabled: Boolean): Modifier = this.semantics {
        contentDescription = "$name is ${if (isEnabled) "enabled" else "disabled"}. Double tap to toggle."
        stateDescription = if (isEnabled) "On" else "Off"
        role = Role.Switch
    }
    
    fun Modifier.safetyScore(score: Int): Modifier = this.semantics {
        contentDescription = "Your safety score is $score out of 100. " +
            when {
                score >= 80 -> "Excellent protection."
                score >= 60 -> "Good protection."
                score >= 40 -> "Moderate protection. Consider improving."
                else -> "Low protection. Please review your settings."
            }
    }
    
    fun Modifier.scanResult(isScam: Boolean, confidence: Float): Modifier = this.semantics {
        contentDescription = if (isScam) {
            "Warning: This content was flagged as potentially dangerous with ${(confidence * 100).toInt()} percent confidence."
        } else {
            "This content appears safe with ${(confidence * 100).toInt()} percent confidence."
        }
    }
    
    fun Modifier.navigationItem(label: String): Modifier = this.semantics {
        contentDescription = "Navigate to $label"
        role = Role.Button
    }
    
    fun Modifier.streakIndicator(days: Int): Modifier = this.semantics {
        contentDescription = "$days day${if (days != 1) "s" else ""} streak. Keep it going!"
    }
    
    fun Modifier.liveRegion(): Modifier = this.semantics {
        liveRegion = LiveRegionMode.Polite
    }
    
    fun Modifier.urgentLiveRegion(): Modifier = this.semantics {
        liveRegion = LiveRegionMode.Assertive
    }
}
