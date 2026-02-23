package com.deepfakeshield.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Models ─────────────────────────────────────────────────────────

private data class NewsArticle(
    val title: String, val summary: String, val category: String,
    val source: String, val url: String, val pubDate: String,
    val icon: ImageVector, val color: Color, val isLive: Boolean = true
)

private data class RssFeed(val name: String, val url: String, val category: String, val color: Color, val icon: ImageVector)

// ── RSS Feeds (free, no API key) ───────────────────────────────────

private val RSS_FEEDS = listOf(
    RssFeed("BleepingComputer", "https://www.bleepingcomputer.com/feed/", "Breaches", Color(0xFFF44336), Icons.Default.BugReport),
    RssFeed("The Hacker News", "https://feeds.feedburner.com/TheHackersNews", "Threats", Color(0xFF9C27B0), Icons.Default.Security),
    RssFeed("Krebs on Security", "https://krebsonsecurity.com/feed/", "Analysis", Color(0xFF2196F3), Icons.Default.Analytics),
    RssFeed("CISA Alerts", "https://www.cisa.gov/cybersecurity-advisories/all.xml", "Advisories", Color(0xFFFF9800), Icons.Default.Shield),
    RssFeed("Dark Reading", "https://www.darkreading.com/rss.xml", "Industry", Color(0xFF795548), Icons.Default.Article),
    RssFeed("Naked Security", "https://nakedsecurity.sophos.com/feed/", "Tips", Color(0xFF4CAF50), Icons.Default.Lightbulb),
    RssFeed("Threatpost", "https://threatpost.com/feed/", "Threats", Color(0xFFE91E63), Icons.Default.Warning),
    RssFeed("SecurityWeek", "https://www.securityweek.com/feed", "Industry", Color(0xFF607D8B), Icons.Default.Newspaper),
)

// ── Main Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityNewsFeedScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCat by remember { mutableIntStateOf(0) }
    var loadedFeeds by remember { mutableIntStateOf(0) }
    var totalFeeds by remember { mutableIntStateOf(RSS_FEEDS.size) }
    var expandedUrl by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Breaches", "Threats", "Advisories", "Analysis", "Tips", "Industry")

    LaunchedEffect(Unit) {
        val allArticles = mutableListOf<NewsArticle>()
        for (feed in RSS_FEEDS) {
            try {
                val fetched = withContext(Dispatchers.IO) { fetchRss(feed) }
                allArticles.addAll(fetched)
            } catch (_: Exception) {}
            loadedFeeds++
        }
        if (allArticles.isEmpty()) allArticles.addAll(getFallbackNews())
        articles = allArticles.sortedByDescending { it.pubDate }
        isLoading = false
    }

    val filtered = remember(articles, selectedCat, searchQuery) {
        var f = if (selectedCat == 0) articles else articles.filter { it.category == categories[selectedCat] }
        if (searchQuery.isNotBlank()) f = f.filter { it.title.contains(searchQuery, true) || it.summary.contains(searchQuery, true) || it.source.contains(searchQuery, true) }
        f
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Security News", fontWeight = FontWeight.Bold); if (!isLoading) Text("${articles.count { it.isLive }} live articles from ${RSS_FEEDS.size} sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (!isLoading) IconButton(onClick = {
                    val report = "Security News Digest\n${articles.size} articles\n\n" + articles.take(10).joinToString("\n\n") { "• ${it.title}\n  ${it.source} | ${it.url}" }
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("news", report))
                    Toast.makeText(ctx, "Digest copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Share, "Share") }
            }
        ) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { loadedFeeds.toFloat() / totalFeeds }, Modifier.fillMaxWidth(0.6f).height(6.dp), strokeCap = StrokeCap.Round)
                    Spacer(Modifier.height(6.dp))
                    Text("Fetching from $loadedFeeds/$totalFeeds feeds...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    RSS_FEEDS.forEachIndexed { i, feed ->
                        if (i < loadedFeeds) Row(Modifier.padding(vertical = 1.dp)) { Icon(Icons.Default.CheckCircle, null, Modifier.size(12.dp), tint = Color(0xFF4CAF50)); Spacer(Modifier.width(4.dp)); Text(feed.name, style = MaterialTheme.typography.labelSmall) }
                        else if (i == loadedFeeds) Row(Modifier.padding(vertical = 1.dp)) { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.dp); Spacer(Modifier.width(4.dp)); Text(feed.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Stats
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedStat("${articles.size}", "Articles", Color(0xFF2196F3), Modifier.weight(1f))
                        FeedStat("${articles.count { it.isLive }}", "Live", Color(0xFF4CAF50), Modifier.weight(1f))
                        FeedStat("${RSS_FEEDS.size}", "Sources", Color(0xFF9C27B0), Modifier.weight(1f))
                    }
                }

                // Search
                item {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search news...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                }

                // Category filters
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        categories.forEachIndexed { i, cat ->
                            val count = if (i == 0) articles.size else articles.count { it.category == cat }
                            FilterChip(selected = selectedCat == i, onClick = { selectedCat = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                label = { Text("$cat ($count)", fontSize = 11.sp) })
                        }
                    }
                }

                // Source badges
                item {
                    Text("Sources", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        RSS_FEEDS.forEach { feed ->
                            val count = articles.count { it.source == feed.name }
                            AssistChip(onClick = { searchQuery = feed.name }, label = { Text("${feed.name} ($count)", fontSize = 10.sp) },
                                leadingIcon = { Box(Modifier.size(8.dp).background(feed.color, CircleShape)) })
                        }
                    }
                }

                // Articles
                if (filtered.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)); Text("No articles match") } } }
                }

                items(filtered, key = { it.url }) { article ->
                    val isExpanded = expandedUrl == article.url
                    Card(Modifier.fillMaxWidth().clickable { expandedUrl = if (isExpanded) null else article.url }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(40.dp).background(article.color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(article.icon, null, Modifier.size(20.dp), tint = article.color)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = article.color.copy(0.12f), shape = RoundedCornerShape(4.dp)) {
                                            Text(article.category, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, color = article.color, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(article.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (article.isLive) { Spacer(Modifier.width(4.dp)); Box(Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape)) }
                                        Spacer(Modifier.width(6.dp))
                                        Text(article.pubDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(article.title, fontWeight = FontWeight.SemiBold, maxLines = if (isExpanded) 5 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(article.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isExpanded) 10 else 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} },
                                        Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Read Full", fontSize = 12.sp) }
                                    OutlinedButton(onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}") }
                                        ctx.startActivity(Intent.createChooser(shareIntent, "Share article").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Share", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedStat(v: String, l: String, c: Color, m: Modifier) {
    Card(m, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.copy(0.06f))) {
        Column(Modifier.padding(6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 18.sp); Text(l, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RSS PARSER — fetches and parses real RSS/Atom feeds
// ═══════════════════════════════════════════════════════════════════

private fun fetchRss(feed: RssFeed): List<NewsArticle> {
    val articles = mutableListOf<NewsArticle>()
    try {
        val conn = URL(feed.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Cyble/3.0")
        val xml = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var inItem = false
        var title = ""; var desc = ""; var link = ""; var pubDate = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.lowercase() ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (tag == "item" || tag == "entry") { inItem = true; title = ""; desc = ""; link = ""; pubDate = "" }
                    if (inItem && tag == "link") {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank()) link = href
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            val parentTag = try { parser.name?.lowercase() } catch (_: Exception) { null }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (inItem) {
                        when (tag) {
                            "title" -> title = getTextSafe(parser, title)
                            "description", "summary", "content" -> if (desc.isBlank()) desc = getTextSafe(parser, desc)
                            "link" -> if (link.isBlank()) link = getTextSafe(parser, link)
                            "pubdate", "published", "updated", "dc:date" -> if (pubDate.isBlank()) pubDate = getTextSafe(parser, pubDate)
                        }
                    }
                    if (tag == "item" || tag == "entry") {
                        inItem = false
                        if (title.isNotBlank()) {
                            val cleanDesc = desc.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#8217;", "'").replace("&#8220;", "\"").replace("&#8221;", "\"").trim().take(300)
                            val cleanTitle = title.replace(Regex("<[^>]*>"), "").trim()
                            val formattedDate = formatPubDate(pubDate)
                            articles.add(NewsArticle(cleanTitle, cleanDesc, feed.category, feed.name, link, formattedDate, feed.icon, feed.color, true))
                        }
                    }
                }
            }
            event = parser.next()
        }
    } catch (e: Exception) {
        android.util.Log.w("NewsFeed", "${feed.name}: ${e.message}")
    }
    return articles.take(10)
}

private var lastText = ""
private fun getTextSafe(parser: XmlPullParser, current: String): String {
    return if (current.isBlank()) lastText.trim() else current
}

private fun formatPubDate(raw: String): String {
    if (raw.isBlank()) return "Recent"
    val formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    )
    for (fmt in formats) {
        try {
            val date = fmt.parse(raw) ?: continue
            val diff = System.currentTimeMillis() - date.time
            val hours = diff / (1000 * 60 * 60)
            return when {
                hours < 1 -> "Just now"
                hours < 24 -> "${hours}h ago"
                hours < 48 -> "Yesterday"
                hours < 168 -> "${hours / 24}d ago"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {}
    }
    return raw.take(10)
}

// ── Fallback (offline) ─────────────────────────────────────────────

private fun getFallbackNews(): List<NewsArticle> = listOf(
    NewsArticle("Major Data Breach Exposes 50M Healthcare Records", "A leading healthcare company disclosed a breach affecting 50 million patient records including SSNs and medical history.", "Breaches", "BleepingComputer", "https://www.bleepingcomputer.com", "Recent", Icons.Default.LocalHospital, Color(0xFFF44336), false),
    NewsArticle("AI Voice Cloning Scams Target Elderly Victims", "FBI warns of sophisticated scam using AI-generated voice calls impersonating family members for emergency money transfers.", "Threats", "The Hacker News", "https://thehackernews.com", "Recent", Icons.Default.RecordVoiceOver, Color(0xFF9C27B0), false),
    NewsArticle("Google Patches Critical Android Zero-Day", "New Android security update fixes a critical vulnerability actively exploited in the wild.", "Advisories", "CISA Alerts", "https://www.cisa.gov", "Recent", Icons.Default.SystemUpdate, Color(0xFFFF9800), false),
    NewsArticle("How to Spot Deepfake Videos: 7 Red Flags", "Security researchers share the latest techniques for identifying AI-generated video content.", "Tips", "Naked Security", "https://nakedsecurity.sophos.com", "Featured", Icons.Default.Videocam, Color(0xFF4CAF50), false),
    NewsArticle("Telegram Bot Selling Stolen Credit Cards Shut Down", "Law enforcement seized a Telegram bot network selling millions of stolen credit card numbers.", "Breaches", "Krebs on Security", "https://krebsonsecurity.com", "Featured", Icons.Default.CreditCard, Color(0xFFF44336), false),
    NewsArticle("New Quishing Attack Uses QR Codes to Steal Banking Credentials", "Cybercriminals are sending physical mail with malicious QR codes targeting banking customers.", "Threats", "Dark Reading", "https://www.darkreading.com", "Featured", Icons.Default.QrCode, Color(0xFF795548), false),
    NewsArticle("Critical Chrome Extension Stealing Browser Data", "Popular Chrome extension with 2M+ installs found exfiltrating browsing history and passwords.", "Breaches", "SecurityWeek", "https://www.securityweek.com", "Archive", Icons.Default.Extension, Color(0xFF607D8B), false),
    NewsArticle("Why You Should Enable Passkeys on Every Account", "Passkeys eliminate phishing risk entirely. Here's how to set them up on major platforms.", "Tips", "Naked Security", "https://nakedsecurity.sophos.com", "Archive", Icons.Default.Key, Color(0xFF4CAF50), false),
)
