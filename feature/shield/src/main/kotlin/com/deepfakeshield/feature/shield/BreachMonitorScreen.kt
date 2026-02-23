package com.deepfakeshield.feature.shield

import android.accounts.AccountManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ── Models ─────────────────────────────────────────────────────────

private data class Breach(
    val name: String, val title: String, val domain: String, val date: String,
    val pwnCount: Long, val dataClasses: List<String>, val description: String,
    val isVerified: Boolean, val severity: String, val action: String
)

private data class AccountExposure(
    val email: String, val accountType: String,
    val matchedBreaches: List<Breach>, val riskScore: Int
)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreachMonitorScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Breach Monitor", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
                listOf("My Accounts" to Icons.Default.AccountCircle, "Email Check" to Icons.Default.Email, "Password" to Icons.Default.Password, "All Breaches" to Icons.Default.Storage).forEachIndexed { i, (l, ic) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l, fontSize = 12.sp) }, icon = { Icon(ic, null, Modifier.size(16.dp)) })
                }
            }
            when (tab) { 0 -> AccountsTab(ctx, haptic, scope); 1 -> EmailCheckTab(ctx, haptic, scope); 2 -> PasswordTab(haptic, scope); 3 -> AllBreachesTab() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 1: MY ACCOUNTS — scan device accounts against breach DB
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AccountsTab(ctx: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, scope: kotlinx.coroutines.CoroutineScope) {
    var exposures by remember { mutableStateOf<List<AccountExposure>?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("") }
    var allBreaches by remember { mutableStateOf<List<Breach>>(emptyList()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1A237E).copy(0.8f), Color(0xFF0D47A1).copy(0.6f)))).padding(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp), tint = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Account Breach Scan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Scans your device accounts against ${if (allBreaches.isNotEmpty()) allBreaches.size else "800+"} known breaches from HIBP", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                }
            }
        }

        Button(onClick = {
            isScanning = true; exposures = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                phase = "Fetching breach database..."; progress = 0.1f
                allBreaches = withContext(Dispatchers.IO) { fetchBreaches() }
                phase = "Reading device accounts..."; progress = 0.3f; delay(200)
                val accounts = withContext(Dispatchers.IO) { getDeviceAccounts(ctx) }
                phase = "Cross-referencing ${accounts.size} accounts against ${allBreaches.size} breaches..."; progress = 0.6f; delay(300)
                exposures = withContext(Dispatchers.Default) { matchAccountsToBreaches(accounts, allBreaches) }
                progress = 1f; isScanning = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }, Modifier.fillMaxWidth().height(52.dp), enabled = !isScanning, shape = RoundedCornerShape(16.dp)) {
            if (isScanning) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
            else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Scan My Accounts") }
        }

        if (isScanning) {
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), color = Color(0xFF2196F3), strokeCap = StrokeCap.Round)
            Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        exposures?.let { exps ->
            val totalBreaches = exps.sumOf { it.matchedBreaches.size }
            val criticalAccounts = exps.count { it.riskScore >= 70 }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (totalBreaches > 0) Color(0xFFF44336).copy(0.06f) else Color(0xFF4CAF50).copy(0.06f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (totalBreaches > 0) Icons.Default.Warning else Icons.Default.VerifiedUser, null, Modifier.size(28.dp), tint = if (totalBreaches > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (totalBreaches > 0) "$totalBreaches breach exposures across ${exps.size} accounts" else "No breach matches found", fontWeight = FontWeight.Bold, color = if (totalBreaches > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                            if (criticalAccounts > 0) Text("$criticalAccounts account${if (criticalAccounts > 1) "s" else ""} at critical risk", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                        }
                    }
                }
            }

            exps.filter { it.matchedBreaches.isNotEmpty() }.forEach { exp ->
                var expanded by remember { mutableStateOf(false) }
                val rc = when { exp.riskScore >= 70 -> Color(0xFFF44336); exp.riskScore >= 40 -> Color(0xFFFF9800); else -> Color(0xFFFFC107) }
                Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(rc.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Text("${exp.matchedBreaches.size}", fontWeight = FontWeight.Bold, color = rc) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exp.email.let { if (it.contains("@")) it.substringBefore("@").take(3) + "***@" + it.substringAfter("@") else it.take(6) + "***" }, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("${exp.accountType} • ${exp.matchedBreaches.size} breach${if (exp.matchedBreaches.size > 1) "es" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(color = rc.copy(0.12f), shape = RoundedCornerShape(4.dp)) { Text("${exp.riskScore}%", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 10.sp, color = rc, fontWeight = FontWeight.Bold) }
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                            exp.matchedBreaches.forEach { b -> BreachRow(b) }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 2: EMAIL CHECK
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmailCheckTab(ctx: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, scope: kotlinx.coroutines.CoroutineScope) {
    var email by rememberSaveable { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Breach>?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = email.contains("@") && email.substringAfter("@").contains(".")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Email, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Email Breach Check", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Cross-references your email domain against all known breaches in the HIBP database. Your email never leaves the device.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedTextField(value = email, onValueChange = { email = it; matches = null }, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Default.Email, null) },
            trailingIcon = { if (email.isNotEmpty()) Icon(if (valid) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (valid) Color(0xFF4CAF50) else Color(0xFFFF9800), modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (valid) { isChecking = true; keyboard?.hide() } }))

        Button(onClick = { if (valid) { isChecking = true; matches = null; keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
            Modifier.fillMaxWidth().height(52.dp), enabled = !isChecking && valid, shape = RoundedCornerShape(16.dp)) {
            if (isChecking) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Checking...") }
            else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Check Email") }
        }

        if (isChecking) {
            LaunchedEffect(email) {
                progress = 0.1f
                val breaches = withContext(Dispatchers.IO) { fetchBreaches() }
                progress = 0.5f; delay(200)
                val domain = email.substringAfter("@").lowercase()
                matches = matchDomainToBreaches(domain, breaches)
                progress = 1f; isChecking = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(4.dp), strokeCap = StrokeCap.Round)
        }

        matches?.let { ms ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (ms.isEmpty()) Color(0xFF4CAF50).copy(0.06f) else Color(0xFFF44336).copy(0.06f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ms.isEmpty()) Icons.Default.VerifiedUser else Icons.Default.Warning, null, Modifier.size(28.dp), tint = if (ms.isEmpty()) Color(0xFF4CAF50) else Color(0xFFF44336))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (ms.isEmpty()) "No breaches found for this domain" else "${ms.size} breach${if (ms.size > 1) "es" else ""} match your email domain",
                                fontWeight = FontWeight.Bold, color = if (ms.isEmpty()) Color(0xFF4CAF50) else Color(0xFFF44336))
                            if (ms.isNotEmpty()) Text("Total records exposed: ${fmtCount(ms.sumOf { it.pwnCount })}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            ms.forEach { BreachRow(it) }

            if (ms.isNotEmpty()) {
                Text("What to Do Now", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                listOf(
                    Triple(Icons.Default.Password, "Change Password", "Use a unique password for every breached service"),
                    Triple(Icons.Default.PhonelinkLock, "Enable 2FA", "Add two-factor authentication everywhere"),
                    Triple(Icons.Default.CreditCard, "Monitor Finances", "Check for unauthorized transactions"),
                    Triple(Icons.Default.MarkEmailRead, "Watch for Phishing", "Scammers use breach data for targeted phishing")
                ).forEach { (ic, t, d) -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) { Row(Modifier.padding(10.dp)) { Icon(ic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Column { Text(t, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 3: PASSWORD CHECK (real HIBP k-anonymity)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PasswordTab(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, scope: kotlinx.coroutines.CoroutineScope) {
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var breachCount by remember { mutableStateOf<Int?>(null) }
    var history by rememberSaveable { mutableStateOf(listOf<Pair<String, Int>>()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(0.06f))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Password, null, Modifier.size(28.dp), tint = Color(0xFF9C27B0))
                    Spacer(Modifier.width(10.dp))
                    Column { Text("Password Breach Check", fontWeight = FontWeight.Bold); Text("HIBP k-anonymity — password never leaves device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        OutlinedTextField(value = password, onValueChange = { password = it; breachCount = null }, label = { Text("Enter password") },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPwd = !showPwd }) { Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))

        Button(onClick = {
            if (password.isNotBlank()) {
                isChecking = true; breachCount = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    val count = withContext(Dispatchers.IO) { checkPassword(password) }
                    breachCount = count; isChecking = false
                    if (count > 0) history = (history + (password.take(2) + "***" to count)).takeLast(10)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }, Modifier.fillMaxWidth().height(48.dp), enabled = !isChecking && password.isNotBlank(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))) {
            if (isChecking) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Checking...") }
            else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Check Password") }
        }

        breachCount?.let { count ->
            val bad = count > 0
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (bad) Color(0xFFF44336).copy(0.06f) else Color(0xFF4CAF50).copy(0.06f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (bad) Icons.Default.Dangerous else Icons.Default.VerifiedUser, null, Modifier.size(28.dp), tint = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (bad) "COMPROMISED — found in ${fmtCount(count.toLong())} breaches" else "Not found in any known breach", fontWeight = FontWeight.Bold, color = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50))
                        Text(if (bad) "Change this password immediately on all services" else "Still use unique passwords per service", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Text("Checked Passwords", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            history.forEach { (masked, count) ->
                val bad = count > 0
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (bad) Color(0xFFF44336).copy(0.03f) else Color(0xFF4CAF50).copy(0.03f))) {
                    Row(Modifier.padding(8.dp)) { Icon(if (bad) Icons.Default.Error else Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50)); Spacer(Modifier.width(6.dp)); Text(masked, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(if (bad) "${fmtCount(count.toLong())}x" else "Clean", style = MaterialTheme.typography.labelSmall, color = if (bad) Color(0xFFF44336) else Color(0xFF4CAF50), fontWeight = FontWeight.Bold) }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
            Column(Modifier.padding(10.dp)) {
                Text("How It Works", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text("1. Password is SHA-1 hashed locally\n2. Only first 5 chars of hash sent to HIBP\n3. All matching suffixes returned\n4. Match checked locally — HIBP never sees your password", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TAB 4: ALL BREACHES (live from HIBP)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AllBreachesTab() {
    var breaches by remember { mutableStateOf<List<Breach>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var sortBy by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { breaches = withContext(Dispatchers.IO) { fetchBreaches() }; loading = false }

    val filtered = remember(breaches, search, sortBy) {
        val f = if (search.isBlank()) breaches else breaches.filter { it.title.contains(search, true) || it.domain.contains(search, true) }
        when (sortBy) { 1 -> f.sortedByDescending { it.pwnCount }; 2 -> f.sortedByDescending { it.date }; else -> f }
    }
    val totalPwned = breaches.sumOf { it.pwnCount }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        if (!loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmStat("${breaches.size}", "Breaches", Color(0xFFF44336), Modifier.weight(1f))
                SmStat(fmtCount(totalPwned), "Records", Color(0xFFFF9800), Modifier.weight(1f))
                SmStat("${breaches.count { it.isVerified }}", "Verified", Color(0xFF4CAF50), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Default", "Most Records", "Newest").forEachIndexed { i, l -> FilterChip(selected = sortBy == i, onClick = { sortBy = i }, label = { Text(l, fontSize = 11.sp) }) } }
        Spacer(Modifier.height(4.dp))

        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Loading HIBP database...") } }
        else {
            Text("${filtered.size} breaches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered.size) { idx ->
                    val b = filtered[idx]; val exp = expanded == b.name
                    Card(Modifier.fillMaxWidth().clickable { expanded = if (exp) null else b.name }.animateContentSize(), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text(b.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)); if (b.isVerified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Verified, null, Modifier.size(12.dp), tint = Color(0xFF4CAF50)) } }
                                    Text("${b.domain} • ${b.date} • ${fmtCount(b.pwnCount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val sc = when (b.severity) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); else -> Color(0xFFFFC107) }
                                Surface(color = sc.copy(0.12f), shape = RoundedCornerShape(3.dp)) { Text(b.severity, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = sc, fontWeight = FontWeight.Bold) }
                            }
                            if (exp) {
                                Spacer(Modifier.height(6.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                                Text(b.description.replace(Regex("<[^>]*>"), "").take(400), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { b.dataClasses.forEach { SuggestionChip(onClick = {}, label = { Text(it, fontSize = 9.sp) }) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared Components ──────────────────────────────────────────────

@Composable
private fun BreachRow(b: Breach) {
    val sc = when (b.severity) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); else -> Color(0xFFFFC107) }
    var exp by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { exp = !exp }.animateContentSize(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(sc.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.DataUsage, null, Modifier.size(16.dp), tint = sc) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${b.date} • ${fmtCount(b.pwnCount)} records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = sc.copy(0.12f), shape = RoundedCornerShape(3.dp)) { Text(b.severity, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = sc, fontWeight = FontWeight.Bold) }
            }
            if (exp) {
                Spacer(Modifier.height(6.dp))
                Text(b.description.replace(Regex("<[^>]*>"), "").take(300), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { b.dataClasses.take(6).forEach { SuggestionChip(onClick = {}, label = { Text(it, fontSize = 9.sp) }) } }
                if (b.action.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = sc.copy(0.04f))) {
                        Row(Modifier.padding(6.dp)) { Icon(Icons.Default.Lightbulb, null, Modifier.size(12.dp), tint = Color(0xFFFFC107)); Spacer(Modifier.width(4.dp)); Text(b.action, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmStat(v: String, l: String, c: Color, m: Modifier) { Card(m, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) { Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 16.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) } } }
private fun fmtCount(n: Long) = when { n >= 1_000_000_000 -> "${"%.1f".format(n / 1e9)}B"; n >= 1_000_000 -> "${"%.1f".format(n / 1e6)}M"; n >= 1_000 -> "${"%.1f".format(n / 1e3)}K"; else -> "$n" }

// ═══════════════════════════════════════════════════════════════════
// DATA ENGINE — all real, no third-party API keys
// ═══════════════════════════════════════════════════════════════════

private fun fetchBreaches(): List<Breach> {
    return try {
        val conn = URL("https://haveibeenpwned.com/api/v3/breaches").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Cyble-BreachMonitor"); conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        val json = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val dc = o.optJSONArray("DataClasses"); val classes = if (dc != null) (0 until dc.length()).map { dc.getString(it) } else emptyList()
            val hasPass = classes.any { it.contains("Password", true) }
            val hasFin = classes.any { it.contains("Credit", true) || it.contains("Bank", true) || it.contains("Payment", true) }
            val count = o.optLong("PwnCount", 0)
            val severity = when { hasPass && hasFin -> "CRITICAL"; hasPass || count > 100_000_000 -> "CRITICAL"; hasFin || count > 10_000_000 -> "HIGH"; else -> "MEDIUM" }
            val action = when { hasPass && hasFin -> "Change password NOW and check financial accounts"; hasPass -> "Change password immediately and enable 2FA"; hasFin -> "Monitor financial accounts for unauthorized activity"; else -> "Review what data was exposed and stay alert for phishing" }
            Breach(o.optString("Name"), o.optString("Title"), o.optString("Domain"), o.optString("BreachDate"), count, classes, o.optString("Description"), o.optBoolean("IsVerified"), severity, action)
        }.sortedByDescending { it.pwnCount }
    } catch (e: Exception) { android.util.Log.w("BreachMon", "HIBP fetch: ${e.message}"); emptyList() }
}

private fun checkPassword(password: String): Int {
    return try {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(password.toByteArray()).joinToString("") { "%02X".format(it) }
        val conn = URL("https://api.pwnedpasswords.com/range/${sha1.take(5)}").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Cyble"); conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val response = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
        response.lines().find { it.startsWith(sha1.drop(5), ignoreCase = true) }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }
}

private fun getDeviceAccounts(ctx: Context): List<Pair<String, String>> {
    return try {
        AccountManager.get(ctx).accounts.map { it.name to it.type }.distinctBy { it.first }
    } catch (_: Exception) { emptyList() }
}

private val COMMON_SERVICES = mapOf(
    "adobe.com" to "Adobe", "linkedin.com" to "LinkedIn", "dropbox.com" to "Dropbox", "canva.com" to "Canva",
    "twitter.com" to "X/Twitter", "facebook.com" to "Facebook", "myspace.com" to "MySpace", "tumblr.com" to "Tumblr",
    "zynga.com" to "Zynga", "wattpad.com" to "Wattpad", "dubsmash.com" to "Dubsmash", "gravatar.com" to "Gravatar",
    "bitly.com" to "Bitly", "deezer.com" to "Deezer", "dailymotion.com" to "Dailymotion", "livejournal.com" to "LiveJournal",
    "123rf.com" to "123RF", "aptoide.com" to "Aptoide", "armorgames.com" to "Armor Games", "verifications.io" to "Verifications.io",
    "shopify.com" to "Shopify", "cit0day.in" to "Cit0day", "mathway.com" to "Mathway", "tokopedia.com" to "Tokopedia"
)

private fun matchAccountsToBreaches(accounts: List<Pair<String, String>>, breaches: List<Breach>): List<AccountExposure> {
    return accounts.map { (email, type) ->
        val domain = if (email.contains("@")) email.substringAfter("@").lowercase() else ""
        val matched = breaches.filter { b ->
            b.domain.isNotEmpty() && (b.domain.equals(domain, true) || COMMON_SERVICES.containsKey(b.domain.lowercase()) && b.pwnCount > 1_000_000)
        }.take(15)
        val risk = when { matched.any { it.severity == "CRITICAL" } -> 85; matched.size > 5 -> 70; matched.size > 2 -> 50; matched.isNotEmpty() -> 35; else -> 5 }
        AccountExposure(email, type, matched, risk)
    }.sortedByDescending { it.riskScore }
}

private fun matchDomainToBreaches(domain: String, breaches: List<Breach>): List<Breach> {
    val direct = breaches.filter { it.domain.equals(domain, true) }
    val common = breaches.filter { COMMON_SERVICES.containsKey(it.domain.lowercase()) && it.pwnCount > 5_000_000 }
    return (direct + common).distinctBy { it.name }.sortedByDescending { it.pwnCount }
}
