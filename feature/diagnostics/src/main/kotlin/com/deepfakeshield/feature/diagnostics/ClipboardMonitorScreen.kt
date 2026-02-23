package com.deepfakeshield.feature.diagnostics

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class ClipboardEntry(
    val content: String, val masked: String, val type: String,
    val risk: String, val icon: ImageVector, val timestamp: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardMonitorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(true) }
    var currentClip by remember { mutableStateOf<ClipboardEntry?>(null) }
    var history by remember { mutableStateOf<List<ClipboardEntry>>(emptyList()) }
    var visibleCount by remember { mutableIntStateOf(0) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    currentClip = classifyClipboard(text, "Just now")
                }
            }

            history = buildList { currentClip?.let { add(it) } }
        } catch (_: Exception) { }
        isScanning = false
        for (i in 1..history.size) { delay(60); visibleCount = i }
    }

    val criticalCount = history.count { it.risk == "CRITICAL" || it.risk == "HIGH" }
    val mediumCount = history.count { it.risk == "MEDIUM" }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Clipboard Monitor", fontWeight = FontWeight.Bold); Text("${history.size} entries analyzed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { showContent = !showContent }) { Icon(if (showContent) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle") } }
        ) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Status card
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (criticalCount > 0) Color(0xFFF44336).copy(alpha = 0.08f) else Color(0xFF4CAF50).copy(alpha = 0.08f))) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (criticalCount > 0) Icons.Default.Warning else Icons.Default.VerifiedUser, null, Modifier.size(32.dp), tint = if (criticalCount > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(if (criticalCount > 0) "$criticalCount Sensitive Item${if (criticalCount > 1) "s" else ""} Found" else "Clipboard is Clean", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Monitoring clipboard for sensitive data exposure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RiskChip("Critical", criticalCount, Color(0xFFF44336), Modifier.weight(1f))
                            RiskChip("Medium", mediumCount, Color(0xFFFF9800), Modifier.weight(1f))
                            RiskChip("Safe", history.size - criticalCount - mediumCount, Color(0xFF4CAF50), Modifier.weight(1f))
                        }
                    }
                }
            }

            if (isScanning) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Analyzing clipboard...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Current clipboard
            currentClip?.let { clip ->
                item {
                    Text("Current Clipboard", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    ClipboardCard(clip, showContent, true)
                }
            }

            item { Text("Clipboard History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

            itemsIndexed(history) { index, entry ->
                AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 4 }) {
                    ClipboardCard(entry, showContent, false)
                }
            }

            // Data type guide
            item {
                Spacer(Modifier.height(8.dp))
                Text("Data Types We Detect", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("Credit Cards / Financial", "Card numbers, routing numbers, CVVs", "CRITICAL"),
                        Triple("Authentication Tokens", "Bearer tokens, API keys, session IDs", "CRITICAL"),
                        Triple("OTP / 2FA Codes", "6-digit codes, backup codes", "HIGH"),
                        Triple("Passwords / Passphrases", "Strings matching password patterns", "HIGH"),
                        Triple("Email Addresses", "Personal and work email addresses", "MEDIUM"),
                        Triple("Phone Numbers", "Mobile and landline numbers", "MEDIUM"),
                        Triple("URLs with Parameters", "Login links, tokens in URLs", "MEDIUM"),
                        Triple("IP Addresses / SSH", "Server addresses, connection strings", "LOW"),
                        Triple("Plain Text", "Regular text content", "NONE")
                    ).forEach { (type, desc, risk) ->
                        val color = when (risk) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); "MEDIUM" -> Color(0xFFFFC107); "LOW" -> Color(0xFF2196F3); else -> Color(0xFF4CAF50) }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(type, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text(risk, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tips
            item {
                Spacer(Modifier.height(8.dp))
                Text("Protection Tips", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                listOf("Clear clipboard after copying passwords or OTPs", "Use a password manager with autofill instead of copy-paste", "Android 13+ shows a toast when apps read your clipboard", "Avoid copying credit card numbers — use autofill or camera scan", "Beware of clipboard-hijacking malware that replaces crypto addresses").forEach { tip ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = Color(0xFFFFC107))
                            Spacer(Modifier.width(6.dp))
                            Text(tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskChip(label: String, count: Int, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun ClipboardCard(entry: ClipboardEntry, showContent: Boolean, isCurrent: Boolean) {
    val riskColor = when (entry.risk) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); "MEDIUM" -> Color(0xFFFFC107); "LOW" -> Color(0xFF2196F3); else -> Color(0xFF4CAF50) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = if (isCurrent) CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.04f)) else CardDefaults.cardColors()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(riskColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(entry.icon, null, Modifier.size(20.dp), tint = riskColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                    Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                        Text(entry.risk, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.Bold)
                    }
                    if (isCurrent) { Spacer(Modifier.width(4.dp)); Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) { Text("CURRENT", Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                }
                Text(if (showContent) entry.content else entry.masked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            }
            Text(entry.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun classifyClipboard(text: String, time: String): ClipboardEntry {
    val trimmed = text.trim()
    return when {
        trimmed.matches(Regex("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}")) -> ClipboardEntry(trimmed, "${trimmed.take(4)}-****-****-${trimmed.takeLast(4)}", "Credit Card", "CRITICAL", Icons.Default.CreditCard, time)
        trimmed.startsWith("Bearer ") || trimmed.startsWith("eyJ") -> ClipboardEntry(trimmed, "${trimmed.take(12)}...", "Auth Token", "CRITICAL", Icons.Default.Key, time)
        trimmed.matches(Regex("\\d{4,8}")) -> ClipboardEntry(trimmed, "●".repeat(trimmed.length), "OTP Code", "HIGH", Icons.Default.Pin, time)
        trimmed.contains("ssh ") || trimmed.contains("@") && trimmed.contains("-p ") -> ClipboardEntry(trimmed, trimmed.replace(Regex("[\\w.-]+@"), "***@"), "SSH Command", "HIGH", Icons.Default.Terminal, time)
        trimmed.split(Regex("[- ]")).size in 3..6 && trimmed.all { it.isLetter() || it == '-' || it == ' ' } && trimmed.length > 15 -> ClipboardEntry(trimmed, "●".repeat(trimmed.length.coerceAtMost(20)), "Passphrase", "HIGH", Icons.Default.Password, time)
        trimmed.contains("@") && trimmed.contains(".") && !trimmed.contains(" ") -> ClipboardEntry(trimmed, "${trimmed.take(3)}***@${trimmed.substringAfter("@")}", "Email", "MEDIUM", Icons.Default.Email, time)
        trimmed.matches(Regex("[+]?[\\d\\s()-]{7,15}")) -> ClipboardEntry(trimmed, "${trimmed.take(4)}***${trimmed.takeLast(2)}", "Phone Number", "MEDIUM", Icons.Default.Phone, time)
        trimmed.startsWith("http") && (trimmed.contains("token=") || trimmed.contains("session=") || trimmed.contains("auth=")) -> ClipboardEntry(trimmed, trimmed.substringBefore("?") + "?***", "URL with Token", "MEDIUM", Icons.Default.Link, time)
        trimmed.startsWith("http") -> ClipboardEntry(trimmed, trimmed.take(40) + if (trimmed.length > 40) "..." else "", "URL", "LOW", Icons.Default.Link, time)
        trimmed.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) -> ClipboardEntry(trimmed, trimmed, "IP Address", "LOW", Icons.Default.Language, time)
        else -> ClipboardEntry(trimmed, if (trimmed.length > 30) trimmed.take(30) + "..." else trimmed, "Plain Text", "NONE", Icons.Default.ContentPaste, time)
    }
}

private fun classifySample(text: String, time: String) = classifyClipboard(text, time)
