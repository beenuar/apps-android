package com.deepfakeshield.feature.shield

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deepfakeshield.core.engine.UrlSafetyEngine
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * QR Code / URL Scanner Screen
 * 
 * Since CameraX barcode scanning requires complex camera setup,
 * this provides a manual URL input with deep analysis using UrlSafetyEngine.
 * Users can paste URLs from QR code scanner apps or type them directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var urlInput by rememberSaveable { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<UrlSafetyEngine.UrlAnalysis?>(null) }
    var isAnalyzing by rememberSaveable { mutableStateOf(false) }
    
    val urlSafetyEngine = remember { UrlSafetyEngine() }
    val scope = rememberCoroutineScope()
    // Use remember (same as analysisResult/isAnalyzing) so all state resets together on config change.
    // Prevents stale error shown with no result after rotation.
    var analysisError by remember { mutableStateOf<String?>(null) }
    
    fun analyzeUrl() {
        if (urlInput.isBlank()) return
        isAnalyzing = true
        analysisError = null
        val url = if (!urlInput.startsWith("http")) "https://$urlInput" else urlInput
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    urlSafetyEngine.analyzeUrl(url)
                }
                analysisResult = result
            } catch (e: Exception) {
                analysisError = "Analysis failed: ${e.message}"
            } finally {
                isAnalyzing = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("URL Safety Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info Card
            AnimatedFadeIn {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                        Column {
                            Text("Paste any URL to check safety", 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Scan QR codes with your camera, then paste the URL here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            
            // URL Input
            AnimatedFadeIn(delayMillis = 100) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter URL") },
                    placeholder = { Text("https://example.com") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { urlInput = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); analyzeUrl() }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            // Paste from Clipboard
            AnimatedFadeIn(delayMillis = 150) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clipData = clipboard?.primaryClip
                            val pasted = if (clipData != null && clipData.itemCount > 0) clipData.getItemAt(0)?.text?.toString() else null
                            if (pasted.isNullOrBlank()) {
                                android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                urlInput = pasted
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste")
                    }
                    
                    Button(
                        onClick = { analyzeUrl() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = urlInput.isNotBlank() && !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Analyze")
                    }
                }
            }
            
            // Results
            analysisResult?.let { result ->
                AnimatedFadeIn {
                    val (bgColor, statusIcon, statusText) = when {
                        result.riskScore >= 70 -> Triple(
                            DangerRed.copy(alpha = 0.1f),
                            Icons.Default.Dangerous,
                            "DANGEROUS"
                        )
                        result.riskScore >= 40 -> Triple(
                            WarningYellow.copy(alpha = 0.15f),
                            Icons.Default.Warning,
                            "SUSPICIOUS"
                        )
                        result.riskScore >= 15 -> Triple(
                            Color(0xFFFFF3E0),
                            Icons.Default.Info,
                            "CAUTION"
                        )
                        else -> Triple(
                            SafeGreen.copy(alpha = 0.1f),
                            Icons.Default.CheckCircle,
                            "APPEARS SAFE"
                        )
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(statusIcon, null, 
                                        tint = when {
                                            result.riskScore >= 70 -> DangerRed
                                            result.riskScore >= 40 -> WarningYellow
                                            else -> SafeGreen
                                        },
                                        modifier = Modifier.size(28.dp))
                                    Text(statusText, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge)
                                }
                                
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        result.riskScore >= 70 -> DangerRed
                                        result.riskScore >= 40 -> WarningYellow
                                        else -> SafeGreen
                                    }
                                ) {
                                    Text(
                                        "${result.riskScore}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            // Domain info
                            Text("Domain: ${result.domain}", style = MaterialTheme.typography.bodyMedium)
                            Text("Protocol: ${if (result.isHttps) "HTTPS (Secure)" else "HTTP (Not Secure)"}",
                                style = MaterialTheme.typography.bodyMedium)
                            
                            HorizontalDivider()
                            
                            // Recommendation
                            Text(result.recommendation, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            
                            // Threats
                            if (result.threats.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Detected Issues:", 
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold)
                                
                                result.threats.forEach { threat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            when (threat.severity) {
                                                "CRITICAL" -> Icons.Default.Dangerous
                                                "HIGH" -> Icons.Default.Warning
                                                else -> Icons.Default.Info
                                            },
                                            null,
                                            modifier = Modifier.size(18.dp),
                                            tint = when (threat.severity) {
                                                "CRITICAL" -> DangerRed
                                                "HIGH" -> SeverityHigh
                                                "MEDIUM" -> WarningYellow
                                                else -> InfoBlue
                                            }
                                        )
                                        Column {
                                            Text(threat.description, 
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            
                            // Share result
                            OutlinedButton(
                                onClick = {
                                    val shareText = "URL Safety Check Result:\n" +
                                        "URL: ${result.url}\n" +
                                        "Status: $statusText\n" +
                                        "Risk Score: ${result.riskScore}/100\n" +
                                        "${result.recommendation}\n\n" +
                                        "Scanned with DeepFake Shield"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Share Result")
                            }
                        }
                    }
                }
            }
            
            // Error display
            analysisError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Tips
            AnimatedFadeIn(delayMillis = 200) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("URL Safety Tips", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Text("- Always check for HTTPS before entering personal info", 
                            style = MaterialTheme.typography.bodySmall)
                        Text("- Beware of URLs with misspelled brand names", 
                            style = MaterialTheme.typography.bodySmall)
                        Text("- Shortened URLs (bit.ly, etc.) can hide malicious links", 
                            style = MaterialTheme.typography.bodySmall)
                        Text("- Check for excessive subdomains (login.bank.evil.com)", 
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
