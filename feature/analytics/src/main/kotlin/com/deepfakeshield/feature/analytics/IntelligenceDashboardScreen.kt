package com.deepfakeshield.feature.analytics

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.components.*
import com.deepfakeshield.core.ui.theme.*

/**
 * INTELLIGENCE DASHBOARD
 * Shows all advanced engine capabilities
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceDashboardScreen(
    viewModel: IntelligenceDashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Intelligence Center")
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(uiState.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Card - Network Stats
            AnimatedFadeIn {
                GradientCard(
                    gradient = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Community Network",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "${uiState.networkStats.totalUsers.format()} users protecting each other",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            
                            AnimatedShieldIcon(isActive = true, size = 56.dp)
                        }
                        
                        // Stats Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NetworkStatItem("Threats Blocked", uiState.networkStats.totalThreats.format())
                            NetworkStatItem("Today", "+${uiState.networkStats.threatsToday}")
                            NetworkStatItem("Your Score", "${uiState.networkStats.protectionScore}")
                        }
                    }
                }
            }
            
            // Learning Statistics
            StaggeredAnimation(index = 0) {
                Text(
                    "Adaptive Learning",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            StaggeredAnimation(index = 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LearningStatRow(
                            label = "Accuracy Rate",
                            value = "${(uiState.learningStats.accuracyRate * 100).toInt()}%",
                            icon = Icons.Default.CheckCircle,
                            color = SafeGreen
                        )
                        
                        LearningStatRow(
                            label = "False Positives",
                            value = "${(uiState.learningStats.falsePositiveRate * 100).toInt()}%",
                            icon = Icons.Default.Warning,
                            color = WarningYellow
                        )
                        
                        LearningStatRow(
                            label = "New Patterns Discovered",
                            value = "${uiState.learningStats.newPatternsDiscovered}",
                            icon = Icons.Default.Lightbulb,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        LearningStatRow(
                            label = "Total Feedback",
                            value = "${uiState.learningStats.totalFeedback}",
                            icon = Icons.Default.Feedback,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            // Threat Forecast
            StaggeredAnimation(index = 2) {
                Text(
                    "Threat Forecast",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            uiState.threatForecast.predictions.take(3).forEachIndexed { index, prediction ->
                StaggeredAnimation(index = index + 3) {
                    ThreatPredictionCard(prediction = prediction)
                }
            }
            
            // User Risk Profile
            if (uiState.userRiskProfile != null) {
                StaggeredAnimation(index = 6) {
                    Text(
                        "Your Risk Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                uiState.userRiskProfile?.let { profile ->
                    StaggeredAnimation(index = 7) {
                        UserRiskProfileCard(profile = profile)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NetworkStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun LearningStatRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ThreatPredictionCard(prediction: com.deepfakeshield.core.intelligence.ThreatPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                prediction.probability > 0.7f -> WarningYellow.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    prediction.threatType.replace("_", " ").capitalize(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        prediction.probability > 0.7f -> WarningYellow
                        else -> MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Text(
                        "${(prediction.probability * 100).toInt()}% likely",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(
                prediction.reasoning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (prediction.preventionTips.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Prevention Tips:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                prediction.preventionTips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", modifier = Modifier.padding(end = 8.dp))
                        Text(
                            tip,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRiskProfileCard(profile: com.deepfakeshield.core.intelligence.UserRiskProfile) {
    GradientCard(
        gradient = when {
            profile.overallRiskScore > 70 -> listOf(SeverityHigh.copy(alpha = 0.8f), SeverityCritical)
            profile.overallRiskScore > 40 -> listOf(WarningYellow.copy(alpha = 0.8f), SeverityMedium)
            else -> listOf(SafeGreen.copy(alpha = 0.8f), SafeGreen)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Risk Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Your Risk Score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        when {
                            profile.overallRiskScore > 70 -> "High Risk - Extra Caution Needed"
                            profile.overallRiskScore > 40 -> "Moderate Risk - Stay Alert"
                            else -> "Low Risk - Well Protected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                Text(
                    "${profile.overallRiskScore}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Vulnerabilities
            if (profile.vulnerabilities.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "⚠️ Vulnerabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        profile.vulnerabilities.forEach { vuln ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = WarningYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        vuln.type.replace("_", " ").capitalize(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        vuln.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Recommendations
            if (profile.recommendations.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "✨ Recommendations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        profile.recommendations.forEach { rec ->
                            Row {
                                Text("•", modifier = Modifier.padding(end = 8.dp))
                                Text(rec, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Int.format(): String {
    return when {
        this >= 1000000 -> "${this / 1000000}M"
        this >= 1000 -> "${this / 1000}K"
        else -> this.toString()
    }
}

private fun String.capitalize(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}
