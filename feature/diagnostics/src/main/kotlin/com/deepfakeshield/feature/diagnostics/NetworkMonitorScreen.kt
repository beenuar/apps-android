package com.deepfakeshield.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

// ── Models ─────────────────────────────────────────────────────────

private data class LiveConnection(
    val uid: Int, val appName: String, val packageName: String,
    val localPort: Int, val remoteIp: String, val remotePort: Int,
    val state: String, val protocol: String, val remoteHost: String,
    val portService: String, val serverOrg: String, val category: String
)

private data class AppNetworkInfo(
    val name: String, val packageName: String, val uid: Int,
    val txBytes: Long, val rxBytes: Long, val totalBytes: Long,
    val riskLevel: String, val riskReason: String, val percentage: Float
)

private data class NetSecCheck(
    val name: String, val value: String, val status: String,
    val detail: String, val fixAction: String = "", val fixIntent: Intent? = null
)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(onNavigateBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Network Monitor", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
                listOf("Live" to Icons.Default.Wifi, "Usage" to Icons.Default.DataUsage, "Security" to Icons.Default.Security).forEachIndexed { i, (l, ic) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l, fontSize = 13.sp) }, icon = { Icon(ic, null, Modifier.size(18.dp)) })
                }
            }
            when (tab) { 0 -> LiveTab(); 1 -> UsageTab(); 2 -> SecurityTab() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 1: LIVE CONNECTIONS (enhanced)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveTab() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var conns by remember { mutableStateOf<List<LiveConnection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var expandedApp by remember { mutableStateOf<String?>(null) }
    var filterApp by remember { mutableStateOf<String?>(null) }
    var filterCat by remember { mutableIntStateOf(0) }
    var connHistory by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(paused) {
        while (isActive && !paused) {
            conns = withContext(Dispatchers.IO) { readConnections(ctx) }
            connHistory = (connHistory + conns.size).takeLast(30)
            loading = false; delay(3000)
        }
    }

    val categories = listOf("All", "Tracking", "CDN", "Cloud", "Social", "Other")
    val filtered = remember(conns, filterApp, filterCat) {
        var f = if (filterApp != null) conns.filter { it.packageName == filterApp } else conns
        if (filterCat > 0) f = f.filter { it.category == categories[filterCat] }
        f
    }
    val grouped = filtered.groupBy { it.appName }.toSortedMap()
    val serverCategories = conns.groupBy { it.category }.mapValues { it.value.size }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.06f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Live Network Activity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Polling every 3s from /proc/net/*", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        IconButton(onClick = { paused = !paused; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, Modifier.size(32.dp)) {
                            Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, Modifier.size(18.dp), tint = Color(0xFF2196F3))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${conns.size}", "Conn", Color(0xFF2196F3), Modifier.weight(1f))
                        Stat("${conns.map { it.uid }.distinct().size}", "Apps", Color(0xFF7C4DFF), Modifier.weight(1f))
                        Stat("${conns.map { it.remoteIp }.distinct().size}", "Servers", Color(0xFFFF9800), Modifier.weight(1f))
                        Stat("${conns.count { it.category == "Tracking" }}", "Trackers", Color(0xFFF44336), Modifier.weight(1f))
                    }
                    if (!paused && !loading) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF2196F3).copy(alpha = 0.4f), strokeCap = StrokeCap.Round) }

                    // Mini connection graph
                    if (connHistory.size > 2) {
                        Spacer(Modifier.height(8.dp))
                        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                            val max = (connHistory.max() + 1).toFloat()
                            val w = size.width / (connHistory.size - 1).coerceAtLeast(1)
                            for (i in 1 until connHistory.size) {
                                val y1 = size.height - (connHistory[i-1] / max * size.height)
                                val y2 = size.height - (connHistory[i] / max * size.height)
                                drawLine(Color(0xFF2196F3), Offset((i-1)*w, y1), Offset(i*w, y2), strokeWidth = 2.dp.toPx())
                            }
                        }
                        Text("Connection count over time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
            }
        }

        // Server category breakdown
        if (serverCategories.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    categories.forEachIndexed { i, c ->
                        val count = if (i == 0) conns.size else conns.count { it.category == c }
                        FilterChip(selected = filterCat == i, onClick = { filterCat = i }, label = { Text("$c ($count)", fontSize = 10.sp) })
                    }
                }
            }
        }

        if (filterApp != null) {
            item { AssistChip(onClick = { filterApp = null }, label = { Text("App: ${conns.find { it.packageName == filterApp }?.appName ?: filterApp}") }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }) }
        }

        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Scanning...") } } }
        } else if (grouped.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)); Text("No connections") } } }
        }

        grouped.forEach { (app, cs) ->
            item(key = "h_$app") {
                Card(Modifier.fillMaxWidth().clickable { expandedApp = if (expandedApp == app) null else app }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).background(Color(0xFF2196F3).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Text("${cs.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2196F3)) }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                val trackers = cs.count { it.category == "Tracking" }
                                Text("${cs.size} conn${if (cs.size > 1) "s" else ""}${if (trackers > 0) " • $trackers tracker${if (trackers > 1) "s" else ""}" else ""}", style = MaterialTheme.typography.labelSmall, color = if (trackers > 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { filterApp = cs.first().packageName }, Modifier.size(24.dp)) { Icon(Icons.Default.FilterAlt, null, Modifier.size(14.dp)) }
                            Icon(if (expandedApp == app) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                        }
                        if (expandedApp == app) {
                            Spacer(Modifier.height(6.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                            cs.forEach { c ->
                                val stateCol = when (c.state) { "ESTABLISHED" -> Color(0xFF4CAF50); "SYN_SENT", "SYN_RECV" -> Color(0xFFFF9800); "LISTEN" -> Color(0xFF2196F3); else -> Color(0xFF9E9E9E) }
                                val catCol = when (c.category) { "Tracking" -> Color(0xFFF44336); "CDN" -> Color(0xFF2196F3); "Cloud" -> Color(0xFF7C4DFF); "Social" -> Color(0xFFFF9800); else -> Color(0xFF607D8B) }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(stateCol, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (c.remoteHost.isNotEmpty() && c.remoteHost != c.remoteIp) c.remoteHost else c.remoteIp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                            if (c.serverOrg.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Surface(color = catCol.copy(0.12f), shape = RoundedCornerShape(3.dp)) { Text(c.serverOrg, Modifier.padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 7.sp, color = catCol, fontWeight = FontWeight.Bold) } }
                                        }
                                        Text("${c.protocol} :${c.localPort} → :${c.remotePort} ${c.portService}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                    }
                                    Surface(color = stateCol.copy(0.12f), shape = RoundedCornerShape(3.dp)) { Text(c.state, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 7.sp, color = stateCol, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${cs.first().packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Settings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("App Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 2: DATA USAGE
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun UsageTab() {
    val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    var apps by remember { mutableStateOf<List<AppNetworkInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { getUsage(ctx) }; loading = false }

    val totalTx = apps.sumOf { it.txBytes }; val totalRx = apps.sumOf { it.rxBytes }; val totalAll = totalTx + totalRx
    val suspicious = apps.count { it.riskLevel == "SUSPICIOUS" }

    val filtered = remember(apps, filter, search) {
        val f = when (filter) { 1 -> apps.filter { it.riskLevel == "SUSPICIOUS" }; 2 -> apps.filter { it.totalBytes > 10_000_000 }; 3 -> apps.filter { it.txBytes > it.rxBytes * 2 }; else -> apps }
        if (search.isBlank()) f else f.filter { it.name.contains(search, true) || it.packageName.contains(search, true) }
    }

    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (suspicious > 0) Color(0xFFFF9800).copy(0.05f) else Color(0xFF4CAF50).copy(0.05f))) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            val pct = if (totalAll > 0) totalTx.toFloat() / totalAll else 0.5f
                            Canvas(Modifier.fillMaxSize()) { val sw = 10.dp.toPx(); drawArc(Color(0xFF2196F3), -90f, pct*360, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw/2, sw/2), size = Size(size.width-sw, size.height-sw)); drawArc(Color(0xFF4CAF50), -90f+pct*360, (1-pct)*360, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw/2, sw/2), size = Size(size.width-sw, size.height-sw)) }
                            Text(fmtB(totalAll), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Data Overview", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row { Box(Modifier.size(8.dp).background(Color(0xFF2196F3), CircleShape)); Spacer(Modifier.width(6.dp)); Text("↑ ${fmtB(totalTx)}", style = MaterialTheme.typography.bodySmall) }
                            Row { Box(Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape)); Spacer(Modifier.width(6.dp)); Text("↓ ${fmtB(totalRx)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${apps.size}", "Apps", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Stat("$suspicious", "Suspicious", Color(0xFFFF9800), Modifier.weight(1f))
                        Stat("${apps.count { it.totalBytes > 100_000_000 }}", "Heavy", Color(0xFF2196F3), Modifier.weight(1f))
                    }
                }
            }
        }
        if (apps.size >= 3) { item { Text("Top Bandwidth", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall); apps.take(5).forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(a.name, Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); LinearProgressIndicator(progress = { a.percentage / 100f }, Modifier.weight(1f).height(8.dp), strokeCap = StrokeCap.Round, color = when (a.riskLevel) { "SUSPICIOUS" -> Color(0xFFFF9800); "HIGH_USAGE" -> Color(0xFF2196F3); else -> Color(0xFF4CAF50) }); Spacer(Modifier.width(6.dp)); Text("${"%.0f".format(a.percentage)}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp)) }
        } } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All" to apps.size, "Suspicious" to suspicious, "Heavy" to apps.count { it.totalBytes > 10_000_000 }, "Upload" to apps.count { it.txBytes > it.rxBytes * 2 }).forEachIndexed { i, (l, c) -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text("$l ($c)", fontSize = 11.sp) }) } } }
        items(filtered, key = { it.packageName }) { a ->
            val rc = when (a.riskLevel) { "SUSPICIOUS" -> Color(0xFFFF9800); "HIGH_USAGE" -> Color(0xFF2196F3); else -> Color(0xFF4CAF50) }
            val exp = expanded == a.packageName
            Card(Modifier.fillMaxWidth().clickable { expanded = if (exp) null else a.packageName }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(rc.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(when (a.riskLevel) { "SUSPICIOUS" -> Icons.Default.Warning; "HIGH_USAGE" -> Icons.Default.DataUsage; else -> Icons.Default.CheckCircle }, null, tint = rc, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("↑${fmtB(a.txBytes)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2196F3)); Text("↓${fmtB(a.rxBytes)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50)) } }
                        Column(horizontalAlignment = Alignment.End) { Text(fmtB(a.totalBytes), fontWeight = FontWeight.Bold, fontSize = 12.sp); Surface(color = rc.copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text(a.riskLevel.replace("_", " "), Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = rc, fontWeight = FontWeight.Bold) } }
                    }
                    if (exp) {
                        Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                        Text("Package: ${a.packageName}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("UID: ${a.uid} • Upload ratio: ${"%.0f".format(if (a.totalBytes > 0) a.txBytes * 100.0 / a.totalBytes else 0.0)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (a.riskReason.isNotBlank()) { Spacer(Modifier.height(4.dp)); Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = rc.copy(0.06f))) { Row(Modifier.padding(6.dp)) { Icon(Icons.Default.Info, null, Modifier.size(12.dp), tint = rc); Spacer(Modifier.width(4.dp)); Text(a.riskReason, style = MaterialTheme.typography.labelSmall, color = rc) } } }
                        Spacer(Modifier.height(6.dp)); Button(onClick = { try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${a.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Settings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Manage") }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 3: NETWORK SECURITY
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SecurityTab() {
    val ctx = LocalContext.current
    var checks by remember { mutableStateOf<List<NetSecCheck>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var netInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runSecurityChecks(ctx) }
        checks = result.first; netInfo = result.second; loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            val good = checks.count { it.status == "GOOD" }; val warn = checks.count { it.status == "WARN" }; val bad = checks.count { it.status == "BAD" }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (bad > 0) Color(0xFFF44336).copy(0.05f) else Color(0xFF4CAF50).copy(0.05f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Network Security", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("$good", "Good", Color(0xFF4CAF50), Modifier.weight(1f))
                        Stat("$warn", "Warn", Color(0xFFFF9800), Modifier.weight(1f))
                        Stat("$bad", "Risk", Color(0xFFF44336), Modifier.weight(1f))
                    }
                }
            }

            // Network info
            if (netInfo.isNotEmpty()) {
                Text("Network Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                    Column(Modifier.padding(12.dp)) {
                        netInfo.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(k, Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Text("Security Checks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            checks.forEach { c ->
                val sc = when (c.status) { "GOOD" -> Color(0xFF4CAF50); "WARN" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                var exp by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().clickable { exp = !exp }.animateContentSize(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).background(sc.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(when (c.status) { "GOOD" -> Icons.Default.CheckCircle; "WARN" -> Icons.Default.Warning; else -> Icons.Default.Error }, null, Modifier.size(16.dp), tint = sc) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(c.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Surface(color = sc.copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text(c.status, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 8.sp, color = sc, fontWeight = FontWeight.Bold) }
                        }
                        if (exp) {
                            Spacer(Modifier.height(6.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (c.fixIntent != null) { Spacer(Modifier.height(4.dp)); Button(onClick = { try { ctx.startActivity(c.fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(c.fixAction, fontSize = 12.sp) } }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared ─────────────────────────────────────────────────────────

@Composable private fun Stat(v: String, l: String, c: Color, m: Modifier) { Card(m, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) { Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 16.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) } } }
private fun fmtB(b: Long): String = when { b < 1024 -> "$b B"; b < 1048576 -> "${"%.1f".format(b / 1024.0)} KB"; b < 1073741824 -> "${"%.1f".format(b / 1048576.0)} MB"; else -> "${"%.2f".format(b / 1073741824.0)} GB" }

// ═══════════════════════════════════════════════════════════════════
// DATA ENGINE
// ═══════════════════════════════════════════════════════════════════

private val PORT_MAP = mapOf(
    80 to "HTTP", 443 to "HTTPS", 853 to "DNS/TLS", 8080 to "HTTP-ALT", 8443 to "HTTPS-ALT",
    53 to "DNS", 5228 to "GCM", 5229 to "GCM", 5230 to "GCM", 5222 to "XMPP", 5223 to "XMPP/TLS",
    993 to "IMAPS", 995 to "POP3S", 587 to "SMTP", 465 to "SMTPS", 143 to "IMAP", 110 to "POP3",
    22 to "SSH", 21 to "FTP", 3478 to "STUN", 3479 to "STUN", 19302 to "STUN/WebRTC",
    1935 to "RTMP", 8883 to "MQTT/TLS", 1883 to "MQTT", 5060 to "SIP", 5061 to "SIP/TLS",
    9050 to "SOCKS/Tor", 9150 to "Tor", 9001 to "Tor-OR"
)

private val SERVER_SIGS = listOf(
    Triple("Google", "Cloud", listOf("142.250.", "172.217.", "216.58.", "74.125.", "173.194.", "209.85.", "64.233.")),
    Triple("Meta", "Social", listOf("157.240.", "31.13.", "179.60.", "185.89.")),
    Triple("Amazon", "Cloud", listOf("52.", "54.", "34.", "3.", "13.", "18.")),
    Triple("Microsoft", "Cloud", listOf("40.76.", "40.90.", "40.112.", "40.126.", "13.64.", "13.67.", "13.104.", "52.96.", "204.79.")),
    Triple("Apple", "Cloud", listOf("17.248.", "17.249.", "17.250.", "17.252.", "17.253.")),
    Triple("Cloudflare", "CDN", listOf("104.16.", "104.17.", "104.18.", "104.19.", "104.20.", "104.21.", "104.22.", "104.23.", "104.24.", "104.25.", "104.26.", "104.27.", "172.64.", "172.65.", "172.66.", "172.67.")),
    Triple("Akamai", "CDN", listOf("23.32.", "23.33.", "23.34.", "23.35.", "23.36.", "23.37.", "23.38.", "23.39.", "23.40.", "23.41.", "23.42.", "23.43.", "23.44.", "23.45.", "23.46.", "23.47.", "23.48.", "23.49.", "23.50.", "23.51.", "23.52.", "23.53.", "23.54.", "23.55.", "23.56.", "23.57.", "23.58.", "23.59.", "23.60.", "23.61.", "23.62.", "23.63.")),
    Triple("Fastly", "CDN", listOf("151.101.", "199.232.")),
    Triple("Facebook Ads", "Tracking", listOf("157.240.1.", "157.240.3.", "157.240.7.", "157.240.11.")),
    Triple("Google Ads", "Tracking", listOf("216.58.214.", "142.250.80.", "142.250.189.")),
    Triple("DoubleClick", "Tracking", listOf("172.217.14.")),
    Triple("Twitter", "Social", listOf("104.244.")),
    Triple("TikTok", "Social", listOf("161.117.")),
    Triple("Snap", "Social", listOf("35.190.")),
)

private fun identifyServer(ip: String): Pair<String, String> {
    for ((name, cat, prefixes) in SERVER_SIGS) { if (prefixes.any { ip.startsWith(it) }) return name to cat }
    return "" to "Other"
}

private val TCP_STATES = mapOf("01" to "ESTABLISHED", "02" to "SYN_SENT", "03" to "SYN_RECV", "04" to "FIN_WAIT1", "05" to "FIN_WAIT2", "06" to "TIME_WAIT", "07" to "CLOSE", "08" to "CLOSE_WAIT", "09" to "LAST_ACK", "0A" to "LISTEN", "0B" to "CLOSING")

private fun readConnections(ctx: Context): List<LiveConnection> {
    val pm = ctx.packageManager; val uidMap = HashMap<Int, Pair<String, String>>()
    val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.getInstalledPackages(0)
    pkgs.forEach { p -> p.applicationInfo?.let { uidMap[it.uid] = Pair(try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { p.packageName }, p.packageName) } }
    val r = mutableListOf<LiveConnection>()
    listOf("/proc/net/tcp" to "TCP", "/proc/net/tcp6" to "TCP6", "/proc/net/udp" to "UDP", "/proc/net/udp6" to "UDP6").forEach { (f, p) ->
        try { File(f).readLines().drop(1).forEach { line -> parseLine(line, p, uidMap)?.let { r.add(it) } } } catch (_: Exception) {}
    }
    return r.filter { it.remoteIp != "0.0.0.0" && it.remoteIp != "::" && it.remotePort != 0 }.sortedWith(compareByDescending<LiveConnection> { it.state == "ESTABLISHED" }.thenBy { it.appName })
}

private fun parseLine(line: String, proto: String, uidMap: Map<Int, Pair<String, String>>): LiveConnection? {
    val p = line.trim().split("\\s+".toRegex()); if (p.size < 10) return null
    val uid = try { p[7].toInt() } catch (_: Exception) { return null }
    val (_, lPort) = parseHex(p[1], proto.contains("6")); val (rIp, rPort) = parseHex(p[2], proto.contains("6"))
    val state = if (proto.startsWith("UDP")) { if (rPort == 0) "LISTEN" else "ACTIVE" } else TCP_STATES[p[3].uppercase()] ?: "?"
    val (app, pkg) = uidMap[uid] ?: ("UID $uid" to "uid:$uid")
    val portSvc = PORT_MAP[rPort]?.let { "($it)" } ?: ""
    val (org, cat) = identifyServer(rIp)
    val host = try { if (rIp != "0.0.0.0" && rIp != "::" && !rIp.startsWith("10.") && !rIp.startsWith("127.")) InetAddress.getByName(rIp).canonicalHostName.let { if (it != rIp) it else "" } else "" } catch (_: Exception) { "" }
    return LiveConnection(uid, app, pkg, lPort, rIp, rPort, state, proto.replace("6", ""), host, portSvc, org, cat)
}

private fun parseHex(hex: String, v6: Boolean): Pair<String, Int> {
    val sp = hex.split(":"); if (sp.size != 2) return "0.0.0.0" to 0
    val port = try { sp[1].toInt(16) } catch (_: Exception) { 0 }; val ipH = sp[0]
    if (!v6 && ipH.length == 8) { val n = try { ipH.toLong(16) } catch (_: Exception) { 0L }; return "${n and 0xFF}.${(n shr 8) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 24) and 0xFF}" to port }
    if (v6 && ipH.length == 32 && (ipH.startsWith("0000000000000000FFFF0000", true) || ipH.startsWith("0000000000000000ffff0000"))) { val v4 = ipH.substring(24); val n = try { v4.toLong(16) } catch (_: Exception) { 0L }; return "${n and 0xFF}.${(n shr 8) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 24) and 0xFF}" to port }
    if (v6) return "::" to port
    return "0.0.0.0" to port
}

private fun getUsage(ctx: Context): List<AppNetworkInfo> {
    val pm = ctx.packageManager; val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.getInstalledPackages(0)
    val susPat = listOf("vpn", "proxy", "tunnel", "tor", "hide", "anon", "socks")
    val r = pkgs.mapNotNull { p -> val ai = p.applicationInfo ?: return@mapNotNull null; val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { return@mapNotNull null }; val tx = TrafficStats.getUidTxBytes(ai.uid).coerceAtLeast(0); val rx = TrafficStats.getUidRxBytes(ai.uid).coerceAtLeast(0); val tot = tx + rx; if (tot <= 0) return@mapNotNull null
        val sus = susPat.any { p.packageName.contains(it, true) } || (tx > rx * 5 && tx > 1_000_000)
        val risk = when { sus -> "SUSPICIOUS"; tot > 100_000_000 -> "HIGH_USAGE"; else -> "NORMAL" }
        val reason = when { tx > rx * 5 && tx > 1_000_000 -> "Sends 5x more than receives — data exfil?"; susPat.any { p.packageName.contains(it, true) } -> "Proxy/tunnel pattern in package name"; tot > 500_000_000 -> "Extreme bandwidth (${fmtB(tot)})"; tot > 100_000_000 -> "High bandwidth"; else -> "" }
        AppNetworkInfo(name, p.packageName, ai.uid, tx, rx, tot, risk, reason, 0f) }.sortedByDescending { it.totalBytes }
    val max = r.firstOrNull()?.totalBytes?.toFloat()?.coerceAtLeast(1f) ?: 1f
    return r.map { it.copy(percentage = it.totalBytes * 100f / max) }
}

private fun runSecurityChecks(ctx: Context): Pair<List<NetSecCheck>, Map<String, String>> {
    val checks = mutableListOf<NetSecCheck>()
    val info = linkedMapOf<String, String>()

    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val net = cm?.activeNetwork; val caps = net?.let { cm.getNetworkCapabilities(it) }; val link = net?.let { cm.getLinkProperties(it) }

    val type = when { caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"; else -> "None" }
    info["Connection"] = type

    try {
        val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val v4 = ifaces.flatMap { Collections.list(it.inetAddresses) }.find { !it.isLoopbackAddress && it is Inet4Address }
        info["Local IP"] = v4?.hostAddress ?: "Unknown"
    } catch (_: Exception) { info["Local IP"] = "Unknown" }

    link?.let { l ->
        info["DNS Servers"] = l.dnsServers.joinToString(", ") { it.hostAddress ?: "?" }
        info["Gateway"] = l.routes.find { it.isDefaultRoute }?.gateway?.hostAddress ?: "Unknown"
        info["Interface"] = l.interfaceName ?: "Unknown"
        info["Domains"] = l.domains ?: "None"
    }

    val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    checks += NetSecCheck("VPN Status", if (hasVpn) "Active — traffic encrypted" else "Not active — ISP sees traffic", if (hasVpn) "GOOD" else "BAD",
        "A VPN encrypts all traffic and hides your IP. Without one, your ISP can see every website.", "Enable VPN", null)

    val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Settings.Global.getString(ctx.contentResolver, "private_dns_mode").let { it == "hostname" || it == "opportunistic" } else false
    checks += NetSecCheck("Private DNS", if (privateDns) "Enabled — DNS encrypted" else "Disabled — DNS queries visible to ISP", if (privateDns) "GOOD" else "BAD",
        "Private DNS encrypts domain name lookups. Without it, your ISP sees every domain you visit.", "Enable Private DNS", Intent(Settings.ACTION_WIRELESS_SETTINGS))

    val dnsServers = link?.dnsServers?.map { it.hostAddress } ?: emptyList()
    val knownSecureDns = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "208.67.222.222")
    val usingSecureDns = dnsServers.any { it in knownSecureDns }
    checks += NetSecCheck("DNS Servers", dnsServers.joinToString(", ").ifEmpty { "Unknown" }, if (usingSecureDns) "GOOD" else "WARN",
        if (usingSecureDns) "Using a reputable DNS provider." else "Using ISP DNS — consider switching to 1.1.1.1 (Cloudflare) or 8.8.8.8 (Google).")

    if (type == "Wi-Fi") {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION") val wifiInfo = wm?.connectionInfo
        val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Unknown"
        val rssi = wifiInfo?.rssi ?: -100
        val signalStrength = when { rssi >= -50 -> "Excellent"; rssi >= -60 -> "Good"; rssi >= -70 -> "Fair"; else -> "Weak" }
        info["Wi-Fi SSID"] = ssid; info["Signal"] = "$signalStrength ($rssi dBm)"
        checks += NetSecCheck("Wi-Fi Signal", "$signalStrength ($rssi dBm)", if (rssi >= -70) "GOOD" else "WARN", "Weak signal can cause connection drops and may indicate you're far from the router.")
    }

    val btOn = try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter?.isEnabled == true } catch (_: Exception) { false }
    checks += NetSecCheck("Bluetooth", if (btOn) "ON — discoverable" else "OFF", if (btOn) "WARN" else "GOOD", "Bluetooth can be exploited for proximity attacks. Turn off when not using.", "Bluetooth Settings", Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    val locOn = try { (ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager)?.isLocationEnabled == true } catch (_: Exception) { false }
    checks += NetSecCheck("Location", if (locOn) "ON — GPS tracking active" else "OFF", if (locOn) "WARN" else "GOOD", "Location can be used for tracking. Disable when not navigating.", "Location Settings", Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    info["Android"] = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    info["Security Patch"] = Build.VERSION.SECURITY_PATCH

    return checks to info
}
