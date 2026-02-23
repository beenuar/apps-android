package com.deepfakeshield.feature.education

import androidx.compose.animation.core.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.ui.animations.*
import com.deepfakeshield.core.ui.theme.*

data class DailyChallenge(
    val id: Int,
    val title: String,
    val description: String,
    val task: String,
    val hint: String,
    val xpReward: Int,
    val category: String,
    val options: List<String> = emptyList()
)

private val challenges = listOf(
    DailyChallenge(1, "Spot the Phishing Link", "Can you identify which URL is real?", "Which of these is the real PayPal login?", "Look for the official domain name without extra words or characters", 50, "URLs", listOf("paypal-secure-login.com", "paypal.com/signin", "pay-pal.verify.tk", "paypa1.com/login")),
    DailyChallenge(2, "OTP Safety Check", "Test your knowledge about OTP security", "Your bank sends you an OTP. Someone calls claiming to be the bank and asks for it. What should you do?", "Legitimate banks NEVER ask for your OTP", 50, "Calls", listOf("Share it, they're from the bank", "Hang up and call your bank directly", "Share only the first 3 digits", "Ask them to verify first")),
    DailyChallenge(3, "Deepfake Detective", "Learn to spot AI-generated content", "Which of these is a common sign of a deepfake video?", "AI often struggles with natural eye movements", 75, "Video", listOf("High video resolution", "Unnatural blinking patterns", "Good lighting", "Clear audio")),
    DailyChallenge(4, "Scam Message Alert", "Identify the scam message patterns", "'URGENT: Your account will be SUSPENDED! Click here NOW to verify: bit.ly/x7kq2'\n\nHow many red flags can you spot?", "Count: urgency, caps, threats, shortened URL, generic greeting, no name...", 50, "Messages", listOf("1-2 red flags", "3-4 red flags", "5-6 red flags", "7+ red flags")),
    DailyChallenge(5, "Password Fortress", "Strengthen your security knowledge", "Which password is strongest?", "Length and randomness beat complexity tricks", 50, "Security", listOf("P@ssw0rd123!", "CorrectHorseBatteryStaple", "abc123xyz", "MyDog'sName2024")),
    DailyChallenge(6, "Social Engineering", "Defend against manipulation tactics", "A 'tech support' caller says your computer has a virus and asks you to install AnyDesk. What should you do?", "Real tech companies don't make unsolicited calls", 75, "Calls", listOf("Install it to fix the problem", "Hang up immediately", "Ask for their employee ID", "Give them limited access")),
    DailyChallenge(7, "Privacy Shield", "Protect your digital footprint", "Which of these shares the LEAST personal data?", "Consider what data each method shares with the service", 50, "Privacy", listOf("Signing in with Google", "Creating a new account with email", "Signing in with Facebook", "Using a phone number")),
    DailyChallenge(8, "Wi-Fi Trap", "Public Wi-Fi security awareness", "You're at a coffee shop and see two networks: 'CoffeeShop_Free' and 'CoffeeShop_Guest_5G'. Which is safer?", "Neither is guaranteed safe — always use a VPN on public Wi-Fi", 75, "Security", listOf("CoffeeShop_Free — it's the official one", "CoffeeShop_Guest_5G — 5G means more secure", "Neither — use a VPN on any public Wi-Fi", "Both are equally safe")),
    DailyChallenge(9, "QR Code Danger", "Recognize malicious QR codes", "Someone puts a sticker with a QR code over the restaurant's menu QR. What's the risk?", "Physical QR code overlays are a common attack vector", 50, "URLs", listOf("No risk — QR codes are always safe", "It could redirect to a phishing site", "QR codes can't contain malware", "It's just a menu, no risk")),
    DailyChallenge(10, "Two-Factor Mastery", "Understand 2FA methods", "Which 2FA method is MOST secure against phishing?", "Hardware keys can't be phished remotely", 75, "Security", listOf("SMS text codes", "Email verification", "Authenticator app (TOTP)", "Hardware security key (FIDO2)")),
    DailyChallenge(11, "AI Voice Clone", "Detect AI voice scams", "Your 'parent' calls crying, saying they've been in an accident and need money wired immediately. What should you do?", "AI voice cloning can perfectly mimic familiar voices", 100, "Calls", listOf("Send money immediately — they're in trouble", "Ask them a personal question only they would know", "Hang up and call them back on their known number", "Ask for the hospital name first")),
    DailyChallenge(12, "Email Header Check", "Learn to verify email authenticity", "You get an email from 'security@amaz0n-support.com' about suspicious activity. What's the red flag?", "Always check the actual sender domain — not just the display name", 50, "Messages", listOf("The word 'security' in the address", "The domain 'amaz0n-support.com' is not Amazon", "Emails about suspicious activity are always scams", "All emails from unknown senders are fake")),
    DailyChallenge(13, "App Permissions", "Understand dangerous app permissions", "A flashlight app asks for Camera, Contacts, Location, and Microphone. What should you do?", "A flashlight only needs camera access (for the flash LED)", 50, "Privacy", listOf("Grant all — the app needs them", "Only grant Camera", "Deny all and uninstall", "Grant Location only")),
    DailyChallenge(14, "Ransomware Defense", "Know how to respond to ransomware", "Your computer screen shows: 'Your files are encrypted. Pay $500 in Bitcoin to recover.' What's the FIRST thing to do?", "Disconnect to prevent spread; never pay the ransom", 100, "Security", listOf("Pay the ransom to get files back", "Disconnect from the internet immediately", "Try to delete the virus with antivirus", "Call the phone number shown")),
    DailyChallenge(15, "Cookie Consent", "Understand tracking cookies", "A website shows 'Accept All Cookies' or 'Manage Preferences'. What's the privacy-conscious choice?", "Rejecting non-essential cookies limits tracking across sites", 50, "Privacy", listOf("Accept All — cookies are harmless", "Manage Preferences and reject non-essential", "Close the popup and ignore it", "It doesn't matter either way")),
    DailyChallenge(16, "SIM Swap Attack", "Understand SIM swapping threats", "You suddenly lose cell service. An hour later, your email password is changed. What likely happened?", "SIM swap attacks let hackers receive your 2FA codes", 100, "Security", listOf("Your phone broke", "A SIM swap attack redirected your number", "The email provider was hacked", "Bad weather affected the signal")),
    DailyChallenge(17, "USB Drop Attack", "Recognize physical attack vectors", "You find a USB drive in the parking lot labeled 'Salary Report 2025'. What should you do?", "Unknown USB drives can install malware the moment they're plugged in", 75, "Security", listOf("Plug it in to return it to the owner", "Never plug in unknown USB drives", "Scan it with antivirus first", "Give it to IT to check")),
    DailyChallenge(18, "HTTPS Lock", "Understand HTTPS limitations", "A phishing site has a padlock (HTTPS) in the browser. Does that mean it's safe?", "HTTPS only means the connection is encrypted — not that the site is legitimate", 50, "URLs", listOf("Yes — the padlock means it's verified", "No — HTTPS only encrypts the connection, not trust", "Only if the padlock is green", "Yes — browsers block unsafe HTTPS sites")),
    DailyChallenge(19, "Data Broker Opt-Out", "Protect your personal information", "Which of these is the MOST effective way to remove your data from data brokers?", "Manual opt-out from each broker is the most reliable method", 50, "Privacy", listOf("Delete your social media accounts", "Use incognito mode for browsing", "Submit opt-out requests to each data broker", "Change your email address")),
    DailyChallenge(20, "Bluetooth Attack", "Understand wireless attack vectors", "What is 'BlueBorne'?", "BlueBorne spreads through Bluetooth without pairing — just having BT on is enough", 75, "Security", listOf("A Bluetooth speaker brand", "A vulnerability that spreads via Bluetooth without pairing", "A type of blue-screen error", "A secure Bluetooth protocol")),
    DailyChallenge(21, "Credential Stuffing", "Understand password reuse attacks", "Hackers got your email+password from a breached gaming site. What are they most likely to do?", "Hackers automate trying leaked credentials on hundreds of other sites", 75, "Security", listOf("Nothing — it's just a game account", "Try the same email+password on banking, email, and shopping sites", "Send you spam emails", "Only target other gaming sites"))
)

private val correctAnswers = mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 2, 5 to 1, 6 to 1, 7 to 1, 8 to 2, 9 to 1, 10 to 3, 11 to 2, 12 to 1, 13 to 2, 14 to 1, 15 to 1, 16 to 1, 17 to 1, 18 to 1, 19 to 2, 20 to 1, 21 to 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeScreen(
    viewModel: DailyChallengeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val today = java.time.LocalDate.now().toEpochDay().toInt()
    val todayChallenge = challenges[today % challenges.size]
    val uiState by viewModel.uiState.collectAsState()
    val streak = uiState.streak
    val totalXp = uiState.totalXp
    val isAlreadyCompleted = uiState.completedIds.contains(today.toString())
    
    var selectedAnswer by rememberSaveable { mutableIntStateOf(-1) }
    var isAnswered by rememberSaveable(isAlreadyCompleted) { mutableStateOf(isAlreadyCompleted) }
    
    val correctAnswer = correctAnswers[todayChallenge.id] ?: 0
    val isCorrect = selectedAnswer == correctAnswer
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Challenge", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Streak indicator
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, null, 
                                tint = Color(0xFFFF6D00), modifier = Modifier.size(18.dp))
                            Text("$streak", fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
            // Streak & XP Banner
            AnimatedFadeIn {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalFireDepartment, null, 
                                tint = Color(0xFFFF6D00), modifier = Modifier.size(32.dp))
                            Text("$streak Day${if (streak != 1) "s" else ""}", 
                                fontWeight = FontWeight.Bold)
                            Text("Streak", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Star, null,
                                tint = Color(0xFFFFD600), modifier = Modifier.size(32.dp))
                            Text("$totalXp XP", fontWeight = FontWeight.Bold)
                            Text("Total", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEvents, null,
                                tint = Color(0xFFFF6D00), modifier = Modifier.size(32.dp))
                            Text("Level ${totalXp / 100 + 1}", fontWeight = FontWeight.Bold)
                            Text("Rank", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            
            // Challenge Card
            AnimatedFadeIn(delayMillis = 100) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    todayChallenge.category,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text("+${todayChallenge.xpReward} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD600),
                                fontWeight = FontWeight.Bold)
                        }
                        
                        Text(todayChallenge.title, 
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        Text(todayChallenge.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        HorizontalDivider()
                        
                        Text(todayChallenge.task,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            
            // Answer Options
            val optionLabels = listOf("A", "B", "C", "D")
            val optionTexts = todayChallenge.options.ifEmpty { listOf("Option A", "Option B", "Option C", "Option D") }
            optionLabels.forEachIndexed { index, option ->
                AnimatedFadeIn(delayMillis = 200 + index * 50) {
                    val isSelected = selectedAnswer == index
                    val containerColor = when {
                        !isAnswered && isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isAnswered && index == correctAnswer -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        isAnswered && isSelected && !isCorrect -> Color(0xFFF44336).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val borderColor = when {
                        !isAnswered && isSelected -> MaterialTheme.colorScheme.primary
                        isAnswered && index == correctAnswer -> Color(0xFF4CAF50)
                        isAnswered && isSelected && !isCorrect -> Color(0xFFF44336)
                        else -> Color.Transparent
                    }
                    
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = borderColor
                        ),
                        onClick = { if (!isAnswered) selectedAnswer = index }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isAnswered && index == correctAnswer -> Color(0xFF4CAF50)
                                            isAnswered && isSelected && !isCorrect -> Color(0xFFF44336)
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isAnswered && index == correctAnswer) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                } else if (isAnswered && isSelected && !isCorrect) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(option, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text(optionTexts.getOrElse(index) { "Option $option" }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            // Submit / Result
            if (!isAnswered && selectedAnswer >= 0) {
                Button(
                    onClick = {
                        isAnswered = true
                        if (isCorrect) viewModel.onChallengeCompleted(today, todayChallenge.xpReward)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit Answer", fontWeight = FontWeight.Bold)
                }
            }
            
            if (isAnswered) {
                AnimatedFadeIn {
                    if (isAlreadyCompleted && !selectedAnswer.let { it >= 0 }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                                Text("You already completed today's challenge!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Come back tomorrow for a new one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect) Color(0xFF4CAF50).copy(alpha = 0.1f)
                            else Color(0xFFF44336).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    null,
                                    tint = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    if (isCorrect) "Correct! +${todayChallenge.xpReward} XP" else "Not Quite!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                            }
                            
                            Text(
                                "Hint: ${todayChallenge.hint}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    }
                }
                
                // Share result button
                val shareContext = androidx.compose.ui.platform.LocalContext.current
                OutlinedButton(
                    onClick = {
                        val shareText = "DeepFake Shield Daily Challenge:\n" +
                            "${todayChallenge.title}\n" +
                            "I ${if (isCorrect) "got it right!" else "learned something new!"}\n" +
                            "Streak: $streak days | XP: $totalXp\n\n" +
                            "Test your scam detection skills!"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        shareContext.startActivity(android.content.Intent.createChooser(intent, "Share Challenge").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Challenge")
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
