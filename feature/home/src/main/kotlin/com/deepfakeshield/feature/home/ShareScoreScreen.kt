package com.deepfakeshield.feature.home

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.Toast
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val shields = listOf(uiState.videoShieldEnabled, uiState.messageShieldEnabled, uiState.callShieldEnabled, uiState.masterProtectionEnabled)
    val protectionScore = (shields.count { it } * 25).coerceIn(0, 100)
    val scoreColor = when { protectionScore >= 75 -> Color(0xFF4CAF50); protectionScore >= 50 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Share Your Score", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Preview card
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDEE1\uFE0F DeepfakeShield", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 12.dp.toPx(); val pad = sw / 2
                            drawArc(Color.White.copy(alpha = 0.15f), 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
                            drawArc(scoreColor, 135f, protectionScore * 2.7f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$protectionScore%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                            Text("protected", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ScoreStatItem("${uiState.totalThreatsBlocked}", "Threats\nBlocked", Color(0xFFF44336))
                        ScoreStatItem("${uiState.streak}", "Day\nStreak", Color(0xFFFF9800))
                        ScoreStatItem("${shields.count { it }}/4", "Shields\nActive", Color(0xFF4CAF50))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Are you protected? Get DeepfakeShield free", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }

            // Share buttons
            Button(onClick = {
                val shareText = "\uD83D\uDEE1\uFE0F My DeepfakeShield Score: $protectionScore%\n\n\uD83D\uDD25 ${uiState.streak}-day streak\n\uD83D\uDEAB ${uiState.totalThreatsBlocked} threats blocked\n\u2705 ${shields.count { it }}/4 shields active\n\nProtect yourself from scams, deepfakes & identity theft — free:\nhttps://deepfakeshield.app"
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(Intent.createChooser(intent, "Share your safety score").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share Score", fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val text = "\uD83D\uDEE1\uFE0F I'm $protectionScore% protected with DeepfakeShield! ${uiState.totalThreatsBlocked} threats blocked. Get it free: https://deepfakeshield.app"
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage("com.whatsapp"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { context.startActivity(intent) } catch (_: Exception) { Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("WhatsApp") }
                OutlinedButton(onClick = {
                    val text = "\uD83D\uDEE1\uFE0F $protectionScore% protected. ${uiState.totalThreatsBlocked} threats blocked. #DeepfakeShield https://deepfakeshield.app"
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Twitter/X") }
                OutlinedButton(onClick = {
                    val text = "My security score is $protectionScore% on DeepfakeShield. Are you protected? https://deepfakeshield.app"
                    val smsIntent = Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("sms:"); putExtra("sms_body", text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { context.startActivity(smsIntent) } catch (_: Exception) { }
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("SMS") }
            }

            Text("Sharing helps protect your friends and family from scams", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ScoreStatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
    }
}
