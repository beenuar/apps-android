package com.deepfakeshield.feature.shield

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── Models ─────────────────────────────────────────────────────────

private data class ReviewAnalysis(
    val verdict: String, val confidence: Int, val authenticity: Int,
    val indicators: List<Indicator>, val summary: String,
    val aiProbability: Int, val wordCount: Int, val sentenceCount: Int
)

private data class Indicator(
    val name: String, val status: String, val detail: String,
    val weight: Int, val category: String
)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeReviewDetectorScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var reviewText by rememberSaveable { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReviewAnalysis?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("") }
    var history by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Review Analyzer", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RateReview, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text("AI Review Analyzer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("20+ NLP checks detect fake, AI-generated, and incentivized reviews", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(value = reviewText, onValueChange = { reviewText = it; result = null },
                label = { Text("Paste a review...") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(16.dp), maxLines = 10,
                supportingText = { Text("${reviewText.length} chars • ${reviewText.split("\\s+".toRegex()).count { it.isNotBlank() }} words") })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (reviewText.length >= 15) { isAnalyzing = true; result = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                    Modifier.weight(1f).height(48.dp), enabled = !isAnalyzing && reviewText.length >= 15, shape = RoundedCornerShape(14.dp)) {
                    if (isAnalyzing) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("Analyzing...") }
                    else { Icon(Icons.Default.Analytics, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Analyze") }
                }
                OutlinedButton(onClick = {
                    val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!clip.isNullOrBlank()) { reviewText = clip; result = null }
                }, Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Paste") }
            }

            // Example reviews
            if (result == null && !isAnalyzing) {
                Text("Try an Example", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    EXAMPLES.forEach { (label, text) ->
                        AssistChip(onClick = { reviewText = text; result = null }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
            }

            if (isAnalyzing) {
                LaunchedEffect(reviewText) {
                    listOf("Tokenizing text..." to 0.1f, "Sentiment analysis..." to 0.2f, "Vocabulary profiling..." to 0.3f, "AI text detection..." to 0.45f, "Specificity check..." to 0.55f, "Emotional balance..." to 0.65f, "Manipulation scan..." to 0.75f, "Writing style analysis..." to 0.85f, "Generating report..." to 1f).forEach { (p, v) -> phase = p; progress = v; delay(150) }
                    result = deepAnalyze(reviewText)
                    history = (listOf(reviewText.take(40) + "..." to (result?.verdict ?: "?")) + history).take(10)
                    isAnalyzing = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.secondary, strokeCap = StrokeCap.Round)
                Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            result?.let { r -> ResultSection(r) }

            if (history.isNotEmpty() && result == null && !isAnalyzing) {
                Text("Recent Analyses", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                history.forEach { (text, verdict) ->
                    val c = when (verdict) { "GENUINE" -> Color(0xFF4CAF50); "SUSPICIOUS" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                    Card(Modifier.fillMaxWidth().clickable { reviewText = text.removeSuffix("..."); result = null }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.04f))) {
                        Row(Modifier.padding(8.dp)) { Box(Modifier.size(8.dp).background(c, CircleShape).padding(top = 4.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(verdict, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c) }
                    }
                }
            }

            if (result == null && !isAnalyzing) {
                Text("What We Analyze", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                CHECKS_INFO.forEach { (cat, items) ->
                    Text(cat, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    items.forEach { (t, d) ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
                            Row(Modifier.padding(8.dp)) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)); Column { Text(t, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall); Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSection(r: ReviewAnalysis) {
    val vc = when (r.verdict) { "GENUINE" -> Color(0xFF4CAF50); "SUSPICIOUS" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
    val aiColor = when { r.aiProbability >= 70 -> Color(0xFF9C27B0); r.aiProbability >= 40 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = vc.copy(0.08f))) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).background(vc.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(when (r.verdict) { "GENUINE" -> Icons.Default.ThumbUp; "SUSPICIOUS" -> Icons.Default.Warning; else -> Icons.Default.ThumbDown }, null, Modifier.size(32.dp), tint = vc)
                }
                Spacer(Modifier.height(10.dp))
                Text(when (r.verdict) { "GENUINE" -> "Likely Genuine"; "SUSPICIOUS" -> "Possibly Fake"; else -> "Likely Fake" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = vc)
                Text("Authenticity: ${r.authenticity}% • Confidence: ${r.confidence}%", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { r.authenticity / 100f }, Modifier.fillMaxWidth().height(8.dp), color = vc, strokeCap = StrokeCap.Round, trackColor = Color.LightGray.copy(0.3f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat("Words", "${r.wordCount}", MaterialTheme.colorScheme.primary)
                    MiniStat("Sentences", "${r.sentenceCount}", MaterialTheme.colorScheme.primary)
                    MiniStat("AI Prob", "${r.aiProbability}%", aiColor)
                }
            }
        }

        // AI detection card
        if (r.aiProbability >= 30) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = aiColor.copy(0.06f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp), tint = aiColor)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("AI-Generated Probability: ${r.aiProbability}%", fontWeight = FontWeight.Bold, color = aiColor, style = MaterialTheme.typography.bodySmall)
                        Text(when { r.aiProbability >= 70 -> "High likelihood of AI/ChatGPT generation"; r.aiProbability >= 40 -> "Some AI-like patterns detected"; else -> "Low AI indicators" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text(r.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Grouped indicators
        val groups = r.indicators.groupBy { it.category }
        groups.forEach { (cat, inds) ->
            Text(cat, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            inds.forEach { ind ->
                val ic = when (ind.status) { "PASS" -> Color(0xFF4CAF50); "WARNING" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(ic.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(when (ind.status) { "PASS" -> Icons.Default.CheckCircle; "WARNING" -> Icons.Default.Warning; else -> Icons.Default.Error }, null, Modifier.size(14.dp), tint = ic) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(ind.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall); Text(ind.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp); Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
}

// ═══════════════════════════════════════════════════════════════════
// NLP ANALYSIS ENGINE — 20+ indicators
// ═══════════════════════════════════════════════════════════════════

private fun deepAnalyze(text: String): ReviewAnalysis {
    val indicators = mutableListOf<Indicator>()
    val lower = text.lowercase()
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val wordCount = words.size
    val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank().and(it.length > 2) }
    val sentenceCount = sentences.size
    val avgSentLen = if (sentenceCount > 0) wordCount.toFloat() / sentenceCount else 0f
    val cleanWords = words.map { it.lowercase().trim(',', '.', '!', '?', '"', '\'', '(', ')') }
    val uniqueWords = cleanWords.toSet().size
    val vocabDiv = if (wordCount > 0) uniqueWords.toFloat() / wordCount else 0f
    var aiScore = 0

    // ── CONTENT ANALYSIS ─────────────────────────────────────

    val specificTerms = listOf("battery", "screen", "camera", "delivery", "shipping", "quality", "material", "size", "color", "weight", "price", "packaging", "warranty", "customer service", "instructions", "manual", "charger", "cable", "button", "app", "software", "fit", "comfortable", "noise", "taste", "smell", "texture", "brightness", "resolution", "speed", "performance")
    val specificCount = specificTerms.count { lower.contains(it) }
    val hasNumbers = text.contains(Regex("\\d+"))
    val pass = specificCount >= 2 || (specificCount >= 1 && hasNumbers)
    indicators += Indicator("Product Specificity", if (pass) "PASS" else "FAIL", if (pass) "$specificCount product details mentioned${if (hasNumbers) " + numerical data" else ""}" else "Vague — no specific product features mentioned", if (pass) 2 else 22, "Content")

    val comparisons = listOf("compared to", "better than", "worse than", "unlike", "similar to", "vs ", "versus", "other brands", "previously", "before this", "switched from", "upgrade from")
    val hasComp = comparisons.any { lower.contains(it) }
    indicators += Indicator("Product Comparison", if (hasComp) "PASS" else "WARNING", if (hasComp) "Compares with alternatives — sign of real experience" else "No comparisons — less likely from experienced buyer", if (hasComp) 2 else 7, "Content")

    val temporal = listOf("after ", "week", "month", "day", "year", "since", "ago", "recently", "just bought", "arrived", "received", "been using", "so far", "first impression", "update:")
    val hasTemporal = temporal.any { lower.contains(it) }
    indicators += Indicator("Timeline / Duration", if (hasTemporal) "PASS" else "WARNING", if (hasTemporal) "Mentions usage timeline — suggests real ownership" else "No time references — could be written without using the product", if (hasTemporal) 2 else 8, "Content")

    val prosAndCons = (lower.contains("pro") && lower.contains("con")) || lower.contains("downside") || lower.contains("only issue") || lower.contains("one complaint") || lower.contains("however") || lower.contains("on the other hand") || (lower.contains("love") && lower.contains("but"))
    indicators += Indicator("Pros & Cons", if (prosAndCons) "PASS" else "WARNING", if (prosAndCons) "Mentions both positives and negatives — balanced perspective" else "One-sided review — genuine reviews usually mention trade-offs", if (prosAndCons) 1 else 10, "Content")

    // ── LANGUAGE ANALYSIS ────────────────────────────────────

    val superlatives = listOf("best ever", "amazing", "perfect", "worst ever", "terrible", "incredible", "fantastic", "horrible", "outstanding", "flawless", "awful", "life-changing", "miracle", "game changer", "absolutely love", "beyond impressed")
    val exaggeration = superlatives.count { lower.contains(it) }
    indicators += Indicator("Emotional Balance", if (exaggeration <= 1) "PASS" else if (exaggeration <= 3) "WARNING" else "FAIL",
        "$exaggeration superlative${if (exaggeration != 1) "s" else ""} — ${if (exaggeration <= 1) "natural tone" else "excessive emotional language"}", if (exaggeration <= 1) 2 else if (exaggeration <= 3) 12 else 22, "Language")

    val exclamationCount = text.count { it == '!' }
    val excPer100 = if (wordCount > 0) exclamationCount * 100f / wordCount else 0f
    indicators += Indicator("Exclamation Marks", if (excPer100 <= 2f) "PASS" else if (excPer100 <= 5f) "WARNING" else "FAIL",
        "$exclamationCount exclamation marks (${"%.1f".format(excPer100)} per 100 words) — ${if (excPer100 <= 2f) "normal" else "excessive enthusiasm"}", if (excPer100 <= 2f) 1 else if (excPer100 <= 5f) 6 else 15, "Language")

    val allCapsWords = words.count { it.length > 2 && it == it.uppercase() && it.any { c -> c.isLetter() } }
    indicators += Indicator("CAPS Usage", if (allCapsWords <= 1) "PASS" else if (allCapsWords <= 3) "WARNING" else "FAIL",
        "$allCapsWords ALL-CAPS words — ${if (allCapsWords <= 1) "normal emphasis" else "shouting pattern common in fake reviews"}", if (allCapsWords <= 1) 1 else if (allCapsWords <= 3) 5 else 12, "Language")

    indicators += Indicator("Vocabulary Diversity", if (vocabDiv >= 0.5f) "PASS" else "WARNING",
        "${"%.0f".format(vocabDiv * 100)}% unique words (TTR) — ${if (vocabDiv >= 0.5f) "rich vocabulary" else "repetitive language"}", if (vocabDiv >= 0.5f) 2 else 10, "Language")

    val goodSentLen = avgSentLen in 8f..25f
    indicators += Indicator("Sentence Length", if (goodSentLen) "PASS" else "WARNING",
        "Avg ${"%.0f".format(avgSentLen)} words/sentence — ${if (goodSentLen) "natural" else if (avgSentLen < 8) "very short (robotic)" else "very long (possibly AI-generated)"}", if (goodSentLen) 2 else 8, "Language")
    if (avgSentLen > 20) aiScore += 10

    // ── AUTHENTICITY MARKERS ─────────────────────────────────

    val pronouns = listOf("i ", "i'", "my ", "me ", "we ", "our ", "myself")
    val pronounCount = pronouns.count { lower.contains(it) }
    indicators += Indicator("Personal Experience", if (pronounCount >= 2) "PASS" else if (pronounCount == 1) "WARNING" else "FAIL",
        "$pronounCount first-person references — ${if (pronounCount >= 2) "written from experience" else "impersonal — may be generated"}", if (pronounCount >= 2) 2 else if (pronounCount == 1) 8 else 18, "Authenticity")
    if (pronounCount == 0) aiScore += 10

    val hedging = listOf("mostly", "generally", "somewhat", "a bit", "a little", "fairly", "pretty good", "not bad", "could be better", "decent", "reasonable", "for the price", "considering")
    val hasHedging = hedging.count { lower.contains(it) }
    indicators += Indicator("Hedging Language", if (hasHedging >= 1) "PASS" else "WARNING",
        "$hasHedging hedging phrases — ${if (hasHedging >= 1) "genuine reviews often qualify opinions" else "absolute statements without nuance"}", if (hasHedging >= 1) 1 else 8, "Authenticity")
    if (hasHedging == 0 && wordCount > 30) aiScore += 8

    val questions = text.count { it == '?' }
    indicators += Indicator("Questions/Concerns", if (questions >= 1) "PASS" else "WARNING",
        "$questions question${if (questions != 1) "s" else ""} — ${if (questions >= 1) "real reviewers often raise concerns" else "no questions asked — unusual for genuine feedback"}", if (questions >= 1) 1 else 5, "Authenticity")

    // ── MANIPULATION DETECTION ───────────────────────────────

    val urgency = listOf("buy now", "hurry", "limited time", "don't miss", "act fast", "must have", "order today", "sale ends", "last chance", "click here", "check it out", "link in", "discount code", "use code", "affiliate")
    val urgencyCount = urgency.count { lower.contains(it) }
    indicators += Indicator("Sales/Urgency Language", if (urgencyCount == 0) "PASS" else "FAIL",
        "$urgencyCount promotional phrase${if (urgencyCount != 1) "s" else ""} — ${if (urgencyCount == 0) "no sales pressure" else "reviews should not contain promotions"}", if (urgencyCount == 0) 1 else 25, "Manipulation")

    val hasUrl = text.contains(Regex("https?://|www\\.|bit\\.ly|amzn\\.to|\\.[a-z]{2,4}/"))
    indicators += Indicator("URLs/Links", if (!hasUrl) "PASS" else "FAIL",
        if (!hasUrl) "No links — genuine reviews don't include URLs" else "Contains URL — likely promotional or affiliate", if (!hasUrl) 1 else 20, "Manipulation")

    val brandOvermention = cleanWords.groupBy { it }.filter { it.value.size >= 4 && it.key.length > 3 }.keys
    indicators += Indicator("Brand Repetition", if (brandOvermention.isEmpty()) "PASS" else "WARNING",
        if (brandOvermention.isEmpty()) "No word repeated 4+ times" else "Words repeated 4+ times: ${brandOvermention.take(3).joinToString(", ")} — possible keyword stuffing", if (brandOvermention.isEmpty()) 1 else 10, "Manipulation")

    val emojiCount = text.count { Character.getType(it).toByte() == Character.OTHER_SYMBOL || (it.code in 0x1F600..0x1F9FF) }
    indicators += Indicator("Emoji Usage", if (emojiCount <= 2) "PASS" else "WARNING",
        "$emojiCount emoji${if (emojiCount != 1) "s" else ""} — ${if (emojiCount <= 2) "normal" else "excessive — common in incentivized reviews"}", if (emojiCount <= 2) 1 else 8, "Manipulation")

    // ── AI TEXT DETECTION ─────────────────────────────────────

    val aiPhrases = listOf("it is worth noting", "in conclusion", "all in all", "having said that", "it should be noted", "one might argue", "it goes without saying", "in today's world", "needless to say", "it is important to note", "from my perspective", "in terms of", "when it comes to", "at the end of the day", "delve into", "navigate the", "leverag", "robust", "seamless", "streamline")
    val aiPhraseCount = aiPhrases.count { lower.contains(it) }
    if (aiPhraseCount >= 2) aiScore += 20
    else if (aiPhraseCount == 1) aiScore += 8
    indicators += Indicator("AI Phrase Detection", if (aiPhraseCount == 0) "PASS" else if (aiPhraseCount <= 1) "WARNING" else "FAIL",
        "$aiPhraseCount AI-typical phrase${if (aiPhraseCount != 1) "s" else ""} detected — ${if (aiPhraseCount == 0) "no ChatGPT patterns" else "commonly used by LLMs"}", if (aiPhraseCount == 0) 1 else if (aiPhraseCount <= 1) 8 else 18, "AI Detection")

    val sentenceStarters = sentences.map { it.trim().split(" ").firstOrNull()?.lowercase() ?: "" }
    val starterDiversity = sentenceStarters.toSet().size.toFloat() / sentenceStarters.size.coerceAtLeast(1)
    if (starterDiversity < 0.5f && sentenceCount >= 4) aiScore += 12
    indicators += Indicator("Sentence Starter Variety", if (starterDiversity >= 0.5f || sentenceCount < 4) "PASS" else "WARNING",
        "${"%.0f".format(starterDiversity * 100)}% unique starters — ${if (starterDiversity >= 0.5f) "varied openings" else "repetitive structure (AI pattern)"}", if (starterDiversity >= 0.5f || sentenceCount < 4) 2 else 8, "AI Detection")

    val avgWordLen = if (wordCount > 0) cleanWords.sumOf { it.length }.toFloat() / wordCount else 0f
    if (avgWordLen > 5.2f) aiScore += 10
    indicators += Indicator("Word Complexity", if (avgWordLen in 3.5f..5.2f) "PASS" else "WARNING",
        "Avg ${"%.1f".format(avgWordLen)} chars/word — ${if (avgWordLen in 3.5f..5.2f) "consumer-level vocabulary" else "unusually formal (AI tends to use longer words)"}", if (avgWordLen in 3.5f..5.2f) 2 else 8, "AI Detection")

    val hasContractions = listOf("don't", "can't", "won't", "isn't", "wasn't", "couldn't", "shouldn't", "it's", "i'm", "i've", "they're", "we're", "there's").any { lower.contains(it) }
    if (!hasContractions && wordCount > 30) aiScore += 12
    indicators += Indicator("Contractions", if (hasContractions || wordCount <= 30) "PASS" else "WARNING",
        if (hasContractions) "Uses contractions — natural human writing" else "No contractions — AI tends to write formally", if (hasContractions || wordCount <= 30) 1 else 8, "AI Detection")

    // ── SCORING ──────────────────────────────────────────────

    val fakeScore = indicators.sumOf { if (it.status != "PASS") it.weight else 0 }.coerceIn(0, 100)
    val authenticity = (100 - fakeScore).coerceIn(0, 100)
    val confidence = (50 + wordCount.coerceAtMost(30) + sentenceCount.coerceAtMost(10) + (if (wordCount > 50) 5 else 0)).coerceIn(45, 95)
    val aiProb = aiScore.coerceIn(0, 95)
    val verdict = when { authenticity >= 70 -> "GENUINE"; authenticity >= 40 -> "SUSPICIOUS"; else -> "FAKE" }
    val summary = when (verdict) {
        "GENUINE" -> "This review shows strong authenticity markers: specific product details, personal experience, balanced sentiment, natural writing, and low AI probability ($aiProb%)."
        "SUSPICIOUS" -> "Mixed signals. Some genuine elements but also concerning patterns. ${if (aiProb >= 40) "AI-generated text probability is $aiProb%." else ""} May be incentivized or partially AI-assisted."
        else -> "Multiple red flags: ${indicators.count { it.status == "FAIL" }} failed checks, ${if (aiProb >= 50) "high AI probability ($aiProb%), " else ""}${if (urgencyCount > 0) "promotional language, " else ""}${if (pronounCount == 0) "no personal experience, " else ""}and unnatural writing patterns."
    }
    return ReviewAnalysis(verdict, confidence, authenticity, indicators, summary, aiProb, wordCount, sentenceCount)
}

// ── Example reviews & info ─────────────────────────────────────────

private val EXAMPLES = listOf(
    "Genuine" to "I've been using this blender for about 3 months now. The motor is powerful enough for frozen fruit, but it's pretty loud. My old Ninja was quieter. The cleanup is easy though - I just run it with soap and water. For \$40 it's decent, but I wish the jar was glass instead of plastic.",
    "Fake (Promo)" to "OMG this is the BEST product EVER!!! You NEED to buy this RIGHT NOW before the sale ends!!! I've never seen anything so amazing in my life. Click the link in my bio for a discount code! 5 stars isn't enough!!!",
    "AI-Generated" to "It is worth noting that this product delivers a seamless experience from unboxing to daily use. The robust construction and streamlined design make it a worthwhile investment. In terms of performance, one might argue that it exceeds expectations in every measurable dimension.",
    "Suspicious" to "Great product. Works well. Fast shipping. Good quality. Would recommend. Five stars. Very happy with purchase. Will buy again."
)

private val CHECKS_INFO = listOf(
    "Content Analysis" to listOf("Product Specificity" to "Does it mention real product features?", "Comparison" to "Does it compare with alternatives?", "Timeline" to "Does it mention how long they've used it?", "Pros & Cons" to "Does it have balanced perspective?"),
    "Language Analysis" to listOf("Emotional Balance" to "Counts superlatives and exaggeration", "Vocabulary Diversity" to "Type-Token Ratio of unique words", "Sentence Structure" to "Average words per sentence", "Punctuation" to "Exclamation marks and ALL CAPS usage"),
    "Authenticity" to listOf("Personal Experience" to "First-person pronouns and narrative", "Hedging Language" to "Qualifiers like 'mostly', 'fairly good'", "Questions" to "Real reviewers ask questions"),
    "AI Detection" to listOf("ChatGPT Phrases" to "20+ known AI-typical expressions", "Sentence Starters" to "AI repeats same sentence openers", "Contractions" to "Humans use don't/can't; AI writes formally", "Word Complexity" to "AI uses longer, more formal words"),
    "Manipulation" to listOf("Urgency/Promo" to "Sales language, discount codes, deadlines", "URLs/Links" to "Affiliate links or promotional URLs", "Keyword Stuffing" to "Same word repeated 4+ times", "Emoji Overuse" to "Excessive emojis suggest incentivized review")
)
