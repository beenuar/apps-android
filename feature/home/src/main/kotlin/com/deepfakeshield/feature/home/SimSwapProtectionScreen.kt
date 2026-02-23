package com.deepfakeshield.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSwapProtectionScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var completedSteps by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("SIM Swap Protection", fontWeight = FontWeight.Bold); Text("${completedSteps.size}/7 steps complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Risk assessment
            val riskScore = (7 - completedSteps.size) * 14
            val riskColor = when { riskScore <= 28 -> Color(0xFF4CAF50); riskScore <= 56 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.06f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${100 - riskScore}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = riskColor)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(when { riskScore <= 28 -> "Well Protected"; riskScore <= 56 -> "Moderate Risk"; else -> "High Risk" }, fontWeight = FontWeight.Bold, color = riskColor)
                        Text("${completedSteps.size}/7 protections active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.06f))) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SimCardAlert, null, Modifier.size(32.dp), tint = Color(0xFFF44336))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("What is SIM Swapping?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Attackers convince your carrier to transfer your phone number to their SIM card. They then receive your 2FA codes, reset passwords, and drain bank accounts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { completedSteps.size / 7f }, Modifier.fillMaxWidth().height(6.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }

            val steps = listOf(
                Triple("Set a Carrier PIN/Passcode", "Call your carrier and set up a Port-Out PIN or account passcode. This prevents anyone from transferring your number without the PIN.", listOf("T-Mobile: 611 or 1-800-937-8997" to "tel:611", "AT&T: 611 or 1-800-331-0500" to "tel:611", "Verizon: *611 or 1-800-922-0204" to "tel:*611")),
                Triple("Enable Number Lock / Port Freeze", "Most carriers offer a 'Number Lock' that blocks port-out requests entirely. Enable it in your carrier's app or account settings.", listOf("T-Mobile: App > Account > Number Lock" to "", "AT&T: App > Security > Extra Security" to "", "Verizon: My Verizon > Number Lock" to "")),
                Triple("Switch SMS 2FA to Authenticator Apps", "Replace SMS-based 2FA with TOTP apps (Google Authenticator, Authy). SIM swaps can't intercept app-based codes.", listOf("Google Authenticator" to "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2", "Authy" to "https://play.google.com/store/apps/details?id=com.authy.authy")),
                Triple("Use Hardware Security Keys", "For critical accounts (email, banking), use FIDO2 hardware keys (YubiKey, Google Titan). These are completely immune to SIM swaps.", listOf("Learn about YubiKey" to "https://www.yubico.com/")),
                Triple("Set Up Account Recovery Alternatives", "Add backup email addresses and recovery methods that don't depend on your phone number.", listOf()),
                Triple("Monitor Your Phone Signal", "If you suddenly lose cell service for no reason, contact your carrier IMMEDIATELY — this is the #1 sign of an active SIM swap.", listOf()),
                Triple("Reduce Public Phone Number Exposure", "Remove your phone number from social media profiles, public directories, and data broker sites. Use the Data Broker Opt-Out tool.", listOf())
            )

            steps.forEachIndexed { idx, (title, desc, actions) ->
                val isDone = idx in completedSteps
                var expanded by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp),
                    colors = if (isDone) CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.04f)) else CardDefaults.cardColors()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isDone, onCheckedChange = { completedSteps = if (isDone) completedSteps - idx else completedSteps + idx; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                            Text("${idx + 1}. $title", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (actions.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                actions.forEach { (label, url) ->
                                    if (url.isNotBlank()) {
                                        OutlinedButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} },
                                            Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(10.dp)) { Text(label, style = MaterialTheme.typography.bodySmall) }
                                    } else {
                                        Text("• $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.06f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("If You're Being SIM Swapped RIGHT NOW", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                    Spacer(Modifier.height(6.dp))
                    listOf("Call your carrier from another phone IMMEDIATELY", "Ask them to FREEZE your account and reverse the port", "Change passwords on email and banking from a computer", "Enable hardware 2FA on your email account", "File a police report and FTC complaint").forEachIndexed { i, s ->
                        Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}
