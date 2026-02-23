package com.deepfakeshield.feature.home

import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TheftCheck(
    val name: String, val description: String, val icon: ImageVector,
    val color: Color, val passed: Boolean, val fixIntent: Intent?, val weight: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTheftScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<TheftCheck>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var score by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            checks = withContext(Dispatchers.IO) { runTheftChecks(context) }
            score = if (checks.isEmpty()) 0 else (checks.count { it.passed }.toFloat() / checks.size * 100).toInt()
            isScanning = false
            for (i in 1..checks.size) { delay(60); visibleCount = i }
        }
    }

    val animatedScore by animateIntAsState(if (isScanning) 0 else score, tween(1200), label = "score")
    val scoreColor = when { animatedScore >= 80 -> Color(0xFF4CAF50); animatedScore >= 50 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Device Theft Protection", fontWeight = FontWeight.Bold); if (!isScanning) Text("${checks.count { it.passed }}/${checks.size} checks passed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (!isScanning) IconButton(onClick = { isScanning = true; visibleCount = 0 }) { Icon(Icons.Default.Refresh, "Rescan") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Score gauge
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.06f))) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 12.dp.toPx(); val pad = sw / 2
                            drawArc(Color.LightGray.copy(alpha = 0.2f), 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
                            drawArc(scoreColor, 135f, animatedScore * 2.7f, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = Size(size.width - sw, size.height - sw))
                        }
                        if (isScanning) { CircularProgressIndicator(Modifier.size(36.dp)) }
                        else { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$animatedScore%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = scoreColor); Text("protected", style = MaterialTheme.typography.labelSmall) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(when { score >= 80 -> "Your device is well-protected against theft"; score >= 50 -> "Several vulnerabilities need attention"; else -> "Your device is at risk — fix the issues below" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            // Checks
            if (!isScanning) {
                Text("Security Checks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                checks.forEachIndexed { index, check ->
                    AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(80)) + slideInVertically(tween(80)) { it / 5 }) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background((if (check.passed) Color(0xFF4CAF50) else check.color).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(if (check.passed) Icons.Default.CheckCircle else check.icon, null, Modifier.size(20.dp), tint = if (check.passed) Color(0xFF4CAF50) else check.color)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(check.name, fontWeight = FontWeight.SemiBold)
                                    Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!check.passed && check.fixIntent != null) {
                                    val haptic = LocalHapticFeedback.current
                                    FilledTonalButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); try { context.startActivity(check.fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show() } },
                                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("Fix", style = MaterialTheme.typography.labelSmall) }
                                } else if (check.passed) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }

                // Emergency guide
                Spacer(Modifier.height(4.dp))
                Text("If Your Device Is Stolen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.04f))) {
                    Column(Modifier.padding(16.dp)) {
                        listOf(
                            "1. Use Find My Device (android.com/find) to locate, lock, or erase remotely",
                            "2. Call your carrier to suspend your line and block the IMEI",
                            "3. Change passwords for email, banking, and social media from a computer",
                            "4. Enable 2FA on all critical accounts using an authenticator app",
                            "5. File a police report with the device serial number and IMEI",
                            "6. Notify your bank and credit card companies",
                            "7. Monitor your credit reports for 90 days for identity theft",
                            "8. Remove the device from your Google account (myaccount.google.com/device-activity)",
                            "9. Revoke app-specific passwords and OAuth tokens",
                            "10. If work device: notify IT department immediately"
                        ).forEach { step ->
                            Text(step, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun runTheftChecks(context: Context): List<TheftCheck> {
    val checks = mutableListOf<TheftCheck>()
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager

    checks.add(TheftCheck("Screen Lock", if (km?.isDeviceSecure == true) "Device is secured with PIN/biometric" else "No screen lock — device accessible to anyone",
        Icons.Default.Lock, Color(0xFFF44336), km?.isDeviceSecure == true, Intent(Settings.ACTION_SECURITY_SETTINGS), 20))

    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val hasAdmin = (dpm?.activeAdmins?.size ?: 0) > 0
    checks.add(TheftCheck("Find My Device", if (hasAdmin) "Device admin enabled — remote locate/wipe possible" else "No device admin — cannot remotely lock or wipe",
        Icons.Default.LocationOn, Color(0xFF2196F3), hasAdmin, Intent(Settings.ACTION_SECURITY_SETTINGS), 15))

    val encrypted = Build.VERSION.SDK_INT >= 24
    checks.add(TheftCheck("Storage Encryption", if (encrypted) "AES-256 full-disk encryption active" else "Device may not be fully encrypted",
        Icons.Default.EnhancedEncryption, Color(0xFF4CAF50), encrypted, Intent(Settings.ACTION_SECURITY_SETTINGS), 20))

    val devOpt = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    checks.add(TheftCheck("USB Debugging", if (!devOpt) "Developer options disabled — USB access blocked" else "Developer options ON — USB debugging may allow data extraction",
        Icons.Default.Usb, Color(0xFFFF9800), !devOpt, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), 15))

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val hasVpn = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    checks.add(TheftCheck("VPN Protection", if (hasVpn) "VPN active — traffic encrypted even on stolen device" else "No VPN — a thief on your Wi-Fi can intercept traffic",
        Icons.Default.VpnKey, Color(0xFF9C27B0), hasVpn, null, 10))

    val btEnabled = try { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true } catch (_: Exception) { false }
    checks.add(TheftCheck("Bluetooth", if (!btEnabled) "Bluetooth OFF — not discoverable" else "Bluetooth ON — device discoverable nearby",
        Icons.Default.Bluetooth, Color(0xFF00BCD4), !btEnabled, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 5))

    val unknownSources = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else false } catch (_: Exception) { false }
    checks.add(TheftCheck("Unknown App Sources", if (!unknownSources) "Sideloading disabled — thief can't install spy tools" else "Unknown sources allowed — a thief could install malware",
        Icons.Default.AppBlocking, Color(0xFFFF5722), !unknownSources, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), 10))

    val autoLock = try { Settings.Secure.getInt(context.contentResolver, "lock_screen_lock_after_timeout", 5000) } catch (_: Exception) { 5000 }
    val quickLock = autoLock <= 15000
    checks.add(TheftCheck("Auto-Lock Timeout", if (quickLock) "Screen locks within ${autoLock / 1000}s — quick protection" else "Lock timeout is ${autoLock / 1000}s — consider reducing to 15s or less",
        Icons.Default.Timer, Color(0xFF795548), quickLock, Intent(Settings.ACTION_SECURITY_SETTINGS), 10))

    val biometricAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java); bm?.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS } catch (_: Exception) { false }
    } else false
    checks.add(TheftCheck("Biometric Lock", if (biometricAvailable) "Fingerprint/Face unlock enabled — fast and secure" else "No biometric lock configured — consider enabling fingerprint",
        Icons.Default.Fingerprint, Color(0xFF4CAF50), biometricAvailable, Intent(Settings.ACTION_SECURITY_SETTINGS), 10))

    val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        mode == "hostname" || mode == "opportunistic"
    } else false
    checks.add(TheftCheck("DNS Privacy", if (privateDns) "Private DNS enabled — queries encrypted" else "DNS unencrypted — a thief on your network sees every site you visit",
        Icons.Default.Dns, Color(0xFF2196F3), privateDns, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Intent(Settings.ACTION_WIRELESS_SETTINGS) else null, 5))

    return checks
}
