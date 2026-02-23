package com.deepfakeshield.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(onNavigateBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("What's New", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Version 3.0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            listOf(
                "Tor Privacy Mode" to "Embedded Tor routes all app traffic through the Tor network to hide your IP",
                "App Install Scanner" to "Automatically scans every newly installed app for malware",
                "Permission Change Monitor" to "Alerts when apps gain new dangerous permissions after updates",
                "Component Exposure Analysis" to "App Auditor detects exported activities/services that increase attack surface",
                "Privacy Grade A-F" to "Per-app privacy grade with tracker SDK detection (15 families)",
                "54K+ Offline Threat DB" to "Expanded bundled malware hash database for day-zero protection",
                "5 Languages" to "Spanish, French, Portuguese, Hindi, Arabic support",
                "Saved Wi-Fi Hygiene" to "Detects and warns about saved open networks",
                "7-Day Protection Trend" to "Home screen shows your security score trend over the past week",
                "Community Scam Reporting" to "Report scam phone numbers, URLs, and emails to protect others",
                "Fake Domain Monitor" to "Detects typosquat and homoglyph impersonation domains",
                "Dark Web Telegram Intelligence" to "Monitors 18 Telegram leak channels with IOC extraction",
                "Shareable Safety Score" to "Share your protection score via WhatsApp, Twitter, SMS",
                "Daily Digest Notifications" to "Morning safety summary with threat count and streak",
                "Onboarding Personalization" to "Choose your security priorities for a customized experience"
            ).forEach { (title, desc) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
