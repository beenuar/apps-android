package com.deepfakeshield.feature.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisposableEmailScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var generatedEmails by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedDomain by remember { mutableIntStateOf(0) }
    var customPrefix by remember { mutableStateOf("") }
    val domains = listOf("shield-mail.org", "tempbox.net", "nospy.email", "privaterelay.app", "quickdrop.io")

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Disposable Email", fontWeight = FontWeight.Bold); Text("${generatedEmails.size} alias${if (generatedEmails.size != 1) "es" else ""} created", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AlternateEmail, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Email Alias Generator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Generate disposable email aliases for sign-ups so your real email stays private. If an alias gets spammed, just delete it.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Domain selector
            Text("Select Domain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                domains.forEachIndexed { i, d -> FilterChip(selected = selectedDomain == i, onClick = { selectedDomain = i }, label = { Text(d, fontSize = 11.sp) }) }
            }

            // Custom prefix
            OutlinedTextField(value = customPrefix, onValueChange = { customPrefix = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' }.take(30) },
                label = { Text("Custom prefix (optional)") }, placeholder = { Text("e.g., shopping, newsletter") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                supportingText = { Text("Leave blank for a random alias") })

            // Generate buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val prefix = if (customPrefix.isNotBlank()) customPrefix else generateRandomPrefix()
                    val email = "$prefix@${domains[selectedDomain]}"
                    val purpose = if (customPrefix.isNotBlank()) customPrefix.replaceFirstChar { it.uppercase() } else "General"
                    generatedEmails = listOf(email to purpose) + generatedEmails
                    customPrefix = ""
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Generate Alias")
                }
                OutlinedButton(onClick = {
                    repeat(5) {
                        val prefix = generateRandomPrefix()
                        val domain = domains[(Math.random() * domains.size).toInt()]
                        generatedEmails = listOf("$prefix@$domain" to "Batch") + generatedEmails
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Generate 5") }
            }

            // Generated emails
            if (generatedEmails.isNotEmpty()) {
                Text("Your Aliases", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                generatedEmails.forEachIndexed { idx, (email, purpose) ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AlternateEmail, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(email, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(purpose, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("email", email))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                            IconButton(onClick = { generatedEmails = generatedEmails.toMutableList().apply { removeAt(idx) } }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }

            // When to use
            Spacer(Modifier.height(4.dp))
            Text("When to Use Aliases", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            listOf(
                "Free trial sign-ups" to "Avoid spam after trial ends",
                "One-time purchases" to "Retailers sell your email to advertisers",
                "Newsletter subscriptions" to "Use a dedicated alias per newsletter",
                "Social media accounts" to "Limit exposure if the platform is breached",
                "Contests and giveaways" to "Almost always result in spam",
                "Wi-Fi captive portals" to "Airport/hotel Wi-Fi often requires email"
            ).forEach { (title, desc) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50)); Spacer(Modifier.width(8.dp))
                        Column { Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall); Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private fun generateRandomPrefix(): String {
    val adjectives = listOf("swift", "silent", "hidden", "shadow", "rapid", "stealth", "ghost", "cipher", "proxy", "shield", "vault", "iron", "dark", "pixel", "quantum")
    val nouns = listOf("fox", "owl", "wolf", "hawk", "bear", "raven", "lynx", "viper", "tiger", "crane", "eagle", "cobra", "falcon", "panther", "phoenix")
    val num = (10..99).random()
    return "${adjectives.random()}.${nouns.random()}$num"
}
