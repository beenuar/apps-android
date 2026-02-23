package com.deepfakeshield

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.feature.home.HomeScreen
import com.deepfakeshield.feature.shield.MessageScanScreen
import com.deepfakeshield.feature.shield.VideoScanScreen
import com.deepfakeshield.feature.alerts.AlertsScreen
import com.deepfakeshield.feature.alerts.AlertDetailScreen
import com.deepfakeshield.feature.vault.VaultScreen
import com.deepfakeshield.feature.settings.SettingsScreen
import com.deepfakeshield.feature.settings.PrivacyScreen
import com.deepfakeshield.feature.education.EducationScreen
import com.deepfakeshield.feature.diagnostics.DiagnosticsScreen
import com.deepfakeshield.feature.callprotection.CallProtectionScreen
import com.deepfakeshield.feature.analytics.AnalyticsScreen
import com.deepfakeshield.feature.analytics.IntelligenceDashboardScreen
import com.deepfakeshield.feature.onboarding.OnboardingScreen
import com.deepfakeshield.feature.education.DailyChallengeScreen
import com.deepfakeshield.feature.shield.QrScannerScreen
import com.deepfakeshield.feature.shield.SafeBrowserScreen
// Deprecated: ReferralScreen, FamilyCircleScreen (no backend)
import com.deepfakeshield.feature.home.AchievementsScreen
import com.deepfakeshield.feature.home.ThreatMapScreen
import com.deepfakeshield.feature.onboarding.PermissionSetupScreen
import com.deepfakeshield.feature.onboarding.ScamSimulatorScreen
import com.deepfakeshield.feature.analytics.PrivacyScoreScreen
import com.deepfakeshield.feature.diagnostics.AppAuditorScreen
import com.deepfakeshield.feature.diagnostics.WifiScannerScreen
import com.deepfakeshield.feature.shield.BreachMonitorScreen
import com.deepfakeshield.feature.shield.PasswordCheckerScreen
import com.deepfakeshield.feature.shield.PhotoForensicsScreen
import com.deepfakeshield.feature.home.EmergencySosScreen
import com.deepfakeshield.feature.shield.DarkWebMonitorScreen
import com.deepfakeshield.feature.shield.ScamNumberLookupScreen
import com.deepfakeshield.feature.shield.FakeReviewDetectorScreen
import com.deepfakeshield.feature.analytics.DigitalFootprintScreen
import com.deepfakeshield.feature.analytics.DeviceTimelineScreen
import com.deepfakeshield.feature.home.SecurityNewsFeedScreen
import com.deepfakeshield.feature.vault.SecureNotesScreen
import com.deepfakeshield.feature.diagnostics.NetworkMonitorScreen
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.deepfakeshield.service.FloatingBubbleService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object PermissionSetup : Screen("permission_setup")
    object PermissionSetupStandalone : Screen("permission_setup_standalone")
    object ScamSimulator : Screen("scam_simulator")
    object Home : Screen("home")
    object Alerts : Screen("alerts")
    object AlertDetail : Screen("alert/{alertId}") {
        fun createRoute(alertId: Long) = "alert/$alertId"
    }
    object Vault : Screen("vault")
    object Settings : Screen("settings")
    object Privacy : Screen("privacy")
    object Education : Screen("education")
    object Diagnostics : Screen("diagnostics")
    object VideoScan : Screen("video_scan")
    object MessageScan : Screen("message_scan")
    object CallProtection : Screen("call_protection")
    object Analytics : Screen("analytics")
    object IntelligenceDashboard : Screen("intelligence_dashboard")
    object DailyChallenge : Screen("daily_challenge")
    object QrScanner : Screen("qr_scanner")
    object Referral : Screen("referral")
    object FamilyCircle : Screen("family_circle")
    object Achievements : Screen("achievements")
    object ThreatMap : Screen("threat_map")
    object SafeBrowser : Screen("safe_browser")
    object PrivacyScore : Screen("privacy_score")
    object AppAuditor : Screen("app_auditor")
    object BreachMonitor : Screen("breach_monitor")
    object WifiScanner : Screen("wifi_scanner")
    object PasswordChecker : Screen("password_checker")
    object PhotoForensics : Screen("photo_forensics")
    object EmergencySos : Screen("emergency_sos")
    object DarkWebMonitor : Screen("dark_web_monitor")
    object ScamNumberLookup : Screen("scam_number_lookup")
    object DigitalFootprint : Screen("digital_footprint")
    object SecurityNewsFeed : Screen("security_news_feed")
    object DeviceTimeline : Screen("device_timeline")
    object FakeReviewDetector : Screen("fake_review_detector")
    object SecureNotes : Screen("secure_notes")
    object NetworkMonitor : Screen("network_monitor")
    object EmailScanner : Screen("email_scanner")
    object ScamRecovery : Screen("scam_recovery")
    object WeeklyReport : Screen("weekly_report")
    object ClipboardMonitor : Screen("clipboard_monitor")
    object DeviceTheft : Screen("device_theft")
    object SecureFileVault : Screen("secure_file_vault")
    object ToolsHub : Screen("tools_hub")
    object ScanHub : Screen("scan_hub")
    object PermissionTimeline : Screen("permission_timeline")
    object DataBrokerOptOut : Screen("data_broker_optout")
    object SimSwapProtection : Screen("sim_swap_protection")
    object MetadataStripper : Screen("metadata_stripper")
    object DisposableEmail : Screen("disposable_email")
    object FakeDomainMonitor : Screen("fake_domain_monitor")
    object CommunityReport : Screen("community_report")
    object ShareScore : Screen("share_score")
    object IdentityDashboard2 : Screen("identity_dashboard_v2")
    object Personalization : Screen("personalization")
    object TorSettings : Screen("tor_settings")
    object WhatsNew : Screen("whats_new")
    object ThreatModel : Screen("threat_model")
}

@Composable
fun DeepfakeShieldApp(
    userPreferences: UserPreferences,
    pendingOpenRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
    onStartProtectionServices: () -> Unit = {}
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val onboardingDone = userPreferences.onboardingCompleted.first()
            startDestination = if (onboardingDone) Screen.Home.route else Screen.Onboarding.route
        } catch (_: Exception) {
            startDestination = Screen.Onboarding.route
        }
    }

    startDestination?.let { destination ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val topLevelRoutes = setOf(Screen.Home.route, Screen.ScanHub.route, Screen.Alerts.route, Screen.ToolsHub.route, Screen.Settings.route)
        val showBottomBar = currentRoute in topLevelRoutes

        data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)
        val navItems = listOf(
            NavItem(Screen.Home.route, "Home") { Icon(Icons.Default.Home, null) },
            NavItem(Screen.ScanHub.route, "Scan") { Icon(Icons.Default.Radar, null) },
            NavItem(Screen.Alerts.route, "Alerts") { Icon(Icons.Default.Notifications, null) },
            NavItem(Screen.ToolsHub.route, "Tools") { Icon(Icons.Default.Construction, null) },
            NavItem(Screen.Settings.route, "Settings") { Icon(Icons.Default.Settings, null) }
        )

        Scaffold(
            bottomBar = {
                if (showBottomBar && destination != Screen.Onboarding.route) {
                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = item.icon,
                                label = { Text(item.label, fontWeight = if (currentRoute == item.route) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            DeepfakeShieldNavHost(
                navController = navController,
                startDestination = destination,
                userPreferences = userPreferences,
                pendingOpenRoute = pendingOpenRoute,
                onRouteConsumed = onRouteConsumed,
                onStartProtectionServices = onStartProtectionServices,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun DeepfakeShieldNavHost(
    navController: NavHostController,
    startDestination: String,
    userPreferences: UserPreferences,
    pendingOpenRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
    onStartProtectionServices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Deep link from bubble/notification: open Alerts or Analytics
    // Guard: never navigate past onboarding if user hasn't completed it
    // Allowed deep link routes — reject unknown routes to prevent navigation injection
    val allowedRoutes = setOf(
        "alerts", "message_scan", "analytics", "home", "video_scan",
        "settings", "call_protection", "qr_scanner", "safe_browser",
        "vault", "education", "diagnostics",
        "privacy_score", "app_auditor", "breach_monitor", "wifi_scanner",
        "password_checker", "photo_forensics", "emergency_sos",
        "dark_web_monitor", "scam_number_lookup", "digital_footprint",
        "security_news_feed", "device_timeline", "fake_review_detector",
        "secure_notes", "network_monitor", "email_scanner", "scam_recovery",
        "weekly_report", "clipboard_monitor", "device_theft", "secure_file_vault",
        "tools_hub", "scan_hub", "permission_timeline", "data_broker_optout",
        "sim_swap_protection", "metadata_stripper", "disposable_email", "fake_domain_monitor"
    )
    LaunchedEffect(pendingOpenRoute) {
        pendingOpenRoute?.let { route ->
            if (startDestination == Screen.Onboarding.route) return@LaunchedEffect
            if (route !in allowedRoutes) return@LaunchedEffect
            try {
                navController.navigate(route) { launchSingleTop = true }
            } catch (_: Exception) { }
            // Clear after consuming so repeated same-route intents re-trigger
            onRouteConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300)) }
    ) {
        // Onboarding
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    // Go to scam simulator demo, then permission setup
                    navController.navigate(Screen.ScamSimulator.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Permission Setup (after onboarding, goes to personalization)
        composable(Screen.PermissionSetup.route) {
            val permCtx = androidx.compose.ui.platform.LocalContext.current
            PermissionSetupScreen(
                onComplete = {
                    scope.launch {
                        onStartProtectionServices()
                        if (Settings.canDrawOverlays(permCtx)) {
                            try { FloatingBubbleService.start(permCtx) } catch (_: Exception) { }
                        }
                        navController.navigate(Screen.Personalization.route) {
                            popUpTo(Screen.PermissionSetup.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Personalization (after permissions, before home)
        composable(Screen.Personalization.route) {
            com.deepfakeshield.feature.onboarding.PersonalizationScreen(
                onComplete = { priorities ->
                    scope.launch {
                        userPreferences.setOnboardingCompleted(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Personalization.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.PermissionSetupStandalone.route) {
            PermissionSetupScreen(
                onComplete = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            // Start protection as soon as user reaches Home (fixes first-time flow)
            LaunchedEffect(Unit) {
                onStartProtectionServices()
            }
            HomeScreen(
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToVault = { navController.navigate(Screen.Vault.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                onNavigateToVideoScan = { navController.navigate(Screen.VideoScan.route) },
                onNavigateToMessageScan = { navController.navigate(Screen.MessageScan.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToEducation = { navController.navigate(Screen.Education.route) },
                onNavigateToCallProtection = { navController.navigate(Screen.CallProtection.route) },
                onNavigateToDailyChallenge = { navController.navigate(Screen.DailyChallenge.route) },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) },
                onNavigateToReferral = { },
                onNavigateToFamilyCircle = { },
                onNavigateToAchievements = { navController.navigate(Screen.Achievements.route) },
                onNavigateToThreatMap = { navController.navigate(Screen.ThreatMap.route) },
                onNavigateToSafeBrowser = { navController.navigate(Screen.SafeBrowser.route) },
                onNavigateToPermissionSetup = { navController.navigate(Screen.PermissionSetupStandalone.route) },
                onNavigateToIntelligenceDashboard = { navController.navigate(Screen.IntelligenceDashboard.route) },
                onNavigateToPrivacyScore = { navController.navigate(Screen.PrivacyScore.route) },
                onNavigateToAppAuditor = { navController.navigate(Screen.AppAuditor.route) },
                onNavigateToBreachMonitor = { navController.navigate(Screen.BreachMonitor.route) },
                onNavigateToWifiScanner = { navController.navigate(Screen.WifiScanner.route) },
                onNavigateToPasswordChecker = { navController.navigate(Screen.PasswordChecker.route) },
                onNavigateToPhotoForensics = { navController.navigate(Screen.PhotoForensics.route) },
                onNavigateToEmergencySos = { navController.navigate(Screen.EmergencySos.route) },
                onNavigateToDarkWebMonitor = { navController.navigate(Screen.DarkWebMonitor.route) },
                onNavigateToScamNumberLookup = { navController.navigate(Screen.ScamNumberLookup.route) },
                onNavigateToDigitalFootprint = { navController.navigate(Screen.DigitalFootprint.route) },
                onNavigateToSecurityNewsFeed = { navController.navigate(Screen.SecurityNewsFeed.route) },
                onNavigateToDeviceTimeline = { navController.navigate(Screen.DeviceTimeline.route) },
                onNavigateToFakeReviewDetector = { navController.navigate(Screen.FakeReviewDetector.route) },
                onNavigateToSecureNotes = { navController.navigate(Screen.SecureNotes.route) },
                onNavigateToNetworkMonitor = { navController.navigate(Screen.NetworkMonitor.route) },
                onNavigateToTorSettings = { navController.navigate(Screen.TorSettings.route) },
                onStartOverlayBubble = {
                    try {
                        if (Settings.canDrawOverlays(ctx)) {
                            FloatingBubbleService.start(ctx)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("Navigation", "Could not start bubble: ${e.message}")
                    }
                },
                onStopOverlayBubble = {
                    try {
                        FloatingBubbleService.stop(ctx)
                    } catch (e: Exception) {
                        android.util.Log.w("Navigation", "Could not stop bubble: ${e.message}")
                    }
                }
            )
        }

        composable(Screen.Alerts.route) {
            AlertsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { alertId -> navController.navigate(Screen.AlertDetail.createRoute(alertId)) }
            )
        }

        composable(
            route = Screen.AlertDetail.route,
            arguments = listOf(navArgument("alertId") { type = NavType.LongType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getLong("alertId") ?: 0L
            AlertDetailScreen(_alertId = alertId, onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Vault.route) {
            VaultScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlertDetail = { alertId -> navController.navigate(Screen.AlertDetail.createRoute(alertId)) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateToTor = { navController.navigate(Screen.TorSettings.route) }
            )
        }

        composable(Screen.Privacy.route) {
            PrivacyScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Education.route) {
            EducationScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.VideoScan.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            VideoScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onShowFullScreenOverlay = {
                    if (!Settings.canDrawOverlays(ctx)) {
                        Toast.makeText(
                            ctx,
                            "Overlay permission required. Please enable 'Display over other apps' for DeepFake Shield.",
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            val settingsIntent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(settingsIntent)
                        } catch (_: Exception) { }
                        return@VideoScanScreen
                    }
                    try {
                        val intent = Intent(ctx, FloatingBubbleService::class.java).apply {
                            action = FloatingBubbleService.ACTION_TOGGLE_FULLSCREEN
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.startForegroundService(intent)
                        } else {
                            ctx.startService(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Could not show overlay: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        composable(Screen.MessageScan.route) {
            MessageScanScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.CallProtection.route) {
            CallProtectionScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.IntelligenceDashboard.route) {
            IntelligenceDashboardScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.DailyChallenge.route) {
            DailyChallengeScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.QrScanner.route) {
            QrScannerScreen(onNavigateBack = { navController.popBackStack() })
        }

        // Deprecated: Referral and FamilyCircle removed (no backend)

        composable(Screen.Achievements.route) {
            AchievementsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ThreatMap.route) {
            ThreatMapScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.SafeBrowser.route) {
            SafeBrowserScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ScamSimulator.route) {
            ScamSimulatorScreen(
                onComplete = {
                    navController.navigate(Screen.PermissionSetup.route) {
                        popUpTo(Screen.ScamSimulator.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PrivacyScore.route) {
            PrivacyScoreScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.AppAuditor.route) {
            AppAuditorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.BreachMonitor.route) {
            BreachMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.WifiScanner.route) {
            WifiScannerScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.PasswordChecker.route) {
            PasswordCheckerScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.PhotoForensics.route) {
            PhotoForensicsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.EmergencySos.route) {
            EmergencySosScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DarkWebMonitor.route) {
            DarkWebMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ScamNumberLookup.route) {
            ScamNumberLookupScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DigitalFootprint.route) {
            DigitalFootprintScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.SecurityNewsFeed.route) {
            SecurityNewsFeedScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DeviceTimeline.route) {
            DeviceTimelineScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.FakeReviewDetector.route) {
            FakeReviewDetectorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.SecureNotes.route) {
            SecureNotesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.NetworkMonitor.route) {
            NetworkMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ━━━ New Feature Screens ━━━

        composable(Screen.ToolsHub.route) {
            com.deepfakeshield.feature.home.ToolsHubScreen(
                onNavigateToTool = { route -> navController.navigate(route) },
                onNavigateToSearch = {}
            )
        }

        composable(Screen.ScanHub.route) {
            com.deepfakeshield.feature.shield.ScanHubScreen(
                onNavigateToVideoScan = { navController.navigate(Screen.VideoScan.route) },
                onNavigateToMessageScan = { navController.navigate(Screen.MessageScan.route) },
                onNavigateToPhotoForensics = { navController.navigate(Screen.PhotoForensics.route) },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) },
                onNavigateToEmailScanner = { navController.navigate(Screen.EmailScanner.route) },
                onNavigateToFakeReview = { navController.navigate(Screen.FakeReviewDetector.route) },
                onNavigateToUrlScanner = { navController.navigate(Screen.SafeBrowser.route) }
            )
        }

        composable(Screen.EmailScanner.route) {
            com.deepfakeshield.feature.shield.EmailScannerScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ScamRecovery.route) {
            com.deepfakeshield.feature.home.ScamRecoveryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.WeeklyReport.route) {
            com.deepfakeshield.feature.analytics.WeeklyReportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ClipboardMonitor.route) {
            com.deepfakeshield.feature.diagnostics.ClipboardMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.DeviceTheft.route) {
            com.deepfakeshield.feature.home.DeviceTheftScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.SecureFileVault.route) {
            com.deepfakeshield.feature.vault.SecureFileVaultScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.PermissionTimeline.route) {
            com.deepfakeshield.feature.diagnostics.PermissionTimelineScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DataBrokerOptOut.route) {
            com.deepfakeshield.feature.home.DataBrokerOptOutScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.SimSwapProtection.route) {
            com.deepfakeshield.feature.home.SimSwapProtectionScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.MetadataStripper.route) {
            com.deepfakeshield.feature.shield.MetadataStripperScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.DisposableEmail.route) {
            com.deepfakeshield.feature.shield.DisposableEmailScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.FakeDomainMonitor.route) {
            com.deepfakeshield.feature.shield.FakeDomainMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.CommunityReport.route) {
            com.deepfakeshield.feature.home.CommunityReportScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.TorSettings.route) {
            com.deepfakeshield.feature.settings.TorSettingsScreen(onNavigateBack = { navController.popBackStack() }, userPreferences = userPreferences)
        }
        composable(Screen.WhatsNew.route) {
            com.deepfakeshield.feature.settings.WhatsNewScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ThreatModel.route) {
            com.deepfakeshield.feature.settings.ThreatModelScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ShareScore.route) {
            com.deepfakeshield.feature.home.ShareScoreScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.IdentityDashboard2.route) {
            com.deepfakeshield.feature.analytics.IdentityDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBreach = { navController.navigate(Screen.BreachMonitor.route) },
                onNavigateToDarkWeb = { navController.navigate(Screen.DarkWebMonitor.route) },
                onNavigateToFootprint = { navController.navigate(Screen.DigitalFootprint.route) },
                onNavigateToDataBroker = { navController.navigate("data_broker_optout") }
            )
        }
    }
}
