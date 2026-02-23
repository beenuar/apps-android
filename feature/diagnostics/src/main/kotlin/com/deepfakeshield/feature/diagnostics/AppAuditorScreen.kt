package com.deepfakeshield.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Models ─────────────────────────────────────────────────────────

private data class AuditedApp(
    val name: String, val packageName: String,
    val riskLevel: String, val riskScore: Int, val privacyGrade: String,
    val permissions: List<PermDetail>, val permCount: Int,
    val isSystem: Boolean, val source: String,
    val trackersFound: Int, val trackerNames: List<String>,
    val exportedComponents: Int, val usesCleartext: Boolean,
    val targetSdk: Int, val minSdk: Int,
    val hasNativeCode: Boolean, val startsOnBoot: Boolean,
    val txBytes: Long, val rxBytes: Long,
    val installedDate: String, val updatedDate: String,
    val apkSizeMb: Float, val sha256: String,
    val riskFactors: List<String>
)

private data class PermDetail(
    val name: String, val friendlyName: String,
    val category: String, val risk: String, val explanation: String
)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAuditorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var apps by remember { mutableStateOf<List<AuditedApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { deepAudit(context) { p, ph -> progress = p; phase = ph } }
        isLoading = false
    }

    val high = apps.count { it.riskLevel == "HIGH" }
    val medium = apps.count { it.riskLevel == "MEDIUM" }
    val low = apps.count { it.riskLevel == "LOW" }
    val totalTrackers = apps.sumOf { it.trackersFound }
    val worstApp = apps.firstOrNull()

    val filtered = remember(apps, selectedFilter, searchQuery) {
        val f = when (selectedFilter) { 1 -> apps.filter { it.riskLevel == "HIGH" }; 2 -> apps.filter { it.riskLevel == "MEDIUM" }; 3 -> apps.filter { !it.isSystem }; 4 -> apps.filter { it.trackersFound > 0 }; 5 -> apps.filter { it.targetSdk < 29 }; else -> apps }
        if (searchQuery.isBlank()) f else f.filter { it.name.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("App Auditor", fontWeight = FontWeight.Bold); if (!isLoading) Text("${apps.size} apps • $high high risk • $totalTrackers trackers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (!isLoading) IconButton(onClick = {
                    val r = "App Audit Report\n${apps.size} apps, $high high risk, $totalTrackers trackers\n\n" +
                        apps.filter { it.riskLevel != "LOW" }.joinToString("\n") { "${it.privacyGrade} ${it.name}: ${it.riskLevel} (${it.permCount} perms, ${it.trackersFound} trackers)" }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("audit", r))
                    Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Share, "Export") }
            }
        ) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(0.6f).height(4.dp), strokeCap = StrokeCap.Round)
                    Spacer(Modifier.height(6.dp))
                    Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Summary
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
                        containerColor = if (high > 0) Color(0xFFF44336).copy(alpha = 0.05f) else Color(0xFF4CAF50).copy(alpha = 0.05f)
                    )) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Security Audit Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                AuditStat("${apps.size}", "Apps", Color(0xFF2196F3))
                                AuditStat("$high", "High", Color(0xFFF44336))
                                AuditStat("$medium", "Medium", Color(0xFFFF9800))
                                AuditStat("$totalTrackers", "Trackers", Color(0xFF9C27B0))
                            }
                            if (worstApp != null && worstApp.riskLevel == "HIGH") {
                                Spacer(Modifier.height(12.dp))
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.06f))) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color(0xFFF44336))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Most invasive: ${worstApp.name} (${worstApp.permCount} permissions, ${worstApp.trackersFound} trackers)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("All" to apps.size, "High Risk" to high, "Medium" to medium, "User Apps" to apps.count { !it.isSystem }, "Trackers" to apps.count { it.trackersFound > 0 }, "Old SDK" to apps.count { it.targetSdk < 29 }).forEachIndexed { i, (l, c) ->
                            FilterChip(selected = selectedFilter == i, onClick = { selectedFilter = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, label = { Text("$l ($c)", fontSize = 11.sp) })
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)); Spacer(Modifier.height(8.dp)); Text("No matches") } } }
                }

                items(filtered, key = { it.packageName }) { app ->
                    AppCard(app, expandedPkg == app.packageName, { expandedPkg = if (expandedPkg == app.packageName) null else app.packageName }, context)
                }
            }
        }
    }
}

@Composable
private fun AuditStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 22.sp)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppCard(app: AuditedApp, expanded: Boolean, onToggle: () -> Unit, ctx: Context) {
    val rc = when (app.riskLevel) { "HIGH" -> Color(0xFFF44336); "MEDIUM" -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
    val gc = when (app.privacyGrade) { "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF8BC34A); "C" -> Color(0xFFFFC107); "D" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle).animateContentSize(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(rc.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(app.privacyGrade, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = gc)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${app.permCount} perms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (app.trackersFound > 0) Text("${app.trackersFound} trackers", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9C27B0))
                        if (app.targetSdk < 29) Text("SDK ${app.targetSdk}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
                    }
                }
                Surface(color = rc.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(app.riskLevel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = rc, fontWeight = FontWeight.Bold)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))

                // Risk factors
                if (app.riskFactors.isNotEmpty()) {
                    Text("Risk Factors", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = rc)
                    Spacer(Modifier.height(4.dp))
                    app.riskFactors.forEach { f ->
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Icon(Icons.Default.Error, null, Modifier.size(12.dp), tint = rc)
                            Spacer(Modifier.width(4.dp))
                            Text(f, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // App metadata grid
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Column(Modifier.padding(10.dp)) {
                        MetaRow("Package", app.packageName)
                        MetaRow("Source", app.source)
                        MetaRow("Target SDK", "API ${app.targetSdk} (min ${app.minSdk})")
                        MetaRow("Installed", app.installedDate)
                        MetaRow("Updated", app.updatedDate)
                        MetaRow("APK Size", "${"%.1f".format(app.apkSizeMb)} MB")
                        MetaRow("Network", "↑${fmtBytes(app.txBytes)} ↓${fmtBytes(app.rxBytes)}")
                        MetaRow("Exported", "${app.exportedComponents} components")
                        if (app.hasNativeCode) MetaRow("Native Code", "Yes — includes .so libraries")
                        if (app.startsOnBoot) MetaRow("Auto-Start", "Runs at boot")
                        if (app.usesCleartext) MetaRow("Cleartext", "HTTP allowed (unencrypted)")
                        MetaRow("SHA-256", app.sha256.take(16) + "...")
                    }
                }

                // Trackers
                if (app.trackerNames.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Detected Trackers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Color(0xFF9C27B0))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        app.trackerNames.forEach { SuggestionChip(onClick = {}, label = { Text(it, fontSize = 10.sp) }) }
                    }
                }

                // Permissions by category
                if (app.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Permissions (${app.permissions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    app.permissions.groupBy { it.category }.forEach { (cat, perms) ->
                        val catColor = when (cat) { "Surveillance" -> Color(0xFFF44336); "Data Access" -> Color(0xFFFF9800); "Network" -> Color(0xFF2196F3); "System" -> Color(0xFF9C27B0); else -> Color(0xFF607D8B) }
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = catColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text(cat, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, color = catColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        perms.forEach { p ->
                            Row(Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp)) {
                                val pColor = when (p.risk) { "HIGH" -> Color(0xFFF44336); "MEDIUM" -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
                                Icon(Icons.Default.Circle, null, Modifier.size(6.dp).padding(top = 6.dp), tint = pColor)
                                Spacer(Modifier.width(4.dp))
                                Column {
                                    Text(p.friendlyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(p.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${app.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
                    }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Settings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Settings", fontSize = 12.sp) }

                    if (!app.isSystem) {
                        OutlinedButton(onClick = {
                            try { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))) {
                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Uninstall", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun fmtBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}

// ── Deep Audit Engine ──────────────────────────────────────────────

private val PERM_DB = mapOf(
    "CAMERA" to PermDetail("", "Camera", "Surveillance", "HIGH", "Can take photos/video silently in background"),
    "RECORD_AUDIO" to PermDetail("", "Microphone", "Surveillance", "HIGH", "Can record audio without visible indicator"),
    "ACCESS_FINE_LOCATION" to PermDetail("", "Precise Location", "Surveillance", "HIGH", "Tracks your exact GPS position"),
    "ACCESS_COARSE_LOCATION" to PermDetail("", "Approximate Location", "Surveillance", "MEDIUM", "Knows your general area"),
    "ACCESS_BACKGROUND_LOCATION" to PermDetail("", "Background Location", "Surveillance", "HIGH", "Tracks you even when app is closed"),
    "READ_CONTACTS" to PermDetail("", "Contacts", "Data Access", "MEDIUM", "Reads your entire contact list"),
    "READ_SMS" to PermDetail("", "Read SMS", "Data Access", "HIGH", "Can read all text messages including OTPs"),
    "RECEIVE_SMS" to PermDetail("", "Intercept SMS", "Data Access", "HIGH", "Intercepts incoming messages in real-time"),
    "SEND_SMS" to PermDetail("", "Send SMS", "Data Access", "HIGH", "Can send texts (premium SMS fraud)"),
    "READ_CALL_LOG" to PermDetail("", "Call History", "Data Access", "HIGH", "Reads who you called, when, and for how long"),
    "READ_PHONE_STATE" to PermDetail("", "Phone Identity", "Data Access", "MEDIUM", "Reads device ID, phone number, carrier"),
    "BODY_SENSORS" to PermDetail("", "Body Sensors", "Surveillance", "MEDIUM", "Heart rate, step count, health data"),
    "READ_CALENDAR" to PermDetail("", "Calendar", "Data Access", "MEDIUM", "Reads your schedule and event details"),
    "READ_EXTERNAL_STORAGE" to PermDetail("", "Read Files", "Data Access", "MEDIUM", "Access photos, downloads, documents"),
    "WRITE_EXTERNAL_STORAGE" to PermDetail("", "Write Files", "Data Access", "MEDIUM", "Can modify or delete your files"),
    "READ_MEDIA_IMAGES" to PermDetail("", "Photos", "Data Access", "MEDIUM", "Access to all your photos"),
    "READ_MEDIA_VIDEO" to PermDetail("", "Videos", "Data Access", "MEDIUM", "Access to all your videos"),
    "READ_MEDIA_AUDIO" to PermDetail("", "Audio Files", "Data Access", "LOW", "Access to music and recordings"),
    "BLUETOOTH_CONNECT" to PermDetail("", "Bluetooth", "Network", "LOW", "Connect to Bluetooth devices"),
    "NEARBY_WIFI_DEVICES" to PermDetail("", "Nearby Devices", "Network", "MEDIUM", "Scan for nearby Wi-Fi devices"),
    "POST_NOTIFICATIONS" to PermDetail("", "Notifications", "System", "LOW", "Can send you notifications"),
    "SYSTEM_ALERT_WINDOW" to PermDetail("", "Overlay", "System", "HIGH", "Can draw over other apps (tap hijacking)"),
    "REQUEST_INSTALL_PACKAGES" to PermDetail("", "Install Apps", "System", "HIGH", "Can install other APKs (dropper risk)"),
    "MANAGE_EXTERNAL_STORAGE" to PermDetail("", "All Files", "Data Access", "HIGH", "Access every file on the device"),
)

private val TRACKER_SIGS = listOf(
    "com.facebook.ads" to "Facebook Ads", "com.facebook.appevents" to "Facebook Analytics",
    "com.google.firebase.analytics" to "Firebase Analytics", "com.google.android.gms.ads" to "Google Ads (AdMob)",
    "com.adjust." to "Adjust", "com.appsflyer." to "AppsFlyer", "com.mixpanel." to "Mixpanel",
    "com.segment." to "Segment", "com.flurry." to "Flurry (Yahoo)", "com.amplitude." to "Amplitude",
    "com.braze." to "Braze", "io.branch." to "Branch", "com.kochava." to "Kochava",
    "com.crashlytics." to "Crashlytics", "com.newrelic." to "New Relic",
    "com.urbanairship." to "Airship", "ly.count.android" to "Countly",
    "com.onesignal." to "OneSignal", "com.clevertap." to "CleverTap",
    "com.batch." to "Batch", "io.sentry." to "Sentry"
)

private suspend fun deepAudit(ctx: Context, onProgress: suspend (Float, String) -> Unit): List<AuditedApp> {
    val pm = ctx.packageManager
    onProgress(0.05f, "Loading installed packages...")
    val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA).toLong()))
    else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)

    val df = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val results = mutableListOf<AuditedApp>()
    val total = pkgs.size.toFloat()

    pkgs.forEachIndexed { idx, pkg ->
        if (idx % 20 == 0) onProgress(0.1f + 0.85f * idx / total, "Auditing ${pkg.packageName.substringAfterLast('.')}...")

        val ai = pkg.applicationInfo ?: return@forEachIndexed
        val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { return@forEachIndexed }
        val isSys = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0

        val reqPerms = (pkg.requestedPermissions ?: emptyArray()).toList()
        val permDetails = reqPerms.mapNotNull { p ->
            val key = p.substringAfterLast(".")
            PERM_DB[key]?.copy(name = p)
        }
        if (permDetails.isEmpty() && isSys) return@forEachIndexed

        @Suppress("DEPRECATION")
        val installer = try { pm.getInstallerPackageName(pkg.packageName) ?: "" } catch (_: Exception) { "" }
        val source = when {
            installer.contains("vending") || installer.contains("google") -> "Google Play"
            installer.contains("samsung") -> "Galaxy Store"
            installer.contains("amazon") -> "Amazon"
            isSys -> "Pre-installed"
            installer.isBlank() -> "Sideloaded"
            else -> installer.substringAfterLast(".")
        }

        val metaKeys = ai.metaData?.keySet()?.toList() ?: emptyList()
        val detectedTrackers = TRACKER_SIGS.filter { (sig, _) -> metaKeys.any { it.startsWith(sig) } || reqPerms.any { it.contains(sig) } }
        val trackerNames = detectedTrackers.map { it.second }

        val exported = (pkg.activities?.count { it.exported } ?: 0) + (pkg.services?.count { it.exported } ?: 0) + (pkg.receivers?.count { it.exported } ?: 0) + (pkg.providers?.count { it.exported } ?: 0)
        val cleartext = ai.flags and 0x20000000 != 0
        val nativeCode = try { java.io.File(ai.nativeLibraryDir ?: "").listFiles()?.isNotEmpty() == true } catch (_: Exception) { false }
        val bootReceiver = pkg.receivers?.any { it.name.contains("Boot", true) || it.name.contains("Restart", true) } == true

        val uid = ai.uid
        val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)

        val installed = df.format(Date(pkg.firstInstallTime))
        val updated = df.format(Date(pkg.lastUpdateTime))
        val apkSize = try { java.io.File(ai.sourceDir).length() / (1024f * 1024f) } catch (_: Exception) { 0f }
        val sha256 = try {
            val bytes = java.io.File(ai.sourceDir).inputStream().use { MessageDigest.getInstance("SHA-256").apply { val buf = ByteArray(8192); var n: Int; while (it.read(buf).also { r -> n = r } > 0) update(buf, 0, n) }.digest() }
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "unavailable" }

        val targetSdk = ai.targetSdkVersion
        val minSdk = if (Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 1

        // Risk factors
        val factors = mutableListOf<String>()
        if (source == "Sideloaded") factors.add("Installed from unknown source (sideloaded)")
        if (permDetails.count { it.risk == "HIGH" } >= 3) factors.add("${permDetails.count { it.risk == "HIGH" }} high-risk permissions")
        if (detectedTrackers.size >= 3) factors.add("${detectedTrackers.size} tracking SDKs embedded")
        if (targetSdk < 29) factors.add("Targets old API $targetSdk — bypasses modern security")
        if (cleartext) factors.add("Allows unencrypted HTTP traffic")
        if (exported > 10) factors.add("$exported exported components (large attack surface)")
        if (tx > rx * 5 && tx > 1_000_000) factors.add("Sends 5x more data than it receives")
        if (reqPerms.any { it.contains("INSTALL_PACKAGES") }) factors.add("Can install other apps (dropper risk)")
        if (reqPerms.any { it.contains("SYSTEM_ALERT_WINDOW") }) factors.add("Can draw overlays (tap hijacking)")
        if (reqPerms.any { it.contains("ACCESSIBILITY") }) factors.add("May request accessibility (screen reading)")
        val stale = (System.currentTimeMillis() - pkg.lastUpdateTime) > 365L * 24 * 60 * 60 * 1000
        if (stale && !isSys) factors.add("Not updated in over 1 year")

        val riskScore = (permDetails.count { it.risk == "HIGH" } * 15 + permDetails.count { it.risk == "MEDIUM" } * 6 +
            detectedTrackers.size * 8 + (if (source == "Sideloaded") 20 else 0) + (if (targetSdk < 29) 12 else 0) +
            (if (cleartext) 8 else 0) + (if (exported > 10) 8 else 0) + (if (stale) 5 else 0) +
            (if (tx > rx * 5 && tx > 1_000_000) 10 else 0)).coerceAtMost(100)

        val riskLevel = when { riskScore >= 55 -> "HIGH"; riskScore >= 25 -> "MEDIUM"; else -> "LOW" }
        val grade = when { riskScore >= 70 -> "F"; riskScore >= 55 -> "D"; riskScore >= 35 -> "C"; riskScore >= 15 -> "B"; else -> "A" }

        results.add(AuditedApp(name, pkg.packageName, riskLevel, riskScore, grade, permDetails, reqPerms.size,
            isSys, source, detectedTrackers.size, trackerNames, exported, cleartext,
            targetSdk, minSdk, nativeCode, bootReceiver, tx, rx, installed, updated, apkSize, sha256, factors))
    }

    onProgress(1f, "Complete")
    return results.sortedByDescending { it.riskScore }
}
