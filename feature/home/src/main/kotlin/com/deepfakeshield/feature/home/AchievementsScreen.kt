package com.deepfakeshield.feature.home

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val isUnlocked: Boolean,
    val progress: Float,
    val xpReward: Int,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    // Build achievements based on real app usage data from ViewModel
    val achievements = remember(uiState.alertCount, uiState.threatsDetected, uiState.allShieldsEnabled, uiState.unlockedIds) { 
        getAchievements(uiState.alertCount, uiState.threatsDetected, uiState.allShieldsEnabled, uiState.unlockedIds) 
    }
    LaunchedEffect(achievements) {
        try {
            achievements.filter { it.isUnlocked && it.id !in uiState.unlockedIds }.forEach { a ->
                viewModel.persistUnlock(a.id)
            }
        } catch (_: Exception) { }
    }
    val totalXp = achievements.filter { it.isUnlocked }.sumOf { it.xpReward }
    val level = totalXp / 200 + 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Level card
            AnimatedFadeIn {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Level $level", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("$totalXp XP Total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Spacer(Modifier.height(12.dp))
                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(progress = (totalXp % 200) / 200f, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(4.dp))
                        Text("${200 - (totalXp % 200)} XP to next level", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Stats row
            AnimatedFadeIn(delayMillis = 100) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${achievements.count { it.isUnlocked }}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = SafeGreen)
                            Text("Unlocked", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${achievements.count { !it.isUnlocked }}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                            Text("Locked", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Categories
            val categories = achievements.groupBy { it.category }
            categories.forEach { (category, badgeList) ->
                AnimatedFadeIn(delayMillis = 200) {
                    Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                badgeList.forEachIndexed { index, achievement ->
                    StaggeredAnimation(index = index) { AchievementCard(achievement) { shareText ->
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(Intent.createChooser(intent, "Share Achievement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }}
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, onShare: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (achievement.isUnlocked) achievement.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (achievement.isUnlocked) achievement.color.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(achievement.icon, null, tint = if (achievement.isUnlocked) achievement.color else Color.Gray, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(achievement.title, fontWeight = FontWeight.SemiBold, color = if (achievement.isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = if (achievement.isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                if (!achievement.isUnlocked && achievement.progress > 0) {
                    Spacer(Modifier.height(4.dp))
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(progress = achievement.progress, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
                }
            }
            if (achievement.isUnlocked) {
                IconButton(onClick = { onShare("I earned the '${achievement.title}' badge on DeepFake Shield! +${achievement.xpReward}XP\n\nProtect yourself too: https://deepfakeshield.app\n#DeepfakeShield") }) {
                    Icon(Icons.Default.Share, null, tint = achievement.color, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun getAchievements(
    alertCount: Int = 0,
    threatsDetected: Int = 0,
    allShieldsEnabled: Boolean = false,
    unlockedIds: Set<String> = emptySet()
): List<Achievement> {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    // Night Owl: once unlocked (persisted), stays unlocked forever
    val nightOwlUnlocked = "night_owl" in unlockedIds || hour in 0..4
    return listOf(
        Achievement("first_scan", "First Scan", "Complete your first message scan", Icons.Default.Search, Color(0xFF4CAF50),
            isUnlocked = alertCount > 0, progress = if (alertCount > 0) 1f else 0f, xpReward = 50, category = "Getting Started"),
        Achievement("shield_up", "Shield Up!", "Enable all protection shields", Icons.Default.Shield, Color(0xFF2196F3),
            isUnlocked = allShieldsEnabled, progress = if (allShieldsEnabled) 1f else 0.5f, xpReward = 100, category = "Getting Started"),
        Achievement("scam_spotter", "Scam Spotter", "Detect 10 threats", Icons.Default.Visibility, Color(0xFFFF9800),
            isUnlocked = threatsDetected >= 10, progress = (threatsDetected.coerceAtMost(10) / 10f), xpReward = 200, category = "Detection"),
        Achievement("link_detective", "Link Detective", "Detect 25 threats", Icons.Default.Link, Color(0xFF00BCD4),
            isUnlocked = threatsDetected >= 25, progress = (threatsDetected.coerceAtMost(25) / 25f), xpReward = 200, category = "Detection"),
        Achievement("deepfake_hunter", "Deepfake Hunter", "Detect 50 threats", Icons.Default.VideoLibrary, Color(0xFFF44336),
            isUnlocked = threatsDetected >= 50, progress = (threatsDetected.coerceAtMost(50) / 50f), xpReward = 300, category = "Detection"),
        Achievement("night_owl", "Night Owl", "Use the app after midnight", Icons.Default.NightsStay, Color(0xFF3F51B5),
            isUnlocked = nightOwlUnlocked, progress = if (nightOwlUnlocked) 1f else 0f, xpReward = 50, category = "Special"),
        Achievement("alert_100", "Centurion", "Have 100+ total alerts processed", Icons.Default.Notifications, Color(0xFF673AB7),
            isUnlocked = alertCount >= 100, progress = (alertCount.coerceAtMost(100) / 100f), xpReward = 500, category = "Milestones")
    )
}
