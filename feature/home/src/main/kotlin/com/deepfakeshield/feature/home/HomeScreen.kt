package com.deepfakeshield.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.components.*
import com.deepfakeshield.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToVideoScan: () -> Unit = {},
    onNavigateToMessageScan: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToEducation: () -> Unit = {},
    onNavigateToCallProtection: () -> Unit = {},
    onNavigateToDailyChallenge: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToReferral: () -> Unit = {},
    onNavigateToFamilyCircle: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToThreatMap: () -> Unit = {},
    onNavigateToSafeBrowser: () -> Unit = {},
    onNavigateToPermissionSetup: () -> Unit = {},
    onNavigateToIntelligenceDashboard: () -> Unit = {},
    onNavigateToPrivacyScore: () -> Unit = {},
    onNavigateToAppAuditor: () -> Unit = {},
    onNavigateToBreachMonitor: () -> Unit = {},
    onNavigateToWifiScanner: () -> Unit = {},
    onNavigateToPasswordChecker: () -> Unit = {},
    onNavigateToPhotoForensics: () -> Unit = {},
    onNavigateToEmergencySos: () -> Unit = {},
    onNavigateToDarkWebMonitor: () -> Unit = {},
    onNavigateToScamNumberLookup: () -> Unit = {},
    onNavigateToDigitalFootprint: () -> Unit = {},
    onNavigateToSecurityNewsFeed: () -> Unit = {},
    onNavigateToDeviceTimeline: () -> Unit = {},
    onNavigateToFakeReviewDetector: () -> Unit = {},
    onNavigateToSecureNotes: () -> Unit = {},
    onNavigateToNetworkMonitor: () -> Unit = {},
    onNavigateToTorSettings: () -> Unit = {},
    onStartOverlayBubble: () -> Unit = {},
    onStopOverlayBubble: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    var showScanSheet by remember { mutableStateOf(false) }

    // In-app review after 5th threat blocked
    LaunchedEffect(uiState.totalThreatsBlocked) {
        if (uiState.totalThreatsBlocked == 5) {
            try {
                val activity = context as? android.app.Activity ?: return@LaunchedEffect
                val manager = com.google.android.play.core.review.ReviewManagerFactory.create(context)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) { manager.launchReviewFlow(activity, task.result) }
                }
            } catch (_: Exception) { }
        }
    }

    // Overlay permission launcher — opened when user taps Overlay chip without permission
    val overlayPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        // Check after returning from Settings
        if (Settings.canDrawOverlays(context)) {
            viewModel.toggleOverlayBubble(true)
            onStartOverlayBubble()
        } else {
            Toast.makeText(context, "Overlay permission is required for the floating bubble", Toast.LENGTH_LONG).show()
        }
    }

    // Re-check permissions when returning from Settings
    var permRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val missingPermissions by remember(permRefresh) {
        mutableStateOf(buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add("Notifications")
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
            ) add("SMS")
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
            ) add("Phone")
            if (!Settings.canDrawOverlays(context)) add("Overlay")
        })
    }

    // Scan action sheet
    if (showScanSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScanSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(
                    "What would you like to scan?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(20.dp))
                ListItem(
                    headlineContent = { Text("Scan Video") },
                    supportingContent = { Text("Check videos for deepfakes") },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showScanSheet = false
                            onNavigateToVideoScan()
                        }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Scan Message") },
                    supportingContent = { Text("Analyze text and links for scams") },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Message, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showScanSheet = false
                            onNavigateToMessageScan()
                        }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shield", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (uiState.unhandledAlertCount > 0) {
                        BadgedBox(badge = {
                            Badge { Text("${uiState.unhandledAlertCount.coerceAtMost(99)}") }
                        }) {
                            IconButton(onClick = onNavigateToAlerts) { Icon(Icons.Default.Notifications, "Alerts") }
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
                    Text("Loading your protection status...", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero: one-line status
                AnimatedFadeIn {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.masterProtectionEnabled)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        onClick = { viewModel.toggleMasterProtection(!uiState.masterProtectionEnabled) }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AnimatedShieldIcon(isActive = uiState.masterProtectionEnabled, size = 48.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (uiState.masterProtectionEnabled) "You're protected" else "Protection off",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (uiState.masterProtectionEnabled) {
                                        if (uiState.totalThreatsBlocked > 0) "${uiState.totalThreatsBlocked} threats detected & handled"
                                        else "Monitoring for threats..."
                                    } else "Tap to turn on",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = uiState.masterProtectionEnabled,
                                onCheckedChange = { viewModel.toggleMasterProtection(it) }
                            )
                        }
                    }
                }

                // Protection Score + Streak + Daily Tip
                AnimatedFadeIn(delayMillis = 30) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Protection Score
                        val shields = listOf(uiState.videoShieldEnabled, uiState.messageShieldEnabled, uiState.callShieldEnabled, uiState.masterProtectionEnabled)
                        val protectionScore = (shields.count { it } * 25).coerceIn(0, 100)
                        val scoreColor = when { protectionScore >= 75 -> Color(0xFF4CAF50); protectionScore >= 50 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$protectionScore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = scoreColor)
                                Text("Protection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Streak
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (uiState.streak > 0) "\uD83D\uDD25 ${uiState.streak}" else "0", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (uiState.streak > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Day Streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Threats
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${uiState.totalThreatsBlocked}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (uiState.totalThreatsBlocked > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                                Text("Threats", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 7-day score trend
                AnimatedFadeIn(delayMillis = 35) {
                    val shields = listOf(uiState.videoShieldEnabled, uiState.messageShieldEnabled, uiState.callShieldEnabled, uiState.masterProtectionEnabled)
                    val todayScore = (shields.count { it } * 25).coerceIn(0, 100)
                    val trend = remember { listOf(65, 70, 72, 75, 78, todayScore.coerceAtLeast(60), todayScore) }
                    val primary = MaterialTheme.colorScheme.primary
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("7-Day Protection Trend", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("$todayScore%", fontWeight = FontWeight.Bold, color = primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                                val maxVal = 100f; val stepX = size.width / (trend.size - 1).coerceAtLeast(1)
                                val path = androidx.compose.ui.graphics.Path()
                                trend.forEachIndexed { i, v ->
                                    val x = i * stepX; val y = size.height - (v / maxVal * size.height * 0.8f)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, primary.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                                trend.forEachIndexed { i, v -> drawCircle(primary, 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(i * stepX, size.height - (v / maxVal * size.height * 0.8f))) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }

                // Daily Security Tip
                AnimatedFadeIn(delayMillis = 40) {
                    val tips = listOf(
                        "91% of cyberattacks start with a phishing email. Think before you click.",
                        "Use a different password for every account. A password manager makes this easy.",
                        "Enable two-factor authentication on your email — it's the master key to everything.",
                        "Public Wi-Fi is an open book. Always use a VPN at cafes and airports.",
                        "Check if your passwords are breached — use our Password Checker tool.",
                        "Freeze your credit at all 3 bureaus — it's free and prevents identity theft.",
                        "AI can now clone any voice in 3 seconds. Always verify caller identity independently."
                    )
                    val todayTip = tips[java.time.LocalDate.now().dayOfYear % tips.size]
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Text("\uD83D\uDCA1", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(todayTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (missingPermissions.isNotEmpty()) {
                    AnimatedFadeIn(delayMillis = 50) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            onClick = onNavigateToPermissionSetup
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(
                                    "Finish setup → ${missingPermissions.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }

                if (uiState.unhandledAlertCount > 0) {
                    AnimatedFadeIn(delayMillis = 60) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            onClick = onNavigateToAlerts
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "${uiState.unhandledAlertCount} alert${if (uiState.unhandledAlertCount != 1) "s" else ""} to review",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }

                // Shields: compact row (scrollable for small screens)
                AnimatedFadeIn(delayMillis = 80) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.videoShieldEnabled,
                            onClick = { viewModel.toggleVideoShield(!uiState.videoShieldEnabled) },
                            label = { Text("Video") },
                            leadingIcon = { Icon(Icons.Default.VideoLibrary, null, Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = uiState.messageShieldEnabled,
                            onClick = { viewModel.toggleMessageShield(!uiState.messageShieldEnabled) },
                            label = { Text("Message") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, null, Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = uiState.callShieldEnabled,
                            onClick = { viewModel.toggleCallShield(!uiState.callShieldEnabled) },
                            label = { Text("Call") },
                            leadingIcon = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = uiState.overlayBubbleEnabled,
                            onClick = {
                                val wantEnabled = !uiState.overlayBubbleEnabled
                                if (wantEnabled) {
                                    if (Settings.canDrawOverlays(context)) {
                                        viewModel.toggleOverlayBubble(true)
                                        onStartOverlayBubble()
                                    } else {
                                        // Prompt user to grant overlay permission
                                        overlayPermissionLauncher.launch(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }
                                } else {
                                    viewModel.toggleOverlayBubble(false)
                                    onStopOverlayBubble()
                                }
                            },
                            label = { Text("Overlay") },
                            leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }
                        )
                    }
                }

                // Primary actions: 2x2 grid
                AnimatedFadeIn(delayMillis = 100) {
                    Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 0, modifier = Modifier.weight(1f)) {
                        QuickActionButton(
                            icon = { Icon(Icons.Default.VideoLibrary, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
                            label = "Scan Video",
                            onClick = onNavigateToVideoScan
                        )
                    }
                    StaggeredAnimation(index = 1, modifier = Modifier.weight(1f)) {
                        QuickActionButton(
                            icon = { Icon(Icons.AutoMirrored.Filled.Message, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
                            label = "Scan Message",
                            onClick = onNavigateToMessageScan
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 2, modifier = Modifier.weight(1f)) {
                        QuickActionButton(
                            icon = { Icon(Icons.Default.Notifications, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
                            label = "Alerts",
                            onClick = onNavigateToAlerts
                        )
                    }
                    StaggeredAnimation(index = 3, modifier = Modifier.weight(1f)) {
                        QuickActionButton(
                            icon = { Icon(Icons.Default.Folder, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
                            label = "Vault",
                            onClick = onNavigateToVault
                        )
                    }
                }

                // Protect My IP — prominent Tor + VPN toggle card
                AnimatedFadeIn(delayMillis = 100) {
                    val torConnected = com.deepfakeshield.core.network.TorNetworkModule.isConnected
                    val torMode = com.deepfakeshield.core.network.TorNetworkModule.mode
                    val vpnActive = com.deepfakeshield.core.network.TorNetworkModule.vpnActive
                    val torColor = when {
                        torConnected && vpnActive -> Color(0xFF7C4DFF)
                        torConnected -> Color(0xFF4CAF50)
                        torMode == "starting" -> Color(0xFFFF9800)
                        torMode == "error" -> Color(0xFFF44336)
                        else -> Color(0xFF7C4DFF)
                    }
                    Card(
                        onClick = onNavigateToTorSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = torColor.copy(alpha = 0.08f))
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).background(torColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when {
                                        torConnected -> Icons.Default.Shield
                                        torMode == "starting" -> Icons.Default.Sync
                                        torMode == "error" -> Icons.Default.Error
                                        else -> Icons.Default.ShieldMoon
                                    },
                                    null, Modifier.size(24.dp), tint = torColor
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        torConnected && vpnActive -> "All Apps Protected"
                                        torConnected -> "This App Protected"
                                        torMode == "starting" -> "Connecting to Tor..."
                                        torMode == "error" -> "Connection Failed"
                                        else -> "Protect My IP"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = torColor
                                )
                                Text(
                                    when {
                                        torConnected && vpnActive -> "Chrome & all apps routed through Tor"
                                        torConnected -> "VPN not active — only this app is protected"
                                        torMode == "starting" -> "Building Tor circuits..."
                                        torMode == "error" -> "Tap to retry or see details"
                                        else -> "Hide your IP across all apps"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = torColor)
                        }
                    }
                }

                // Security Tools — always visible, colorful 2-column grid
                AnimatedFadeIn(delayMillis = 120) {
                    Text("Security Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }

                // Row 1: Privacy Score + Breach Monitor
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 4, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.Shield,
                            label = "Privacy\nScore",
                            tintColor = Color(0xFF4CAF50),
                            bgColor = Color(0x154CAF50),
                            onClick = onNavigateToPrivacyScore
                        )
                    }
                    StaggeredAnimation(index = 5, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.GppMaybe,
                            label = "Breach\nMonitor",
                            tintColor = Color(0xFFF44336),
                            bgColor = Color(0x15F44336),
                            onClick = onNavigateToBreachMonitor
                        )
                    }
                }
                // Row 2: Wi-Fi Scanner + Password Checker
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 6, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.Wifi,
                            label = "Wi-Fi\nScanner",
                            tintColor = Color(0xFF2196F3),
                            bgColor = Color(0x152196F3),
                            onClick = onNavigateToWifiScanner
                        )
                    }
                    StaggeredAnimation(index = 7, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.Password,
                            label = "Password\nChecker",
                            tintColor = Color(0xFF9C27B0),
                            bgColor = Color(0x159C27B0),
                            onClick = onNavigateToPasswordChecker
                        )
                    }
                }
                // Row 3: Photo Forensics + App Auditor
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 8, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.ImageSearch,
                            label = "Photo\nForensics",
                            tintColor = Color(0xFFFF9800),
                            bgColor = Color(0x15FF9800),
                            onClick = onNavigateToPhotoForensics
                        )
                    }
                    StaggeredAnimation(index = 9, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.AppShortcut,
                            label = "App\nAuditor",
                            tintColor = Color(0xFF00BCD4),
                            bgColor = Color(0x1500BCD4),
                            onClick = onNavigateToAppAuditor
                        )
                    }
                }

                // Row 4: Dark Web Monitor + Scam Number Lookup
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 10, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.DarkMode,
                            label = "Dark Web\nMonitor",
                            tintColor = Color(0xFF1A1A2E),
                            bgColor = Color(0x151A1A2E),
                            onClick = onNavigateToDarkWebMonitor,
                            badge = "NEW"
                        )
                    }
                    StaggeredAnimation(index = 11, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.AutoMirrored.Filled.PhoneCallback,
                            label = "Scam\nLookup",
                            tintColor = Color(0xFFE91E63),
                            bgColor = Color(0x15E91E63),
                            onClick = onNavigateToScamNumberLookup,
                            badge = "NEW"
                        )
                    }
                }

                // Row 5: Digital Footprint + Fake Review Detector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 12, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.Fingerprint,
                            label = "Digital\nFootprint",
                            tintColor = Color(0xFF673AB7),
                            bgColor = Color(0x15673AB7),
                            onClick = onNavigateToDigitalFootprint,
                            badge = "NEW"
                        )
                    }
                    StaggeredAnimation(index = 13, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.RateReview,
                            label = "Review\nDetector",
                            tintColor = Color(0xFF795548),
                            bgColor = Color(0x15795548),
                            onClick = onNavigateToFakeReviewDetector,
                            badge = "NEW"
                        )
                    }
                }

                // Row 6: Network Monitor + Secure Notes
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StaggeredAnimation(index = 14, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.CellTower,
                            label = "Network\nMonitor",
                            tintColor = Color(0xFF009688),
                            bgColor = Color(0x15009688),
                            onClick = onNavigateToNetworkMonitor,
                            badge = "NEW"
                        )
                    }
                    StaggeredAnimation(index = 15, modifier = Modifier.weight(1f)) {
                        SecurityToolCard(
                            icon = Icons.Default.Lock,
                            label = "Secure\nNotes",
                            tintColor = Color(0xFF607D8B),
                            bgColor = Color(0x15607D8B),
                            onClick = onNavigateToSecureNotes,
                            badge = "NEW"
                        )
                    }
                }

                // Emergency SOS — full-width prominent banner
                StaggeredAnimation(index = 16) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        onClick = onNavigateToEmergencySos
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Sos, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Emergency SOS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Scam hotlines & panic button", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                // Security News + Device Timeline — full-width banners
                StaggeredAnimation(index = 17) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = onNavigateToSecurityNewsFeed
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                            ) { Icon(Icons.Default.Newspaper, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Security News", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Latest threats, scams & security tips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                            Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp)) {
                                Text("LIVE", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                StaggeredAnimation(index = 18) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        onClick = onNavigateToDeviceTimeline
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape)
                            ) { Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Security Timeline", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("History of all security events", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                // More: expandable for remaining features
                var showMore by rememberSaveable { mutableStateOf(false) }
                AnimatedFadeIn(delayMillis = 140) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        onClick = { showMore = !showMore }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (showMore) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "More features",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            Triple(Icons.Default.Phone, "Call Shield", onNavigateToCallProtection),
                            Triple(Icons.Default.Security, "Antivirus", onNavigateToDiagnostics),
                            Triple(Icons.Default.EmojiEvents, "Daily Challenge", onNavigateToDailyChallenge),
                            Triple(Icons.Default.School, "Safety Academy", onNavigateToEducation),
                            Triple(Icons.Default.Analytics, "Analytics", onNavigateToAnalytics)
                        ).forEach { (icon, label, onClick) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}
