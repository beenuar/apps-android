package com.deepfakeshield.feature.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfakeshield.core.network.TorNetworkModule
import com.deepfakeshield.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExitCountry(
    val code: String,
    val name: String,
    val flag: String
)

private val EXIT_COUNTRIES = listOf(
    ExitCountry("auto", "Auto (Fastest)", "\uD83C\uDF10"),
    ExitCountry("us", "United States", "\uD83C\uDDFA\uD83C\uDDF8"),
    ExitCountry("gb", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7"),
    ExitCountry("de", "Germany", "\uD83C\uDDE9\uD83C\uDDEA"),
    ExitCountry("nl", "Netherlands", "\uD83C\uDDF3\uD83C\uDDF1"),
    ExitCountry("fr", "France", "\uD83C\uDDEB\uD83C\uDDF7"),
    ExitCountry("ch", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED"),
    ExitCountry("ca", "Canada", "\uD83C\uDDE8\uD83C\uDDE6"),
    ExitCountry("se", "Sweden", "\uD83C\uDDF8\uD83C\uDDEA"),
    ExitCountry("no", "Norway", "\uD83C\uDDF3\uD83C\uDDF4"),
    ExitCountry("fi", "Finland", "\uD83C\uDDEB\uD83C\uDDEE"),
    ExitCountry("at", "Austria", "\uD83C\uDDE6\uD83C\uDDF9"),
    ExitCountry("dk", "Denmark", "\uD83C\uDDE9\uD83C\uDDF0"),
    ExitCountry("jp", "Japan", "\uD83C\uDDEF\uD83C\uDDF5"),
    ExitCountry("au", "Australia", "\uD83C\uDDE6\uD83C\uDDFA"),
    ExitCountry("sg", "Singapore", "\uD83C\uDDF8\uD83C\uDDEC"),
    ExitCountry("is", "Iceland", "\uD83C\uDDEE\uD83C\uDDF8"),
    ExitCountry("ro", "Romania", "\uD83C\uDDF7\uD83C\uDDF4"),
    ExitCountry("es", "Spain", "\uD83C\uDDEA\uD83C\uDDF8"),
    ExitCountry("it", "Italy", "\uD83C\uDDEE\uD83C\uDDF9"),
    ExitCountry("pl", "Poland", "\uD83C\uDDF5\uD83C\uDDF1"),
    ExitCountry("cz", "Czech Republic", "\uD83C\uDDE8\uD83C\uDDFF"),
    ExitCountry("br", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7"),
    ExitCountry("in", "India", "\uD83C\uDDEE\uD83C\uDDF3"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorSettingsScreen(onNavigateBack: () -> Unit, userPreferences: UserPreferences) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val disclosureAccepted by userPreferences.torDisclosureAccepted.collectAsState(initial = false)
    val savedExitCountry by userPreferences.torExitCountry.collectAsState(initial = "auto")
    var selectedCountry by remember { mutableStateOf(savedExitCountry) }
    var showDisclosure by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TorNetworkModule.TorTestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var currentIp by remember { mutableStateOf<String?>(null) }
    var isFetchingIp by remember { mutableStateOf(false) }

    LaunchedEffect(savedExitCountry) { selectedCountry = savedExitCountry }

    val torConnected = TorNetworkModule.isConnected
    val torMode = TorNetworkModule.mode
    val torStatus = TorNetworkModule.connectionStatus

    val accentColor = Color(0xFF7C4DFF)
    val statusColor by animateColorAsState(
        when {
            torConnected -> accentColor
            torMode == "starting" -> Color(0xFFFF9800)
            torMode == "error" -> Color(0xFFF44336)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }, label = "statusColor"
    )

    val pulseAlpha = if (torMode == "starting") {
        val t = rememberInfiniteTransition(label = "p")
        t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa").value
    } else 1f

    fun sendToggle(enabled: Boolean) {
        val intent = android.content.Intent("com.deepfakeshield.TOR_TOGGLE")
        intent.putExtra("enabled", enabled)
        intent.putExtra("exit_country", selectedCountry)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protect My IP", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Big status card
            Card(
                Modifier.fillMaxWidth().alpha(pulseAlpha),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f))
            ) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).background(statusColor.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            when {
                                torConnected -> Icons.Default.Shield
                                torMode == "starting" -> Icons.Default.Sync
                                torMode == "error" -> Icons.Default.Error
                                else -> Icons.Default.ShieldMoon
                            },
                            null, Modifier.size(40.dp), tint = statusColor
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val vpnOn = TorNetworkModule.vpnActive
                    Text(
                        when {
                            torConnected && vpnOn -> "All Apps Protected"
                            torConnected -> torStatus
                            else -> torStatus
                        },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                    )
                    if (torConnected && vpnOn) {
                        val countryName = EXIT_COUNTRIES.find { it.code == selectedCountry }?.let { "${it.flag} ${it.name}" } ?: "Auto"
                        Text("VPN active — Chrome & all apps through Tor", style = MaterialTheme.typography.bodySmall, color = accentColor)
                        Text("Exit: $countryName", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f))
                    } else if (torConnected) {
                        val countryName = EXIT_COUNTRIES.find { it.code == selectedCountry }?.let { "${it.flag} ${it.name}" } ?: "Auto"
                        Text("Exit: $countryName", style = MaterialTheme.typography.bodySmall, color = accentColor)
                        Text("VPN pending — approve the connection dialog", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
                    } else if (torMode == "starting") {
                        Text("Building Tor circuits... 10-30 seconds", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                    } else if (torMode == "error") {
                        Text("Tap the switch to retry", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                    }
                }
            }

            // Main toggle
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
                containerColor = if (torConnected) accentColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, Modifier.size(28.dp), tint = if (torConnected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Protect My IP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (torConnected) "Your IP is hidden via Tor" else "Route app traffic through the Tor network",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = torConnected || torMode == "starting",
                        onCheckedChange = { newValue ->
                            if (newValue && !disclosureAccepted) { showDisclosure = true; return@Switch }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (newValue) {
                                // Stop first if in error state (clean restart)
                                if (torMode == "error") sendToggle(false)
                                scope.launch {
                                    if (torMode == "error") kotlinx.coroutines.delay(500)
                                    userPreferences.setTorEnabled(true)
                                    sendToggle(true)
                                }
                            } else {
                                scope.launch { userPreferences.setTorEnabled(false) }
                                sendToggle(false)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
            }

            // Error detail card
            if (torMode == "error") {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, Modifier.size(20.dp), tint = Color(0xFFF44336))
                            Spacer(Modifier.width(8.dp))
                            Text("Connection Failed", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            torStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Possible causes:\n• No internet connection\n• Tor network blocked by your ISP/network\n• Firewall blocking outbound connections\n• Selected exit country has no available relays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                sendToggle(false)
                                scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    userPreferences.setTorEnabled(true)
                                    sendToggle(true)
                                }
                            },
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry Connection")
                        }
                    }
                }
            }

            // Country selector
            Text("Exit Node Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose where your traffic appears to come from",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 800.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(EXIT_COUNTRIES) { country ->
                    val isSelected = selectedCountry == country.code
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedCountry = country.code
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    userPreferences.setTorExitCountry(country.code)
                                    if (torConnected) {
                                        Toast.makeText(context, "Reconnecting with ${country.name} exit...", Toast.LENGTH_SHORT).show()
                                        sendToggle(false)
                                        kotlinx.coroutines.delay(1500)
                                        sendToggle(true)
                                    }
                                }
                            }
                            .then(
                                if (isSelected) Modifier.border(2.dp, accentColor, RoundedCornerShape(14.dp))
                                else Modifier
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(country.flag, fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                country.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 14.sp
                            )
                            if (isSelected) {
                                Spacer(Modifier.height(2.dp))
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = accentColor)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // IP verification
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Verify Your IP", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                isFetchingIp = true; currentIp = null
                                scope.launch {
                                    val ip = withContext(Dispatchers.IO) { TorNetworkModule.getExternalIp() }
                                    currentIp = ip ?: "Failed"
                                    isFetchingIp = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp), enabled = !isFetchingIp
                        ) {
                            if (isFetchingIp) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text("Check IP")
                        }
                        currentIp?.let { ip ->
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(ip, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (torConnected) "Tor exit node IP — you're hidden" else "Your real IP — visible to sites",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (torConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                            }
                        }
                    }
                }
            }

            // Connection test
            OutlinedButton(
                onClick = {
                    isTesting = true; testResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { TorNetworkModule.testConnection() }
                        testResult = result; isTesting = false
                    }
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !isTesting && torConnected
            ) {
                if (isTesting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Icon(Icons.Default.NetworkCheck, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Test Tor Connection")
            }

            AnimatedVisibility(testResult != null) {
                testResult?.let { result ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(
                        containerColor = (if (result.success) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(alpha = 0.06f)
                    )) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (result.success) Icons.Default.CheckCircle else Icons.Default.Error, null,
                                tint = if (result.success) Color(0xFF4CAF50) else Color(0xFFF44336))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(result.message, style = MaterialTheme.typography.bodySmall)
                                if (result.isTor && result.exitIp != null) {
                                    Text("Tor confirmed — exit: ${result.exitIp}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Info cards
            Text("How It Works", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            listOf(
                "All Apps Protected" to "A VPN tunnel captures ALL device traffic — Chrome, Firefox, every app — and routes it through the Tor network.",
                "Embedded Tor" to "The Tor binary runs inside this app. No external downloads needed.",
                "DNS Protection" to "DNS queries are also resolved through Tor, preventing DNS leaks.",
                "Speed" to "Tor routes through 3 relays. Expect 3-10x slower connections. First connect takes 10-30s.",
            ).forEach { (title, desc) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Enable Tor Privacy", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This starts an embedded Tor daemon to hide your IP address.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        "Connections will be 3-10x slower",
                        "Initial connect takes 10-30 seconds",
                        "Only this app's traffic is routed",
                        "A notification shows while Tor is running",
                        "Battery usage increases slightly"
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        userPreferences.setTorDisclosureAccepted(true)
                        userPreferences.setTorEnabled(true)
                    }
                    sendToggle(true)
                    showDisclosure = false
                }) { Text("Enable Tor") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Cancel") } }
        )
    }
}
