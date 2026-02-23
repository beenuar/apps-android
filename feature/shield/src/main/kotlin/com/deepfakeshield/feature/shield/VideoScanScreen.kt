package com.deepfakeshield.feature.shield

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.model.RiskResult
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.ui.components.ConfidenceIndicator
import com.deepfakeshield.core.ui.components.SeverityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScanScreen(
    viewModel: VideoShieldViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onShowFullScreenOverlay: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanVideo(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Shield", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            // Hero: Choose video — primary action first
            Button(
                onClick = { videoPicker.launch("video/*") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isScanning,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Scanning... ${(uiState.scanProgress * 100).toInt()}%")
                } else {
                    Icon(Icons.Filled.Upload, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Choose Video to Scan", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (uiState.isScanning) {
                LinearProgressIndicator(
                    progress = { uiState.scanProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(uiState.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // Overlay — compact
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.systemWideEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Layers, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Full-Screen Overlay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(if (uiState.systemWideEnabled) "Active" else "Scan videos in other apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (uiState.systemWideEnabled) {
                        FilledTonalButton(onClick = { viewModel.stopSystemWideScanning() }) { Text("Stop") }
                    } else {
                        FilledTonalButton(onClick = { viewModel.startSystemWideScanning(null); onShowFullScreenOverlay() }) { Text("Enable") }
                    }
                }
            }

            // Error display
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

            // Scan result
            val ctx = LocalContext.current
            uiState.scanResult?.let { result ->
                VideoScanResultCard(
                    result = result,
                    onScanAnother = { viewModel.clearResult() },
                    onActionTap = { msg -> android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VideoScanResultCard(
    result: RiskResult,
    onScanAnother: () -> Unit = {},
    onActionTap: (String) -> Unit = {}
) {
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Analysis Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text("Analysis Details:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                result.reasons.forEach { reason ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reason.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
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
                Text("Recommended Actions:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                result.recommendedActions.forEach { action ->
                    if (action.isPrimary) {
                        Button(
                            onClick = { onActionTap(action.description) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(action.title)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onActionTap(action.description) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(action.title)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onScanAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Another Video")
            }
        }
    }
}
