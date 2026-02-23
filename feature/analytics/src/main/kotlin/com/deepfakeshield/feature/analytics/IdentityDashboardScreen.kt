package com.deepfakeshield.feature.analytics

import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class IdStat(val label: String, val value: String, val icon: ImageVector, val color: Color, val detail: String)
private data class IdModule(val name: String, val status: String, val icon: ImageVector, val color: Color, val score: Int, val action: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBreach: () -> Unit = {},
    onNavigateToDarkWeb: () -> Unit = {},
    onNavigateToFootprint: () -> Unit = {},
    onNavigateToDataBroker: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var score by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<List<IdStat>>(emptyList()) }
    var modules by remember { mutableStateOf<List<IdModule>>(emptyList()) }
    var actions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.Default) { analyzeIdentity(ctx) }
        score = result.first; stats = result.second; modules = result.third
        actions = generateActions(score, stats)
        loading = false
    }

    val animScore by animateIntAsState(if (loading) 0 else score, tween(1500, easing = FastOutSlowInEasing), label = "s")
    val sc = when { animScore >= 75 -> Color(0xFF4CAF50); animScore >= 50 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }

    Scaffold(topBar = { TopAppBar(title = { Text("Identity Dashboard", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(sc.copy(0.08f), Color.Transparent))).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { animScore / 100f }, Modifier.fillMaxSize(), color = sc, strokeWidth = 12.dp, trackColor = Color.LightGray.copy(0.2f), strokeCap = StrokeCap.Round)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (loading) CircularProgressIndicator(Modifier.size(30.dp))
                                else { Text("$animScore", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = sc); Text("Identity\nHealth", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(when { score >= 75 -> "Strong Identity Protection"; score >= 50 -> "Moderate Exposure"; else -> "High Identity Risk" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (!loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.take(4).forEach { s ->
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = s.color.copy(0.06f))) {
                            Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(s.icon, null, Modifier.size(18.dp), tint = s.color)
                                Text(s.value, fontWeight = FontWeight.Bold, color = s.color, fontSize = 18.sp)
                                Text(s.label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 10.sp)
                            }
                        }
                    }
                }

                Text("Protection Modules", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                modules.forEach { m ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(m.color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(m.icon, null, Modifier.size(18.dp), tint = m.color) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(m.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { m.score / 100f }, Modifier.fillMaxSize(), color = m.color, strokeWidth = 3.dp, trackColor = Color.LightGray.copy(0.2f))
                                Text("${m.score}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = m.color)
                            }
                        }
                    }
                }

                if (actions.isNotEmpty()) {
                    Text("Recommended Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    actions.forEachIndexed { i, a ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))) {
                            Row(Modifier.padding(8.dp)) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp)); Text(a, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

private fun analyzeIdentity(ctx: Context): Triple<Int, List<IdStat>, List<IdModule>> {
    val accounts = try { AccountManager.get(ctx).accounts } catch (_: Exception) { emptyArray() }
    val emailAccounts = accounts.filter { it.name.contains("@") }
    val pm = ctx.packageManager
    val appCount = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)).count { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 } else @Suppress("DEPRECATION") pm.getInstalledPackages(0).count { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 } } catch (_: Exception) { 0 }
    val dangerPerms = listOf("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CONTACTS", "READ_SMS", "READ_CALL_LOG")
    val grantedPerms = dangerPerms.count { try { ctx.checkSelfPermission("android.permission.$it") == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false } }
    val hasVpn = try { val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager; cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true } catch (_: Exception) { false }

    val accountScore = (100 - accounts.size * 8).coerceIn(20, 100)
    val permScore = (100 - grantedPerms * 12).coerceIn(15, 100)
    val appScore = (100 - appCount / 2).coerceIn(20, 100)
    val vpnScore = if (hasVpn) 100 else 40
    val totalScore = ((accountScore + permScore + appScore + vpnScore) / 4.0).toInt()

    val stats = listOf(
        IdStat("Accounts", "${accounts.size}", Icons.Default.AccountCircle, Color(0xFF2196F3), "${emailAccounts.size} email accounts"),
        IdStat("Apps", "$appCount", Icons.Default.Apps, Color(0xFF9C27B0), "User-installed apps"),
        IdStat("Perms", "$grantedPerms/${dangerPerms.size}", Icons.Default.AdminPanelSettings, if (grantedPerms > 4) Color(0xFFF44336) else Color(0xFF4CAF50), "Dangerous permissions"),
        IdStat("VPN", if (hasVpn) "ON" else "OFF", Icons.Default.VpnKey, if (hasVpn) Color(0xFF4CAF50) else Color(0xFFFF9800), "Network protection")
    )

    val modules = listOf(
        IdModule("Account Exposure", "${accounts.size} accounts on ${accounts.map { it.type }.distinct().size} services", Icons.Default.AccountCircle, Color(0xFF2196F3), accountScore, "Remove unused accounts"),
        IdModule("Permission Posture", "$grantedPerms dangerous permissions active", Icons.Default.AdminPanelSettings, if (permScore >= 70) Color(0xFF4CAF50) else Color(0xFFFF9800), permScore, "Revoke unused permissions"),
        IdModule("App Surface", "$appCount apps — each collects data", Icons.Default.Apps, if (appScore >= 70) Color(0xFF4CAF50) else Color(0xFF9C27B0), appScore, "Uninstall unused apps"),
        IdModule("Network Shield", if (hasVpn) "VPN active" else "No VPN — IP exposed", Icons.Default.VpnKey, if (hasVpn) Color(0xFF4CAF50) else Color(0xFFF44336), vpnScore, "Enable Tor VPN"),
        IdModule("Device Security", "Android ${Build.VERSION.RELEASE} • Patch: ${Build.VERSION.SECURITY_PATCH}", Icons.Default.PhoneAndroid, Color(0xFF607D8B), if (Build.VERSION.SDK_INT >= 33) 90 else 50, "Update OS"),
    )

    return Triple(totalScore, stats, modules)
}

private fun generateActions(score: Int, stats: List<IdStat>): List<String> {
    val actions = mutableListOf<String>()
    if (stats.any { it.label == "VPN" && it.value == "OFF" }) actions += "Enable Tor VPN to hide your IP from all apps"
    if (stats.any { it.label == "Perms" && it.value.substringBefore("/").toIntOrNull()?.let { it > 3 } == true }) actions += "Review and revoke unnecessary dangerous permissions"
    if (stats.any { it.label == "Apps" && it.value.toIntOrNull()?.let { it > 50 } == true }) actions += "Uninstall unused apps to reduce your attack surface"
    if (stats.any { it.label == "Accounts" && it.value.toIntOrNull()?.let { it > 3 } == true }) actions += "Remove old accounts linked to this device"
    if (score < 60) actions += "Run a full security scan from the Antivirus tab"
    actions += "Check Breach Monitor to see if your accounts were exposed"
    return actions.take(5)
}
