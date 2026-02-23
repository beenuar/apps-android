package com.deepfakeshield.feature.home

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*

/**
 * Elderly/Simple Mode Dashboard
 * Extra-large text, high contrast, minimal options, voice-ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleModeDashboard(
    isProtected: Boolean = true,
    onScanMessage: () -> Unit = {},
    onCallHelp: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeepFake Shield", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(32.dp)) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big status indicator
            AnimatedFadeIn {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isProtected) SafeGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(if (isProtected) SafeGreen.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Icon(if (isProtected) Icons.Default.Shield else Icons.Default.Warning, null, modifier = Modifier.size(56.dp), tint = if (isProtected) SafeGreen else DangerRed)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (isProtected) "YOU ARE SAFE" else "PROTECTION OFF",
                            fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            color = if (isProtected) SafeGreen else DangerRed,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isProtected) "All shields are protecting you" else "Tap below to turn on protection",
                            fontSize = 20.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Big "Is This Safe?" button
            AnimatedFadeIn(delayMillis = 100) {
                Button(
                    onClick = onScanMessage,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("IS THIS SAFE?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Help text
            AnimatedFadeIn(delayMillis = 150) {
                Text("Got a suspicious message or call?\nTap the button above to check it.", fontSize = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 26.sp)
            }

            // Emergency call button
            AnimatedFadeIn(delayMillis = 200) {
                OutlinedButton(
                    onClick = onCallHelp,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CALL FOR HELP", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Share status
            AnimatedFadeIn(delayMillis = 250) {
                OutlinedButton(
                    onClick = {
                        val shareText = "I'm protected by DeepFake Shield! My status: ${if (isProtected) "SAFE" else "NEEDS HELP"}.\nGet it free: https://deepfakeshield.app"
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TELL FAMILY I'M SAFE", fontSize = 18.sp)
                }
            }

            // Tips
            AnimatedFadeIn(delayMillis = 300) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("REMEMBER", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("• Never share your OTP with anyone", fontSize = 18.sp)
                        Text("• Banks never call asking for your password", fontSize = 18.sp)
                        Text("• If it sounds too good to be true, it is", fontSize = 18.sp)
                        Text("• When in doubt, ask a family member", fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
