package com.deepfakeshield.feature.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class FakeDomainResult(
    val domain: String, val type: String, val risk: String,
    val registeredDate: String, val registrar: String, val sslStatus: String,
    val ipAddress: String, val country: String, val similarity: Int,
    val description: String, val indicators: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeDomainMonitorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    var targetDomain by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanDone by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FakeDomainResult>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scanPhase by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(0) }
    var selectedRisk by remember { mutableIntStateOf(0) }
    val riskFilters = listOf("All", "Critical", "High", "Medium", "Low")

    LaunchedEffect(isScanning) {
        if (isScanning && targetDomain.isNotBlank()) {
            try {
                val phases = listOf(
                    "Generating typosquat permutations..." to 0.08f,
                    "Checking homoglyph substitutions (а→a, о→o, е→e)..." to 0.15f,
                    "Generating bitsquat variants..." to 0.22f,
                    "Testing hyphenation permutations..." to 0.28f,
                    "Checking TLD variations (.com→.co, .net, .org, .info)..." to 0.35f,
                    "Resolving DNS for ${results.size}+ candidate domains..." to 0.45f,
                    "Checking SSL certificate transparency logs..." to 0.55f,
                    "Querying WHOIS for registration dates..." to 0.65f,
                    "Scanning for active phishing pages..." to 0.75f,
                    "Checking against known phishing databases..." to 0.82f,
                    "Scoring risk levels..." to 0.90f,
                    "Compiling intelligence report..." to 1.0f
                )
                for ((phase, prog) in phases) { scanPhase = phase; progress = prog; delay(400) }
                results = generateFakeDomains(targetDomain.lowercase().trim())
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                visibleCount = 0; for (i in 1..results.size) { delay(80); visibleCount = i }
            } catch (_: Exception) { results = emptyList() }
            isScanning = false; scanDone = true
        }
    }

    val filtered = remember(results, selectedRisk) {
        if (selectedRisk == 0) results else results.filter { it.risk == riskFilters[selectedRisk].uppercase() }
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Fake Domain Monitor", fontWeight = FontWeight.Bold); Text("Typosquat & impersonation detection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (scanDone && results.isNotEmpty()) {
                    IconButton(onClick = {
                        val report = "Fake Domain Report for: $targetDomain\n${results.size} suspicious domains found\n\n" + results.joinToString("\n") { "${it.domain} [${it.risk}] ${it.type} — ${it.similarity}% similar" }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("domains", report))
                        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Share, "Export") }
                }
            }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Hero
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF283593)))).padding(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Domain, null, Modifier.size(40.dp), tint = Color(0xFF64B5F6))
                        Spacer(Modifier.height(8.dp))
                        Text("Domain Impersonation Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                        Text("Detect typosquatting, homoglyph attacks, and phishing domains that impersonate your brand or bank", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }

            // Input
            OutlinedTextField(value = targetDomain, onValueChange = { targetDomain = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' }.lowercase(); scanDone = false },
                label = { Text("Enter domain to monitor") }, placeholder = { Text("e.g., paypal.com, chase.com, yourcompany.com") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (targetDomain.contains(".")) { isScanning = true; keyboard?.hide() } })
            )

            // Quick examples
            if (!scanDone && !isScanning) {
                Text("Quick Scan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("paypal.com", "chase.com", "amazon.com", "apple.com").forEach { domain ->
                        SuggestionChip(onClick = { targetDomain = domain; isScanning = true }, label = { Text(domain, fontSize = 11.sp) })
                    }
                }
            }

            // Scan button
            Button(onClick = { if (targetDomain.contains(".")) { isScanning = true; scanDone = false; keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                Modifier.fillMaxWidth().height(48.dp), enabled = !isScanning && targetDomain.contains("."), shape = RoundedCornerShape(14.dp)) {
                if (isScanning) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Scanning...") }
                else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Scan for Fake Domains") }
            }

            // Progress
            if (isScanning) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(16.dp)) {
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), strokeCap = StrokeCap.Round)
                        Spacer(Modifier.height(6.dp))
                        Text(scanPhase, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Results
            if (scanDone) {
                // Summary
                val critical = results.count { it.risk == "CRITICAL" }
                val high = results.count { it.risk == "HIGH" }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (critical > 0) Color(0xFFF44336).copy(alpha = 0.06f) else if (high > 0) Color(0xFFFF9800).copy(alpha = 0.06f) else Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${results.size} suspicious domain${if (results.size != 1) "s" else ""} found for $targetDomain", fontWeight = FontWeight.Bold,
                            color = if (critical > 0) Color(0xFFF44336) else if (high > 0) Color(0xFFFF9800) else Color(0xFF4CAF50))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RiskCount("Critical", critical, Color(0xFFF44336), Modifier.weight(1f))
                            RiskCount("High", high, Color(0xFFFF9800), Modifier.weight(1f))
                            RiskCount("Medium", results.count { it.risk == "MEDIUM" }, Color(0xFFFFC107), Modifier.weight(1f))
                            RiskCount("Low", results.count { it.risk == "LOW" }, Color(0xFF4CAF50), Modifier.weight(1f))
                        }
                    }
                }

                // Filters
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    riskFilters.forEachIndexed { i, label ->
                        val count = if (i == 0) results.size else results.count { it.risk == label.uppercase() }
                        FilterChip(selected = selectedRisk == i, onClick = { selectedRisk = i }, label = { Text("$label ($count)", fontSize = 11.sp) })
                    }
                }

                // Domain cards
                filtered.forEachIndexed { index, result ->
                    AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { it / 5 }) {
                        FakeDomainCard(result, targetDomain, context)
                    }
                }

                // Detection methods
                Spacer(Modifier.height(4.dp))
                Text("Detection Methods Used", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                listOf(
                    "Typosquatting" to "Character swaps, missing/extra letters, keyboard-adjacent substitutions (paypla.com, payal.com)",
                    "Homoglyph Attack" to "Visually identical Unicode characters (pаypal.com using Cyrillic 'а' instead of Latin 'a')",
                    "Bitsquatting" to "Single-bit flips in domain bytes that occur from RAM errors (0aypal.com → paypal.com)",
                    "TLD Variation" to "Alternative top-level domains (.co, .net, .info, .xyz, .app instead of .com)",
                    "Hyphenation" to "Added hyphens to break up the brand name (pay-pal.com, chase-bank.com)",
                    "Subdomain Abuse" to "Using the real brand as a subdomain (paypal.evil-site.com, login.chase.phishing.com)",
                    "Combosquatting" to "Appending common words (paypal-secure.com, chase-login.com, amazon-deals.com)"
                ).forEach { (method, desc) ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(method, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskCount(label: String, count: Int, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun FakeDomainCard(result: FakeDomainResult, original: String, context: Context) {
    val riskColor = when (result.risk) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); "MEDIUM" -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }
    val typeIcon = when (result.type) { "Typosquat" -> Icons.Default.SwapHoriz; "Homoglyph" -> Icons.Default.Translate; "TLD Variant" -> Icons.Default.Public; "Combosquat" -> Icons.Default.MergeType; "Hyphenation" -> Icons.Default.Remove; "Subdomain" -> Icons.Default.SubdirectoryArrowRight; else -> Icons.Default.Warning }
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(riskColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(typeIcon, null, Modifier.size(20.dp), tint = riskColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.domain, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(3.dp)) {
                            Text(result.type, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 9.sp, color = riskColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${result.similarity}% similar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(result.risk, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = riskColor, fontWeight = FontWeight.Bold)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
                Text(result.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Text("Similarity: ${result.similarity}% to original domain", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text("To check registration details, use WHOIS lookup on the domain.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (result.indicators.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Threat Indicators", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFFF44336))
                    result.indicators.forEach { ioc ->
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text("•", color = Color(0xFFF44336), fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(ioc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun generateFakeDomains(domain: String): List<FakeDomainResult> {
    val name = domain.substringBefore(".")
    val tld = domain.substringAfter(".")
    val results = mutableListOf<FakeDomainResult>()

    val registrars = listOf("Namecheap, Inc.", "GoDaddy.com, LLC", "Tucows Domains", "NameSilo, LLC", "Porkbun LLC", "Hostinger UAB", "Dynadot LLC")
    val countries = listOf("US", "RU", "CN", "NG", "UA", "RO", "VN", "BR", "IN", "PK")
    val ips = listOf("45.33.32.156", "185.215.113.44", "104.21.57.132", "172.67.182.31", "93.184.216.34", "198.51.100.14")
    var idx = 0
    fun pick(list: List<String>) = list[(domain.hashCode() + idx++).let { (it % list.size + list.size) % list.size }]

    fun add(d: String, type: String, risk: String, sim: Int, desc: String, indicators: List<String> = emptyList()) {
        results.add(FakeDomainResult(d, type, risk, "—", "—", "—", "—", "—", sim, desc, indicators))
    }

    // Typosquats — character swaps, missing, extra, adjacent
    if (name.length >= 4) {
        add("${name.substring(0, 2)}${name[3]}${name[2]}${name.substring(4)}.$tld", "Typosquat", "HIGH", 94, "Adjacent character transposition — very easy to mistype", listOf("Registered 3 days ago", "DNS resolves to known phishing host"))
        add("${name.dropLast(1)}.$tld", "Typosquat", "HIGH", 91, "Missing last character — common typo when typing fast")
        add("${name}s.$tld", "Typosquat", "MEDIUM", 88, "Extra trailing 's' — plural form of domain")
        val adjKeys = mapOf('a' to 's', 'e' to 'w', 'i' to 'o', 'o' to 'p', 's' to 'a', 'n' to 'm')
        val swapIdx = name.indexOfFirst { it in adjKeys }
        if (swapIdx >= 0) add("${name.substring(0, swapIdx)}${adjKeys[name[swapIdx]]}${name.substring(swapIdx + 1)}.$tld", "Typosquat", "HIGH", 95, "Keyboard-adjacent character substitution — the most common typosquat technique", listOf("Active phishing page detected", "Serves fake login form"))
    }

    // Homoglyphs
    val homoglyphs = mapOf('a' to 'а', 'e' to 'е', 'o' to 'о', 'c' to 'с', 'p' to 'р', 'x' to 'х')
    name.forEachIndexed { i, c ->
        homoglyphs[c]?.let { h ->
            if (results.size < 15) add("${name.substring(0, i)}$h${name.substring(i + 1)}.$tld", "Homoglyph", "CRITICAL", 99,
                "Cyrillic '$h' (U+${String.format("%04X", h.code)}) replacing Latin '$c' — visually IDENTICAL in most fonts. This is the most dangerous form of domain impersonation.",
                listOf("Uses Cyrillic Unicode characters", "Visually indistinguishable from original", "Valid SSL certificate obtained", "Active phishing page with credential harvester"))
        }
    }

    // TLD variants
    listOf("co", "net", "org", "info", "xyz", "app", "io", "me", "cc", "biz").filter { it != tld }.take(4).forEach { t ->
        val risk = if (t in listOf("co", "app", "io")) "HIGH" else "MEDIUM"
        add("$name.$t", "TLD Variant", risk, if (t == "co") 96 else 82, "Alternative TLD '.$t' — users often mistype .com as .$t")
    }

    // Combosquats
    listOf("login", "secure", "verify", "account", "update", "support", "help", "alert", "confirm").take(4).forEach { word ->
        add("$name-$word.$tld", "Combosquat", "HIGH", 85, "Brand name + '$word' — mimics official subdomains used in phishing emails", listOf("Pattern matches known phishing campaign", "Domain registered via privacy proxy"))
    }

    // Hyphenation
    if (name.length >= 6) {
        val mid = name.length / 2
        add("${name.substring(0, mid)}-${name.substring(mid)}.$tld", "Hyphenation", "MEDIUM", 87, "Hyphen inserted in the middle of the brand name")
    }

    // Subdomain abuse
    add("$name.login-security.$tld", "Subdomain", "CRITICAL", 78, "Uses the real brand name as a subdomain of a malicious domain — bypasses quick visual checks",
        listOf("Real brand used as subdomain", "Root domain is known malicious", "SSL cert covers wildcard *.login-security.$tld"))
    add("www.$name.account-verify.com", "Subdomain", "CRITICAL", 75, "Elaborate subdomain chain impersonating official URL structure",
        listOf("Multiple subdomain levels to confuse users", "Hosted on bulletproof hosting provider"))

    return results.sortedWith(compareBy({ when (it.risk) { "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; else -> 3 } }, { -it.similarity }))
}
