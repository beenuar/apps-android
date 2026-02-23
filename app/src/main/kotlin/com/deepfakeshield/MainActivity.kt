package com.deepfakeshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.deepfakeshield.av.engine.AntivirusEngine
import com.deepfakeshield.av.engine.AvScanResult
import com.deepfakeshield.av.engine.QuarantineManager
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import androidx.compose.foundation.isSystemInDarkTheme
import com.deepfakeshield.core.ui.theme.DeepfakeShieldTheme
import com.deepfakeshield.data.entity.AlertEntity
import com.deepfakeshield.data.preferences.UserPreferences
import com.deepfakeshield.data.repository.AlertRepository
import com.deepfakeshield.service.AntivirusForegroundService
import java.io.File
import com.deepfakeshield.service.FloatingBubbleService
import com.deepfakeshield.service.ProtectionForegroundService
import com.deepfakeshield.worker.ServiceStartRetryWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        var pendingOpenRouteState: androidx.compose.runtime.MutableState<String?>? = null
        var pendingSharedText: String? = null
    }

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var antivirusEngine: AntivirusEngine

    @Inject
    lateinit var quarantineManager: QuarantineManager

    // Request POST_NOTIFICATIONS permission on Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "Notification permission granted: $granted")
        // After notification, request storage permissions
        requestStoragePermissions()
    }

    // Storage permission for Android <13
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Log.d("MainActivity", "Storage permissions granted: $allGranted ($results)")
        startProtectionServices()
    }

    // MANAGE_EXTERNAL_STORAGE for Android 11+
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        Log.d("MainActivity", "MANAGE_EXTERNAL_STORAGE granted: $hasAccess")
        startProtectionServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Scan shared file if user shared APK/file to app
        handleSharedFile(intent)

        // Request notification permission first, then start services
        requestNotificationPermissionAndStartServices()

        setContent {
            val simpleModeEnabled by userPreferences.simpleModeEnabled.collectAsState(initial = false)
            val themeMode by userPreferences.themeMode.collectAsState(initial = "system")
            val pendingRouteState = remember {
                mutableStateOf<String?>(
                    when {
                        intent?.getBooleanExtra("open_scan", false) == true -> "message_scan"
                        intent?.getBooleanExtra("open_analytics", false) == true -> "analytics"
                        else -> intent?.getStringExtra(FloatingBubbleService.EXTRA_OPEN_ROUTE)
                            ?: intent?.getStringExtra("open_route")
                    }
                )
            }
            val openRoute by pendingRouteState
            DisposableEffect(Unit) {
                MainActivity.pendingOpenRouteState = pendingRouteState
                onDispose { MainActivity.pendingOpenRouteState = null }
            }

            val isDark = when (themeMode) { "dark", "amoled" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            DeepfakeShieldTheme(darkTheme = isDark, amoledMode = themeMode == "amoled", simpleMode = simpleModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeepfakeShieldApp(
                        userPreferences = userPreferences,
                        pendingOpenRoute = openRoute,
                        onRouteConsumed = { pendingRouteState.value = null },
                        onStartProtectionServices = { startProtectionServices() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedFile(intent)
        val route = when {
            intent.getBooleanExtra("open_scan", false) -> "message_scan"
            intent.getBooleanExtra("open_analytics", false) -> "analytics"
            else -> intent.getStringExtra(FloatingBubbleService.EXTRA_OPEN_ROUTE)
                ?: intent.getStringExtra("open_route")
        }
        if (route != null) pendingOpenRouteState?.value = route
    }

    override fun onResume() {
        super.onResume()
        // Tell clipboard monitor that the user is inside our own UI.
        // Any clipboard changes while foregrounded are from our own screens
        // (threat details, demo text, etc.) and must NOT be re-scanned.
        ProtectionForegroundService.isAppInForeground = true
        lifecycleScope.launch {
            try {
                startProtectionServices()
                val isActive = userPreferences.masterProtectionEnabled.first()
                val count = alertRepository.getAlertCount()
                FloatingBubbleService.updateStatus(this@MainActivity, count, isActive)
                com.deepfakeshield.widget.ShieldWidgetProvider.updateWidget(this@MainActivity)
                val bubbleEnabled = userPreferences.overlayBubbleEnabled.first()
                if (Settings.canDrawOverlays(this@MainActivity) && isActive && bubbleEnabled) {
                    FloatingBubbleService.start(this@MainActivity)
                } else if (!bubbleEnabled) {
                    FloatingBubbleService.stop(this@MainActivity)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error in onResume: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ProtectionForegroundService.isAppInForeground = false
    }

    private fun requestNotificationPermissionAndStartServices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // Request notification first, then storage in callback
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Already have notification permission or pre-Android 13 - request storage
        requestStoragePermissions()
    }

    private fun requestStoragePermissions() {
        // Only prompt for storage permission during onboarding (permission setup screen).
        // On subsequent launches, start services regardless — the onboarding/permission
        // screen already handles the interactive permission grant flow. Prompting on
        // every launch breaks the user experience by reopening system settings.
        startProtectionServices()
    }

    /**
     * Interactively request MANAGE_EXTERNAL_STORAGE. Called ONLY from the
     * onboarding permission-setup screen, never from onCreate.
     */
    fun requestStoragePermissionInteractive() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        } catch (e2: Exception) {
                            Log.w("MainActivity", "Cannot open storage settings: ${e2.message}")
                        }
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val hasRead = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasRead) {
                    storagePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
        }
    }

    /** Check if we have sufficient storage access for AV scanning */
    fun hasStorageAccess(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }

    private fun handleSharedFile(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        // Handle shared plain text — "Check This for Me" feature
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText != null && sharedText.isNotBlank()) {
            // Store shared text and navigate to message scan
            pendingSharedText = sharedText
            pendingOpenRouteState?.value = "message_scan"
            return
        }

        val uri = when {
            intent.clipData != null && (intent.clipData?.itemCount ?: 0) > 0 ->
                intent.clipData?.getItemAt(0)?.uri
            @Suppress("DEPRECATION") intent.extras?.get(Intent.EXTRA_STREAM) != null ->
                @Suppress("DEPRECATION") intent.extras?.get(Intent.EXTRA_STREAM) as? Uri
            else -> null
        } ?: return
        lifecycleScope.launch {
            try {
                val name = uri.lastPathSegment ?: "shared_file"
                val ext = name.substringAfterLast('.', "").lowercase()
                val scanExts = setOf("apk", "dex", "jar", "so", "zip", "exe", "bin")
                if (ext !in scanExts && !name.endsWith(".apk", true)) return@launch
                val input = contentResolver.openInputStream(uri) ?: return@launch
                val dest = File(cacheDir, "share_scan_${System.currentTimeMillis()}_$name")
                // Fix: close BOTH input and output streams to prevent FD leaks
                input.use { inputStream ->
                    dest.outputStream().use { output -> inputStream.copyTo(output) }
                }
                val result = antivirusEngine.scanFile(dest.absolutePath, uri, AntivirusEngine.SCAN_TYPE_FILE)
                if (result.isInfected) {
                    val riskResult = antivirusEngine.toRiskResult(result)
                    if (riskResult != null) {
                        alertRepository.insertAlert(
                            AlertEntity(
                                threatType = ThreatType.MALWARE,
                                source = ThreatSource.REAL_TIME_SCAN,
                                severity = RiskSeverity.CRITICAL,
                                score = 95,
                                confidence = 0.95f,
                                title = "Malware in shared file",
                                summary = riskResult.explainLikeImFive,
                                content = name,
                                senderInfo = result.threatName,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        if (userPreferences.autoQuarantineOnThreat.first()) {
                            quarantineManager.quarantine(result)
                        } else {
                            dest.delete()
                        }
                    } else {
                        // Fix: toRiskResult() returned null but file IS infected — always clean up.
                        // Previously the malware file sat in cacheDir forever on this path.
                        dest.delete()
                    }
                    FloatingBubbleService.updateStatus(this@MainActivity, alertRepository.getAlertCount(), true)
                    pendingOpenRouteState?.value = "alerts"
                } else {
                    dest.delete()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Share scan failed: ${e.message}")
            }
        }
    }

    private fun startProtectionServices() {
        lifecycleScope.launch {
            try {
                val isProtectionEnabled = userPreferences.masterProtectionEnabled.first()
                val onboardingDone = userPreferences.onboardingCompleted.first()

                if (!onboardingDone) return@launch
                if (!isProtectionEnabled) return@launch

                val serviceIntent = Intent(this@MainActivity, ProtectionForegroundService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    val isFgRestriction = Build.VERSION.SDK_INT >= 31 &&
                        e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
                    Log.w("MainActivity", "Could not start protection service: ${e.message} (fgRestriction=$isFgRestriction)")
                    if (isFgRestriction) {
                        ServiceStartRetryWorker.scheduleRetry(this@MainActivity)
                    }
                }

                try {
                    val bubbleEnabled = userPreferences.overlayBubbleEnabled.first()
                    if (Settings.canDrawOverlays(this@MainActivity) && bubbleEnabled) {
                        FloatingBubbleService.start(this@MainActivity)
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Could not start bubble: ${e.message}")
                }

                try {
                    AntivirusForegroundService.start(this@MainActivity)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Could not start antivirus: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to start services: ${e.message}")
            }
        }
    }
}
