package com.deepfakeshield.feature.shield

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.feature.shield.MessageShieldViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHubScreen(
    onNavigateToVideoScan: () -> Unit = {},
    onNavigateToMessageScan: () -> Unit = {},
    onNavigateToPhotoForensics: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToEmailScanner: () -> Unit = {},
    onNavigateToFakeReview: () -> Unit = {},
    onNavigateToUrlScanner: () -> Unit = {}
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Scan & Analyze", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick scan hero
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val scale by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "s")
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color(0xFF2196F3).copy(alpha = 0.15f * scale), radius = size.minDimension / 2 * scale)
                        }
                        Icon(Icons.Default.Radar, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("What do you want to scan?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("All analysis runs 100% on-device — nothing leaves your phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            // Primary scan actions
            Text("Scan Content", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanActionCard("Video", "Deepfake\nDetection", Icons.Default.Videocam, Color(0xFFF44336), Modifier.weight(1f), onNavigateToVideoScan)
                ScanActionCard("Message", "Scam\nAnalysis", Icons.Default.Message, Color(0xFF2196F3), Modifier.weight(1f), onNavigateToMessageScan)
                ScanActionCard("Email", "Phishing\nDetection", Icons.Default.Email, Color(0xFF9C27B0), Modifier.weight(1f), onNavigateToEmailScanner)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanActionCard("Photo", "Image\nForensics", Icons.Default.PhotoCamera, Color(0xFF4CAF50), Modifier.weight(1f), onNavigateToPhotoForensics)
                ScanActionCard("QR Code", "URL\nSafety", Icons.Default.QrCodeScanner, Color(0xFFFF9800), Modifier.weight(1f), onNavigateToQrScanner)
                ScanActionCard("Review", "Fake\nDetection", Icons.Default.RateReview, Color(0xFF00BCD4), Modifier.weight(1f), onNavigateToFakeReview)
            }

            // Safe Browser
            Card(Modifier.fillMaxWidth().clickable(onClick = onNavigateToUrlScanner), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFF795548).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Language, null, Modifier.size(24.dp), tint = Color(0xFF795548))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Safe Browser", fontWeight = FontWeight.SemiBold)
                        Text("Browse the web with real-time URL threat blocking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // How it works
            Spacer(Modifier.height(4.dp))
            Text("How Our Scanning Works", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            listOf(
                Triple("Multi-Layer Detection", "Each scan combines ML models, heuristic analysis, and pattern matching for maximum accuracy", Icons.Default.Layers),
                Triple("Zero Cloud Dependency", "All analysis runs on your device's processor — your data never touches our servers", Icons.Default.PhoneAndroid),
                Triple("Continuous Learning", "Detection models improve with each scan through on-device feedback loops", Icons.Default.Psychology),
                Triple("Evidence Preservation", "All findings are logged to the Vault with timestamps for legal documentation", Icons.Default.Gavel)
            ).forEach { (title, desc, icon) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanActionCard(label: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick).height(120.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = color)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 12.sp)
        }
    }
}
