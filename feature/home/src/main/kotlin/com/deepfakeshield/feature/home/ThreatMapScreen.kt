package com.deepfakeshield.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreatHotspot(
    val region: String,
    val threatCount: Int,
    val primaryThreat: String,
    val trend: String, // "up", "down", "stable"
    val x: Float,
    val y: Float
)

data class ThreatMapUiState(
    val hotspots: List<ThreatHotspot> = emptyList(),
    val totalThreats: Int = 0,
    val hasData: Boolean = false
)

@HiltViewModel
class ThreatMapViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ThreatMapUiState())
    val uiState: StateFlow<ThreatMapUiState> = _uiState.asStateFlow()
    
    init {
        loadRealThreatData()
    }
    
    /**
     * Load real threat data from the alert database.
     * Aggregates detected threats by type and displays actual counts.
     */
    private fun loadRealThreatData() {
        viewModelScope.launch {
            alertRepository.getAllAlerts().catch { e ->
                Log.e("ThreatMapVM", "Error loading alerts", e)
                emit(emptyList())
            }.collect { alerts ->
                if (alerts.isEmpty()) {
                    _uiState.update { it.copy(hotspots = emptyList(), totalThreats = 0, hasData = false) }
                    return@collect
                }
                
                // Group alerts by threat type and create real hotspot data
                val threatGroups = alerts.groupBy { it.threatType }
                val hotspots = mutableListOf<ThreatHotspot>()
                
                // Map threat types to display regions (visual positioning on the map)
                val threatMappings = mapOf(
                    ThreatType.SCAM_MESSAGE to Triple("Scam Messages", 0.25f, 0.3f),
                    ThreatType.PHISHING_ATTEMPT to Triple("Phishing", 0.65f, 0.25f),
                    ThreatType.DEEPFAKE_VIDEO to Triple("Deepfakes", 0.45f, 0.45f),
                    ThreatType.SUSPICIOUS_CALL to Triple("Suspicious Calls", 0.75f, 0.55f),
                    ThreatType.MALICIOUS_LINK to Triple("Malicious Links", 0.35f, 0.65f),
                    ThreatType.IMPERSONATION to Triple("Impersonation", 0.55f, 0.35f),
                    ThreatType.OTP_TRAP to Triple("OTP Traps", 0.2f, 0.55f),
                    ThreatType.PAYMENT_SCAM to Triple("Payment Scams", 0.8f, 0.4f),
                    ThreatType.REMOTE_ACCESS_SCAM to Triple("Remote Access Scams", 0.3f, 0.5f),
                    ThreatType.ROMANCE_SCAM to Triple("Romance Scams", 0.7f, 0.65f),
                    ThreatType.JOB_SCAM to Triple("Job Scams", 0.15f, 0.4f),
                    ThreatType.CRYPTO_SCAM to Triple("Crypto Scams", 0.6f, 0.5f),
                    ThreatType.UNKNOWN to Triple("Unknown Threats", 0.5f, 0.7f)
                )
                
                // Get alerts from last 24 hours for trend calculation
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val twoDaysAgo = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000)
                
                threatGroups.forEach { (type, typeAlerts) ->
                    val mapping = threatMappings[type] ?: Triple(type.name, 0.5f, 0.5f)
                    
                    // Calculate real trend
                    val recentCount = typeAlerts.count { it.timestamp > oneDayAgo }
                    val olderCount = typeAlerts.count { it.timestamp in twoDaysAgo..oneDayAgo }
                    val trend = when {
                        recentCount > olderCount -> "up"
                        recentCount < olderCount -> "down"
                        else -> "stable"
                    }
                    
                    hotspots.add(
                        ThreatHotspot(
                            region = mapping.first,
                            threatCount = typeAlerts.size,
                            primaryThreat = "${typeAlerts.size} detected",
                            trend = trend,
                            x = mapping.second,
                            y = mapping.third
                        )
                    )
                }
                
                _uiState.update {
                    it.copy(
                        hotspots = hotspots.sortedByDescending { h -> h.threatCount },
                        totalThreats = alerts.size,
                        hasData = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatMapScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ThreatMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pulseAlpha = rememberPulseAnimation()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat Map", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedFadeIn {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (state.hasData) DangerRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MyLocation, null, tint = if (state.hasData) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Detected Threats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (state.hasData) {
                            Text("${state.totalThreats} threats detected on this device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("No threats detected yet. Your shields are active and monitoring.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (state.hasData) {
                // Visual threat map
                AnimatedFadeIn(delayMillis = 100) {
                    Card(modifier = Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(16.dp)) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw grid
                                for (i in 0..4) {
                                    val y = size.height * i / 4
                                    drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                }
                                for (i in 0..4) {
                                    val x = size.width * i / 4
                                    drawLine(Color.Gray.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                }
                                // Draw hotspots (sized by real threat count)
                                state.hotspots.forEach { spot ->
                                    val x = spot.x * size.width
                                    val y = spot.y * size.height
                                    val radius = (spot.threatCount * 3f).coerceIn(8f, 40f)
                                    drawCircle(DangerRed.copy(alpha = 0.15f * pulseAlpha), radius * 2.5f, Offset(x, y))
                                    drawCircle(DangerRed.copy(alpha = 0.3f * pulseAlpha), radius * 1.5f, Offset(x, y))
                                    drawCircle(DangerRed.copy(alpha = 0.7f), radius, Offset(x, y))
                                }
                            }
                            Text("Device Threat Map", modifier = Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                // Hotspot list
                AnimatedFadeIn(delayMillis = 200) { Text("Threats by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

                state.hotspots.forEachIndexed { index, spot ->
                    StaggeredAnimation(index = index) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(
                                    when { spot.threatCount > 10 -> DangerRed.copy(alpha = 0.2f); spot.threatCount > 3 -> WarningYellow.copy(alpha = 0.2f); else -> SafeGreen.copy(alpha = 0.2f) }
                                ), contentAlignment = Alignment.Center) {
                                    Text("${spot.threatCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                                        color = when { spot.threatCount > 10 -> DangerRed; spot.threatCount > 3 -> WarningYellow; else -> SafeGreen })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spot.region, fontWeight = FontWeight.SemiBold)
                                    Text(spot.primaryThreat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                @Suppress("DEPRECATION")
                                Icon(
                                    when (spot.trend) { "up" -> Icons.Default.TrendingUp; "down" -> Icons.Default.TrendingDown; else -> Icons.Default.TrendingFlat },
                                    null,
                                    tint = when (spot.trend) { "up" -> DangerRed; "down" -> SafeGreen; else -> WarningYellow },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // No threats detected - show explanation
                AnimatedFadeIn(delayMillis = 100) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Shield, null, modifier = Modifier.size(48.dp), tint = SafeGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("All Clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No threats have been detected on your device. The threat map will populate as the app scans messages, calls, and videos for potential threats.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Disclaimer
            AnimatedFadeIn(delayMillis = 400) {
                Text("Data is from your device only. No personal information is collected or shared.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
