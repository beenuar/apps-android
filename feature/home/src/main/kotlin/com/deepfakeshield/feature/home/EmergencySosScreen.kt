package com.deepfakeshield.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySosScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var emergencyContacts by remember { mutableStateOf(listOf("Emergency Services (911)", "Local Police", "Cyber Crime Helpline")) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactNumber by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFF3F0))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SOS Button with countdown confirmation
            var countdown by remember { mutableIntStateOf(0) }
            var isCounting by remember { mutableStateOf(false) }

            LaunchedEffect(isCounting) {
                if (isCounting) {
                    for (i in 3 downTo 1) {
                        countdown = i
                        delay(1000)
                    }
                    triggerSos(context)
                    isCounting = false
                    countdown = 0
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .scale(if (isCounting) 1f else pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isCounting) listOf(Color(0xFFFF6659), Color(0xFFFF1744)) else listOf(Color(0xFFFF1744), Color(0xFFD50000)),
                            radius = 300f
                        )
                    )
                    .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        if (isCounting) {
                            isCounting = false
                            countdown = 0
                        } else {
                            isCounting = true
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isCounting) {
                        Text("$countdown", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 56.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to cancel", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Sos, null, tint = Color.White, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("EMERGENCY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Tap to call 911", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                if (isCounting) "Calling 911 in $countdown seconds... Tap SOS to cancel" else "Press the button above — 3-second countdown before calling",
                style = MaterialTheme.typography.bodySmall, color = if (isCounting) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )

            // Quick call buttons
            Text("Quick Emergency Contacts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

            // Emergency services
            QuickCallCard(Icons.Default.LocalHospital, "Emergency Services", "911", Color(0xFFF44336)) {
                dialNumber(context, "911")
            }

            QuickCallCard(Icons.Default.LocalPolice, "Police Non-Emergency", "311", Color(0xFF2196F3)) {
                dialNumber(context, "311")
            }

            QuickCallCard(Icons.Default.ReportProblem, "FTC Fraud Reporting", "1-877-382-4357", Color(0xFFFF9800)) {
                dialNumber(context, "18773824357")
            }

            QuickCallCard(Icons.Default.Psychology, "Identity Theft Hotline", "1-877-438-4338", Color(0xFF9C27B0)) {
                dialNumber(context, "18774384338")
            }

            // Scam-specific actions
            Spacer(Modifier.height(4.dp))
            Text("Scam Response Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

            listOf(
                Triple("Report to FTC", "File a complaint at reportfraud.ftc.gov", Icons.Default.Report),
                Triple("Freeze Your Credit", "Contact Equifax, Experian, and TransUnion", Icons.Default.CreditCard),
                Triple("Change Passwords", "Update all compromised account passwords", Icons.Default.Password),
                Triple("Alert Your Bank", "Notify financial institutions of fraud", Icons.Default.AccountBalance),
                Triple("Document Everything", "Screenshot conversations and save evidence", Icons.Default.PhotoCamera),
                Triple("File Police Report", "Report to local law enforcement", Icons.Default.LocalPolice)
            ).forEachIndexed { index, (title, description, icon) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Safety tips
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x15FF9800))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("If You're Being Scammed Right Now", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Do NOT send money, gift cards, or crypto",
                        "Hang up the phone — real agencies don't threaten",
                        "Do NOT give remote access to your device",
                        "Do NOT share OTPs, PINs, or passwords",
                        "Take a screenshot of the scam for evidence",
                        "Talk to someone you trust before acting"
                    ).forEach { tip ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                            Spacer(Modifier.width(6.dp))
                            Text(tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Emergency Contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newContactName, onValueChange = { newContactName = it }, label = { Text("Name") })
                    OutlinedTextField(value = newContactNumber, onValueChange = { newContactNumber = it }, label = { Text("Phone Number") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newContactName.isNotBlank()) {
                        emergencyContacts = emergencyContacts + "$newContactName ($newContactNumber)"
                        showAddDialog = false
                        newContactName = ""
                        newContactNumber = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickCallCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, number: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Phone, null, tint = color, modifier = Modifier.size(24.dp))
        }
    }
}

private fun triggerSos(context: Context) {
    // Vibrate for haptic feedback
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) { }

    dialNumber(context, "911")
}

private fun dialNumber(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
    }
}
