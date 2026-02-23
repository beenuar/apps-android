package com.deepfakeshield.feature.shield

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.engine.UrlSafetyEngine
import com.deepfakeshield.core.ui.theme.*

/**
 * Safe Browser - sandboxed WebView with real-time URL safety analysis.
 * Integrates PhishTank, OpenPhish feeds + short link resolution. Blocks credential entry on phishing sites.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBrowserScreen(
    initialUrl: String = "",
    onNavigateBack: () -> Unit = {},
    viewModel: SafeBrowserViewModel = hiltViewModel()
) {
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val safetyResult by viewModel.urlAnalysis.collectAsState()
    var showBlockDialog by remember { mutableStateOf(false) }
    var isBrowsing by rememberSaveable { mutableStateOf(initialUrl.isNotBlank()) }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            viewModel.analyzeUrl(currentUrl)
        }
    }

    val safetyColor = when {
        (safetyResult?.riskScore ?: 0) >= 70 -> DangerRed
        (safetyResult?.riskScore ?: 0) >= 50 -> WarningYellow
        (safetyResult?.riskScore ?: 0) >= 30 -> Color(0xFFFF9800)
        else -> SafeGreen
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Safe Browser", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (currentUrl.isNotBlank()) {
                            Text(currentUrl.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // Safety indicator
                    safetyResult?.let { result ->
                        Surface(shape = RoundedCornerShape(8.dp), color = safetyColor.copy(alpha = 0.2f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    when { result.riskScore >= 70 -> Icons.Default.Dangerous; result.riskScore >= 50 -> Icons.Default.Warning; else -> Icons.Default.CheckCircle },
                                    null, tint = safetyColor, modifier = Modifier.size(16.dp)
                                )
                                Text("${result.riskScore}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = safetyColor)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Safety banner
            safetyResult?.let { result ->
                if (result.riskScore >= 50) {
                    Surface(color = safetyColor.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = safetyColor, modifier = Modifier.size(20.dp))
                            Text(
                                when { result.riskScore >= 70 -> "DANGEROUS: This site may steal your information"; result.riskScore >= 50 -> "CAUTION: This site has suspicious characteristics"; else -> "" },
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = safetyColor, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onNavigateBack) { Text("Leave", color = safetyColor) }
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            // Tor VPN status banner
            if (com.deepfakeshield.core.network.TorNetworkModule.isConnected) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF7C4DFF).copy(alpha = 0.08f))) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, Modifier.size(18.dp), tint = Color(0xFF7C4DFF))
                        Spacer(Modifier.width(8.dp))
                        Text("All traffic through Tor — IP hidden", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C4DFF), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // WebView or URL entry
            if (isBrowsing && currentUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false // Disabled for safety
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    url?.let { currentUrl = it }
                                    isLoading = true
                                }
                                override fun onPageFinished(view: WebView?, url: String?) { isLoading = false }
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val requestUrl = request?.url?.toString() ?: return false
                                    // Block non-HTTP schemes (javascript:, intent://, data:, file://)
                                    val scheme = request.url?.scheme?.lowercase()
                                    if (scheme !in listOf("http", "https")) return true
                                    // analyzeUrlSync uses local pattern matching only (no network) so it's fast
                                    try {
                                        val analysis = viewModel.analyzeUrlSync(requestUrl)
                                        if (analysis.riskScore >= 70) { showBlockDialog = true; return true }
                                    } catch (e: Exception) {
                                        android.util.Log.w("SafeBrowser", "URL analysis failed: ${e.message}")
                                    }
                                    currentUrl = requestUrl
                                    return false
                                }
                            }
                            loadUrl(currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.clearHistory()
                        webView.onPause()
                        webView.removeAllViews()
                        webView.destroy()
                    }
                )
            } else {
                // URL entry screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Safe Browser",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter a URL to browse safely with real-time threat analysis",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Enter URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Language, null) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val url = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                                urlInput
                            } else {
                                "https://$urlInput"
                            }
                            currentUrl = url
                            isBrowsing = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = urlInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Safely")
                    }
                }
            }
        }
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            icon = { Icon(Icons.Default.Dangerous, null, tint = DangerRed, modifier = Modifier.size(32.dp)) },
            title = { Text("Dangerous Site Blocked", fontWeight = FontWeight.Bold) },
            text = { Text("This link was blocked because it appears to be a phishing or malware site. Opening it could compromise your personal information.") },
            confirmButton = { TextButton(onClick = { showBlockDialog = false; onNavigateBack() }) { Text("Go Back to Safety") } },
            dismissButton = { TextButton(onClick = { showBlockDialog = false /* dismiss dialog, stay on current page */ }) { Text("Dismiss") } }
        )
    }
}
