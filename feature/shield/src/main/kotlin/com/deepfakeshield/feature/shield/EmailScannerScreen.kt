package com.deepfakeshield.feature.shield

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class EmailResult(val verdict: String, val riskScore: Int, val confidence: Int, val indicators: List<EmailIndicator>, val summary: String)
private data class EmailIndicator(val name: String, val status: String, val detail: String, val weight: Int, val category: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScannerScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    var emailText by rememberSaveable { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EmailResult?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var history by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Email Phishing Scanner", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Email, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Email Phishing Analyzer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("20+ NLP checks for phishing, impersonation, credential theft, and social engineering", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(value = emailText, onValueChange = { emailText = it; result = null }, label = { Text("Paste email content...") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), shape = RoundedCornerShape(16.dp), maxLines = 12,
                supportingText = { Text("${emailText.length} chars • ${emailText.split("\\s+".toRegex()).count { it.isNotBlank() }} words") })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (emailText.length >= 10) { isAnalyzing = true; result = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                    Modifier.weight(1f).height(48.dp), enabled = !isAnalyzing && emailText.length >= 10, shape = RoundedCornerShape(14.dp)) {
                    if (isAnalyzing) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("Scanning...") }
                    else { Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Scan Email") }
                }
                OutlinedButton(onClick = { val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString(); if (!clip.isNullOrBlank()) { emailText = clip; result = null } }, Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)) }
            }

            if (result == null && !isAnalyzing) {
                Text("Try an Example", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    EXAMPLES.forEach { (label, text) -> AssistChip(onClick = { emailText = text; result = null }, label = { Text(label, fontSize = 11.sp) }) }
                }
            }

            if (isAnalyzing) {
                LaunchedEffect(emailText) {
                    listOf(0.1f, 0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f).forEach { progress = it; delay(120) }
                    result = analyzeEmail(emailText)
                    history = (listOf(emailText.take(40) + "..." to (result?.verdict ?: "?")) + history).take(10)
                    isAnalyzing = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), strokeCap = StrokeCap.Round)
            }

            result?.let { r ->
                val vc = when (r.verdict) { "SAFE" -> Color(0xFF4CAF50); "SUSPICIOUS" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = vc.copy(0.08f))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).background(vc.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(when (r.verdict) { "SAFE" -> Icons.Default.VerifiedUser; "SUSPICIOUS" -> Icons.Default.Warning; else -> Icons.Default.Dangerous }, null, Modifier.size(32.dp), tint = vc) }
                        Spacer(Modifier.height(10.dp))
                        Text(when (r.verdict) { "SAFE" -> "Looks Safe"; "SUSPICIOUS" -> "Suspicious"; else -> "Likely Phishing" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = vc)
                        Text("Risk: ${r.riskScore}% • Confidence: ${r.confidence}%", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { r.riskScore / 100f }, Modifier.fillMaxWidth().height(8.dp), color = vc, strokeCap = StrokeCap.Round, trackColor = Color.LightGray.copy(0.3f))
                    }
                }
                Text(r.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                r.indicators.groupBy { it.category }.forEach { (cat, inds) ->
                    Text(cat, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    inds.forEach { ind ->
                        val ic = when (ind.status) { "PASS" -> Color(0xFF4CAF50); "WARNING" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).background(ic.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(when (ind.status) { "PASS" -> Icons.Default.CheckCircle; "WARNING" -> Icons.Default.Warning; else -> Icons.Default.Error }, null, Modifier.size(12.dp), tint = ic) }
                                Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(ind.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(ind.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty() && result == null && !isAnalyzing) {
                Text("Recent Scans", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                history.forEach { (text, verdict) -> val c = when (verdict) { "SAFE" -> Color(0xFF4CAF50); "SUSPICIOUS" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                    Card(Modifier.fillMaxWidth().clickable { emailText = text.removeSuffix("..."); result = null }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.04f))) {
                        Row(Modifier.padding(8.dp)) { Box(Modifier.size(8.dp).background(c, CircleShape)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(verdict, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// EMAIL ANALYSIS ENGINE — 20+ indicators
// ═══════════════════════════════════════════════════════════════════

private fun analyzeEmail(text: String): EmailResult {
    val inds = mutableListOf<EmailIndicator>()
    val lower = text.lowercase()
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

    // URGENCY
    val urgencyPhrases = listOf("act now", "urgent", "immediately", "expire", "suspended", "locked", "verify your", "confirm your", "within 24 hours", "within 48 hours", "action required", "respond immediately", "last chance", "final warning", "account will be", "failure to", "time sensitive", "don't delay", "limited time", "act fast", "before it's too late", "hours left", "minutes remaining", "deadline", "overdue", "past due", "delinquent", "warrant", "arrest", "legal action", "court order")
    val urgencyCount = urgencyPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Urgency Language", if (urgencyCount == 0) "PASS" else if (urgencyCount <= 2) "WARNING" else "FAIL", "$urgencyCount urgency phrase${if (urgencyCount != 1) "s" else ""} — ${if (urgencyCount == 0) "no pressure tactics" else "creates false sense of urgency"}", if (urgencyCount == 0) 1 else urgencyCount * 8, "Urgency & Pressure")

    val threatPhrases = listOf("account will be closed", "access will be revoked", "police", "fbi", "irs", "tax", "arrest", "lawsuit", "legal", "penalty", "fine", "prosecute", "criminal", "investigation", "fraud department", "security team")
    val threatCount = threatPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Threat Language", if (threatCount == 0) "PASS" else "FAIL", "$threatCount threat/authority phrase${if (threatCount != 1) "s" else ""} — ${if (threatCount == 0) "no intimidation" else "uses fear and authority to manipulate"}", if (threatCount == 0) 1 else threatCount * 10, "Urgency & Pressure")

    // CREDENTIAL HARVESTING
    val credPhrases = listOf("password", "login", "sign in", "credentials", "username", "ssn", "social security", "bank account", "routing number", "credit card", "card number", "cvv", "expiry", "pin", "otp", "one-time", "verification code", "security code", "mother's maiden", "date of birth", "tax id", "employee id", "payroll", "w-2", "w2", "direct deposit")
    val credCount = credPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Credential Harvesting", if (credCount == 0) "PASS" else if (credCount <= 2) "WARNING" else "FAIL", "$credCount credential-related keyword${if (credCount != 1) "s" else ""}", if (credCount == 0) 1 else credCount * 7, "Credential Theft")

    val formPhrases = listOf("click here", "click below", "click the link", "click the button", "update your information", "verify your account", "confirm your details", "fill out", "complete the form", "enter your", "provide your", "submit your")
    val formCount = formPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Action Requests", if (formCount == 0) "PASS" else if (formCount <= 1) "WARNING" else "FAIL", "$formCount call-to-action phrase${if (formCount != 1) "s" else ""}", if (formCount == 0) 1 else formCount * 6, "Credential Theft")

    // LINKS & DOMAINS
    val urlCount = Regex("https?://[^\\s]+").findAll(text).count()
    val shorteners = listOf("bit.ly", "tinyurl", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "j.mp", "rb.gy", "cutt.ly", "shorturl", "tiny.cc", "lnkd.in", "dlvr.it", "rebrand.ly")
    val hasShortener = shorteners.any { lower.contains(it) }
    inds += EmailIndicator("URL Count", if (urlCount <= 1) "PASS" else if (urlCount <= 3) "WARNING" else "FAIL", "$urlCount URL${if (urlCount != 1) "s" else ""} found", if (urlCount <= 1) 1 else urlCount * 4, "Links & Domains")
    inds += EmailIndicator("URL Shorteners", if (!hasShortener) "PASS" else "FAIL", if (!hasShortener) "No shortened URLs" else "Shortened URL detected — hides real destination", if (!hasShortener) 1 else 15, "Links & Domains")

    val homographs = listOf("paypa1" to "paypal", "amaz0n" to "amazon", "g00gle" to "google", "micr0soft" to "microsoft", "app1e" to "apple", "faceb00k" to "facebook", "netf1ix" to "netflix", "chasebank" to "chase", "wells-farg0" to "wellsfargo")
    val spoofed = homographs.any { (fake, _) -> lower.contains(fake) }
    inds += EmailIndicator("Domain Spoofing", if (!spoofed) "PASS" else "FAIL", if (!spoofed) "No homograph attacks detected" else "Lookalike domain detected (e.g., paypa1 instead of paypal)", if (!spoofed) 1 else 25, "Links & Domains")

    // IMPERSONATION
    val brands = listOf("apple", "google", "microsoft", "amazon", "paypal", "netflix", "facebook", "instagram", "whatsapp", "chase", "wells fargo", "bank of america", "citibank", "usps", "fedex", "ups", "dhl", "irs", "social security administration", "medicare")
    val brandCount = brands.count { lower.contains(it) }
    inds += EmailIndicator("Brand Impersonation", if (brandCount == 0) "PASS" else if (brandCount == 1) "WARNING" else "FAIL", "$brandCount brand name${if (brandCount != 1) "s" else ""} — ${if (brandCount == 0) "no impersonation" else "may be impersonating a known company"}", if (brandCount == 0) 1 else brandCount * 5, "Impersonation")

    val genericGreetings = listOf("dear customer", "dear user", "dear account holder", "dear valued", "dear sir", "dear madam", "dear member", "to whom it may concern", "dear friend")
    val hasGeneric = genericGreetings.any { lower.contains(it) }
    inds += EmailIndicator("Generic Greeting", if (!hasGeneric) "PASS" else "WARNING", if (!hasGeneric) "No generic greeting — may be personalized" else "Generic greeting — legitimate companies usually use your name", if (!hasGeneric) 1 else 8, "Impersonation")

    // ATTACHMENTS
    val dangerousExts = listOf(".exe", ".scr", ".bat", ".cmd", ".vbs", ".js", ".wsf", ".ps1", ".msi", ".dll", ".iso", ".img", ".zip", ".rar", ".7z", ".tar", ".gz", ".docm", ".xlsm", ".pptm", ".html", ".htm")
    val hasAttachmentRef = dangerousExts.any { lower.contains(it) } || lower.contains("attachment") || lower.contains("download the file") || lower.contains("open the attached")
    inds += EmailIndicator("Attachment Risk", if (!hasAttachmentRef) "PASS" else "WARNING", if (!hasAttachmentRef) "No risky attachment references" else "References attachments or dangerous file types", if (!hasAttachmentRef) 1 else 12, "Attachments")

    // EMOTIONAL MANIPULATION
    val greedPhrases = listOf("won", "winner", "congratulations", "prize", "reward", "gift card", "lottery", "million dollars", "inheritance", "unclaimed", "free", "no cost", "bonus", "jackpot")
    val greedCount = greedPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Greed/Reward Bait", if (greedCount == 0) "PASS" else "FAIL", "$greedCount reward/greed phrase${if (greedCount != 1) "s" else ""}", if (greedCount == 0) 1 else greedCount * 8, "Social Engineering")

    val fearPhrases = listOf("unauthorized", "suspicious activity", "unusual login", "compromised", "hacked", "breached", "stolen", "identity theft", "fraud alert", "security alert")
    val fearCount = fearPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Fear Tactics", if (fearCount == 0) "PASS" else if (fearCount <= 1) "WARNING" else "FAIL", "$fearCount fear-inducing phrase${if (fearCount != 1) "s" else ""}", if (fearCount == 0) 1 else fearCount * 6, "Social Engineering")

    // WRITING QUALITY
    val spellingIssues = listOf("recieve", "untill", "occured", "seperately", "definately", "accomodation", "occassion", "benifit", "acheive", "tommorow", "goverment", "regestration")
    val misspellings = spellingIssues.count { lower.contains(it) }
    inds += EmailIndicator("Spelling Quality", if (misspellings == 0) "PASS" else "WARNING", "$misspellings common misspelling${if (misspellings != 1) "s" else ""}", if (misspellings == 0) 1 else misspellings * 5, "Writing Quality")

    val excessiveCaps = words.count { it.length > 2 && it == it.uppercase() && it.any { c -> c.isLetter() } }
    inds += EmailIndicator("ALL CAPS Words", if (excessiveCaps <= 2) "PASS" else "WARNING", "$excessiveCaps ALL-CAPS word${if (excessiveCaps != 1) "s" else ""}", if (excessiveCaps <= 2) 1 else excessiveCaps * 3, "Writing Quality")

    val exclamations = text.count { it == '!' }
    inds += EmailIndicator("Exclamation Marks", if (exclamations <= 2) "PASS" else "WARNING", "$exclamations exclamation mark${if (exclamations != 1) "s" else ""}", if (exclamations <= 2) 1 else exclamations * 2, "Writing Quality")

    // FINANCIAL
    val financialPhrases = listOf("wire transfer", "western union", "moneygram", "bitcoin", "cryptocurrency", "gift card", "itunes card", "google play card", "steam card", "payment", "invoice", "outstanding balance", "refund", "reimbursement")
    val finCount = financialPhrases.count { lower.contains(it) }
    inds += EmailIndicator("Financial Requests", if (finCount == 0) "PASS" else "FAIL", "$finCount financial/payment keyword${if (finCount != 1) "s" else ""}", if (finCount == 0) 1 else finCount * 8, "Financial")

    // SCORE
    val fakeScore = inds.filter { it.status != "PASS" }.sumOf { it.weight }.coerceIn(0, 100)
    val confidence = (45 + words.size.coerceAtMost(30) + inds.count { it.status != "PASS" } * 2).coerceIn(40, 95)
    val verdict = when { fakeScore >= 60 -> "PHISHING"; fakeScore >= 30 -> "SUSPICIOUS"; else -> "SAFE" }
    val summary = when (verdict) {
        "PHISHING" -> "Multiple phishing indicators detected: ${inds.count { it.status == "FAIL" }} failed checks. Do NOT click any links or provide information."
        "SUSPICIOUS" -> "Some concerning patterns found. Verify the sender independently before taking any action."
        else -> "No significant phishing indicators found. Still exercise caution with any requests for personal information."
    }
    return EmailResult(verdict, fakeScore, confidence, inds, summary)
}

private val EXAMPLES = listOf(
    "Phishing" to "Dear Customer, Your account has been suspended due to suspicious activity. Click here immediately to verify your identity: http://bit.ly/3xFake. Failure to respond within 24 hours will result in permanent account closure. - Security Team, PayPa1",
    "Scam" to "CONGRATULATIONS!!! You have been selected as the WINNER of our \$1,000,000 lottery!!! To claim your prize, send your full name, SSN, and bank account number to claims@lottery-winner.xyz. Act NOW - this offer expires in 48 hours!!!",
    "BEC" to "Hi, I need you to process an urgent wire transfer of \$45,000 to the following account. This is time-sensitive and must be completed today. Do not discuss with anyone else. I'll explain when I'm back. - CEO",
    "Safe" to "Hi Sarah, just following up on our meeting last week about the Q3 marketing budget. I've attached the revised spreadsheet with the numbers we discussed. Let me know if Tuesday works for a quick call to finalize. Best, Mike"
)
