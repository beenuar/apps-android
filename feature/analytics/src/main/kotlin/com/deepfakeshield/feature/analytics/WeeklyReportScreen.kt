package com.deepfakeshield.feature.analytics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(onNavigateBack: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val totalScans = uiState.videoScansTotal + uiState.messageScansTotal
    val threats = uiState.summary.threatsBlocked
    val safetyScore = uiState.summary.safetyScore
    val week = java.time.LocalDate.now().let { "${it.minusDays(6).monthValue}/${it.minusDays(6).dayOfMonth} — ${it.monthValue}/${it.dayOfMonth}" }
    val animScore by animateIntAsState(safetyScore, tween(1200), label = "s")
    val sc = when { animScore >= 80 -> Color(0xFF4CAF50); animScore >= 60 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
    val chartData = remember(uiState.threatsByDay) { uiState.threatsByDay.takeLast(7).map { it.count }.let { if (it.isEmpty()) listOf(0, 0, 0, 0, 0, 0, 0) else it } }

    val pm = ctx.packageManager
    val appCount = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)).count { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 } else @Suppress("DEPRECATION") pm.getInstalledPackages(0).count { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 } } catch (_: Exception) { 0 }
    val dangerPerms = listOf("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CONTACTS", "READ_SMS", "READ_CALL_LOG")
    val grantedPerms = dangerPerms.count { try { ctx.checkSelfPermission("android.permission.$it") == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false } }

    val recommendations = remember(totalScans, threats, safetyScore, grantedPerms) { buildRecommendations(totalScans, threats, safetyScore, grantedPerms, appCount) }

    Scaffold(topBar = { TopAppBar(title = { Column { Text("Weekly Report", fontWeight = FontWeight.Bold); Text(week, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { IconButton(onClick = {
            val rpt = "Weekly Security Report ($week)\nScore: $safetyScore/100\nScans: $totalScans • Threats: $threats\nApps: $appCount • Perms: $grantedPerms/${dangerPerms.size}\n\nRecommendations:\n${recommendations.joinToString("\n") { "• $it" }}"
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("report", rpt)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }) { Icon(Icons.Default.Share, "Share") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Score
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = sc.copy(0.06f))) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { animScore / 100f }, Modifier.fillMaxSize(), color = sc, strokeWidth = 12.dp, trackColor = Color.LightGray.copy(0.2f), strokeCap = StrokeCap.Round)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$animScore", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = sc); Text("Safety", style = MaterialTheme.typography.labelSmall) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(when { safetyScore >= 80 -> "Excellent Protection"; safetyScore >= 60 -> "Good — Room to Improve"; else -> "Needs Attention" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WkStat("$totalScans", "Scans", Icons.Default.Search, Color(0xFF2196F3), Modifier.weight(1f))
                WkStat("$threats", "Threats", Icons.Default.Warning, Color(0xFFF44336), Modifier.weight(1f))
                WkStat("$appCount", "Apps", Icons.Default.Apps, Color(0xFF9C27B0), Modifier.weight(1f))
                WkStat("$grantedPerms", "Perms", Icons.Default.AdminPanelSettings, Color(0xFFFF9800), Modifier.weight(1f))
            }

            // Threat chart
            Text("Threat Trend (7 days)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                        val max = (chartData.max() + 1).toFloat()
                        val w = size.width / (chartData.size - 1).coerceAtLeast(1)
                        val path = Path()
                        chartData.forEachIndexed { i, v ->
                            val x = i * w; val y = size.height - (v / max * size.height)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(Color(0xFF2196F3), 4.dp.toPx(), Offset(x, y))
                        }
                        drawPath(path, Color(0xFF2196F3), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            // Activity breakdown
            Text("Activity Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            listOf(
                Triple("Video Scans", "${uiState.videoScansTotal}", Color(0xFF2196F3)),
                Triple("Message Scans", "${uiState.messageScansTotal}", Color(0xFF4CAF50)),
                Triple("Threats Blocked", "$threats", Color(0xFFF44336)),
                Triple("Safety Score", "$safetyScore/100", sc),
                Triple("Installed Apps", "$appCount", Color(0xFF9C27B0)),
                Triple("Dangerous Perms", "$grantedPerms/${dangerPerms.size}", Color(0xFFFF9800)),
                Triple("Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Color(0xFF607D8B)),
                Triple("Security Patch", Build.VERSION.SECURITY_PATCH, Color(0xFF607D8B)),
            ).forEach { (label, value, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(color, CircleShape)); Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }

            // Recommendations
            Text("This Week's Recommendations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            recommendations.forEachIndexed { i, rec ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))) {
                    Row(Modifier.padding(10.dp)) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp)); Text(rec, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun WkStat(v: String, l: String, ic: ImageVector, c: Color, m: Modifier) {
    Card(m, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(ic, null, Modifier.size(18.dp), tint = c); Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 18.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

private fun buildRecommendations(scans: Int, threats: Int, score: Int, perms: Int, apps: Int): List<String> {
    val r = mutableListOf<String>()
    if (scans == 0) r += "No scans this week — run a Video or Message scan to stay protected"
    if (threats > 0) r += "$threats threat${if (threats > 1) "s" else ""} detected — review and remediate in Alerts"
    if (score < 60) r += "Safety score is low ($score) — enable all shields and run a full scan"
    if (perms > 4) r += "You have $perms dangerous permissions granted — review in Privacy Score"
    if (apps > 60) r += "$apps apps installed — uninstall unused ones to reduce attack surface"
    if (r.isEmpty()) r += "Great week! All shields active and no threats detected"
    r += "Check Dark Web Monitor for new breach exposures"
    r += "Verify your passwords haven't been compromised in Breach Monitor"
    return r.take(5)
}
