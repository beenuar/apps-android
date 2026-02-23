package com.deepfakeshield.feature.callprotection

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ── Models ─────────────────────────────────────────────────────────

private data class CallEntry(
    val number: String, val name: String?, val type: Int,
    val date: Long, val duration: Long, val riskScore: Int,
    val riskReasons: List<String>, val areaInfo: String
)

private data class HourStat(val hour: Int, val total: Int, val suspicious: Int)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallProtectionScreen(
    viewModel: CallProtectionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableIntStateOf(0) }

    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms -> if (perms.values.all { it }) viewModel.enableCallProtection() }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.toggleSpeakerphoneMode(it) }
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) remember { ctx.getSystemService(Context.ROLE_SERVICE) as? RoleManager } else null
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.checkCallScreeningRole(it.resultCode == android.app.Activity.RESULT_OK) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Call Shield", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
                listOf("Dashboard" to Icons.Default.Dashboard, "Call Log" to Icons.Default.Phone, "Settings" to Icons.Default.Settings).forEachIndexed { i, (l, ic) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l, fontSize = 13.sp) }, icon = { Icon(ic, null, Modifier.size(18.dp)) })
                }
            }
            when (tab) {
                0 -> DashboardTab(uiState, ctx, haptic, roleManager, roleLauncher, phoneLauncher)
                1 -> CallLogTab(ctx, haptic)
                2 -> SettingsTab(uiState, viewModel, ctx, haptic, roleManager, roleLauncher, phoneLauncher, audioLauncher)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 1: DASHBOARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DashboardTab(
    uiState: CallProtectionUiState, ctx: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    roleManager: RoleManager?, roleLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    phoneLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var hourStats by remember { mutableStateOf<List<HourStat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val hasCallLogPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (hasCallLogPerm) {
            val result = withContext(Dispatchers.IO) { analyzeCallLog(ctx) }
            calls = result.first; hourStats = result.second
        }
        isLoading = false
    }

    val suspiciousCalls = calls.count { it.riskScore >= 50 }
    val unknownCallers = calls.count { it.name == null && it.type == CallLog.Calls.INCOMING_TYPE }
    val shortCalls = calls.count { it.duration in 1..3 && it.type == CallLog.Calls.INCOMING_TYPE }
    val intlCalls = calls.count { it.number.startsWith("+") && !it.number.startsWith("+1") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Protection status
        val isActive = uiState.callScreeningEnabled
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF4CAF50).copy(0.06f) else Color(0xFFF44336).copy(0.06f))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background((if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (isActive) Icons.Default.Shield else Icons.Default.ShieldMoon, null, Modifier.size(28.dp), tint = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (isActive) "Call Shield Active" else "Call Shield Disabled", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336))
                        Text(if (isActive) "Screening calls in real-time" else "Enable to protect against scam calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!isActive) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                            if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) { /* already held */ }
                            else roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        } else { phoneLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)) }
                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Enable Call Shield") }
                }
            }
        }

        // Stats
        if (isActive || calls.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${uiState.totalCallsAnalyzed}", "Screened", Color(0xFF2196F3), Modifier.weight(1f))
                StatCard("${uiState.scamsDetected}", "Blocked", Color(0xFFF44336), Modifier.weight(1f))
                StatCard("$suspiciousCalls", "Suspicious", Color(0xFFFF9800), Modifier.weight(1f))
                StatCard("$unknownCallers", "Unknown", Color(0xFF9E9E9E), Modifier.weight(1f))
            }
        }

        // Risk indicators
        if (calls.isNotEmpty()) {
            Text("Risk Indicators", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            listOf(
                Triple("Short Duration Calls (1-3s)", "$shortCalls", if (shortCalls > 3) Color(0xFFF44336) else Color(0xFF4CAF50)),
                Triple("Unknown Callers", "$unknownCallers", if (unknownCallers > 10) Color(0xFFFF9800) else Color(0xFF4CAF50)),
                Triple("International Calls", "$intlCalls", if (intlCalls > 5) Color(0xFFFF9800) else Color(0xFF4CAF50))
            ).forEach { (label, value, color) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
                    }
                }
            }

            // Hour distribution
            if (hourStats.isNotEmpty()) {
                Text("Call Activity by Hour", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        val maxH = hourStats.maxOf { it.total }.coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            hourStats.forEach { h ->
                                val pct = h.total.toFloat() / maxH
                                val color = when { h.suspicious > 0 -> Color(0xFFF44336); h.hour in 0..5 -> Color(0xFFFF9800); else -> Color(0xFF2196F3) }
                                Box(Modifier.weight(1f).fillMaxHeight(pct.coerceAtLeast(0.02f)).background(color.copy(0.6f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("12am", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6am", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("12pm", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6pm", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("12am", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(Color(0xFF2196F3).copy(0.6f), CircleShape)); Spacer(Modifier.width(4.dp)); Text("Normal", fontSize = 9.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(Color(0xFFFF9800).copy(0.6f), CircleShape)); Spacer(Modifier.width(4.dp)); Text("Late Night", fontSize = 9.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(Color(0xFFF44336).copy(0.6f), CircleShape)); Spacer(Modifier.width(4.dp)); Text("Suspicious", fontSize = 9.sp) }
                        }
                    }
                }
            }

            // Recent suspicious calls
            val suspicious = calls.filter { it.riskScore >= 40 }.take(8)
            if (suspicious.isNotEmpty()) {
                Text("Recent Suspicious Calls", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                suspicious.forEach { call -> CallCard(call) }
            }
        } else if (!hasCallLogPerm) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.06f))) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhoneLocked, null, Modifier.size(40.dp), tint = Color(0xFFFF9800))
                    Spacer(Modifier.height(8.dp)); Text("Call Log Access Needed", fontWeight = FontWeight.Bold)
                    Text("Grant call log permission to analyze your call history for scam patterns.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { phoneLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)) }, shape = RoundedCornerShape(12.dp)) { Text("Grant Permission") }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 2: CALL LOG ANALYSIS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CallLogTab(ctx: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    val filtered = remember(calls, filter) {
        when (filter) { 1 -> calls.filter { it.riskScore >= 60 }; 2 -> calls.filter { it.riskScore in 30..59 }; 3 -> calls.filter { it.type == CallLog.Calls.MISSED_TYPE }; 4 -> calls.filter { it.name == null }; else -> calls }
    }

    if (!hasPerm) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Call log permission required", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = {
                    isScanning = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { calls = withContext(Dispatchers.IO) { analyzeCallLog(ctx).first }; isScanning = false }
                }, Modifier.fillMaxWidth().height(48.dp), enabled = !isScanning, shape = RoundedCornerShape(14.dp)) {
                    if (isScanning) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
                    else { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("Scan Call Log") }
                }
            }

            if (calls.isNotEmpty()) {
                item {
                    val high = calls.count { it.riskScore >= 60 }; val med = calls.count { it.riskScore in 30..59 }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (high > 0) Color(0xFFF44336).copy(0.05f) else Color(0xFF4CAF50).copy(0.05f))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${calls.size} calls analyzed • $high high risk • $med suspicious", fontWeight = FontWeight.Bold, color = if (high > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("All" to calls.size, "High Risk" to calls.count { it.riskScore >= 60 }, "Medium" to calls.count { it.riskScore in 30..59 }, "Missed" to calls.count { it.type == CallLog.Calls.MISSED_TYPE }, "Unknown" to calls.count { it.name == null }).forEachIndexed { i, (l, c) ->
                            FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text("$l ($c)", fontSize = 10.sp) })
                        }
                    }
                }

                items(filtered.take(100)) { call -> CallCard(call) }
            }
        }
    }
}

@Composable
private fun CallCard(call: CallEntry) {
    val rc = when { call.riskScore >= 60 -> Color(0xFFF44336); call.riskScore >= 30 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
    val typeIcon = when (call.type) { CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived; CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade; CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed; else -> Icons.Default.Phone }
    val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(call.name ?: call.number, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, fontFamily = if (call.name == null) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${df.format(Date(call.date))} • ${call.duration}s${if (call.areaInfo.isNotEmpty()) " • ${call.areaInfo}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (call.riskScore >= 30) {
                    Surface(color = rc.copy(0.12f), shape = RoundedCornerShape(4.dp)) {
                        Text("${call.riskScore}%", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 10.sp, color = rc, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (expanded && call.riskReasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                call.riskReasons.forEach { r ->
                    Row(Modifier.padding(vertical = 1.dp)) { Icon(Icons.Default.Warning, null, Modifier.size(12.dp), tint = rc); Spacer(Modifier.width(4.dp)); Text(r, style = MaterialTheme.typography.labelSmall, color = rc) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 3: SETTINGS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SettingsTab(
    uiState: CallProtectionUiState, viewModel: CallProtectionViewModel, ctx: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    roleManager: RoleManager?, roleLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    phoneLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    audioLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Call Screening
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Call Screening", fontWeight = FontWeight.Bold)
                        Text("Automatically screens incoming calls and blocks scams", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.callScreeningEnabled) {
                        Surface(color = Color(0xFF4CAF50).copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text("ACTIVE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold) }
                    }
                }
                if (!uiState.callScreeningEnabled) {
                    Spacer(Modifier.height(8.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Text("To enable: set DeepfakeShield as your call screening app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                            else viewModel.enableCallProtection()
                        } else phoneLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG))
                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Enable Call Screening") }
                }
            }
        }

        // How screening works
        Text("How Screening Works", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        listOf(
            Triple("Score ≥ 65", "Call BLOCKED — high confidence scam (premium rate, known scam pattern, blocked list)", Color(0xFFF44336)),
            Triple("Score 45-64", "Call SILENCED — suspicious but uncertain (unknown international, VOIP, short pattern)", Color(0xFFFF9800)),
            Triple("Score < 45", "Call ALLOWED — appears legitimate (known contact, local number, low risk)", Color(0xFF4CAF50))
        ).forEach { (label, desc, color) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(0.04f))) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = color)
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Speakerphone mode
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Live Voice Analysis", fontWeight = FontWeight.Bold)
                        Text("Detects scam keywords during speakerphone calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.speakerphoneEnabled, onCheckedChange = { if (it) audioLauncher.launch(Manifest.permission.RECORD_AUDIO) else viewModel.toggleSpeakerphoneMode(false) })
                }
                Spacer(Modifier.height(6.dp))
                Text("Listens for phrases like 'social security', 'gift card', 'wire transfer', 'verify your account' and alerts you in real-time.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Risk factors explained
        Text("What We Check", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        listOf(
            "Number Pattern" to "International callback codes (876, 809), premium rate (900), sequential digits",
            "Call Behavior" to "Very short calls (1-3s), late night (12am-5am), rapid repeat calls",
            "Contact Match" to "Numbers not in your contacts are flagged as unknown",
            "Area Code Intel" to "300+ area codes mapped — flags high-risk Caribbean and premium codes",
            "VOIP Detection" to "Internet-based numbers are commonly used for spoofing",
            "Database" to "Checks against local blocked/whitelisted number database"
        ).forEach { (title, desc) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                Column(Modifier.padding(10.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Statistics
        if (uiState.totalCallsAnalyzed > 0 || uiState.blockedNumbers > 0) {
            Text("Statistics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.5f))) {
                Column(Modifier.padding(14.dp)) {
                    StatRow("Calls Screened", "${uiState.totalCallsAnalyzed}")
                    StatRow("Scams Detected", "${uiState.scamsDetected}")
                    StatRow("Numbers Blocked", "${uiState.blockedNumbers}")
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(0.06f))) {
        Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp); Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// CALL LOG ANALYSIS ENGINE
// ═══════════════════════════════════════════════════════════════════

private val AREA_CODES: Map<String, String> by lazy {
    mapOf("201" to "NJ", "202" to "DC", "206" to "Seattle", "212" to "NYC", "213" to "LA", "214" to "Dallas", "305" to "Miami", "312" to "Chicago", "404" to "Atlanta", "415" to "SF",
        "469" to "Dallas", "503" to "Portland", "512" to "Austin", "602" to "Phoenix", "617" to "Boston", "646" to "NYC", "702" to "Vegas", "713" to "Houston", "718" to "NYC", "720" to "Denver",
        "786" to "Miami", "818" to "LA", "832" to "Houston", "800" to "Toll-Free", "833" to "Toll-Free", "844" to "Toll-Free", "855" to "Toll-Free", "866" to "Toll-Free", "877" to "Toll-Free", "888" to "Toll-Free", "900" to "Premium")
}

private val HIGH_RISK_CODES = setOf("900", "976", "809", "876", "284", "649", "268", "473", "664", "767")

private fun analyzeCallLog(ctx: Context): Pair<List<CallEntry>, List<HourStat>> {
    val calls = mutableListOf<CallEntry>()
    val hourCounts = IntArray(24)
    val hourSuspicious = IntArray(24)

    try {
        val cursor = ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "${CallLog.Calls.DATE} DESC") ?: return emptyList<CallEntry>() to emptyList()
        var count = 0
        while (cursor.moveToNext() && count < 500) {
            val number = cursor.getString(0) ?: continue
            val name = cursor.getString(1)
            val type = cursor.getInt(2)
            val date = cursor.getLong(3)
            val dur = cursor.getLong(4)
            val digits = number.filter { it.isDigit() }
            if (digits.length < 4) continue

            var risk = 0; val reasons = mutableListOf<String>()
            val ac = if (digits.length >= 10) digits.takeLast(10).take(3) else digits.take(3)
            val areaInfo = AREA_CODES[ac] ?: ""

            if (ac in HIGH_RISK_CODES) { risk += 45; reasons += "High-risk area code ($ac)" }
            if (ac == "900" || ac == "976") { risk += 50; reasons += "Premium rate number" }
            if (dur == 0L && type == CallLog.Calls.INCOMING_TYPE) { risk += 8; reasons += "Missed/0-duration incoming" }
            if (dur in 1..3 && type == CallLog.Calls.INCOMING_TYPE) { risk += 15; reasons += "Very short call (${dur}s) — robocall pattern" }
            if (name == null && type == CallLog.Calls.INCOMING_TYPE) { risk += 8; reasons += "Unknown caller (not in contacts)" }

            val cal = Calendar.getInstance().apply { timeInMillis = date }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..5 && type == CallLog.Calls.INCOMING_TYPE) { risk += 12; reasons += "Late night call (${hour}:00)" }

            if (number.startsWith("+") && !number.startsWith("+1")) { risk += 10; reasons += "International number" }
            if (digits.length >= 7 && digits.takeLast(7).toSet().size == 1) { risk += 25; reasons += "Repeating digits pattern" }

            hourCounts[hour]++
            if (risk >= 30) hourSuspicious[hour]++
            calls += CallEntry(number, name, type, date, dur, risk.coerceAtMost(100), reasons, areaInfo)
            count++
        }
        cursor.close()
    } catch (_: Exception) {}

    val hourStats = (0..23).map { HourStat(it, hourCounts[it], hourSuspicious[it]) }
    return calls.sortedByDescending { it.riskScore } to hourStats
}
