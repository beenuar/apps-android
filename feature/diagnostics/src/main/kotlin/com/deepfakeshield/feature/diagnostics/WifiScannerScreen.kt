package com.deepfakeshield.feature.diagnostics

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

// ───── Data Models ─────

private data class WifiCheckResult(
    val name: String, val description: String, val icon: ImageVector,
    val status: String, val statusColor: Color
)

private data class SpeedTestResult(
    val downloadMbps: Float, val uploadMbps: Float,
    val latencyMs: Int, val jitterMs: Int, val packetLoss: Float
)

private data class NearbyNetwork(
    val ssid: String, val bssid: String, val rssi: Int, val frequency: Int,
    val channel: Int, val security: String, val channelWidth: String
)

private data class PortScanResult(val port: Int, val name: String, val open: Boolean)

private data class ConnectedDevice(val ip: String, val mac: String, val hostname: String)

private data class NetworkDetailItem(val label: String, val value: String, val icon: ImageVector)

private data class SecurityCheck(
    val name: String, val status: String, val description: String,
    val icon: ImageVector, val color: Color, val severity: Int
)

// ───── Tab definitions ─────

private enum class WifiTab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    SpeedTest("Speed", Icons.Default.Speed),
    Analyzer("Analyzer", Icons.Default.Analytics),
    Security("Security", Icons.Default.Security),
    Advanced("Advanced", Icons.Default.Terminal)
}

// ───── Main Screen ─────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WifiScannerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(pageCount = { WifiTab.entries.size })
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wi-Fi Analytics", fontWeight = FontWeight.Bold)
                        Text("Comprehensive Network Diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp,
                divider = {}
            ) {
                WifiTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.label, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(tab.icon, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (WifiTab.entries[page]) {
                    WifiTab.Dashboard -> DashboardTab(context, haptic)
                    WifiTab.SpeedTest -> SpeedTestTab(context, haptic)
                    WifiTab.Analyzer -> AnalyzerTab(context, haptic, hasLocationPermission) { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    WifiTab.Security -> SecurityTab(context, haptic)
                    WifiTab.Advanced -> AdvancedTab(context)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// TAB 1: DASHBOARD
// ═══════════════════════════════════════════════════

@Composable
private fun DashboardTab(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }

    var isConnected by remember { mutableStateOf(false) }
    var ssid by remember { mutableStateOf("Unknown") }
    var rssi by remember { mutableIntStateOf(-100) }
    var frequency by remember { mutableIntStateOf(0) }
    var linkSpeed by remember { mutableIntStateOf(0) }
    var ipAddress by remember { mutableStateOf("—") }
    var gateway by remember { mutableStateOf("—") }
    var hasVpn by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableIntStateOf(-1) }
    var isRefreshing by remember { mutableStateOf(true) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            withContext(Dispatchers.IO) {
                try {
                    val net = cm?.activeNetwork
                    val caps = net?.let { cm.getNetworkCapabilities(it) }
                    isConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

                    if (isConnected) {
                        @Suppress("DEPRECATION")
                        val info = wifiManager?.connectionInfo
                        rssi = info?.rssi ?: -100
                        frequency = info?.frequency ?: 0
                        linkSpeed = info?.linkSpeed ?: 0
                        @Suppress("DEPRECATION")
                        val rawSsid = info?.ssid?.replace("\"", "") ?: "Unknown"
                        ssid = if (rawSsid == "<unknown ssid>") "Hidden Network" else rawSsid

                        val lp = net?.let { cm.getLinkProperties(it) }
                        ipAddress = lp?.linkAddresses
                            ?.firstOrNull { it.address is Inet4Address }
                            ?.address?.hostAddress ?: "—"
                        val routes = lp?.routes
                        gateway = routes?.firstOrNull { it.isDefaultRoute }
                            ?.gateway?.hostAddress ?: "—"

                        latencyMs = try {
                            val start = System.currentTimeMillis()
                            InetAddress.getByName("8.8.8.8").isReachable(3000)
                            (System.currentTimeMillis() - start).toInt()
                        } catch (_: Exception) { -1 }
                    }
                } catch (_: Exception) { }
            }
            isRefreshing = false
        }
    }

    val animatedRssi by animateIntAsState(rssi, tween(1000), label = "rssi")
    val signalPct = ((animatedRssi + 100).coerceIn(0, 70).toFloat() / 70f * 100).roundToInt()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero connection card
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isRefreshing) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Gathering network info...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    SignalGauge(signalPercent = signalPct, rssi = animatedRssi, isConnected = isConnected)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isConnected) ssid else "Not Connected",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    if (isConnected) {
                        Text(
                            "${if (frequency > 4900) "5 GHz" else "2.4 GHz"} • $linkSpeed Mbps link • ${if (hasVpn) "VPN Active" else "No VPN"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!isRefreshing && isConnected) {
            // Metric tiles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Signal", "$signalPct%", signalColor(signalPct), Icons.Default.SignalWifi4Bar, Modifier.weight(1f))
                MetricTile("Latency", if (latencyMs > 0) "${latencyMs}ms" else "—", latencyColor(latencyMs), Icons.Default.Timer, Modifier.weight(1f))
                MetricTile("Speed", "$linkSpeed Mbps", Color(0xFF2196F3), Icons.Default.Speed, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("IP", ipAddress, MaterialTheme.colorScheme.tertiary, Icons.Default.Language, Modifier.weight(1f))
                MetricTile("Gateway", gateway, MaterialTheme.colorScheme.secondary, Icons.Default.Router, Modifier.weight(1f))
            }

            // Quick health checks
            Text("Network Health", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            val checks = buildDashboardChecks(isConnected, signalPct, latencyMs, hasVpn, frequency)
            checks.forEach { check ->
                HealthCheckRow(check)
            }
        }

        // Refresh button
        if (!isRefreshing) {
            OutlinedButton(
                onClick = { isRefreshing = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun SignalGauge(signalPercent: Int, rssi: Int, isConnected: Boolean) {
    val color = if (!isConnected) MaterialTheme.colorScheme.error else signalColor(signalPercent)
    val animatedSweep by animateFloatAsState(
        if (isConnected) signalPercent * 2.4f else 0f, tween(1200, easing = FastOutSlowInEasing), label = "sweep"
    )

    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val pad = sw / 2
            drawArc(
                color.copy(alpha = 0.15f), 150f, 240f, false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw)
            )
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.4f), color)),
                150f, animatedSweep, false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isConnected) {
                Text("$signalPercent%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
                Text("$rssi dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.WifiOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier.height(88.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HealthCheckRow(check: WifiCheckResult) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(check.statusColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(check.icon, null, Modifier.size(18.dp), tint = check.statusColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(check.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(
                onClick = {}, label = { Text(check.status, fontSize = 9.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = check.statusColor.copy(alpha = 0.12f), labelColor = check.statusColor)
            )
        }
    }
}

private fun buildDashboardChecks(connected: Boolean, signal: Int, latency: Int, vpn: Boolean, freq: Int): List<WifiCheckResult> {
    if (!connected) return listOf(WifiCheckResult("Connection", "Not connected to Wi-Fi", Icons.Default.WifiOff, "NONE", Color.Gray))
    return listOf(
        WifiCheckResult("Signal", if (signal >= 60) "Strong signal" else if (signal >= 30) "Moderate signal" else "Weak signal",
            Icons.Default.SignalWifi4Bar, if (signal >= 60) "GOOD" else if (signal >= 30) "FAIR" else "WEAK", signalColor(signal)),
        WifiCheckResult("Latency", if (latency in 1..50) "Low latency (${latency}ms)" else if (latency in 51..150) "Moderate (${latency}ms)" else if (latency > 150) "High (${latency}ms)" else "Could not measure",
            Icons.Default.Timer, if (latency in 1..50) "GOOD" else if (latency in 51..150) "OK" else "SLOW", latencyColor(latency)),
        WifiCheckResult("VPN", if (vpn) "Traffic is encrypted via VPN" else "No VPN — traffic visible to ISP",
            Icons.Default.VpnKey, if (vpn) "SECURE" else "RISK", if (vpn) Color(0xFF4CAF50) else Color(0xFFFF9800)),
        WifiCheckResult("Band", if (freq > 4900) "5 GHz — faster, less interference" else "2.4 GHz — wider range, more congestion",
            Icons.Default.SettingsInputAntenna, if (freq > 4900) "GOOD" else "OK", if (freq > 4900) Color(0xFF4CAF50) else Color(0xFF2196F3)),
        WifiCheckResult("DNS", getDnsStatus(null), Icons.Default.Dns, "CHECK", Color(0xFF2196F3))
    )
}

// ═══════════════════════════════════════════════════
// TAB 2: SPEED TEST
// ═══════════════════════════════════════════════════

@Composable
private fun SpeedTestTab(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SpeedTestResult?>(null) }
    var phase by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            try {
                result = withContext(Dispatchers.IO) { runSpeedTest { p, ph -> progress = p; phase = ph } }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {
                result = SpeedTestResult(0f, 0f, -1, -1, 100f)
            }
            isRunning = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        if (isRunning) {
            SpeedGauge(value = progress * 100, label = phase, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(0.7f).height(6.dp), strokeCap = StrokeCap.Round)
            Text(phase, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (result != null) {
            val r = result ?: return@Column
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SpeedGauge(r.downloadMbps, "Download", downloadColor(r.downloadMbps), "Mbps")
                SpeedGauge(r.uploadMbps, "Upload", uploadColor(r.uploadMbps), "Mbps")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Ping", "${r.latencyMs} ms", latencyColor(r.latencyMs), Icons.Default.Timer, Modifier.weight(1f))
                MetricTile("Jitter", "${r.jitterMs} ms", if (r.jitterMs < 10) Color(0xFF4CAF50) else Color(0xFFFF9800), Icons.Default.Grain, Modifier.weight(1f))
                MetricTile("Loss", "${"%.1f".format(r.packetLoss)}%", if (r.packetLoss < 1f) Color(0xFF4CAF50) else Color(0xFFF44336), Icons.Default.ErrorOutline, Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            SpeedRating(r)

            // Copy results
            OutlinedButton(
                onClick = {
                    val txt = "WiFi Speed Test Results\nDownload: ${"%.1f".format(r.downloadMbps)} Mbps\nUpload: ${"%.1f".format(r.uploadMbps)} Mbps\nPing: ${r.latencyMs} ms | Jitter: ${r.jitterMs} ms | Loss: ${"%.1f".format(r.packetLoss)}%\n\nTested by DeepfakeShield"
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("speed_test", txt))
                    Toast.makeText(context, "Results copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Results")
            }
        } else {
            SpeedGauge(0f, "Ready", MaterialTheme.colorScheme.outline, "")
            Spacer(Modifier.height(8.dp))
            Text("Measures download, upload, latency, jitter, and packet loss", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { isRunning = true; result = null; progress = 0f; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            enabled = !isRunning, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
        ) {
            Icon(if (result != null) Icons.Default.Refresh else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Testing..." else if (result != null) "Test Again" else "Start Speed Test", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpeedGauge(value: Float, label: String, color: Color, unit: String = "") {
    val animated by animateFloatAsState(value, tween(1000, easing = FastOutSlowInEasing), label = "speed")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 10.dp.toPx()
                val pad = sw / 2
                drawArc(color.copy(alpha = 0.12f), 150f, 240f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
                val sweep = (animated / 200f).coerceIn(0f, 1f) * 240f
                drawArc(color, 150f, sweep, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.1f".format(animated)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpeedRating(r: SpeedTestResult) {
    val rating = when {
        r.downloadMbps >= 50 && r.latencyMs < 30 -> "Excellent" to Color(0xFF4CAF50)
        r.downloadMbps >= 25 && r.latencyMs < 60 -> "Very Good" to Color(0xFF8BC34A)
        r.downloadMbps >= 10 && r.latencyMs < 100 -> "Good" to Color(0xFFFFC107)
        r.downloadMbps >= 3 -> "Fair" to Color(0xFFFF9800)
        else -> "Poor" to Color(0xFFF44336)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = rating.second.copy(alpha = 0.08f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rating.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = rating.second)
            Spacer(Modifier.width(12.dp))
            Column {
                val desc = when {
                    r.downloadMbps >= 50 -> "Great for 4K streaming, gaming, and video calls simultaneously"
                    r.downloadMbps >= 25 -> "Suitable for HD streaming and video calls"
                    r.downloadMbps >= 10 -> "Adequate for browsing and standard streaming"
                    r.downloadMbps >= 3 -> "May experience buffering with video content"
                    else -> "Connection issues detected — check your router or ISP"
                }
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// TAB 3: NETWORK ANALYZER
// ═══════════════════════════════════════════════════

@Composable
private fun AnalyzerTab(
    context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    hasPermission: Boolean, requestPermission: () -> Unit
) {
    val wm = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var networks by remember { mutableStateOf<List<NearbyNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanDone by remember { mutableStateOf(false) }

    LaunchedEffect(isScanning) {
        if (isScanning && hasPermission) {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    wm?.startScan()
                    delay(3000)
                    @Suppress("MissingPermission")
                    val results = wm?.scanResults ?: emptyList()
                    networks = results.map { sr ->
                        NearbyNetwork(
                            ssid = sr.SSID.ifBlank { "(Hidden)" },
                            bssid = sr.BSSID,
                            rssi = sr.level,
                            frequency = sr.frequency,
                            channel = frequencyToChannel(sr.frequency),
                            security = getSecurityType(sr),
                            channelWidth = getChannelWidth(sr)
                        )
                    }.sortedByDescending { it.rssi }
                } catch (_: Exception) { }
            }
            scanDone = true
            isScanning = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LocationOn, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Location Permission Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Android requires location permission to scan nearby Wi-Fi networks. Your location data is never collected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = requestPermission, shape = RoundedCornerShape(14.dp)) {
                Text("Grant Permission")
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { isScanning = true; scanDone = false; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                enabled = !isScanning, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)
            ) {
                if (isScanning) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
                else { Icon(Icons.Default.Radar, null); Spacer(Modifier.width(8.dp)); Text(if (scanDone) "Scan Again" else "Scan Nearby Networks") }
            }
        }

        if (scanDone && networks.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("Networks", "${networks.size}", MaterialTheme.colorScheme.primary, Icons.Default.Wifi, Modifier.weight(1f))
                    val on5g = networks.count { it.frequency > 4900 }
                    MetricTile("5 GHz", "$on5g", Color(0xFF4CAF50), Icons.Default.NetworkWifi, Modifier.weight(1f))
                    MetricTile("2.4 GHz", "${networks.size - on5g}", Color(0xFF2196F3), Icons.Default.SettingsInputAntenna, Modifier.weight(1f))
                }
            }

            // Channel congestion
            item {
                Text("2.4 GHz Channel Map", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                ChannelCongestionChart(networks.filter { it.frequency < 4900 }, is5Ghz = false)
            }

            val fiveGhzNetworks = networks.filter { it.frequency > 4900 }
            if (fiveGhzNetworks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("5 GHz Channel Map", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    ChannelCongestionChart(fiveGhzNetworks, is5Ghz = true)
                }
            }

            item { Spacer(Modifier.height(4.dp)); Text("Nearby Networks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

            items(networks, key = { it.bssid }) { net ->
                NearbyNetworkCard(net)
            }
        } else if (scanDone) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WifiFind, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No networks found", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCongestionChart(networks: List<NearbyNetwork>, is5Ghz: Boolean) {
    val channelCounts = networks.groupingBy { it.channel }.eachCount()
    val channels = if (is5Ghz) listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
    else (1..13).toList()
    val maxCount = (channelCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val scrollState = rememberScrollState()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(scrollState).height(100.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom
            ) {
                channels.forEach { ch ->
                    val count = channelCounts[ch] ?: 0
                    val barHeight = if (count > 0) (count.toFloat() / maxCount * 70).dp else 4.dp
                    val color = when (count) { 0 -> surfaceVar; 1 -> Color(0xFF4CAF50); 2 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                        if (count > 0) Text("$count", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
                        Box(Modifier.fillMaxWidth(0.6f).height(barHeight).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                        Spacer(Modifier.height(2.dp))
                        Text("$ch", fontSize = 8.sp, color = onSurfaceVar)
                    }
                }
            }

            val best = channels.minByOrNull { channelCounts[it] ?: 0 }
            if (best != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = Color(0xFFFFC107))
                    Spacer(Modifier.width(6.dp))
                    Text("Best channel: $best (least congestion)", style = MaterialTheme.typography.bodySmall, color = primary)
                }
            }
        }
    }
}

@Composable
private fun NearbyNetworkCard(net: NearbyNetwork) {
    val signalPct = ((net.rssi + 100).coerceIn(0, 70).toFloat() / 70f * 100).roundToInt()
    val color = signalColor(signalPct)
    val secColor = when {
        net.security.contains("WPA3") -> Color(0xFF4CAF50)
        net.security.contains("WPA2") -> Color(0xFF8BC34A)
        net.security.contains("WPA") -> Color(0xFFFFC107)
        net.security.contains("WEP") -> Color(0xFFF44336)
        net.security == "Open" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Wifi, null, Modifier.size(20.dp), tint = color)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(net.ssid, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Ch ${net.channel} • ${if (net.frequency > 4900) "5G" else "2.4G"} • ${net.channelWidth}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$signalPct%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                Text(net.security, fontSize = 9.sp, color = secColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// TAB 4: SECURITY
// ═══════════════════════════════════════════════════

@Composable
private fun SecurityTab(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    var isRunning by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<SecurityCheck>>(emptyList()) }
    var portResults by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var devices by remember { mutableStateOf<List<ConnectedDevice>>(emptyList()) }
    var securityScore by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            try {
                withContext(Dispatchers.IO) {
                    val secChecks = mutableListOf<SecurityCheck>()

                    // 1. Encryption type
                    progress = 0.1f
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    val info = wm?.connectionInfo
                    val net = cm?.activeNetwork
                    val caps = net?.let { cm.getNetworkCapabilities(it) }
                    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    @Suppress("DEPRECATION")
                    val ssid = info?.ssid?.replace("\"", "") ?: "Unknown"

                    secChecks.add(SecurityCheck(
                        "Connection", if (isWifi) "PASS" else "FAIL",
                        if (isWifi) "Connected to Wi-Fi ($ssid)" else "Not connected to Wi-Fi",
                        Icons.Default.Wifi, if (isWifi) Color(0xFF4CAF50) else Color(0xFFF44336), if (isWifi) 0 else 50
                    ))

                    // 2. VPN
                    progress = 0.2f
                    val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    secChecks.add(SecurityCheck(
                        "VPN Protection", if (hasVpn) "SECURE" else "RISK",
                        if (hasVpn) "Traffic is encrypted via VPN tunnel" else "No VPN — ISP can inspect your traffic",
                        Icons.Default.VpnKey, if (hasVpn) Color(0xFF4CAF50) else Color(0xFFFF9800), if (hasVpn) 0 else 15
                    ))

                    // 3. DNS
                    progress = 0.3f
                    val dnsSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val mode = android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode")
                        mode == "hostname" || mode == "opportunistic"
                    } else false
                    secChecks.add(SecurityCheck(
                        "DNS Security", if (dnsSecure) "SECURE" else "RISK",
                        if (dnsSecure) "Private DNS enabled — queries encrypted" else "DNS not encrypted — queries visible to network",
                        Icons.Default.Dns, if (dnsSecure) Color(0xFF4CAF50) else Color(0xFFFF9800), if (dnsSecure) 0 else 10
                    ))

                    // 4. Network type
                    progress = 0.4f
                    val isPublic = ssid.contains("Free", true) || ssid.contains("Public", true) || ssid.contains("Guest", true) || ssid.contains("Airport", true) || ssid.contains("Hotel", true) || ssid.contains("Starbucks", true)
                    secChecks.add(SecurityCheck(
                        "Network Type", if (isPublic) "WARNING" else "SAFE",
                        if (isPublic) "Public/guest network detected — higher risk" else "Appears to be a private network",
                        Icons.Default.Public, if (isPublic) Color(0xFFF44336) else Color(0xFF4CAF50), if (isPublic) 20 else 0
                    ))

                    // 5. Gateway port scan
                    progress = 0.5f
                    val lp = net?.let { cm.getLinkProperties(it) }
                    val gw = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
                    if (gw != null) {
                        val ports = scanCommonPorts(gw)
                        portResults = ports
                        val openPorts = ports.count { it.open }
                        secChecks.add(SecurityCheck(
                            "Gateway Ports", if (openPorts <= 1) "SAFE" else "WARNING",
                            "$openPorts open port${if (openPorts != 1) "s" else ""} on gateway ($gw)",
                            Icons.Default.Router, if (openPorts <= 1) Color(0xFF4CAF50) else Color(0xFFFF9800), openPorts.coerceAtMost(15)
                        ))
                    }

                    // 6. ARP table / devices
                    progress = 0.7f
                    devices = readArpTable()
                    secChecks.add(SecurityCheck(
                        "Connected Devices", if (devices.size <= 10) "OK" else "INFO",
                        "${devices.size} device${if (devices.size != 1) "s" else ""} on this network",
                        Icons.Default.Devices, if (devices.size <= 10) Color(0xFF4CAF50) else Color(0xFF2196F3), 0
                    ))

                    // 7. HTTPS enforcement
                    progress = 0.85f
                    val httpsWorks = try {
                        var c: HttpURLConnection? = null
                        try { c = URL("https://www.google.com").openConnection() as HttpURLConnection; c.connectTimeout = 5000; c.readTimeout = 5000; c.requestMethod = "HEAD"; c.responseCode in 200..399 } finally { c?.disconnect() }
                    } catch (_: Exception) { false }
                    secChecks.add(SecurityCheck(
                        "HTTPS", if (httpsWorks) "PASS" else "FAIL",
                        if (httpsWorks) "HTTPS connections are working correctly" else "HTTPS connectivity issue detected",
                        Icons.Default.Lock, if (httpsWorks) Color(0xFF4CAF50) else Color(0xFFF44336), if (httpsWorks) 0 else 20
                    ))

                    // 8. Captive portal check
                    progress = 0.95f
                    val captive = try {
                        var c: HttpURLConnection? = null
                        try { c = URL("http://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection; c.connectTimeout = 5000; c.instanceFollowRedirects = false; c.responseCode != 204 } finally { c?.disconnect() }
                    } catch (_: Exception) { false }
                    secChecks.add(SecurityCheck(
                        "Captive Portal", if (captive) "WARNING" else "CLEAR",
                        if (captive) "Captive portal detected — you may be on a restricted network" else "No captive portal — direct internet access",
                        Icons.Default.Web, if (captive) Color(0xFFFF9800) else Color(0xFF4CAF50), if (captive) 10 else 0
                    ))

                    progress = 1f
                    checks = secChecks
                    securityScore = 100 - secChecks.sumOf { it.severity }.coerceIn(0, 100)
                }
            } catch (_: Exception) { }
            done = true
            isRunning = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val animatedScore by animateIntAsState(if (done) securityScore else 0, tween(1200), label = "sec_score")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { isRunning = true; done = false; checks = emptyList(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            enabled = !isRunning, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)
        ) {
            if (isRunning) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Auditing...") }
            else { Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text(if (done) "Run Again" else "Run Security Audit") }
        }

        if (isRunning) {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), strokeCap = StrokeCap.Round)
        }

        if (done) {
            // Score card
            val scoreColor = when { animatedScore >= 80 -> Color(0xFF4CAF50); animatedScore >= 60 -> Color(0xFFFFC107); animatedScore >= 40 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.08f))) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { animatedScore / 100f }, Modifier.fillMaxSize(), color = scoreColor, strokeWidth = 6.dp, trackColor = scoreColor.copy(alpha = 0.15f))
                        Text("$animatedScore", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(when { securityScore >= 80 -> "Secure"; securityScore >= 60 -> "Moderate"; securityScore >= 40 -> "Vulnerable"; else -> "Critical" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${checks.count { it.severity == 0 }} passed • ${checks.count { it.severity > 0 }} issues", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Security Checks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            checks.forEach { c ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(c.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(c.icon, null, Modifier.size(18.dp), tint = c.color)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Medium)
                            Text(c.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(c.status, fontSize = 9.sp) }, colors = AssistChipDefaults.assistChipColors(containerColor = c.color.copy(alpha = 0.12f), labelColor = c.color))
                    }
                }
            }

            // Open ports
            if (portResults.any { it.open }) {
                Spacer(Modifier.height(4.dp))
                Text("Open Gateway Ports", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                portResults.filter { it.open }.forEach { port ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${port.port}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
                            Text(port.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Devices
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Devices on Network (${devices.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                devices.take(20).forEach { dev ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeviceHub, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dev.ip, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text(dev.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Saved network hygiene
            if (done) {
                Spacer(Modifier.height(4.dp))
                Text("Saved Network Hygiene", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val wm2 = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION", "MissingPermission")
                val saved = try { wm2?.configuredNetworks ?: emptyList() } catch (_: Exception) { emptyList() }
                val openSaved = saved.filter { it.allowedKeyManagement?.get(0) == true && it.allowedKeyManagement?.cardinality() == 1 }
                if (openSaved.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.06f))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${openSaved.size} open network${if (openSaved.size > 1) "s" else ""} saved", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9800))
                            Text("Open Wi-Fi networks have no encryption. Remove saved open networks you don't use regularly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = { try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }, shape = RoundedCornerShape(10.dp)) { Text("Manage Saved Networks") }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)); Spacer(Modifier.width(8.dp))
                            Text("No open networks saved — good hygiene", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                        }
                    }
                }
                listOf("Forget networks you no longer use", "Disable auto-join for public networks", "Prefer WPA3 or WPA2 networks").forEach { tip ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                        Row(Modifier.padding(10.dp)) { Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = Color(0xFFFFC107)); Spacer(Modifier.width(6.dp)); Text(tip, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// TAB 5: ADVANCED / DETAILS
// ═══════════════════════════════════════════════════

@Composable
private fun AdvancedTab(context: Context) {
    val wm = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }

    var details by remember { mutableStateOf<List<NetworkDetailItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val items = mutableListOf<NetworkDetailItem>()
                val net = cm?.activeNetwork
                val caps = net?.let { cm.getNetworkCapabilities(it) }
                val lp = net?.let { cm.getLinkProperties(it) }
                @Suppress("DEPRECATION")
                val info = wm?.connectionInfo

                @Suppress("DEPRECATION")
                val rawSsid = info?.ssid?.replace("\"", "") ?: "Unknown"
                items.add(NetworkDetailItem("SSID", if (rawSsid == "<unknown ssid>") "Hidden" else rawSsid, Icons.Default.Wifi))
                items.add(NetworkDetailItem("BSSID", info?.bssid ?: "—", Icons.Default.Router))
                items.add(NetworkDetailItem("RSSI", "${info?.rssi ?: -100} dBm", Icons.Default.SignalWifi4Bar))
                items.add(NetworkDetailItem("Frequency", "${info?.frequency ?: 0} MHz", Icons.Default.SettingsInputAntenna))
                @Suppress("DEPRECATION")
                items.add(NetworkDetailItem("Link Speed", "${info?.linkSpeed ?: 0} Mbps", Icons.Default.Speed))
                items.add(NetworkDetailItem("Channel", "${frequencyToChannel(info?.frequency ?: 0)}", Icons.Default.Tag))

                val ipv4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                items.add(NetworkDetailItem("IPv4 Address", ipv4?.address?.hostAddress ?: "—", Icons.Default.Language))
                items.add(NetworkDetailItem("Subnet Mask", "/${ipv4?.prefixLength ?: "—"}", Icons.Default.Lan))
                val gw = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
                items.add(NetworkDetailItem("Gateway", gw ?: "—", Icons.Default.Hub))

                val dnsServers = lp?.dnsServers?.map { it.hostAddress ?: "?" } ?: emptyList()
                items.add(NetworkDetailItem("DNS Servers", dnsServers.joinToString(", ").ifBlank { "—" }, Icons.Default.Dns))
                items.add(NetworkDetailItem("Domain", lp?.domains ?: "—", Icons.Default.Domain))

                items.add(NetworkDetailItem("VPN Active", if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) "Yes" else "No", Icons.Default.VpnKey))
                items.add(NetworkDetailItem("Metered", if (cm?.isActiveNetworkMetered == true) "Yes" else "No", Icons.Default.DataUsage))

                val txBytes = TrafficStats.getTotalTxBytes()
                val rxBytes = TrafficStats.getTotalRxBytes()
                items.add(NetworkDetailItem("Total TX", formatBytes(txBytes), Icons.Default.Upload))
                items.add(NetworkDetailItem("Total RX", formatBytes(rxBytes), Icons.Default.Download))

                items.add(NetworkDetailItem("Android API", "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})", Icons.Default.Android))

                // Network interfaces
                try {
                    val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                    val wlanIface = ifaces.firstOrNull { it.name.startsWith("wlan") }
                    if (wlanIface != null) {
                        items.add(NetworkDetailItem("Interface", wlanIface.name, Icons.Default.SettingsEthernet))
                        items.add(NetworkDetailItem("MTU", "${wlanIface.mtu}", Icons.Default.Tune))
                        val mac = wlanIface.hardwareAddress?.joinToString(":") { "%02x".format(it) }
                        items.add(NetworkDetailItem("MAC Address", mac ?: "Randomized", Icons.Default.Fingerprint))
                    }
                } catch (_: Exception) { }

                // Private DNS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val dnsMode = android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "off"
                    val dnsHost = android.provider.Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: "—"
                    items.add(NetworkDetailItem("Private DNS Mode", dnsMode, Icons.Default.Lock))
                    if (dnsMode == "hostname") items.add(NetworkDetailItem("Private DNS Host", dnsHost, Icons.Default.Dns))
                }

                details = items
            } catch (_: Exception) { }
        }
        loaded = true
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Network Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)) }
        items(details) { item ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.value, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val txt = details.joinToString("\n") { "${it.label}: ${it.value}" }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("wifi_details", txt))
                    Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Copy All Details") }
        }
    }
}

// ═══════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════

private suspend fun runSpeedTest(onProgress: (Float, String) -> Unit): SpeedTestResult {
    // Latency test
    onProgress(0.05f, "Measuring latency...")
    val pings = mutableListOf<Int>()
    repeat(5) {
        try {
            val start = System.currentTimeMillis()
            InetAddress.getByName("8.8.8.8").isReachable(3000)
            pings.add((System.currentTimeMillis() - start).toInt())
        } catch (_: Exception) { pings.add(-1) }
    }
    val validPings = pings.filter { it > 0 }
    val avgLatency = if (validPings.isNotEmpty()) validPings.average().toInt() else -1
    val jitter = if (validPings.size >= 2) {
        validPings.zipWithNext().map { (a, b) -> abs(a - b) }.average().toInt()
    } else 0
    val packetLoss = (pings.count { it < 0 }.toFloat() / pings.size) * 100f
    onProgress(0.15f, "Latency: ${avgLatency}ms")

    // Download speed test — download from multiple well-known CDN endpoints
    onProgress(0.2f, "Testing download speed...")
    val downloadMbps = measureDownloadSpeed { p -> onProgress(0.2f + p * 0.4f, "Downloading... ${"%.1f".format(p * 100)}%") }
    onProgress(0.65f, "Download: ${"%.1f".format(downloadMbps)} Mbps")

    // Upload speed test — measure by POSTing data (use timing heuristic)
    onProgress(0.7f, "Testing upload speed...")
    val uploadMbps = measureUploadSpeed { p -> onProgress(0.7f + p * 0.25f, "Uploading... ${"%.1f".format(p * 100)}%") }
    onProgress(1f, "Complete")

    return SpeedTestResult(downloadMbps, uploadMbps, avgLatency, jitter, packetLoss)
}

private fun measureDownloadSpeed(onProgress: (Float) -> Unit): Float {
    val urls = listOf(
        "https://speed.cloudflare.com/__down?bytes=5000000",
        "https://proof.ovh.net/files/1Mb.dat",
        "https://speed.hetzner.de/1MB.bin"
    )

    for (url in urls) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Cyble-SpeedTest")

            val startTime = System.nanoTime()
            var totalBytes = 0L
            val buffer = ByteArray(32768)

            conn.inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > 5_000_000) break
                    onProgress((totalBytes / 5_000_000f).coerceAtMost(1f))
                }
            }

            val elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0
            if (elapsedSec > 0 && totalBytes > 10000) {
                return ((totalBytes * 8.0) / elapsedSec / 1_000_000).toFloat()
            }
        } catch (_: Exception) { continue }
        finally { conn?.disconnect() }
    }
    return 0f
}

private fun measureUploadSpeed(onProgress: (Float) -> Unit): Float {
    return try {
        val testData = ByteArray(500_000) { (it % 256).toByte() }
        val conn = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(testData.size)

        val startTime = System.nanoTime()
        conn.outputStream.use { out ->
            var offset = 0
            val chunk = 16384
            while (offset < testData.size) {
                val len = minOf(chunk, testData.size - offset)
                out.write(testData, offset, len)
                offset += len
                onProgress(offset.toFloat() / testData.size)
            }
            out.flush()
        }
        conn.responseCode
        conn.disconnect()

        val elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0
        if (elapsedSec > 0) ((testData.size * 8.0) / elapsedSec / 1_000_000).toFloat() else 0f
    } catch (_: Exception) {
        onProgress(1f)
        0f
    }
}

private fun scanCommonPorts(host: String): List<PortScanResult> {
    val portsToScan = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 53 to "DNS",
        80 to "HTTP", 443 to "HTTPS", 445 to "SMB", 548 to "AFP",
        554 to "RTSP", 631 to "IPP/CUPS", 3389 to "RDP",
        5000 to "UPnP", 8080 to "HTTP-Alt", 8443 to "HTTPS-Alt"
    )
    return portsToScan.map { (port, name) ->
        val open = try {
            Socket().use { s -> s.connect(InetSocketAddress(host, port), 800); true }
        } catch (_: Exception) { false }
        PortScanResult(port, name, open)
    }
}

private fun readArpTable(): List<ConnectedDevice> {
    return try {
        val proc = Runtime.getRuntime().exec("cat /proc/net/arp")
        val devices = mutableListOf<ConnectedDevice>()
        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            reader.readLine() // skip header
            var line = reader.readLine()
            while (line != null) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                    devices.add(ConnectedDevice(parts[0], parts[3], ""))
                }
                line = reader.readLine()
            }
        }
        proc.waitFor()
        devices
    } catch (_: Exception) { emptyList() }
}

private fun frequencyToChannel(freq: Int): Int = when {
    freq in 2412..2484 -> (freq - 2407) / 5
    freq in 5170..5825 -> (freq - 5000) / 5
    freq == 2484 -> 14
    else -> 0
}

private fun getSecurityType(sr: ScanResult): String {
    val caps = sr.capabilities ?: return "Unknown"
    return when {
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") && caps.contains("WPA-") -> "WPA/WPA2"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        caps.contains("OWE") -> "OWE"
        caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> "Open"
        else -> "Unknown"
    }
}

private fun getChannelWidth(sr: ScanResult): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "—"
    return when (sr.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
        else -> "—"
    }
}

private fun getDnsStatus(context: Context?): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "See Security tab" else "Check Settings"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "$bytes B"
}

private fun signalColor(pct: Int): Color = when {
    pct >= 60 -> Color(0xFF4CAF50)
    pct >= 30 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun latencyColor(ms: Int): Color = when {
    ms < 0 -> Color.Gray
    ms <= 30 -> Color(0xFF4CAF50)
    ms <= 80 -> Color(0xFF8BC34A)
    ms <= 150 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun downloadColor(mbps: Float): Color = when {
    mbps >= 50 -> Color(0xFF4CAF50)
    mbps >= 20 -> Color(0xFF8BC34A)
    mbps >= 5 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun uploadColor(mbps: Float): Color = when {
    mbps >= 20 -> Color(0xFF4CAF50)
    mbps >= 5 -> Color(0xFF8BC34A)
    mbps >= 1 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}
