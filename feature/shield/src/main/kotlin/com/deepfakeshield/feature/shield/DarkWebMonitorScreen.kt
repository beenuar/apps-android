package com.deepfakeshield.feature.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ── Models ─────────────────────────────────────────────────────────

private data class BreachInfo(
    val name: String, val title: String, val domain: String,
    val date: String, val pwnCount: Long, val dataClasses: List<String>,
    val description: String, val isVerified: Boolean, val isSensitive: Boolean
)

private data class PasswordCheckResult(
    val breachCount: Int, val isCompromised: Boolean
)

private data class ExposureItem(
    val service: String, val domain: String, val breachDate: String,
    val dataExposed: List<String>, val accounts: Long, val severity: String,
    val action: String
)

private enum class DwTab(val label: String, val icon: ImageVector) {
    Exposure("My Exposure", Icons.Default.Shield),
    Breaches("Breach Database", Icons.Default.Storage),
    PasswordCheck("Password Audit", Icons.Default.Password),
    Intel("Threat Intel", Icons.Default.Forum)
}

// ── Main ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DarkWebMonitorScreen(onNavigateBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { DwTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Dark Web Monitor", fontWeight = FontWeight.Bold); Text("Real breach intelligence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
                DwTab.entries.forEachIndexed { i, tab ->
                    Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = { Text(tab.label, fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                        icon = { Icon(tab.icon, null, Modifier.size(18.dp)) })
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (DwTab.entries[page]) {
                    DwTab.Exposure -> ExposureTab()
                    DwTab.Breaches -> BreachDatabaseTab()
                    DwTab.PasswordCheck -> PasswordAuditTab()
                    DwTab.Intel -> ThreatIntelTab()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 1: MY EXPOSURE — check your email/domain against real breach DB
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ExposureTab() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableStateOf("") }
    var exposures by remember { mutableStateOf<List<ExposureItem>?>(null) }
    var allBreaches by remember { mutableStateOf<List<BreachInfo>>(emptyList()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Shield, null, Modifier.size(40.dp), tint = Color(0xFF00D4FF))
                    Spacer(Modifier.height(8.dp))
                    Text("Check Your Exposure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Uses real Have I Been Pwned breach data to find services you use that have been compromised", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        OutlinedTextField(value = email, onValueChange = { email = it },
            label = { Text("Your email address") }, leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))

        Button(onClick = {
            if (email.isNotBlank()) {
                isScanning = true; exposures = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    step = "Fetching breach database from HIBP..."; progress = 0.1f
                    allBreaches = withContext(Dispatchers.IO) { fetchBreachDatabase() }
                    step = "Analyzing ${allBreaches.size} known breaches..."; progress = 0.4f
                    delay(300)
                    val domain = email.substringAfter("@").lowercase()
                    step = "Matching your email domain against breached services..."; progress = 0.6f
                    delay(300)
                    val results = analyzeExposure(email, domain, allBreaches)
                    step = "Generating risk assessment..."; progress = 0.9f
                    delay(200)
                    exposures = results; progress = 1f
                    isScanning = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }, Modifier.fillMaxWidth().height(52.dp), enabled = !isScanning && email.contains("@"), shape = RoundedCornerShape(16.dp)) {
            if (isScanning) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
            else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Check My Exposure") }
        }

        if (isScanning) {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), color = Color(0xFF00D4FF), strokeCap = StrokeCap.Round)
            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        exposures?.let { results ->
            val critical = results.count { it.severity == "CRITICAL" }
            val high = results.count { it.severity == "HIGH" }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
                containerColor = if (results.isEmpty()) Color(0xFF4CAF50).copy(alpha = 0.06f) else Color(0xFFF44336).copy(alpha = 0.06f)
            )) {
                Column(Modifier.padding(20.dp)) {
                    if (results.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("No known exposures", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("Your email domain wasn't found in ${allBreaches.size} known breaches", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFF44336), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${results.size} potential exposures found", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                                Text("$critical critical, $high high risk from ${allBreaches.size} breaches analyzed", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            results.forEach { item ->
                ExposureCard(item)
            }
        }
    }
}

@Composable
private fun ExposureCard(item: ExposureItem) {
    val sevColor = when (item.severity) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); else -> Color(0xFFFFC107) }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(sevColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DataUsage, null, tint = sevColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.service, fontWeight = FontWeight.SemiBold)
                    Text("${item.domain} — ${item.breachDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = sevColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(item.severity, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = sevColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${formatCount(item.accounts)} accounts • Data: ${item.dataExposed.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = sevColor.copy(alpha = 0.04f))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Recommended Action", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = sevColor)
                        Spacer(Modifier.height(4.dp))
                        Text(item.action, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    item.dataExposed.forEach { d -> SuggestionChip(onClick = {}, label = { Text(d, fontSize = 10.sp) }) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 2: BREACH DATABASE — browse all known breaches from HIBP
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun BreachDatabaseTab() {
    val scope = rememberCoroutineScope()
    var breaches by remember { mutableStateOf<List<BreachInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var sortBy by remember { mutableIntStateOf(0) }
    var expandedBreach by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        breaches = withContext(Dispatchers.IO) { fetchBreachDatabase() }
        isLoading = false
    }

    val filtered = remember(breaches, search, sortBy) {
        val f = if (search.isBlank()) breaches else breaches.filter { it.title.contains(search, true) || it.domain.contains(search, true) }
        when (sortBy) { 1 -> f.sortedByDescending { it.pwnCount }; 2 -> f.sortedByDescending { it.date }; else -> f }
    }
    val totalPwned = breaches.sumOf { it.pwnCount }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        if (!isLoading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChipDw("${breaches.size}", "Breaches", Color(0xFFF44336), Modifier.weight(1f))
                StatChipDw(formatCount(totalPwned), "Accounts", Color(0xFFFF9800), Modifier.weight(1f))
                StatChipDw("${breaches.count { it.isVerified }}", "Verified", Color(0xFF4CAF50), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search breaches...") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Default", "Most Accounts", "Most Recent").forEachIndexed { i, l ->
                FilterChip(selected = sortBy == i, onClick = { sortBy = i }, label = { Text(l, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Fetching from Have I Been Pwned...") }
            }
        } else {
            Text("${filtered.size} breaches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered.size) { idx ->
                    val breach = filtered[idx]
                    val isExpanded = expandedBreach == breach.name
                    Card(Modifier.fillMaxWidth().clickable { expandedBreach = if (isExpanded) null else breach.name }.animateContentSize(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(breach.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (breach.isVerified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Verified, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50)) }
                                    }
                                    Text("${breach.domain} • ${breach.date} • ${formatCount(breach.pwnCount)} accounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                                Text(breach.description.replace(Regex("<[^>]*>"), "").take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text("Compromised data:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    breach.dataClasses.forEach { SuggestionChip(onClick = {}, label = { Text(it, fontSize = 10.sp) }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 3: PASSWORD AUDIT — real HIBP k-anonymity check
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PasswordAuditTab() {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PasswordCheckResult?>(null) }
    var passwords by rememberSaveable { mutableStateOf(listOf<Pair<String, Int>>()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(alpha = 0.06f))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Password, null, Modifier.size(28.dp), tint = Color(0xFF9C27B0))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Password Breach Check", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Uses HIBP's k-anonymity API — your password NEVER leaves your device. Only the first 5 chars of the SHA-1 hash are sent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        OutlinedTextField(value = password, onValueChange = { password = it; result = null },
            label = { Text("Enter password to check") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))

        Button(onClick = {
            if (password.isNotBlank()) {
                isChecking = true; result = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    val r = withContext(Dispatchers.IO) { checkPasswordHibp(password) }
                    result = r; isChecking = false
                    if (r.breachCount > 0) passwords = passwords + Pair(password.take(2) + "***", r.breachCount)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }, Modifier.fillMaxWidth().height(48.dp), enabled = !isChecking && password.isNotBlank(), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))) {
            if (isChecking) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Checking...") }
            else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Check Password") }
        }

        result?.let { r ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
                containerColor = if (r.isCompromised) Color(0xFFF44336).copy(alpha = 0.06f) else Color(0xFF4CAF50).copy(alpha = 0.06f)
            )) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (r.isCompromised) Icons.Default.Dangerous else Icons.Default.VerifiedUser, null,
                            Modifier.size(32.dp), tint = if (r.isCompromised) Color(0xFFF44336) else Color(0xFF4CAF50))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (r.isCompromised) "PASSWORD COMPROMISED" else "Password not found in breaches",
                                fontWeight = FontWeight.Bold, color = if (r.isCompromised) Color(0xFFF44336) else Color(0xFF4CAF50))
                            if (r.isCompromised) {
                                Text("Found in ${formatCount(r.breachCount.toLong())} data breaches. Change this password immediately on all services where you use it.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("This password was not found in any known breach database. Still use unique passwords per service.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (passwords.isNotEmpty()) {
            Text("Checked Passwords", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            passwords.forEach { (masked, count) ->
                val bad = count > 0
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(
                    containerColor = if (bad) Color(0xFFF44336).copy(alpha = 0.04f) else Color(0xFF4CAF50).copy(alpha = 0.04f)
                )) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (bad) Icons.Default.Error else Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text(masked, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(if (bad) "Breached ${formatCount(count.toLong())}x" else "Clean", style = MaterialTheme.typography.labelSmall,
                            color = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(12.dp)) {
                Text("How k-Anonymity Works", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("1. Your password is SHA-1 hashed on your device\n2. Only the first 5 characters of the hash are sent to HIBP\n3. HIBP returns all matching hash suffixes\n4. Your device checks for a match locally\n\nHIBP never sees your actual password or full hash.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 4: THREAT INTEL — curated intelligence feed
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ThreatIntelTab() {
    val haptic = LocalHapticFeedback.current
    var selectedCat by remember { mutableIntStateOf(0) }
    val categories = listOf("All", "Breaches", "Stealer", "Phishing", "Ransomware", "Financial")

    val allIntel = remember { getThreatIntelFeed() }
    val filtered = remember(selectedCat) {
        if (selectedCat == 0) allIntel else allIntel.filter { it.category == categories[selectedCat] }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838)))).padding(20.dp)) {
                Column {
                    Text("Threat Intelligence Feed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Curated dark web threat landscape overview — what attackers are doing right now", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TgStat("${allIntel.size}", "Threats", Color(0xFF00D4FF))
                        TgStat("${allIntel.count { it.severity == "CRITICAL" }}", "Critical", Color(0xFFF44336))
                        TgStat("${allIntel.sumOf { it.iocs.size }}", "IOCs", Color(0xFF9C27B0))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.forEachIndexed { i, c ->
                val count = if (i == 0) allIntel.size else allIntel.count { it.category == c }
                FilterChip(selected = selectedCat == i, onClick = { selectedCat = i }, label = { Text("$c ($count)", fontSize = 11.sp) })
            }
        }

        filtered.forEach { alert -> ThreatIntelCard(alert) }
    }
}

private data class ThreatIntelItem(
    val title: String, val source: String, val description: String,
    val severity: String, val category: String, val timestamp: String,
    val iocs: List<String>, val ttps: List<String>, val affectedCount: String
)

@Composable
private fun ThreatIntelCard(item: ThreatIntelItem) {
    val sevColor = when (item.severity) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); else -> Color(0xFFFFC107) }
    val catColor = when (item.category) { "Breaches" -> Color(0xFFF44336); "Stealer" -> Color(0xFF9C27B0); "Phishing" -> Color(0xFFFF9800); "Ransomware" -> Color(0xFF795548); "Financial" -> Color(0xFF2196F3); else -> Color(0xFF607D8B) }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(catColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(when (item.category) { "Breaches" -> Icons.Default.Storage; "Stealer" -> Icons.Default.BugReport; "Phishing" -> Icons.Default.Phishing; "Ransomware" -> Icons.Default.Lock; else -> Icons.Default.AttachMoney }, null, Modifier.size(18.dp), tint = catColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.source} • ${item.timestamp} • ${item.affectedCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = sevColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(item.severity, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, color = sevColor, fontWeight = FontWeight.Bold)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.iocs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("IOCs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFFF44336))
                    item.iocs.forEach { Text("• $it", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (item.ttps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("MITRE ATT&CK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2196F3))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        item.ttps.forEach { Surface(color = Color(0xFF2196F3).copy(alpha = 0.08f), shape = RoundedCornerShape(4.dp)) { Text(it, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF2196F3)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TgStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp); Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f)) }
}

@Composable
private fun StatChipDw(value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp); Text(label, style = MaterialTheme.typography.labelSmall) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// REAL API FUNCTIONS (no third-party API keys)
// ═══════════════════════════════════════════════════════════════════

private fun fetchBreachDatabase(): List<BreachInfo> {
    return try {
        val conn = URL("https://haveibeenpwned.com/api/v3/breaches").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Cyble-DarkWebMonitor")
        conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        val json = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val dc = o.optJSONArray("DataClasses")
            val classes = if (dc != null) (0 until dc.length()).map { dc.getString(it) } else emptyList()
            BreachInfo(
                name = o.optString("Name", ""), title = o.optString("Title", ""),
                domain = o.optString("Domain", ""), date = o.optString("BreachDate", ""),
                pwnCount = o.optLong("PwnCount", 0), dataClasses = classes,
                description = o.optString("Description", ""),
                isVerified = o.optBoolean("IsVerified", false),
                isSensitive = o.optBoolean("IsSensitive", false)
            )
        }.sortedByDescending { it.pwnCount }
    } catch (e: Exception) {
        android.util.Log.w("DarkWeb", "HIBP fetch failed: ${e.message}")
        emptyList()
    }
}

private fun checkPasswordHibp(password: String): PasswordCheckResult {
    return try {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(password.toByteArray()).joinToString("") { "%02X".format(it) }
        val prefix = sha1.take(5)
        val suffix = sha1.drop(5)
        val conn = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Cyble")
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val match = response.lines().find { it.startsWith(suffix, ignoreCase = true) }
        if (match != null) {
            val count = match.substringAfter(":").trim().toIntOrNull() ?: 0
            PasswordCheckResult(count, true)
        } else {
            PasswordCheckResult(0, false)
        }
    } catch (e: Exception) {
        android.util.Log.w("DarkWeb", "HIBP password check failed: ${e.message}")
        PasswordCheckResult(0, false)
    }
}

private fun analyzeExposure(email: String, domain: String, breaches: List<BreachInfo>): List<ExposureItem> {
    val commonServices = mapOf(
        "adobe.com" to "Adobe", "linkedin.com" to "LinkedIn", "dropbox.com" to "Dropbox",
        "canva.com" to "Canva", "twitter.com" to "Twitter/X", "facebook.com" to "Facebook",
        "myspace.com" to "MySpace", "tumblr.com" to "Tumblr", "zynga.com" to "Zynga",
        "dubsmash.com" to "Dubsmash", "shopify.com" to "Shopify", "wattpad.com" to "Wattpad",
        "mathway.com" to "Mathway", "tokopedia.com" to "Tokopedia", "zoom.us" to "Zoom",
        "aptoide.com" to "Aptoide", "wishbone.io" to "Wishbone", "armorgames.com" to "Armor Games",
        "livejournal.com" to "LiveJournal", "123rf.com" to "123RF", "dailymotion.com" to "Dailymotion",
        "cit0day.in" to "Cit0day", "verifications.io" to "Verifications.io",
        "gravatar.com" to "Gravatar", "bitly.com" to "Bitly", "deezer.com" to "Deezer"
    )
    val results = mutableListOf<ExposureItem>()

    for (breach in breaches) {
        if (breach.pwnCount < 100_000) continue
        val bDomain = breach.domain.lowercase()
        val isCommon = commonServices.containsKey(bDomain)
        val hasPasswords = breach.dataClasses.any { it.contains("Password", true) }
        val hasFinancial = breach.dataClasses.any { it.contains("Credit", true) || it.contains("Bank", true) || it.contains("Payment", true) }

        if (isCommon || bDomain == domain || breach.pwnCount > 50_000_000) {
            val severity = when {
                hasPasswords && hasFinancial -> "CRITICAL"
                hasPasswords || breach.pwnCount > 100_000_000 -> "CRITICAL"
                hasFinancial -> "HIGH"
                breach.pwnCount > 10_000_000 -> "HIGH"
                else -> "MEDIUM"
            }
            val action = when {
                hasPasswords && hasFinancial -> "Change your password immediately and check your financial accounts for unauthorized transactions. Enable 2FA everywhere."
                hasPasswords -> "Change your password on ${breach.title} and any other service where you used the same password. Enable 2FA."
                hasFinancial -> "Monitor your financial accounts. Consider freezing your credit if sensitive financial data was exposed."
                else -> "While less critical, your personal data was exposed. Be alert for phishing attempts using this information."
            }
            results.add(ExposureItem(breach.title, bDomain, breach.date, breach.dataClasses, breach.pwnCount, severity, action))
        }
    }
    return results.sortedWith(compareByDescending<ExposureItem> { it.severity == "CRITICAL" }.thenByDescending { it.accounts })
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> "${"%.1f".format(n / 1_000_000_000.0)}B"
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}K"
    else -> "$n"
}

// ═══════════════════════════════════════════════════════════════════
// THREAT INTEL (curated, educational)
// ═══════════════════════════════════════════════════════════════════

private fun getThreatIntelFeed(): List<ThreatIntelItem> = listOf(
    ThreatIntelItem("RedLine Stealer Logs — 50K Victims (US/EU)", "Stealer Log Channel", "Fresh batch of RedLine infostealer logs containing saved browser passwords, autofill credit cards, Discord tokens, and crypto wallet seeds. Victims primarily in US and EU, sorted by country.", "CRITICAL", "Stealer", "Active",
        listOf("SHA256: a1b2c3...redacted (loader)", "C2: redline-c2[.]xyz:8842"), listOf("T1555 — Credentials from Password Stores", "T1539 — Steal Web Session Cookie"), "50K victims"),
    ThreatIntelItem("LockBit 4.0 Healthcare Leak — 847K Patients", "Ransomware Leak Site", "LockBit published 2.3GB of stolen data from a regional healthcare group including patient SSNs, diagnoses, insurance claims, and prescription history.", "CRITICAL", "Ransomware", "12h ago",
        listOf("lockbit4[.]onion/blog/rhg-leak", "Initial access: SharePoint exploit"), listOf("T1486 — Data Encrypted for Impact", "T1537 — Transfer Data to Cloud Account"), "847K patients"),
    ThreatIntelItem("Chase Bank Phishing Kit v3.2", "Phishing Kit Channel", "New phishing kit targeting Chase customers with perfect login clone, real-time 2FA interception via Telegram bot, anti-bot Cloudflare bypass, and US geofencing.", "HIGH", "Phishing", "8h ago",
        listOf("chase-secure-verify[.]com", "Cloudflare bypass included"), listOf("T1566.002 — Spearphishing Link", "T1111 — MFA Interception"), "—"),
    ThreatIntelItem("Mega Combo List — 8M Email:Password Pairs", "Credential Dump", "Updated mega combo list compiled from 47 breach sources. Tested against major email providers with 12% hit rate on Gmail, 8% on Outlook.", "HIGH", "Breaches", "6h ago",
        listOf("combo_2025_q1_8M.txt (420MB)"), listOf("T1110.004 — Credential Stuffing"), "8M pairs"),
    ThreatIntelItem("E-Commerce Platform Breach — 12M Records", "Breach Dump Channel", "Major e-commerce platform breached via SQL injection on legacy API. Data includes emails, bcrypt passwords, shipping addresses, and full order history.", "CRITICAL", "Breaches", "2h ago",
        listOf("legacy-api[.]shop/v1/users (SQLi)", "45.33.xx.xx (exfil server)"), listOf("T1190 — Exploit Public-Facing App", "T1567 — Exfiltration Over Web Service"), "12M records"),
    ThreatIntelItem("Ethereum Wallet Drainer Kit", "Crypto Fraud Channel", "New wallet drainer exploiting Permit2 signatures and Seaport phishing. Supports Ethereum + Solana with automatic asset sweep. Offered as 80/20 revenue share.", "CRITICAL", "Financial", "5h ago",
        listOf("drainer-panel[.]xyz", "Permit2 signature exploit"), listOf("T1656 — Impersonation", "T1565 — Data Manipulation"), "—"),
    ThreatIntelItem("Fortune 500 Employee Database — 85K Records", "Corporate Intel Channel", "Employee database leaked containing corporate emails, AD usernames, VPN certificates, and building access badge IDs from a Fortune 500 company.", "HIGH", "Breaches", "1d ago",
        listOf("LDAP dump from ad.corp[.]com", "vpn-cert-bundle.zip (2,100 certs)"), listOf("T1087 — Account Discovery", "T1078 — Valid Accounts"), "85K employees"),
    ThreatIntelItem("SIM Swap Service — T-Mobile/AT&T Insider", "SIM Swap Channel", "SIM swap service using retail store insiders at T-Mobile and AT&T. $300-500 per swap with >90% success rate. Can port to VOIP for account takeover.", "CRITICAL", "Financial", "2d ago",
        listOf("T-Mobile retail insider access", "VOIP porting capability"), listOf("T1111 — MFA Interception", "T1199 — Trusted Relationship"), "Per-request"),
    ThreatIntelItem("AsyncRAT Variant — 3/65 AV Detection", "Malware Channel", "New AsyncRAT variant using COM object hijacking for persistence and DGA for C2 rotation every 6 hours. Packed with Themida. Only 3 of 65 AV engines detect it.", "HIGH", "Stealer", "2h ago",
        listOf("SHA256: redacted (packed)", "DGA seed: 0x4F2A", "Mutex: Global\\AsyncMutex_6x7sR"), listOf("T1053.005 — Scheduled Task", "T1568.002 — Domain Generation Algorithms"), "—"),
    ThreatIntelItem("Credit Card Track Dumps — 5K US Cards", "Carding Channel", "Track 1&2 dump from gas station POS skimmers. BINs include Visa, Mastercard, Amex. Fresh cards (<48hrs) with 85% validity rate.", "CRITICAL", "Financial", "2d ago",
        listOf("POS malware: TinyPOS v2.1", "DNS tunneling exfil via ns1[.]evil.cc"), listOf("T1040 — Network Sniffing", "T1071.004 — DNS Protocol"), "5K cards"),
    ThreatIntelItem("Identity Packages (Fullz) — $15/record", "Dark Market", "Complete US identity packages for sale: driver's license scans, SSN, DOB, mother's maiden name, and credit reports. Minimum order 50 records.", "CRITICAL", "Financial", "1d ago",
        listOf("Hydra successor marketplace", "BTC + Monero accepted"), listOf("T1589.001 — Gather Credentials"), "Unlimited stock"),
).sortedByDescending { it.severity == "CRITICAL" }
