package com.deepfakeshield.feature.analytics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private data class TimelineEvent(
    val title: String, val detail: String, val timestamp: Long,
    val category: String, val icon: ImageVector, val color: Color, val severity: String = "INFO"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTimelineScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var events by remember { mutableStateOf<List<TimelineEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    val categories = listOf("All", "Installs", "Updates", "System", "Security")

    LaunchedEffect(Unit) { events = withContext(Dispatchers.Default) { buildTimeline(ctx) }; loading = false }

    val filtered = remember(events, filter, search) {
        var f = if (filter == 0) events else events.filter { it.category == categories[filter] }
        if (search.isNotBlank()) f = f.filter { it.title.contains(search, true) || it.detail.contains(search, true) }
        f
    }

    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Security Timeline", fontWeight = FontWeight.Bold); if (!loading) Text("${events.size} events from your device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (!loading) IconButton(onClick = {
                val rpt = "Security Timeline\n${events.size} events\n\n" + events.take(30).joinToString("\n") { "${fmtDate(it.timestamp)} [${it.category}] ${it.title}" }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("timeline", rpt))
                Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Default.Share, "Export") } }) }
    ) { padding ->
        if (loading) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val installs = events.count { it.category == "Installs" }; val updates = events.count { it.category == "Updates" }; val sys = events.count { it.category == "System" }; val sec = events.count { it.category == "Security" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TlStat("$installs", "Installs", Color(0xFF4CAF50), Modifier.weight(1f)); TlStat("$updates", "Updates", Color(0xFF2196F3), Modifier.weight(1f))
                    TlStat("$sys", "System", Color(0xFF9C27B0), Modifier.weight(1f)); TlStat("$sec", "Security", Color(0xFFFF9800), Modifier.weight(1f))
                }
            }
            item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { categories.forEachIndexed { i, c -> val count = if (i == 0) events.size else events.count { it.category == c }; FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text("$c ($count)", fontSize = 11.sp) }) } } }

            var lastDate = ""
            filtered.forEach { event ->
                val dateStr = fmtDay(event.timestamp)
                if (dateStr != lastDate) { lastDate = dateStr; item(key = "date_$dateStr") { Text(dateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) } }
                item(key = "${event.timestamp}_${event.title.hashCode()}") {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                                Box(Modifier.size(32.dp).background(event.color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(event.icon, null, Modifier.size(16.dp), tint = event.color) }
                                Box(Modifier.width(2.dp).height(20.dp).background(event.color.copy(0.2f)))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(event.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                    Spacer(Modifier.width(4.dp))
                                    Surface(color = event.color.copy(0.12f), shape = RoundedCornerShape(3.dp)) { Text(event.category, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = event.color, fontWeight = FontWeight.Bold) }
                                }
                                Text(event.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(fmtTime(event.timestamp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TlStat(v: String, l: String, c: Color, m: Modifier) { Card(m, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) { Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 16.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) } } }
private fun fmtDate(ts: Long) = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
private fun fmtDay(ts: Long) = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(ts))
private fun fmtTime(ts: Long) = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

private fun buildTimeline(ctx: Context): List<TimelineEvent> {
    val events = mutableListOf<TimelineEvent>()
    val pm = ctx.packageManager
    val pkgs = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.getInstalledPackages(0) } catch (_: Exception) { emptyList() }
    val now = System.currentTimeMillis(); val weekAgo = now - 7L * 24 * 60 * 60 * 1000

    pkgs.forEach { pkg ->
        val ai = pkg.applicationInfo ?: return@forEach
        val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
        if (isSystem) return@forEach
        val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pkg.packageName }
        if (pkg.firstInstallTime > weekAgo) events += TimelineEvent("Installed: $name", pkg.packageName, pkg.firstInstallTime, "Installs", Icons.Default.GetApp, Color(0xFF4CAF50))
        if (pkg.lastUpdateTime > pkg.firstInstallTime && pkg.lastUpdateTime > weekAgo) events += TimelineEvent("Updated: $name", "v${pkg.versionName ?: "?"} • ${pkg.packageName}", pkg.lastUpdateTime, "Updates", Icons.Default.Update, Color(0xFF2196F3))
    }

    events += TimelineEvent("Security Patch Level", Build.VERSION.SECURITY_PATCH, now - 1000, "Security", Icons.Default.Shield, Color(0xFFFF9800))
    events += TimelineEvent("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", now - 2000, "System", Icons.Default.PhoneAndroid, Color(0xFF9C27B0))
    val uptime = SystemClock.elapsedRealtime()
    val bootTime = now - uptime
    events += TimelineEvent("Last Boot", "Device uptime: ${uptime / 3600000}h ${(uptime % 3600000) / 60000}m", bootTime, "System", Icons.Default.PowerSettingsNew, Color(0xFF9C27B0))
    events += TimelineEvent("Build Fingerprint", Build.FINGERPRINT.takeLast(50), now - 3000, "System", Icons.Default.Fingerprint, Color(0xFF607D8B))

    val dangerPerms = listOf("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CONTACTS", "READ_SMS", "READ_CALL_LOG")
    val granted = dangerPerms.count { try { ctx.checkSelfPermission("android.permission.$it") == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false } }
    events += TimelineEvent("Permission Snapshot", "$granted of ${dangerPerms.size} dangerous permissions granted", now - 5000, "Security", Icons.Default.AdminPanelSettings, if (granted > 4) Color(0xFFF44336) else Color(0xFF4CAF50))

    val devMode = android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    if (devMode) events += TimelineEvent("Developer Mode Active", "USB debugging may be enabled — security risk", now - 6000, "Security", Icons.Default.Code, Color(0xFFF44336), "WARN")

    return events.sortedByDescending { it.timestamp }
}
