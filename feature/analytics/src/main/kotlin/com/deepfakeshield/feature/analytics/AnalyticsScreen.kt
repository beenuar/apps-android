package com.deepfakeshield.feature.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protection Analytics", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary cards row
            ProtectionSummaryCard(uiState.summary)
            // Scan activity
            if (uiState.videoScansTotal > 0 || uiState.messageScansTotal > 0) {
                ScanActivityCard(
                    videoScans = uiState.videoScansTotal,
                    messageScans = uiState.messageScansTotal
                )
            }

            // Threats chart
            if (uiState.threatsByDay.isNotEmpty()) {
                ThreatsBlockedChart(uiState.threatsByDay)
            }

            // Threat types breakdown
            if (uiState.threatsByType.isNotEmpty()) {
                ThreatTypesCard(uiState.threatsByType)
            }

            // Recent activity
            RecentActivityCard(uiState.recentThreats)

            // Insights
            if (uiState.insights.isNotEmpty()) {
                InsightsCard(uiState.insights)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProtectionSummaryCard(summary: ProtectionSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Protection Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItem(
                    "Threats\nBlocked",
                    summary.threatsBlocked.toString(),
                    Color(0xFF4CAF50)
                )
                SummaryItem(
                    "Safety\nScore",
                    "${summary.safetyScore}%",
                    if (summary.safetyScore >= 80) Color(0xFF2196F3) else Color(0xFFFF9800)
                )
                SummaryItem(
                    "Active\nDays",
                    summary.activeDays.toString(),
                    Color(0xFF9C27B0)
                )
            }
        }
    }
}

@Composable
private fun ScanActivityCard(videoScans: Int, messageScans: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Scan Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItem("Videos\nScanned", videoScans.toString(), Color(0xFF2196F3))
                SummaryItem("Messages\nScanned", messageScans.toString(), Color(0xFF4CAF50))
                SummaryItem("Total\nScans", (videoScans + messageScans).toString(), Color(0xFF9C27B0))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ThreatsBlockedChart(data: List<DailyThreatData>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Threats Blocked (Last 7 Days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (data.isNotEmpty()) {
                // Bar chart instead of line chart for better readability
                BarChart(
                    data = data,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Day labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    data.forEach { day ->
                        Text(
                            day.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No threat data yet. Start using the app to see statistics.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChart(data: List<DailyThreatData>, modifier: Modifier = Modifier) {
    val maxValue = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val barWidth = size.width / (data.size * 2f)
        val totalWidth = size.width
        val spacing = totalWidth / data.size

        data.forEachIndexed { index, dailyData ->
            val barHeight = if (maxValue > 0) {
                (dailyData.count.toFloat() / maxValue) * size.height * 0.85f
            } else 0f

            val x = spacing * index + (spacing - barWidth) / 2

            // Background bar
            drawRoundRect(
                color = surfaceColor,
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )

            // Data bar
            if (barHeight > 0) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
            }
        }
    }
}

@Composable
private fun ThreatTypesCard(types: Map<String, Int>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Threat Types Detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            val total = types.values.sum().coerceAtLeast(1)
            val colors = listOf(
                Color(0xFFE53935),
                Color(0xFFFF9800),
                Color(0xFFFBC02D),
                Color(0xFF66BB6A),
                Color(0xFF42A5F5)
            )

            types.entries.forEachIndexed { index, (type, count) ->
                ThreatTypeItem(
                    type = type,
                    count = count,
                    total = total,
                    color = colors[index % colors.size]
                )
                if (index < types.size - 1) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ThreatTypeItem(type: String, count: Int, total: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "$count (${(count * 100 / total)}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = count.toFloat() / total,
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
    }
}

@Composable
private fun RecentActivityCard(threats: List<RecentThreat>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (threats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            "No recent threats. You're safe!",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                val displayedThreats = threats.take(5)
                displayedThreats.forEachIndexed { index, threat ->
                    RecentThreatItem(threat)
                    if (index < displayedThreats.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentThreatItem(threat: RecentThreat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Shield,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(threat.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    threat.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
        ) {
            Text(
                "Blocked",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InsightsCard(insights: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Insights & Recommendations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            insights.forEach { insight ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.TipsAndUpdates,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        insight,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// Data classes
data class ProtectionSummary(
    val threatsBlocked: Int = 0,
    val safetyScore: Int = 100,
    val activeDays: Int = 0
)

data class DailyThreatData(
    val dayLabel: String,
    val count: Int
)

data class RecentThreat(
    val type: String,
    val timeAgo: String
)
