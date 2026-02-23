@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepfakeshield.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTor: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Protection section
            SettingsSection("Protection") {
                if (uiState.isBatteryOptimized) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.BatteryAlert, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery optimization may pause protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Disable to keep 24/7 monitoring active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                            }
                            Button(onClick = { viewModel.requestBatteryExemption() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("Disable")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SettingsToggle("Master Protection", "Global on/off switch", Icons.Filled.Shield, uiState.masterProtection) { viewModel.setMasterProtection(it) }
                SettingsToggle("Video Shield", "Deepfake detection", Icons.Filled.VideoLibrary, uiState.videoShield) { viewModel.setVideoShield(it) }
                SettingsToggle("Message Shield", "SMS & message scanning", Icons.AutoMirrored.Filled.Message, uiState.messageShield) { viewModel.setMessageShield(it) }
                SettingsToggle("Call Shield", "Scam call protection", Icons.Filled.Phone, uiState.callShield) { viewModel.setCallShield(it) }
                SettingsToggle("Clipboard Guard", "Scan copied content", Icons.Filled.ContentPaste, uiState.clipboardScanning) { viewModel.setClipboardScanning(it) }
                SettingsToggle("Auto-quarantine threats", "Isolate malware automatically", Icons.Filled.Security, uiState.autoQuarantineOnThreat) { viewModel.setAutoQuarantineOnThreat(it) }
            }

            // Threat database - update malware hashes from open-source feeds
            SettingsSection("Threat Database") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Malware hash database", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("${uiState.threatDbHashCount} known malware hashes. Update from open-source feeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.updateThreatDatabase() },
                        enabled = !uiState.threatDbUpdateInProgress
                    ) {
                        if (uiState.threatDbUpdateInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.threatDbUpdateInProgress) "Updating…" else "Update Now")
                    }
                }
                uiState.threatDbUpdateMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.clearThreatDbMessage()
                    }
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("✓")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Tor Privacy
            SettingsSection("Privacy & Network") {
                Card(Modifier.fillMaxWidth().clickable { onNavigateToTor() }, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = Color(0xFF7C4DFF))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tor Privacy Mode", fontWeight = FontWeight.SemiBold)
                            Text("Route app traffic through Tor network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }

            // Appearance section
            SettingsSection("Appearance") {
                SettingsChips(
                    label = "Theme",
                    options = listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED"),
                    selected = uiState.themeMode,
                    onSelect = { viewModel.setThemeMode(it) }
                )
            }

            // Auto-scan schedule
            SettingsSection("Scheduled Scanning") {
                SettingsChips(
                    label = "Auto-Scan Frequency",
                    options = listOf("off" to "Off", "daily" to "Daily", "weekly" to "Weekly"),
                    selected = if (uiState.autoDeleteHandled) "daily" else "off",
                    onSelect = { /* Wire to ScheduledScanWorker config */ }
                )
            }

            // Sensitivity section
            SettingsSection("Detection Sensitivity") {
                SettingsChips(
                    label = "Protection Level",
                    options = listOf("gentle" to "Gentle", "balanced" to "Balanced", "strict" to "Strict"),
                    selected = uiState.protectionLevel,
                    onSelect = { viewModel.setProtectionLevel(it) }
                )
                SettingsChips(
                    label = "Alert Sensitivity",
                    options = listOf("low" to "Low", "medium" to "Medium", "high" to "High"),
                    selected = uiState.alertSensitivity,
                    onSelect = { viewModel.setAlertSensitivity(it) }
                )
            }

            // Notifications section
            SettingsSection("Notifications") {
                SettingsToggle("Push Notifications", "Get alerted about threats", Icons.Filled.Notifications, uiState.notificationsEnabled) { viewModel.setNotifications(it) }
                SettingsToggle("Quiet Hours", "Silence alerts at night", Icons.Filled.DoNotDisturb, uiState.quietHoursEnabled) { viewModel.setQuietHours(it) }
                if (uiState.quietHoursEnabled) {
                    // Split into two rows to prevent overflow on phone screens.
                    // A single Row with 6 FilterChips + 2 Text labels needs ~464dp
                    // but only ~316dp is available after padding on standard phones.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("From", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(21, 22, 23).forEach { hour ->
                            FilterChip(
                                selected = uiState.quietHoursStart == hour,
                                onClick = { viewModel.setQuietHoursStart(hour) },
                                label = { Text("$hour:00", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(6, 7, 8).forEach { hour ->
                            FilterChip(
                                selected = uiState.quietHoursEnd == hour,
                                onClick = { viewModel.setQuietHoursEnd(hour) },
                                label = { Text("$hour:00", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Data section
            SettingsSection("Data & Storage") {
                SettingsToggle("Auto-Delete Handled", "Remove resolved alerts automatically", Icons.Filled.AutoDelete, uiState.autoDeleteHandled) { viewModel.setAutoDeleteHandled(it) }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data Retention", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Keep data for ${uiState.dataRetentionDays} days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 90, 365).forEach { days ->
                            FilterChip(
                                selected = uiState.dataRetentionDays == days,
                                onClick = { viewModel.setDataRetentionDays(days) },
                                label = { Text("${days}d") }
                            )
                        }
                    }
                }
            }

            // Privacy & security
            SettingsSection("Privacy & Security") {
                SettingsNavItem("Privacy Center", "Data controls & transparency", Icons.Filled.PrivacyTip, onNavigateToPrivacy)
                SettingsToggle("Analytics", "Help improve the app", Icons.Filled.Analytics, uiState.analyticsEnabled) { viewModel.setAnalyticsEnabled(it) }
                SettingsToggle("Crash Reports", "Send anonymous crash data", Icons.Filled.BugReport, uiState.crashlyticsEnabled) { viewModel.setCrashlyticsEnabled(it) }
            }

            // Enterprise section (when managed or for enterprise users)
            SettingsSection("Enterprise") {
                if (uiState.isManagedConfig) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Business, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Managed by organization", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Some settings may be configured by your IT admin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.exportAllData(com.deepfakeshield.feature.settings.ExportFormat.JSON) },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isExportAllowedByAdmin && uiState.exportStatus != ExportStatus.EXPORTING,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON")
                    }
                    Button(
                        onClick = { viewModel.exportAllData(com.deepfakeshield.feature.settings.ExportFormat.CSV) },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isExportAllowedByAdmin && uiState.exportStatus != ExportStatus.EXPORTING,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV")
                    }
                }
                if (!uiState.isExportAllowedByAdmin) {
                    Text(
                        "Export disabled by admin policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // About section
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DeepFake Shield", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Version ${uiState.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("All analysis runs locally on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsChips(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, display) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(display) },
                    leadingIcon = if (selected == value) { { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) } } else null
                )
            }
        }
    }
}

// ===== PRIVACY SCREEN =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Trust Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy promise
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Our Privacy Promise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("DeepFake Shield processes ALL data locally on your device. Nothing is sent to any server. Your messages, calls, and videos stay private.", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // What we collect
            PrivacyInfoCard("What We Process Locally", listOf(
                "SMS messages (scanned for scam patterns, never stored raw)",
                "Video frames (analyzed for deepfake indicators, immediately discarded)",
                "Call metadata (phone number patterns, no audio recorded)",
                "Notification text (scanned in real-time, not persisted)"
            ))

            // What we DON'T collect
            PrivacyInfoCard("What We NEVER Do", listOf(
                "Send your data to any server",
                "Record your calls or conversations",
                "Store your raw messages",
                "Share data with third parties",
                "Track your location",
                "Access your contacts without permission"
            ), isPositive = false)

            // Data controls
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Data Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SettingsToggle("Analytics", "Anonymous usage stats", Icons.Filled.Analytics, uiState.analyticsEnabled) { viewModel.setAnalyticsEnabled(it) }
                    SettingsToggle("Crash Reports", "Anonymous crash data", Icons.Filled.BugReport, uiState.crashlyticsEnabled) { viewModel.setCrashlyticsEnabled(it) }

                    HorizontalDivider()

                    Button(
                        onClick = { viewModel.exportAllData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.exportStatus != ExportStatus.EXPORTING && uiState.isExportAllowedByAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        if (uiState.exportStatus == ExportStatus.EXPORTING) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (uiState.exportStatus) {
                                ExportStatus.EXPORTING -> "Exporting..."
                                ExportStatus.SUCCESS -> "Exported!"
                                ExportStatus.ERROR -> "Export Failed"
                                else -> "Export My Data (GDPR)"
                            }
                        )
                    }
                    // Export error/success feedback
                    if (uiState.exportStatus == ExportStatus.ERROR && uiState.exportError != null) {
                        Text(
                            uiState.exportError ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Encryption
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EnhancedEncryption, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encryption", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("All stored data is encrypted using AES-256-GCM with device-specific keys. Even if someone accesses your device storage, they cannot read your incident data.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacyInfoCard(title: String, items: List<String>, isPositive: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (isPositive) Icons.Filled.CheckCircle else Icons.Filled.Block,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
