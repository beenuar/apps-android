package com.deepfakeshield.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfakeshield.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val userPreferences: com.deepfakeshield.data.preferences.UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            combine(
                alertRepository.getAllAlerts(),
                userPreferences.videoScansCount,
                userPreferences.messageScansCount
            ) { alerts, videoScans, messageScans ->
                Triple(alerts, videoScans, messageScans)
            }.distinctUntilChanged()
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("AnalyticsVM", "Flow error: ${e.message}")
            }.collect { (alerts, videoScans, messageScans) ->
                if (alerts.isEmpty() && videoScans == 0 && messageScans == 0) {
                    _uiState.update {
                        it.copy(
                            summary = ProtectionSummary(
                                threatsBlocked = 0,
                                safetyScore = 100,
                                activeDays = 1
                            ),
                            threatsByDay = emptyList(),
                            threatsByType = emptyMap(),
                            recentThreats = emptyList(),
                            insights = listOf(
                                "Welcome! Your protection is active.",
                                "All shields are monitoring for threats.",
                                "Tap Scan Message or Scan Video to see detection in action."
                            ),
                            videoScansTotal = 0,
                            messageScansTotal = 0
                        )
                    }
                    return@collect
                }

                // Calculate real statistics from actual alerts
                val totalThreats = alerts.size
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60 * 60 * 1000
                val oldestAlert = alerts.minOfOrNull { it.timestamp } ?: now
                val activeDays = ((now - oldestAlert) / dayMs).toInt().coerceAtLeast(1)

                // Safety score: based on handled vs unhandled ratio
                val handledCount = alerts.count { it.isHandled }
                val safetyScore = if (totalThreats > 0) {
                    ((handledCount.toFloat() / totalThreats) * 100).toInt().coerceIn(0, 100)
                } else 100

                // Threats by day (last 7 days) — aligned to midnight so "Today" is correct
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayMidnight = calendar.timeInMillis
                val threatsByDay = (6 downTo 0).map { daysAgo ->
                    val dayStart = todayMidnight - (daysAgo * dayMs)
                    val dayEnd = dayStart + dayMs
                    val count = alerts.count { it.timestamp in dayStart until dayEnd }
                    val label = dayFormat.format(Date(dayStart))
                    DailyThreatData(label, count)
                }

                // Threats by type
                val threatsByType = alerts
                    .groupBy { it.threatType.name.replace("_", " ") }
                    .mapValues { it.value.size }
                    .entries.sortedByDescending { it.value }
                    .take(5)
                    .associate { it.key to it.value }

                // Recent threats
                val recentThreats = alerts
                    .sortedByDescending { it.timestamp }
                    .take(5)
                    .map { alert ->
                        val ago = formatTimeAgo(now - alert.timestamp)
                        RecentThreat(
                            type = alert.title,
                            timeAgo = ago
                        )
                    }

                // Dynamic insights
                val insights = buildList {
                    if (totalThreats > 10) {
                        add("You've blocked $totalThreats threats! Stay vigilant.")
                    }
                    val topType = threatsByType.entries.firstOrNull()
                    if (topType != null) {
                        add("${topType.key} is the most common threat type (${topType.value} detected).")
                    }
                    val unhandled = alerts.count { !it.isHandled }
                    if (unhandled > 0) {
                        add("You have $unhandled unhandled alerts. Review them for better protection.")
                    }
                    if (safetyScore >= 90) {
                        add("Excellent! Your safety score is ${safetyScore}%. Keep it up!")
                    } else if (safetyScore < 60) {
                        add("Your safety score is ${safetyScore}%. Review and handle alerts to improve it.")
                    }
                    if (isEmpty()) {
                        add("All shields are actively protecting you.")
                    }
                }

                val insightsWithScans = buildList {
                    addAll(insights)
                    if (videoScans > 0 || messageScans > 0) {
                        add("You've run ${videoScans + messageScans} scans (${videoScans} video, ${messageScans} message).")
                    }
                }

                _uiState.update {
                    it.copy(
                        summary = ProtectionSummary(
                            threatsBlocked = totalThreats,
                            safetyScore = safetyScore,
                            activeDays = activeDays
                        ),
                        threatsByDay = threatsByDay,
                        threatsByType = threatsByType,
                        recentThreats = recentThreats,
                        insights = if (insightsWithScans.isNotEmpty()) insightsWithScans else insights,
                        videoScansTotal = videoScans,
                        messageScansTotal = messageScans
                    )
                }
            }
        }
    }

    private fun formatTimeAgo(millisAgo: Long): String {
        val minutes = millisAgo / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }
}

data class AnalyticsUiState(
    val summary: ProtectionSummary = ProtectionSummary(),
    val threatsByDay: List<DailyThreatData> = emptyList(),
    val threatsByType: Map<String, Int> = emptyMap(),
    val recentThreats: List<RecentThreat> = emptyList(),
    val insights: List<String> = emptyList(),
    val videoScansTotal: Int = 0,
    val messageScansTotal: Int = 0
)
