package com.deepfakeshield.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ToolItem(val name: String, val description: String, val icon: ImageVector, val color: Color, val route: String, val category: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(onNavigateToTool: (String) -> Unit = {}, onNavigateToSearch: () -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("All", "Privacy", "Security", "Network", "Monitor", "Recovery")

    val tools = remember { listOf(
        ToolItem("Privacy Audit", "Comprehensive device privacy score", Icons.Default.PrivacyTip, Color(0xFF4CAF50), "privacy_score", "Privacy"),
        ToolItem("App Auditor", "Audit app permissions", Icons.Default.Apps, Color(0xFF2196F3), "app_auditor", "Privacy"),
        ToolItem("Digital Footprint", "Track your online exposure", Icons.Default.Fingerprint, Color(0xFF9C27B0), "digital_footprint", "Privacy"),
        ToolItem("Clipboard Monitor", "See what apps read your clipboard", Icons.Default.ContentPaste, Color(0xFFFF5722), "clipboard_monitor", "Privacy"),
        ToolItem("Password Checker", "Check password strength & breaches", Icons.Default.Password, Color(0xFFE91E63), "password_checker", "Security"),
        ToolItem("Breach Monitor", "Check for data breaches", Icons.Default.ShieldMoon, Color(0xFFF44336), "breach_monitor", "Security"),
        ToolItem("Dark Web Monitor", "Scan dark web for your data", Icons.Default.DarkMode, Color(0xFF1A1A2E), "dark_web_monitor", "Security"),
        ToolItem("Scam Number Lookup", "Check if a phone number is a scam", Icons.Default.Phone, Color(0xFFFF9800), "scam_number_lookup", "Security"),
        ToolItem("Device Theft Protection", "Protect your device from theft", Icons.Default.PhoneLocked, Color(0xFF795548), "device_theft", "Security"),
        ToolItem("Wi-Fi Analytics", "Deep network diagnostics", Icons.Default.Wifi, Color(0xFF00BCD4), "wifi_scanner", "Network"),
        ToolItem("Network Monitor", "Monitor app network traffic", Icons.Default.DataUsage, Color(0xFF3F51B5), "network_monitor", "Network"),
        ToolItem("Emergency SOS", "Scam hotlines & panic button", Icons.Default.Sos, Color(0xFFF44336), "emergency_sos", "Recovery"),
        ToolItem("Scam Recovery", "Step-by-step scam recovery guide", Icons.Default.HealthAndSafety, Color(0xFF009688), "scam_recovery", "Recovery"),
        ToolItem("Weekly Report", "Your weekly safety summary", Icons.Default.Assessment, Color(0xFF607D8B), "weekly_report", "Monitor"),
        ToolItem("Antivirus", "Full device malware scan", Icons.Default.Security, Color(0xFF4CAF50), "diagnostics", "Security"),
        ToolItem("Secure Notes", "Encrypted notes storage", Icons.Default.Note, Color(0xFFFFC107), "secure_notes", "Security"),
        ToolItem("Secure File Vault", "Encrypted document storage", Icons.Default.Folder, Color(0xFF8BC34A), "secure_file_vault", "Security"),
        ToolItem("Security News", "Latest threats & advisories", Icons.Default.Newspaper, Color(0xFF673AB7), "security_news_feed", "Monitor"),
        ToolItem("Device Timeline", "Security event history", Icons.Default.Timeline, Color(0xFF455A64), "device_timeline", "Monitor"),
        ToolItem("Daily Challenge", "Test your security knowledge", Icons.Default.School, Color(0xFFFF9800), "daily_challenge", "Monitor"),
        ToolItem("Achievements", "Track your security milestones", Icons.Default.EmojiEvents, Color(0xFFFFC107), "achievements", "Monitor"),
        ToolItem("Threat Map", "Global threat visualization", Icons.Default.Map, Color(0xFF009688), "threat_map", "Monitor"),
        ToolItem("Analytics", "Protection statistics", Icons.Default.BarChart, Color(0xFF3F51B5), "analytics", "Monitor"),
        ToolItem("Permission Timeline", "See who's accessing camera, mic, location", Icons.Default.Timeline, Color(0xFFE91E63), "permission_timeline", "Privacy"),
        ToolItem("Data Broker Opt-Out", "Remove your data from 14 brokers", Icons.Default.DeleteSweep, Color(0xFFF44336), "data_broker_optout", "Privacy"),
        ToolItem("SIM Swap Protection", "Prevent phone number hijacking", Icons.Default.SimCard, Color(0xFFFF5722), "sim_swap_protection", "Security"),
        ToolItem("Metadata Stripper", "Inspect & remove photo EXIF data", Icons.Default.RemoveRedEye, Color(0xFF00BCD4), "metadata_stripper", "Privacy"),
        ToolItem("Disposable Email", "Generate email aliases for sign-ups", Icons.Default.AlternateEmail, Color(0xFF673AB7), "disposable_email", "Privacy"),
        ToolItem("Fake Domain Monitor", "Detect typosquat & phishing domains", Icons.Default.Domain, Color(0xFF1A237E), "fake_domain_monitor", "Security"),
        ToolItem("Report a Scam", "Help protect the community", Icons.Default.ReportProblem, Color(0xFFF44336), "community_report", "Recovery"),
        ToolItem("Tor Privacy Mode", "Route app traffic through Tor", Icons.Default.Security, Color(0xFF7C4DFF), "tor_settings", "Privacy"),
        ToolItem("Share Score", "Share your safety score with friends", Icons.Default.Share, Color(0xFF2196F3), "share_score", "Monitor"),
        ToolItem("Identity Monitor", "Consolidated identity health dashboard", Icons.Default.Person, Color(0xFF9C27B0), "identity_dashboard_v2", "Privacy"),
    ) }

    val filtered = remember(selectedCategory, searchQuery) {
        val byCat = if (selectedCategory == 0) tools else tools.filter { it.category == categories[selectedCategory] }
        if (searchQuery.isBlank()) byCat else byCat.filter { it.name.contains(searchQuery, true) || it.description.contains(searchQuery, true) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Security Tools", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tools...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEachIndexed { i, cat ->
                        FilterChip(selected = selectedCategory == i, onClick = { selectedCategory = i }, label = { Text(cat) })
                    }
                }
            }
            item { Text("${filtered.size} tools available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp)); Text("No tools match your search", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(filtered, key = { it.route }) { tool ->
                Card(Modifier.fillMaxWidth().clickable { onNavigateToTool(tool.route) }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(tool.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(tool.icon, null, Modifier.size(22.dp), tint = tool.color)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tool.name, fontWeight = FontWeight.SemiBold)
                            Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(tool.category, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
