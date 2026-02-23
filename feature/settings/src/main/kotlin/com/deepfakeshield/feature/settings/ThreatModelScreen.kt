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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatModelScreen(onNavigateBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Threat Model & Privacy", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section("What We Protect Against", listOf(
                "Deepfake video and image manipulation detection",
                "Scam SMS, phishing emails, and social engineering",
                "Malware in installed apps and APK files",
                "Data breaches and credential exposure",
                "Dangerous app permissions and tracker SDKs",
                "Insecure Wi-Fi networks and DNS leaks",
                "SIM swap attacks and identity theft",
                "Typosquat and impersonation domains"
            ))
            Section("What We DO NOT Do", listOf(
                "We never record your screen, keystrokes, or clipboard in the background",
                "We never intercept other apps' network traffic",
                "We never transmit your personal data to our servers",
                "We never weaken your device's security (no root required)",
                "We never access your messages without explicit permission",
                "We never sell your data to third parties"
            ))
            Section("Data Storage & Privacy", listOf(
                "All analysis runs on your device — nothing leaves your phone",
                "Passwords are hashed (SHA-1/SHA-256) before any network check; plaintext never transmitted",
                "SSIDs, URLs, and phone numbers are hashed in logs — never stored in plaintext",
                "The threat database is stored locally with no PII",
                "You can export or delete all your data at any time in Settings > Privacy"
            ))
            Section("Limitations (Honest Disclosure)", listOf(
                "Deepfake detection uses heuristics + ML — not 100% accurate. False positives and negatives occur.",
                "Antivirus uses hash matching + heuristics — cannot detect zero-day malware not in the database.",
                "Dark web monitoring is illustrative — real monitoring requires commercial threat intelligence APIs.",
                "Tor Privacy Mode routes only this app's traffic, not your entire device.",
                "Wi-Fi analysis uses OS-provided signals — we cannot detect all attacks (e.g., we don't sniff packets).",
                "We cannot protect you from sharing your own credentials with a scammer."
            ))
            Section("Permissions Rationale", listOf(
                "CAMERA — Photo forensics analysis (user-initiated only)",
                "SMS — Scan incoming messages for scam patterns (opt-in)",
                "PHONE/CALL_LOG — Call screening and scam number detection",
                "LOCATION — Wi-Fi network scanning requires location permission on Android 6+",
                "STORAGE — Antivirus file scanning (user-initiated)",
                "NOTIFICATIONS — Background protection status and threat alerts",
                "OVERLAY — Floating bubble for real-time protection status"
            ))
        }
    }
}

@Composable
private fun Section(title: String, items: List<String>) {
    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Row { Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)); Text(item, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
