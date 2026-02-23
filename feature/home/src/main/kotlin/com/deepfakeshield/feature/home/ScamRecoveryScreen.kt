package com.deepfakeshield.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class RecoveryStep(
    val number: Int, val title: String, val description: String, val icon: ImageVector,
    val color: Color, val actions: List<Pair<String, String>>, val urgency: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScamRecoveryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var expandedStep by remember { mutableIntStateOf(-1) }
    var completedSteps by remember { mutableStateOf(setOf<Int>()) }
    var scamType by remember { mutableIntStateOf(-1) }
    val scamTypes = listOf(
        "Financial Fraud" to "Money sent to scammer, unauthorized transactions",
        "Identity Theft" to "SSN, ID, or personal info stolen",
        "Romance Scam" to "Catfished into sending money or info",
        "Tech Support Scam" to "Gave remote access or paid for fake service",
        "Phishing/Account Hack" to "Credentials stolen, account compromised",
        "Investment/Crypto Scam" to "Lost money in fake investment scheme"
    )

    val steps = remember { listOf(
        RecoveryStep(1, "Stop All Contact", "Do NOT respond to, call back, or engage further with the scammer. Block their number/email immediately.", Icons.Default.Block, Color(0xFFF44336),
            listOf("Block the scammer's number" to "tel:*67", "Report to carrier (T-Mobile)" to "tel:611"), "IMMEDIATE"),
        RecoveryStep(2, "Freeze Your Credit", "Contact all 3 credit bureaus to freeze your credit. This prevents scammers from opening accounts in your name.", Icons.Default.CreditCard, Color(0xFF2196F3),
            listOf("Equifax Freeze" to "https://www.equifax.com/personal/credit-report-services/credit-freeze/", "Experian Freeze" to "https://www.experian.com/freeze/center.html", "TransUnion Freeze" to "https://www.transunion.com/credit-freeze"), "CRITICAL"),
        RecoveryStep(3, "Change All Passwords", "Change passwords for any accounts that may be compromised. Start with email and banking.", Icons.Default.Password, Color(0xFF9C27B0),
            listOf("Google Account" to "https://myaccount.google.com/security", "Apple ID" to "https://appleid.apple.com"), "CRITICAL"),
        RecoveryStep(4, "Contact Your Bank", "Notify your bank and credit card companies of potential fraud. Request new cards if numbers were shared.", Icons.Default.AccountBalance, Color(0xFF4CAF50),
            listOf(), "CRITICAL"),
        RecoveryStep(5, "Report to Authorities", "File reports with the FTC, FBI IC3, and local police. This creates an official record.", Icons.Default.LocalPolice, Color(0xFF3F51B5),
            listOf("FTC ReportFraud.ftc.gov" to "https://reportfraud.ftc.gov/", "FBI IC3" to "https://www.ic3.gov/", "IdentityTheft.gov" to "https://www.identitytheft.gov/"), "IMPORTANT"),
        RecoveryStep(6, "Enable 2FA Everywhere", "Add two-factor authentication to all important accounts. Use an authenticator app, not SMS.", Icons.Default.PhoneAndroid, Color(0xFFFF9800),
            listOf("Google Authenticator" to "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2"), "IMPORTANT"),
        RecoveryStep(7, "Monitor Your Accounts", "Check bank statements, credit reports, and account activity daily for the next 90 days.", Icons.Default.MonitorHeart, Color(0xFF00BCD4),
            listOf("AnnualCreditReport.com" to "https://www.annualcreditreport.com/"), "ONGOING"),
        RecoveryStep(8, "Document Everything", "Save all evidence: screenshots, emails, text messages, phone records, transaction records.", Icons.Default.PhotoCamera, Color(0xFF795548),
            listOf(), "ONGOING")
    ) }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Scam Recovery Wizard", fontWeight = FontWeight.Bold); Text("${completedSteps.size} of ${steps.size} steps done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("If You've Been Scammed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Follow these steps in order. The first 4 are time-critical — act within 24 hours to minimize damage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                }
            }

            LinearProgressIndicator(progress = { completedSteps.size.toFloat() / steps.size }, Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)

            // Scam type selector
            if (scamType < 0) {
                Text("What Type of Scam?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Select the type so we can prioritize the right steps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                scamTypes.forEachIndexed { idx, (name, desc) ->
                    Card(Modifier.fillMaxWidth().clickable { scamType = idx }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = false, onClick = { scamType = idx })
                            Column { Text(name, fontWeight = FontWeight.SemiBold); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth().clickable { scamType = -1 }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scam type: ${scamTypes[scamType].first}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("Recovery Steps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            steps.forEach { step ->
                val isDone = step.number in completedSteps
                val isExpanded = expandedStep == step.number
                Card(
                    Modifier.fillMaxWidth().clickable { expandedStep = if (isExpanded) -1 else step.number }.animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDone) step.color.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(if (isDone) step.color else step.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                if (isDone) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                else Icon(step.icon, null, tint = step.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(step.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Surface(color = when (step.urgency) { "IMMEDIATE" -> Color(0xFFF44336); "CRITICAL" -> Color(0xFFFF9800); else -> Color(0xFF2196F3) }.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                    Text(step.urgency, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        color = when (step.urgency) { "IMMEDIATE" -> Color(0xFFF44336); "CRITICAL" -> Color(0xFFFF9800); else -> Color(0xFF2196F3) })
                                }
                            }
                            Checkbox(checked = isDone, onCheckedChange = { completedSteps = if (isDone) completedSteps - step.number else completedSteps + step.number })
                        }
                        if (isExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(step.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (step.actions.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                step.actions.forEach { (label, url) ->
                                    OutlinedButton(
                                        onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show() } },
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(10.dp)
                                    ) { Text(label, fontWeight = FontWeight.Medium) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
