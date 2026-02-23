package com.deepfakeshield.feature.shield

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.components.*
import com.deepfakeshield.core.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScanScreen(
    viewModel: MessageShieldViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textInput by rememberSaveable { mutableStateOf("") }
    
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            scope.launch { viewModel.enableSmsScanning() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Message & Link Shield")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        
        // Auto-scroll to results when scan completes
        LaunchedEffect(uiState.scanResult) {
            if (uiState.scanResult != null) {
                // Small delay for animation to start, then scroll to show result
                kotlinx.coroutines.delay(200)
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Primary: Paste & Scan — front and center
            AnimatedFadeIn {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste message or link to scan...") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (textInput.isNotBlank() && !uiState.isScanning) scope.launch { viewModel.scanText(textInput) } }
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipData = clipboardManager?.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val pastedText = clipData.getItemAt(0).text?.toString()
                                    if (pastedText.isNullOrBlank()) {
                                        android.widget.Toast.makeText(context, "Clipboard is empty or has no text", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        textInput = pastedText
                                        scope.launch { viewModel.scanText(pastedText) }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isScanning
                        ) {
                            Icon(Icons.Filled.ContentPaste, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Paste & Scan")
                        }
                        Button(
                            onClick = { if (textInput.isNotBlank()) scope.launch { viewModel.scanText(textInput) } },
                            modifier = Modifier.weight(1f),
                            enabled = textInput.isNotBlank() && !uiState.isScanning
                        ) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Scan")
                            }
                        }
                    }
                }
            }

            // Error display - show RIGHT AFTER scan area
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Filled.Close, "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Demo button - immediately accessible
            FilledTonalButton(
                onClick = {
                    textInput = "URGENT! Your bank account has been compromised. Click here: http://secure-bank-login.xyz/verify or your account will be closed. Reply with your OTP."
                    scope.launch { viewModel.scanText(textInput) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isScanning
            ) {
                Icon(Icons.Filled.Science, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try demo scam")
            }

            // SCAN RESULT - show immediately after scan area so user ALWAYS sees it
            AnimatedFadeIn(visible = uiState.scanResult != null) {
                uiState.scanResult?.let { result ->
                    EnhancedScanResultCard(
                        result = result,
                        onActionTap = { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() }
                    )
                }
            }

            // Compact status
            AnimatedFadeIn(delayMillis = 50) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.smsEnabled || uiState.notificationEnabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (uiState.smsEnabled || uiState.notificationEnabled) SafeGreen else Color.Gray)) {}
                        Text(
                            if (uiState.smsEnabled || uiState.notificationEnabled) "Auto-scanning messages"
                            else "Enable below for real-time protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Feature toggles — compact
            AnimatedFadeIn(delayMillis = 80) {
                Text("Protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            
            StaggeredAnimation(index = 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.smsEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Message,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("SMS Scanning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Scan incoming messages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.smsEnabled,
                            onCheckedChange = {
                                if (it) {
                                    smsPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS
                                        )
                                    )
                                } else {
                                    scope.launch { viewModel.disableSmsScanning() }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = SafeGreen,
                                checkedThumbColor = Color.White
                            )
                        )
                    }
                }
            }
            
            StaggeredAnimation(index = 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.notificationEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notification Scanning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Scan app notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.notificationEnabled) SafeGreen else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (uiState.notificationEnabled) "Enabled" else "Enable")
                        }
                    }
                }
            }
            
            StaggeredAnimation(index = 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.clipboardEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Clipboard Guard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Scan copied content",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.clipboardEnabled,
                            onCheckedChange = { scope.launch { viewModel.toggleClipboardScanning(it) } },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = SafeGreen,
                                checkedThumbColor = Color.White
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EnhancedScanResultCard(result: RiskResult, onActionTap: (String) -> Unit = {}) {
    AnimatedScaleIn {
        GradientCard(
            gradient = when (result.severity) {
                RiskSeverity.CRITICAL -> listOf(SeverityCritical.copy(alpha = 0.9f), SeverityCritical)
                RiskSeverity.HIGH -> listOf(SeverityHigh.copy(alpha = 0.8f), SeverityHigh)
                RiskSeverity.MEDIUM -> listOf(SeverityMedium.copy(alpha = 0.7f), SeverityMedium)
                RiskSeverity.LOW -> listOf(SafeGreen.copy(alpha = 0.7f), SafeGreen)
            },
            elevation = 8.dp
        ) {
            // Threat Level Indicator
            ThreatLevelIndicator(
                severity = result.severity,
                score = result.score
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Explanation
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.95f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = result.explainLikeImFive,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (result.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "🔍 Why This Was Flagged",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        result.reasons.forEachIndexed { index, reason ->
                            StaggeredAnimation(index = index) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SeverityCritical.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = SeverityCritical
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            reason.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            reason.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (result.recommendedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "✨ Recommended Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        result.recommendedActions.filter { it.isPrimary }.forEach { action ->
                            Button(
                                onClick = { onActionTap(action.description) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(action.title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        result.recommendedActions.filterNot { it.isPrimary }.forEach { action ->
                            OutlinedButton(
                                onClick = { onActionTap(action.description) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(action.title)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanResultCard(result: RiskResult, onActionTap: (String) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.severity) {
                RiskSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                RiskSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                RiskSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                RiskSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scan Result", style = MaterialTheme.typography.titleMedium)
                SeverityBadge(result.severity)
            }
            
            ConfidenceIndicator(result.confidence)
            
            HorizontalDivider()
            
            Text(
                text = result.explainLikeImFive,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (result.reasons.isNotEmpty()) {
                HorizontalDivider()
                Text("Why This Was Flagged:", style = MaterialTheme.typography.titleSmall)
                
                result.reasons.forEach { reason ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reason.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                reason.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            if (result.recommendedActions.isNotEmpty()) {
                HorizontalDivider()
                Text("Recommended Actions:", style = MaterialTheme.typography.titleSmall)
                
                result.recommendedActions.filter { it.isPrimary }.forEach { action ->
                    Button(
                        onClick = { onActionTap(action.description) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(action.title)
                    }
                }
                
                result.recommendedActions.filterNot { it.isPrimary }.forEach { action ->
                    OutlinedButton(
                        onClick = { onActionTap(action.description) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(action.title)
                    }
                }
            }
        }
    }
}
