package com.deepfakeshield.feature.diagnostics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.av.engine.QuarantineEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.quarantineMessage) { uiState.quarantineMessage?.let { snackbar.showSnackbar(it); viewModel.clearQuarantineMessage() } }
    LaunchedEffect(uiState.threatDbStatus.updateMessage) { uiState.threatDbStatus.updateMessage?.let { snackbar.showSnackbar(it); viewModel.clearDbUpdateMessage() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Antivirus", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
                listOf("Scanner" to Icons.Default.Security, "Database" to Icons.Default.Storage, "Quarantine" to Icons.Default.Lock, "Settings" to Icons.Default.Settings).forEachIndexed { i, (l, ic) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l, fontSize = 12.sp) }, icon = { Icon(ic, null, Modifier.size(16.dp)) })
                }
            }
            when (tab) {
                0 -> ScannerTab(uiState, viewModel, haptic)
                1 -> DatabaseTab(uiState, viewModel)
                2 -> QuarantineTab(uiState, viewModel)
                3 -> SettingsTab(uiState, viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 1: SCANNER — visual scan with animated radar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ScannerTab(uiState: DiagnosticsUiState, viewModel: DiagnosticsViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val isScanning = uiState.isRunningAvScan
    val threats = uiState.avThreatsFound
    val results = uiState.avScanResults

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        // Animated scanner
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            val statusColor = when { isScanning -> Color(0xFF2196F3); threats > 0 -> Color(0xFFF44336); else -> Color(0xFF4CAF50) }
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(statusColor.copy(0.08f), Color.Transparent))).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            val rotation = rememberInfiniteTransition(label = "r")
                            val angle by rotation.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "a")
                            Canvas(Modifier.fillMaxSize()) {
                                val sw = 4.dp.toPx()
                                drawArc(Color(0xFF2196F3).copy(0.2f), 0f, 360f, false, style = Stroke(sw), topLeft = Offset(sw, sw), size = Size(size.width - 2 * sw, size.height - 2 * sw))
                                drawArc(Color(0xFF2196F3).copy(0.3f), 0f, 360f, false, style = Stroke(sw), topLeft = Offset(sw * 3, sw * 3), size = Size(size.width - 6 * sw, size.height - 6 * sw))
                                rotate(angle) {
                                    drawArc(Color(0xFF2196F3), -90f, 90f, false, style = Stroke(sw * 2, cap = StrokeCap.Round), topLeft = Offset(sw, sw), size = Size(size.width - 2 * sw, size.height - 2 * sw))
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Search, null, Modifier.size(40.dp), tint = Color(0xFF2196F3))
                                Spacer(Modifier.height(4.dp))
                                Text("Scanning...", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                                Text(uiState.avScanProgress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                            }
                        } else {
                            Canvas(Modifier.fillMaxSize()) {
                                val sw = 16.dp.toPx()
                                drawArc(Color.LightGray.copy(0.3f), 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw))
                                val pct = if (results.isNotEmpty()) (results.count { !it.isInfected }.toFloat() / results.size.coerceAtLeast(1)) * 270f else 270f
                                drawArc(statusColor, 135f, pct, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (threats > 0) Icons.Default.Warning else Icons.Default.Shield, null, Modifier.size(44.dp), tint = statusColor)
                                Spacer(Modifier.height(4.dp))
                                Text(when { threats > 0 -> "$threats Threats"; results.isNotEmpty() -> "Clean"; else -> "Ready" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                if (results.isNotEmpty()) Text("${results.size} items scanned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (isScanning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(0.7f).height(6.dp), color = Color(0xFF2196F3), strokeCap = StrokeCap.Round)
                    }
                }
            }
        }

        // Scan buttons
        if (!isScanning) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.runFullAvScan(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Full Scan")
                }
                OutlinedButton(onClick = { viewModel.runSelfTest(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.HealthAndSafety, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Self Test")
                }
            }
        }

        // Stats
        if (results.isNotEmpty() || uiState.systemChecks.isNotEmpty()) {
            val clean = results.count { !it.isInfected }; val infected = results.count { it.isInfected }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanStat("${results.size}", "Scanned", Color(0xFF2196F3), Modifier.weight(1f))
                ScanStat("$clean", "Clean", Color(0xFF4CAF50), Modifier.weight(1f))
                ScanStat("$infected", "Threats", Color(0xFFF44336), Modifier.weight(1f))
                ScanStat("${uiState.quarantinedItems.size}", "Quarantined", Color(0xFF9C27B0), Modifier.weight(1f))
            }
        }

        // Device integrity
        uiState.rootStatus?.let { root ->
            val rc = when (root.riskLevel) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); "MEDIUM" -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = rc.copy(0.06f))) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (root.isRooted) Icons.Default.Warning else Icons.Default.VerifiedUser, null, Modifier.size(24.dp), tint = rc)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Device Integrity", fontWeight = FontWeight.Bold)
                            Text(if (root.isRooted) "${root.reasons.size} issue${if (root.reasons.size > 1) "s" else ""} — ${root.riskLevel}" else "Secure — no root/tampering detected", style = MaterialTheme.typography.bodySmall, color = rc)
                        }
                        Surface(color = rc.copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text(root.riskLevel, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, color = rc, fontWeight = FontWeight.Bold) }
                    }
                    if (root.hookingDetected) { Spacer(Modifier.height(4.dp)); Text("⚠ Hooking framework detected (Frida/Xposed)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF44336)) }
                    if (root.emulatorDetected) { Spacer(Modifier.height(2.dp)); Text("ℹ Running on emulator", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800)) }
                }
            }
        }

        // Protection status
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Protection Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                StatusRow("Video Shield", uiState.videoShieldStatus, uiState.videoShieldHealthy)
                StatusRow("Message Shield", uiState.messageShieldStatus, uiState.messageShieldHealthy)
                StatusRow("Call Shield", uiState.callShieldStatus, uiState.callShieldHealthy)
                StatusRow("Antivirus", if (isScanning) "Scanning..." else if (threats > 0) "$threats threats!" else "Active", threats == 0 && !isScanning)
            }
        }

        // Threat list
        if (threats > 0) {
            Text("Detected Threats ($threats)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFFF44336))
            results.filter { it.isInfected }.forEach { threat ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(0.04f))) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, Modifier.size(20.dp), tint = Color(0xFFF44336))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(threat.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(threat.threatName ?: "Malware", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF44336))
                            Text(threat.path, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Self-test results
        if (uiState.systemChecks.isNotEmpty()) {
            Text("Self-Test", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            uiState.systemChecks.forEach { check ->
                val c = if (check.passed) Color(0xFF4CAF50) else Color(0xFFF44336)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (check.passed) Icons.Default.CheckCircle else Icons.Default.Cancel, null, Modifier.size(16.dp), tint = c)
                        Spacer(Modifier.width(6.dp)); Text(check.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(check.status, style = MaterialTheme.typography.labelSmall, color = c)
                    }
                }
            }
        }

        uiState.error?.let {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp)) { Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { viewModel.clearError(); viewModel.runFullAvScan() }) { Text("Retry") } }
            }
        }
    }
}

@Composable
private fun StatusRow(name: String, status: String, healthy: Boolean) {
    val c = if (healthy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning, null, Modifier.size(16.dp), tint = c)
        Spacer(Modifier.width(8.dp)); Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
private fun ScanStat(v: String, l: String, c: Color, m: Modifier) {
    Card(m, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) {
        Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 18.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 2: DATABASE
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DatabaseTab(uiState: DiagnosticsUiState, viewModel: DiagnosticsViewModel) {
    val db = uiState.threatDbStatus
    val healthy = db.totalHashes > 0 && db.downloadedHashes > 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (healthy) Color(0xFF4CAF50).copy(0.06f) else Color(0xFFFF9800).copy(0.06f))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, Modifier.size(28.dp), tint = if (healthy) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Threat Database", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (healthy) "Database up to date" else "Database needs updating", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DbStat("${db.totalHashes}", "Total Sigs"); DbStat("${db.bundledHashes}", "Bundled"); DbStat("${db.downloadedHashes}", "Downloaded")
                }
                Spacer(Modifier.height(8.dp))
                val lastUpdate = if (db.lastUpdateTimeMs > 0) SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(db.lastUpdateTimeMs)) else "Never"
                Text("Last update: $lastUpdate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (db.isUpdating) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Fetching threat feeds...", style = MaterialTheme.typography.bodySmall) }
        else { Button(onClick = { viewModel.forceUpdateThreatDb() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Update Now") } }

        Text("Threat Feeds (9 sources)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        listOf(
            "MalwareBazaar" to "Recent malware samples (abuse.ch)",
            "Feodo Tracker" to "Banking trojan C2 hashes",
            "ThreatFox" to "IOCs from various malware families",
            "SSLBL" to "SSL/TLS blacklist related malware",
            "URLhaus" to "Malicious URLs and payloads",
            "Botvrij" to "Netherlands CERT IOC list",
            "DigitalSide" to "Community threat intelligence",
            "Maltrail" to "Malware traffic signatures",
            "Phishing Army" to "Phishing domain blocklist",
        ).forEach { (name, desc) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column { Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        Text("YARA Rules Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(0.06f))) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Rule, null, Modifier.size(20.dp), tint = Color(0xFF9C27B0))
                    Spacer(Modifier.width(8.dp)); Text("22 built-in YARA rules active", fontWeight = FontWeight.SemiBold, color = Color(0xFF9C27B0))
                }
                Spacer(Modifier.height(4.dp))
                Text("Ransomware, Banking Trojans, Spyware, RATs, Droppers, Adware, Crypto Miners, Exploits, Phishing, Stalkerware, Toll Fraud, Clipboard Hijackers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Detection Layers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        listOf(
            Triple("Signature Matching", "SHA-256 hash lookup against 54K+ known malware hashes", Color(0xFF2196F3)),
            Triple("Byte Pattern Scan", "250+ hex patterns for shellcode, exploits, trojans, rootkits", Color(0xFF4CAF50)),
            Triple("YARA Rule Engine", "22 behavioral rules matching string combinations in files", Color(0xFF9C27B0)),
            Triple("Heuristic Analysis", "13 permission combos (stalkerware, ransomware, banking trojan, etc.)", Color(0xFFFF9800)),
            Triple("Archive Scanning", "Inspects APK/ZIP contents for nested threats", Color(0xFF795548)),
        ).forEach { (title, desc, color) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column { Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = color); Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun DbStat(v: String, l: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp); Text(l, style = MaterialTheme.typography.labelSmall) } }

// ═══════════════════════════════════════════════════════════════════
// TAB 3: QUARANTINE
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuarantineTab(uiState: DiagnosticsUiState, viewModel: DiagnosticsViewModel) {
    val items = uiState.quarantinedItems
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (items.isNotEmpty()) Color(0xFFF44336).copy(0.05f) else Color(0xFF4CAF50).copy(0.05f))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = if (items.isNotEmpty()) Color(0xFFF44336) else Color(0xFF4CAF50))
                    Spacer(Modifier.width(10.dp))
                    Column { Text("Quarantine Vault", fontWeight = FontWeight.Bold); Text(if (items.isEmpty()) "No quarantined files" else "${items.size} file${if (items.size > 1) "s" else ""} isolated", style = MaterialTheme.typography.bodySmall) }
                }
                if (items.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text("Files are XOR-obfuscated and cannot execute.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        val df = remember { SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()) }
        items.forEach { entry ->
            var showDelete by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(0.03f))) {
                Column(Modifier.padding(10.dp)) {
                    Text(entry.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.threatName ?: "Unknown threat", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF44336))
                    Text("Quarantined: ${df.format(Date(entry.quarantinedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.restoreQuarantinedItem(entry) }) { Icon(Icons.Default.Undo, null, Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)); Text("Restore", fontSize = 11.sp) }
                        if (showDelete) TextButton(onClick = { viewModel.deleteQuarantinedItem(entry); showDelete = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))) { Text("Confirm Delete", fontSize = 11.sp) }
                        else TextButton(onClick = { showDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))) { Icon(Icons.Default.Delete, null, Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)); Text("Delete", fontSize = 11.sp) }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VerifiedUser, null, Modifier.size(48.dp), tint = Color(0xFF4CAF50).copy(0.5f)); Spacer(Modifier.height(8.dp)); Text("Vault is empty — no threats isolated", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 4: SETTINGS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SettingsTab(uiState: DiagnosticsUiState, viewModel: DiagnosticsViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scan Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                SettingToggle("Auto-Quarantine", "Automatically isolate detected threats", uiState.autoQuarantineEnabled) { viewModel.setAutoQuarantine(it) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingToggle("Battery-Optimized", "Reduce scan depth on low battery", uiState.batteryOptimizedScan) { viewModel.setBatteryOptimizedScan(it) }
            }
        }

        Text("Detection Capabilities", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        listOf(
            "54,254+ SHA-256 malware signatures",
            "250+ byte pattern rules (shellcode, C2, rootkit, crypto)",
            "22 YARA behavioral rules (ransomware, spyware, RAT, banking trojan)",
            "13 heuristic combos (stalkerware, keylogger, dropper, clipboard hijacker)",
            "13 root/tamper detections (Magisk, Xposed, Frida, emulator, SELinux)",
            "9 real-time threat feeds (MalwareBazaar, Feodo, ThreatFox, URLhaus, etc.)",
            "Archive scanning (nested APK/ZIP/DEX)",
            "Real-time file monitoring (FileObserver)",
            "Permission-based app analysis (26 dangerous permissions)",
            "Entropy analysis (packer/encryption detection)"
        ).forEach {
            Row(Modifier.padding(vertical = 2.dp)) { Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SettingToggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium); Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
