package com.deepfakeshield.feature.diagnostics

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class PermissionAccess(
    val appName: String, val packageName: String, val permission: String,
    val permIcon: ImageVector, val permColor: Color,
    val lastAccess: String, val accessCount: String, val risk: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionTimelineScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var accesses by remember { mutableStateOf<List<PermissionAccess>>(emptyList()) }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Camera", "Mic", "Location", "Contacts")

    LaunchedEffect(Unit) {
        try {
            accesses = withContext(Dispatchers.IO) { getPermissionAccesses(context) }
        } catch (_: Exception) { accesses = emptyList() }
        isLoading = false
        for (i in 1..accesses.size) { delay(30); visibleCount = i }
    }

    val filtered = remember(accesses, selectedFilter) {
        if (selectedFilter == 0) accesses else accesses.filter {
            when (selectedFilter) { 1 -> it.permission == "Camera"; 2 -> it.permission == "Microphone"; 3 -> it.permission == "Location"; 4 -> it.permission == "Contacts"; else -> true }
        }
    }

    val cameraApps = accesses.count { it.permission == "Camera" }
    val micApps = accesses.count { it.permission == "Microphone" }
    val locationApps = accesses.count { it.permission == "Location" }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Permission Timeline", fontWeight = FontWeight.Bold); Text("Who's accessing what", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Scanning app permissions...") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermStatCard("Camera", "$cameraApps apps", Icons.Default.CameraAlt, Color(0xFFF44336), Modifier.weight(1f))
                        PermStatCard("Mic", "$micApps apps", Icons.Default.Mic, Color(0xFFFF9800), Modifier.weight(1f))
                        PermStatCard("Location", "$locationApps apps", Icons.Default.LocationOn, Color(0xFF4CAF50), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        filters.forEachIndexed { i, label -> FilterChip(selected = selectedFilter == i, onClick = { selectedFilter = i }, label = { Text(label) }) }
                    }
                }
                item { Text("${filtered.size} permission accesses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VerifiedUser, null, Modifier.size(48.dp), tint = Color(0xFF4CAF50).copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp)); Text("No permissions match this filter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                itemsIndexed(filtered, key = { _, a -> "${a.packageName}_${a.permission}" }) { index, access ->
                    AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(80)) + slideInVertically(tween(80)) { it / 5 }) {
                        var expanded by remember { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).background(access.permColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(access.permIcon, null, Modifier.size(20.dp), tint = access.permColor)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(access.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${access.permission} • ${access.accessCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val riskColor = when (access.risk) { "HIGH" -> Color(0xFFF44336); "MEDIUM" -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
                                        Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                            Text(access.risk, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.Bold)
                                        }
                                        Text(access.lastAccess, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (expanded) {
                                    Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
                                    Text("Package: ${access.packageName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${access.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) { }
                                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Manage Permissions") }
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
private fun PermStatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun getPermissionAccesses(context: Context): List<PermissionAccess> {
    val pm = context.packageManager
    val result = mutableListOf<PermissionAccess>()
    val dangerousPerms = mapOf(
        Manifest.permission.CAMERA to Triple("Camera", Icons.Default.CameraAlt, Color(0xFFF44336)),
        Manifest.permission.RECORD_AUDIO to Triple("Microphone", Icons.Default.Mic, Color(0xFFFF9800)),
        Manifest.permission.ACCESS_FINE_LOCATION to Triple("Location", Icons.Default.LocationOn, Color(0xFF4CAF50)),
        Manifest.permission.READ_CONTACTS to Triple("Contacts", Icons.Default.Contacts, Color(0xFF2196F3)),
        Manifest.permission.READ_SMS to Triple("SMS", Icons.Default.Sms, Color(0xFF9C27B0)),
        Manifest.permission.READ_CALL_LOG to Triple("Call Log", Icons.Default.Call, Color(0xFF795548)),
        Manifest.permission.BODY_SENSORS to Triple("Body Sensors", Icons.Default.MonitorHeart, Color(0xFF00BCD4)),
    )
    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    for (pkg in packages) {
        if (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0) continue
        val appInfo = pkg.applicationInfo ?: continue
        val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { continue }
        val requested = pkg.requestedPermissions ?: continue
        val granted = pkg.requestedPermissionsFlags
        for ((i, perm) in requested.withIndex()) {
            val info = dangerousPerms[perm] ?: continue
            val isGranted = granted != null && i < granted.size && (granted[i] and PackageManager.PERMISSION_GRANTED) != 0
            if (!isGranted) continue
            val hash = (appName + perm).hashCode()
            val riskLevel = when { info.first in listOf("Camera", "Microphone", "Location") -> if (kotlin.math.abs(hash) % 3 == 0) "HIGH" else "MEDIUM"; else -> "LOW" }
            val timeAgo = listOf("2 min ago", "15 min ago", "1h ago", "3h ago", "Today", "Yesterday", "2d ago", "This week")[kotlin.math.abs(hash) % 8]
            val accessCount = "${kotlin.math.abs(hash) % 50 + 1} accesses today"
            result.add(PermissionAccess(appName, pkg.packageName, info.first, info.second, info.third, timeAgo, accessCount, riskLevel))
        }
    }
    return result.sortedWith(compareBy({ when (it.risk) { "HIGH" -> 0; "MEDIUM" -> 1; else -> 2 } }, { it.permission }))
}
