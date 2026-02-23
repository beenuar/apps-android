package com.deepfakeshield.core.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "Was this helpful?" feedback component shown after every scan result.
 * Feeds into the adaptive learning system to improve accuracy.
 */
@Composable
fun ScanFeedbackCard(
    onFeedback: (Boolean, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var feedbackGiven by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var selectedFeedback by remember { mutableStateOf<Boolean?>(null) }

    AnimatedVisibility(visible = !feedbackGiven, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Was this scan accurate?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                if (!showDetails) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { selectedFeedback = true; showDetails = true }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(4.dp))
                            Text("Accurate")
                        }
                        OutlinedButton(onClick = { selectedFeedback = false; showDetails = true }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.ThumbDown, null, modifier = Modifier.size(18.dp), tint = Color(0xFFF44336))
                            Spacer(Modifier.width(4.dp))
                            Text("Incorrect")
                        }
                    }
                } else {
                    val detailOptions = if (selectedFeedback == false) {
                        listOf("False positive - it's actually safe", "Missed a real threat", "Wrong threat type", "Other")
                    } else {
                        listOf("Very helpful", "Saved me from a scam", "Good detail level", "Just confirming")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (selectedFeedback == true) "Great! What was most helpful?" else "Sorry about that. What went wrong?", style = MaterialTheme.typography.bodySmall)
                        detailOptions.forEach { option ->
                            OutlinedButton(
                                onClick = {
                                    onFeedback(selectedFeedback ?: true, option)
                                    feedbackGiven = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(option, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = feedbackGiven, enter = fadeIn() + expandVertically()) {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                Text("Thanks! Your feedback helps us improve.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
            }
        }
    }
}

/**
 * Human-readable confidence explanation component
 */
@Composable
fun HumanConfidenceExplanation(
    score: Int,
    threatType: String,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val explanation = getHumanExplanation(score, threatType, confidence)
    val (bgColor, textColor) = when {
        score >= 75 -> Color(0xFFF44336).copy(alpha = 0.08f) to Color(0xFFF44336)
        score >= 50 -> Color(0xFFFF9800).copy(alpha = 0.08f) to Color(0xFFFF9800)
        score >= 25 -> Color(0xFFFFC107).copy(alpha = 0.08f) to Color(0xFFF57F17)
        else -> Color(0xFF4CAF50).copy(alpha = 0.08f) to Color(0xFF4CAF50)
    }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    when { score >= 75 -> Icons.Default.Dangerous; score >= 50 -> Icons.Default.Warning; score >= 25 -> Icons.Default.Info; else -> Icons.Default.CheckCircle },
                    null, tint = textColor, modifier = Modifier.size(24.dp)
                )
                Text(explanation.title, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(explanation.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (explanation.action.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(explanation.action, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = textColor)
            }
        }
    }
}

private data class ConfidenceExplanation(val title: String, val body: String, val action: String)

@Suppress("UNUSED_PARAMETER")
private fun getHumanExplanation(score: Int, threatType: String, _confidence: Float): ConfidenceExplanation {
    return when {
        score >= 80 && threatType.contains("OTP", true) -> ConfidenceExplanation(
            "This is an OTP Scam",
            "This message is trying to trick you into sharing your one-time password. Real banks and companies NEVER ask for your OTP via message or call. This exact pattern has been used to steal from thousands of people.",
            "DO NOT REPLY. Delete this message immediately."
        )
        score >= 80 && threatType.contains("PHISHING", true) -> ConfidenceExplanation(
            "Phishing Attack Detected",
            "This message contains a fake link designed to steal your login credentials. The website looks like a real company but it's controlled by scammers. Entering any information will compromise your accounts.",
            "DO NOT CLICK any links. Report and delete."
        )
        score >= 70 && threatType.contains("IMPERSONATION", true) -> ConfidenceExplanation(
            "Someone is Pretending to Be Someone Else",
            "This message claims to be from an official organization but our analysis shows it's fake. Scammers use fear and urgency to make you act without thinking.",
            "Contact the real organization directly using their official website or phone number."
        )
        score >= 60 -> ConfidenceExplanation(
            "This Looks Suspicious",
            "Multiple warning signs suggest this isn't legitimate. The patterns match known scam techniques that have affected many people.",
            "Don't take any action based on this message. Verify independently."
        )
        score >= 30 -> ConfidenceExplanation(
            "Something Seems Off",
            "A few minor concerns were detected. It might be legitimate, but some elements don't look right. Trust your instincts if something feels wrong.",
            "Proceed with caution. Double-check before sharing any personal info."
        )
        else -> ConfidenceExplanation(
            "Looks Safe",
            "Our analysis didn't find significant threats. This appears to be a normal message, but always stay vigilant.",
            ""
        )
    }
}
