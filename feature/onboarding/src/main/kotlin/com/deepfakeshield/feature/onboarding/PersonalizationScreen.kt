package com.deepfakeshield.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Priority(val id: String, val title: String, val description: String, val icon: ImageVector, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(onComplete: (Set<String>) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf(setOf<String>()) }

    val priorities = listOf(
        Priority("scam_protection", "Scam Protection", "Block scam calls, SMS & phishing", Icons.Default.PhoneDisabled, Color(0xFFF44336)),
        Priority("deepfake_detection", "Deepfake Detection", "Detect AI-generated video & images", Icons.Default.Videocam, Color(0xFF9C27B0)),
        Priority("identity_theft", "Identity Theft", "Monitor breaches & dark web exposure", Icons.Default.Person, Color(0xFF2196F3)),
        Priority("privacy", "Privacy & Tracking", "Audit permissions & digital footprint", Icons.Default.PrivacyTip, Color(0xFF4CAF50)),
        Priority("family_safety", "Family Safety", "Protect parents & children from scams", Icons.Default.FamilyRestroom, Color(0xFFFF9800)),
        Priority("malware", "Malware & Viruses", "Scan files & apps for threats", Icons.Default.Security, Color(0xFF795548)),
        Priority("wifi_security", "Wi-Fi & Network", "Audit network security & VPN status", Icons.Default.Wifi, Color(0xFF00BCD4)),
        Priority("password_security", "Password Security", "Check breached & weak passwords", Icons.Default.Password, Color(0xFFE91E63))
    )

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            // Progress indicator
            LinearProgressIndicator(progress = { (selected.size / 3f).coerceAtMost(1f) }, Modifier.fillMaxWidth().height(6.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
            Text("${selected.size} of 3+ selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("\uD83D\uDEE1\uFE0F", fontSize = 48.sp)
            Text("What matters most to you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("We'll customize your experience based on your priorities. Select at least 3.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))

            priorities.forEach { priority ->
                val isSelected = priority.id in selected
                Card(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        selected = if (isSelected) selected - priority.id else selected + priority.id
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }.then(if (isSelected) Modifier.border(2.dp, priority.color, RoundedCornerShape(16.dp)) else Modifier),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) priority.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(priority.color.copy(alpha = if (isSelected) 0.2f else 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(priority.icon, null, Modifier.size(22.dp), tint = priority.color)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(priority.title, fontWeight = FontWeight.SemiBold)
                            Text(priority.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, null, tint = priority.color, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onComplete(selected) },
                Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = selected.size >= 3
            ) {
                Text(if (selected.size >= 3) "Continue (${selected.size} selected)" else "Select at least 3", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { onComplete(emptySet()) }) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
