package com.deepfakeshield.feature.analytics

import android.Manifest
import android.accounts.AccountManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class FootprintCategory(
    val name: String, val exposure: Int, val icon: ImageVector, val group: String,
    val details: String, val dataPoints: List<String>, val findings: List<String>,
    val reduction: String, val fixAction: String = "", val fixIntent: Intent? = null,
    val dataVolume: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalFootprintScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isScanning by remember { mutableStateOf(true) }
    var totalExposure by remember { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf<List<FootprintCategory>>(emptyList()) }
    var visibleCount by remember { mutableIntStateOf(0) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanPhase by remember { mutableStateOf("") }
    var expandedIdx by remember { mutableIntStateOf(-1) }
    var selectedGroup by remember { mutableStateOf("All") }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            visibleCount = 0
            val result = withContext(Dispatchers.IO) { deepFootprint(context) { p, ph -> scanProgress = p; scanPhase = ph } }
            totalExposure = result.first; categories = result.second
            isScanning = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (i in 1..categories.size) { delay(60); visibleCount = i }
        }
    }

    val animExp by animateIntAsState(if (isScanning) 0 else totalExposure, tween(1500, easing = FastOutSlowInEasing), label = "e")
    val groups = listOf("All") + categories.map { it.group }.distinct()
    val filtered = if (selectedGroup == "All") categories else categories.filter { it.group == selectedGroup }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Digital Footprint", fontWeight = FontWeight.Bold); if (!isScanning) Text("${categories.size} exposure vectors analyzed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (!isScanning) {
                    IconButton(onClick = { isScanning = true; totalExposure = 0; scanProgress = 0f }) { Icon(Icons.Default.Refresh, "Rescan") }
                    IconButton(onClick = {
                        val r = "Digital Footprint Report — $totalExposure% exposed\n\n" + categories.joinToString("\n") { "${it.name}: ${it.exposure}% — ${it.details}" }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("fp", r))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Share, "Share") }
                }
            }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val ec = when { animExp >= 70 -> Color(0xFFF44336); animExp >= 45 -> Color(0xFFFF9800); animExp >= 25 -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }

            // Score gauge
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ec.copy(alpha = 0.06f))) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { val sw = 16.dp.toPx(); drawArc(Color.LightGray.copy(0.3f), 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw/2, sw/2), size = Size(size.width-sw, size.height-sw)); drawArc(ec, 135f, animExp*2.7f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw/2, sw/2), size = Size(size.width-sw, size.height-sw)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isScanning) { CircularProgressIndicator(Modifier.size(36.dp)); Spacer(Modifier.height(4.dp)); Text("${(scanProgress*100).toInt()}%", fontWeight = FontWeight.Bold) }
                            else { Text("$animExp%", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = ec); Text("exposed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (isScanning) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { scanProgress }, Modifier.fillMaxWidth(0.7f).height(4.dp), strokeCap = StrokeCap.Round); Spacer(Modifier.height(4.dp)); Text(scanPhase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else {
                        val v = when { totalExposure >= 70 -> "High Exposure" to "Significant data leakage across multiple vectors"; totalExposure >= 45 -> "Moderate Exposure" to "Several areas need attention"; totalExposure >= 25 -> "Low Exposure" to "Good privacy but room to improve"; else -> "Minimal Exposure" to "Excellent privacy hygiene" }
                        Spacer(Modifier.height(8.dp)); Text(v.first, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(v.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            // Stats
            if (!isScanning && categories.isNotEmpty()) {
                val high = categories.count { it.exposure >= 60 }; val med = categories.count { it.exposure in 30..59 }; val low = categories.count { it.exposure < 30 }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FpChip("$high High", Color(0xFFF44336), Modifier.weight(1f))
                    FpChip("$med Med", Color(0xFFFF9800), Modifier.weight(1f))
                    FpChip("$low Low", Color(0xFF4CAF50), Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    groups.forEach { g ->
                        val c = if (g == "All") categories.size else categories.count { it.group == g }
                        FilterChip(selected = selectedGroup == g, onClick = { selectedGroup = g }, label = { Text("$g ($c)", fontSize = 11.sp) })
                    }
                }
            }

            filtered.forEachIndexed { idx, cat ->
                AnimatedVisibility(visible = idx < visibleCount || selectedGroup != "All", enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it/4 }) {
                    FpCard(cat, expandedIdx == categories.indexOf(cat), { expandedIdx = if (expandedIdx == categories.indexOf(cat)) -1 else categories.indexOf(cat) }, context)
                }
            }

            if (!isScanning && categories.isNotEmpty()) {
                val topActions = categories.filter { it.exposure >= 40 }.take(5)
                if (topActions.isNotEmpty()) {
                    Text("Top Actions to Reduce Exposure", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    topActions.forEachIndexed { i, cat ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i+1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                                Icon(Icons.Default.TipsAndUpdates, null, Modifier.size(16.dp), tint = Color(0xFFFFC107))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cat.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    Text(cat.reduction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FpChip(text: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Text(text, Modifier.padding(vertical = 6.dp).fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun FpCard(cat: FootprintCategory, expanded: Boolean, onToggle: () -> Unit, ctx: Context) {
    val cc = when { cat.exposure >= 60 -> Color(0xFFF44336); cat.exposure >= 35 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle).animateContentSize(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(cc.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(cat.icon, null, Modifier.size(20.dp), tint = cc) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(4.dp))
                        Surface(color = cc.copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text(cat.group, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = cc, fontWeight = FontWeight.Bold) }
                    }
                    Text(cat.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 5 else 1)
                }
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { cat.exposure / 100f }, Modifier.fillMaxSize(), color = cc, strokeWidth = 3.dp, trackColor = Color.LightGray.copy(0.2f))
                    Text("${cat.exposure}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cc)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))

                if (cat.dataVolume.isNotEmpty()) {
                    Text("Data volume: ${cat.dataVolume}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }

                if (cat.dataPoints.isNotEmpty()) {
                    Text("Data exposed:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { cat.dataPoints.forEach { SuggestionChip(onClick = {}, label = { Text(it, fontSize = 10.sp) }) } }
                    Spacer(Modifier.height(6.dp))
                }

                if (cat.findings.isNotEmpty()) {
                    Text("Findings:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = cc)
                    cat.findings.forEach { Row(Modifier.padding(vertical = 1.dp)) { Text("•", color = cc); Spacer(Modifier.width(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    Spacer(Modifier.height(6.dp))
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))) {
                    Row(Modifier.padding(8.dp)) { Icon(Icons.Default.Lightbulb, null, Modifier.size(14.dp), tint = Color(0xFFFFC107)); Spacer(Modifier.width(4.dp)); Text(cat.reduction, style = MaterialTheme.typography.bodySmall) }
                }

                if (cat.fixIntent != null) {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { try { ctx.startActivity(cat.fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Settings, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(cat.fixAction)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// DEEP FOOTPRINT ENGINE
// ═══════════════════════════════════════════════════════════════════

private suspend fun deepFootprint(ctx: Context, onProgress: suspend (Float, String) -> Unit): Pair<Int, List<FootprintCategory>> {
    val cats = mutableListOf<FootprintCategory>()
    val pm = ctx.packageManager
    val total = 18f; var n = 0
    suspend fun step(ph: String) { n++; onProgress(n / total, ph); delay(60) }

    val allPkgs = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    } catch (_: Exception) { emptyList() }
    val userApps = allPkgs.filter { it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0 }
    fun hasApp(pkg: String) = try { @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    // ── APPS & DATA ──────────────────────────────────────────────

    step("App inventory...")
    val appCount = userApps.size
    val totalNetBytes = userApps.sumOf { TrafficStats.getUidTxBytes(it.applicationInfo?.uid ?: 0).coerceAtLeast(0) + TrafficStats.getUidRxBytes(it.applicationInfo?.uid ?: 0).coerceAtLeast(0) }
    cats += FootprintCategory("Installed Apps", (appCount * 0.6).toInt().coerceIn(10, 85), Icons.Default.Apps, "Apps",
        "$appCount user apps — each collects usage data, device info, and analytics",
        listOf("App usage", "Device model", "OS version", "Screen size", "Language"),
        listOf("$appCount user apps installed", "Average app collects 6+ data types", "Top data consumer: ${userApps.maxByOrNull { TrafficStats.getUidTxBytes(it.applicationInfo?.uid ?: 0) }?.let { pm.getApplicationLabel(it.applicationInfo!!).toString() } ?: "Unknown"}",
            "Total network: ${fmtBytes(totalNetBytes)}"),
        "Uninstall unused apps. Each app is a data collection vector.", "Manage Apps", Intent(Settings.ACTION_APPLICATION_SETTINGS), fmtBytes(totalNetBytes))

    step("Social media...")
    val socialApps = mapOf("com.facebook.katana" to "Facebook", "com.instagram.android" to "Instagram", "com.twitter.android" to "X/Twitter", "com.snapchat.android" to "Snapchat", "com.linkedin.android" to "LinkedIn", "com.tiktok.android" to "TikTok", "com.pinterest" to "Pinterest", "com.reddit.frontpage" to "Reddit", "com.tumblr" to "Tumblr", "com.zhiliaoapp.musically" to "TikTok (alt)")
    val foundSocial = socialApps.filter { hasApp(it.key) }
    cats += FootprintCategory("Social Media", (foundSocial.size * 10 + 5).coerceIn(5, 90), Icons.Default.People, "Apps",
        "${foundSocial.size} social apps — profiles, posts, connections, and behavior tracked",
        listOf("Profile data", "Posts/stories", "Connections graph", "Behavioral patterns", "Location tags", "Ad interests"),
        foundSocial.map { "✓ ${it.value}" } + if (foundSocial.size >= 3) listOf("Cross-platform tracking likely via shared ad networks") else emptyList(),
        "Review privacy settings in each app. Disable personalized ads and location tagging.")

    step("Messaging...")
    val msgApps = mapOf("com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram", "com.discord" to "Discord", "com.Slack" to "Slack", "com.viber.voip" to "Viber", "jp.naver.line.android" to "LINE", "com.facebook.orca" to "Messenger", "com.google.android.apps.messaging" to "Google Messages", "com.samsung.android.messaging" to "Samsung Messages")
    val foundMsg = msgApps.filter { hasApp(it.key) }
    cats += FootprintCategory("Messaging", (foundMsg.size * 9).coerceIn(5, 70), Icons.Default.Forum, "Apps",
        "${foundMsg.size} messaging apps — contacts synced, metadata logged",
        listOf("Contacts", "Message metadata", "Group memberships", "Online status", "Read receipts"),
        foundMsg.map { "✓ ${it.value}" } + listOf("Each app syncs your contact list to their servers"),
        "Disable contact sync in messaging app settings. Use disappearing messages.")

    step("Shopping & finance...")
    val shopApps = mapOf("com.amazon.mShop.android.shopping" to "Amazon", "com.ebay.mobile" to "eBay", "com.alibaba.aliexpresshd" to "AliExpress", "com.walmart.android" to "Walmart", "com.target.ui" to "Target", "com.shopify.mobile" to "Shopify")
    val bankApps = mapOf("com.chase.sig.android" to "Chase", "com.wf.wellsfargomobile" to "Wells Fargo", "com.infonow.bofa" to "Bank of America", "com.citi.citimobile" to "Citi", "com.paypal.android.p2pmobile" to "PayPal", "com.venmo" to "Venmo", "com.squareup.cash" to "Cash App", "com.google.android.apps.walletnfcrel" to "Google Pay", "com.apple.android.music" to "Apple")
    val foundShop = shopApps.filter { hasApp(it.key) }
    val foundBank = bankApps.filter { hasApp(it.key) }
    cats += FootprintCategory("Shopping & Finance", ((foundShop.size + foundBank.size) * 8 + 10).coerceIn(10, 85), Icons.Default.ShoppingCart, "Finance",
        "${foundShop.size} shopping + ${foundBank.size} finance apps",
        listOf("Purchase history", "Payment methods", "Shipping addresses", "Account balances", "Transaction patterns"),
        foundShop.map { "🛒 ${it.value}" } + foundBank.map { "🏦 ${it.value}" },
        "Use guest checkout. Limit saved payment methods. Enable transaction alerts.")

    step("Accounts on device...")
    val accounts = try { AccountManager.get(ctx).accounts } catch (_: Exception) { emptyArray() }
    val accountTypes = accounts.map { it.type }.distinct()
    val accountNames = accounts.map { it.name.let { n -> if (n.contains("@")) n.substringBefore("@").take(3) + "***@" + n.substringAfter("@") else n.take(3) + "***" } }
    cats += FootprintCategory("Linked Accounts", (accounts.size * 12).coerceIn(5, 80), Icons.Default.AccountCircle, "Identity",
        "${accounts.size} accounts linked — each syncs data continuously",
        listOf("Email address", "Profile name", "Contacts", "Calendar", "App data"),
        accountNames.take(5) + if (accounts.size > 5) listOf("... and ${accounts.size - 5} more") else emptyList(),
        "Remove old accounts: Settings > Accounts. Each syncs contacts, calendar, and app data.", "Manage Accounts", Intent(Settings.ACTION_SYNC_SETTINGS))

    // ── PERMISSIONS & SENSORS ────────────────────────────────────

    step("Permissions...")
    val dangerPerms = mapOf(Manifest.permission.CAMERA to "Camera", Manifest.permission.RECORD_AUDIO to "Microphone", Manifest.permission.ACCESS_FINE_LOCATION to "GPS Location", Manifest.permission.READ_CONTACTS to "Contacts", Manifest.permission.READ_SMS to "SMS Messages", Manifest.permission.READ_CALL_LOG to "Call History", Manifest.permission.BODY_SENSORS to "Body Sensors")
    val granted = dangerPerms.filter { ContextCompat.checkSelfPermission(ctx, it.key) == PackageManager.PERMISSION_GRANTED }
    cats += FootprintCategory("Sensor Permissions", (granted.size * 12).coerceIn(0, 85), Icons.Default.Sensors, "Device",
        "${granted.size} of ${dangerPerms.size} sensor permissions active — apps can access these anytime",
        granted.values.toList(),
        granted.map { "✓ ${it.value} — accessible to ${userApps.count { pkg -> pkg.requestedPermissions?.any { p -> p == it.key } == true }} apps" },
        "Revoke permissions you don't actively use: Settings > Privacy > Permission Manager.", "Manage Permissions", Intent(Settings.ACTION_APPLICATION_SETTINGS))

    step("Location tracking...")
    val locEnabled = try { (ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled == true } catch (_: Exception) { false }
    val locPerms = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val bgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED else false
    val locApps = userApps.count { it.requestedPermissions?.any { p -> p.contains("LOCATION") } == true }
    cats += FootprintCategory("Location Tracking", if (locEnabled && locPerms) (if (bgLoc) 85 else 60) else 15, Icons.Default.LocationOn, "Tracking",
        if (locEnabled) "Location ON — $locApps apps can track you${if (bgLoc) " (including background)" else ""}" else "Location OFF",
        listOf("GPS coordinates", "Movement patterns", "Places visited", "Duration at locations", "Travel routes"),
        listOf(if (locEnabled) "Location services: ENABLED" else "Location services: DISABLED", "$locApps apps request location access", if (bgLoc) "⚠ Background location: GRANTED (always tracking)" else "Background location: Not granted") +
            if (locApps > 5) listOf("$locApps apps want your location — review each one") else emptyList(),
        "Disable location when not navigating. Use 'While using app' instead of 'Always'.", "Location Settings", Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    // ── NETWORK ──────────────────────────────────────────────────

    step("Network exposure...")
    val hasVpn = try { val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager; cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true } catch (_: Exception) { false }
    val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { val mode = Settings.Global.getString(ctx.contentResolver, "private_dns_mode"); mode == "hostname" || mode == "opportunistic" } else false
    val wifiOn = try { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true } catch (_: Exception) { false }
    cats += FootprintCategory("Network Exposure", when { hasVpn && privateDns -> 10; hasVpn -> 20; privateDns -> 35; else -> 60 }, Icons.Default.Wifi, "Network",
        when { hasVpn -> "VPN active — IP hidden from websites" ; else -> "No VPN — ISP sees all traffic, IP exposed to every website" },
        listOf("IP address", "DNS queries", "Browsing history (ISP)", "Connection metadata", "Device fingerprint"),
        listOf(if (hasVpn) "✓ VPN: Active" else "✗ VPN: Not active", if (privateDns) "✓ Private DNS: Enabled" else "✗ Private DNS: Disabled (ISP sees all domains)", if (wifiOn) "Wi-Fi: ON" else "Wi-Fi: OFF"),
        "Enable VPN (Tor in this app) and Private DNS (Settings > Network > Private DNS).", "Network Settings", Intent(Settings.ACTION_WIRELESS_SETTINGS))

    step("Saved Wi-Fi networks...")
    val savedNetworks = try { if (Build.VERSION.SDK_INT < 30) @Suppress("DEPRECATION") (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.configuredNetworks?.size ?: 0 else -1 } catch (_: Exception) { -1 }
    cats += FootprintCategory("Wi-Fi History", if (savedNetworks > 10) 55 else if (savedNetworks > 5) 35 else 20, Icons.Default.WifiFind, "Network",
        if (savedNetworks >= 0) "$savedNetworks saved Wi-Fi networks — each reveals a location you've visited" else "Wi-Fi history (exact count requires API < 30)",
        listOf("Network names (SSIDs)", "Locations visited", "Connection patterns"),
        if (savedNetworks >= 0) listOf("$savedNetworks saved networks", "Each SSID can reveal: home, office, hotel, airport, cafe") else listOf("Android 11+ restricts access to saved network list", "Your device still stores all networks"),
        "Forget old Wi-Fi networks you no longer use. Each saved SSID reveals a place you've been.")

    // ── DEVICE & IDENTITY ────────────────────────────────────────

    step("Device identifiers...")
    val devOptions = Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    cats += FootprintCategory("Device Fingerprint", if (devOptions) 65 else 45, Icons.Default.Smartphone, "Device",
        "Your device has unique identifiers used for tracking across apps",
        listOf("Advertising ID (GAID)", "Android ID", "Device model", "Screen resolution", "Installed fonts", "Language", "Timezone"),
        listOf("Android ID: unique per device + app combo", "Advertising ID: resettable but most users don't reset it", "Device model: ${Build.MANUFACTURER} ${Build.MODEL}", "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", if (devOptions) "⚠ Developer mode ON: extra fingerprinting vectors" else "Developer mode: OFF"),
        "Reset your Advertising ID periodically: Settings > Privacy > Ads.", "Privacy Settings", Intent(Settings.ACTION_PRIVACY_SETTINGS))

    step("Bluetooth...")
    val btOn = try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true } catch (_: Exception) { false }
    cats += FootprintCategory("Bluetooth Exposure", if (btOn) 40 else 10, Icons.Default.Bluetooth, "Device",
        if (btOn) "Bluetooth ON — device name and MAC visible to nearby scanners" else "Bluetooth OFF — low wireless exposure",
        listOf("Device name", "Bluetooth MAC", "Paired devices", "Proximity data"),
        listOf(if (btOn) "Bluetooth: ON — discoverable" else "Bluetooth: OFF", "Retail stores use BLE beacons to track shoppers", "Your BT MAC is a unique persistent identifier"),
        "Turn off Bluetooth when not using wireless accessories.", "Bluetooth Settings", Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    // ── TRACKING & ADS ───────────────────────────────────────────

    step("Ad trackers in apps...")
    val trackerSigs = listOf("com.facebook.ads", "com.google.android.gms.ads", "com.adjust.", "com.appsflyer.", "com.mixpanel.", "com.amplitude.", "com.braze.", "io.branch.", "com.kochava.", "com.onesignal.")
    var totalTrackers = 0; val appsWithTrackers = mutableListOf<String>()
    userApps.forEach { pkg ->
        val meta = pkg.applicationInfo?.metaData?.keySet()?.toList() ?: emptyList()
        val count = trackerSigs.count { sig -> meta.any { it.startsWith(sig) } }
        if (count > 0) { totalTrackers += count; appsWithTrackers += try { pm.getApplicationLabel(pkg.applicationInfo!!).toString() } catch (_: Exception) { pkg.packageName } }
    }
    cats += FootprintCategory("Ad Trackers", (totalTrackers * 3 + appsWithTrackers.size * 4).coerceIn(10, 90), Icons.Default.Visibility, "Tracking",
        "$totalTrackers tracker SDKs found in ${appsWithTrackers.size} apps",
        listOf("Ad clicks", "Purchase attribution", "Cross-app tracking", "Device graph", "Behavioral profile"),
        appsWithTrackers.take(8).map { "✓ $it" } + if (appsWithTrackers.size > 8) listOf("... and ${appsWithTrackers.size - 8} more apps") else emptyList(),
        "Use apps with fewer trackers. Check Exodus Privacy (exodus-privacy.eu.org) for tracker reports.")

    step("Clipboard exposure...")
    val clipHasData = try { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.hasPrimaryClip() == true } catch (_: Exception) { false }
    cats += FootprintCategory("Clipboard", if (clipHasData) 40 else 10, Icons.Default.ContentPaste, "Device",
        if (clipHasData) "Clipboard has data — all apps can read it (Android < 13)" else "Clipboard empty",
        listOf("Copied text", "URLs", "Passwords", "OTPs"),
        listOf(if (clipHasData) "⚠ Clipboard contains data" else "✓ Clipboard is empty", "Android 12-: any app can silently read clipboard", "Android 13+: shows notification when apps access clipboard"),
        "Never copy passwords manually. Use a password manager with autofill instead.")

    step("Notification listeners...")
    val notifCount = try { Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.split(":")?.count { it.isNotBlank() } ?: 0 } catch (_: Exception) { 0 }
    cats += FootprintCategory("Notification Access", (notifCount * 18).coerceIn(0, 80), Icons.Default.Notifications, "Tracking",
        "$notifCount apps read ALL your notifications — OTPs, messages, banking alerts",
        listOf("OTP codes", "Banking alerts", "Message previews", "Email subjects", "App notifications"),
        listOf("$notifCount notification listener(s) active", "These apps see every notification you receive", "Includes 2FA codes, banking alerts, and private messages"),
        "Revoke notification access for apps that don't need it.", "Notification Access", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    step("Accessibility services...")
    val accCount = try { (ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.size ?: 0 } catch (_: Exception) { 0 }
    cats += FootprintCategory("Screen Reading", (accCount * 25).coerceIn(0, 90), Icons.Default.Accessibility, "Tracking",
        "$accCount accessibility services — can read everything on your screen",
        listOf("All screen text", "Passwords typed", "Messages read", "Buttons tapped", "App content"),
        listOf("$accCount accessibility service(s) enabled", "These see EVERYTHING on screen — passwords, messages, banking", "Legitimate uses: screen readers, password managers"),
        "Only enable accessibility for apps you fully trust.", "Accessibility", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    // ── ESTIMATION ───────────────────────────────────────────────

    step("Data broker estimation...")
    val brokerEstimate = (appsWithTrackers.size * 2 + foundSocial.size * 5 + accounts.size).coerceIn(5, 150)
    cats += FootprintCategory("Data Broker Exposure", (brokerEstimate * 0.45).toInt().coerceIn(15, 85), Icons.Default.Store, "Tracking",
        "~$brokerEstimate data brokers may have your information",
        listOf("Name", "Email", "Phone", "Address", "Demographics", "Purchase history", "Browsing habits"),
        listOf("Estimated $brokerEstimate brokers based on your app + account footprint", "${foundSocial.size} social platforms share data with ad networks", "${appsWithTrackers.size} apps have embedded tracking SDKs", "Each tracker shares data with multiple brokers"),
        "Opt out at optout.aboutads.info and yourdigitalrights.org. Consider a data removal service.")

    step("Browser exposure...")
    val browsers = mapOf("com.android.chrome" to "Chrome", "org.mozilla.firefox" to "Firefox", "com.brave.browser" to "Brave", "com.opera.browser" to "Opera", "com.microsoft.emmx" to "Edge", "org.torproject.torbrowser" to "Tor Browser", "com.duckduckgo.mobile.android" to "DuckDuckGo", "com.sec.android.app.sbrowser" to "Samsung Internet")
    val foundBrowsers = browsers.filter { hasApp(it.key) }
    val hasPrivacyBrowser = foundBrowsers.keys.any { it.contains("brave") || it.contains("duckduckgo") || it.contains("tor") || it.contains("firefox") }
    cats += FootprintCategory("Browsers", if (hasPrivacyBrowser) 30 else 55, Icons.Default.Language, "Apps",
        "${foundBrowsers.size} browser(s) — browsing history, cookies, form data",
        listOf("Browsing history", "Cookies", "Saved passwords", "Form autofill", "Search queries", "Downloads"),
        foundBrowsers.map { "✓ ${it.value}" } + listOf(if (hasPrivacyBrowser) "✓ Privacy-focused browser detected" else "⚠ No privacy browser — consider Brave, Firefox, or DuckDuckGo"),
        "Use a privacy-focused browser. Clear cookies regularly. Enable tracking protection.")

    step("Compiling...")
    return cats.map { it.exposure }.average().toInt() to cats.sortedByDescending { it.exposure }
}

private fun fmtBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"; bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1073741824 -> "${"%.1f".format(bytes / 1048576.0)} MB"; else -> "${"%.2f".format(bytes / 1073741824.0)} GB"
}
