package com.deepfakeshield.feature.education

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(
    onNavigateBack: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Learn", "Quiz", "Tips")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Academy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> LearnTab()
                1 -> QuizTab()
                2 -> TipsTab()
            }
        }
    }
}

// ===== LEARN TAB =====
@Composable
private fun LearnTab() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LearnCard(
            icon = Icons.Filled.VideoLibrary, color = Color(0xFF9C27B0),
            title = "How to Spot a Deepfake",
            points = listOf(
                "Look for unnatural blinking or no blinking at all",
                "Check for mismatched skin tones around the face edges",
                "Watch for inconsistent lighting on the face vs background",
                "Listen for audio that doesn't match lip movements",
                "Look for blurry or warped areas around the jawline and hair"
            )
        )
        LearnCard(
            icon = Icons.AutoMirrored.Filled.Message, color = Color(0xFF2196F3),
            title = "Recognizing Scam Messages",
            points = listOf(
                "Creates urgency: 'Act NOW or your account will be closed'",
                "Asks for OTP codes or passwords - NEVER share these",
                "Contains suspicious links with misspelled domains",
                "Claims to be from a bank/gov but uses a regular phone number",
                "Promises free money, prizes, or too-good-to-be-true offers"
            )
        )
        LearnCard(
            icon = Icons.Filled.Phone, color = Color(0xFF4CAF50),
            title = "Avoiding Phone Scams",
            points = listOf(
                "Legitimate organizations never ask for payment via gift cards",
                "Government agencies don't threaten arrest over the phone",
                "If in doubt, hang up and call the official number yourself",
                "Never give remote access to your computer to an unknown caller",
                "Scammers can fake caller ID - don't trust the number alone"
            )
        )
        LearnCard(
            icon = Icons.Filled.Security, color = Color(0xFFFF9800),
            title = "If You've Been Scammed",
            points = listOf(
                "Contact your bank immediately to freeze accounts",
                "Change passwords on all affected accounts",
                "Report to local police and file a complaint",
                "Report to FTC at reportfraud.ftc.gov",
                "Monitor your credit for unusual activity",
                "Don't blame yourself - scammers are professionals"
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LearnCard(icon: ImageVector, color: Color, title: String, points: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(24.dp), tint = color)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            points.forEach { point ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", fontWeight = FontWeight.Bold, color = color)
                    Text(point, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                }
            }
        }
    }
}

// ===== QUIZ TAB - "Scam or Not?" =====
@Composable
private fun QuizTab() {
    val questions = remember { getQuizQuestions() }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var answered by rememberSaveable { mutableStateOf(false) }
    var selectedAnswer by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var quizComplete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (quizComplete) {
            // Results screen
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (score >= 4) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (score >= 4) "Excellent!" else if (score >= 2) "Good effort!" else "Keep learning!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("You scored $score/${questions.size}", fontSize = 20.sp)
                    Text(
                        when {
                            score == questions.size -> "Perfect score! You're a scam detection expert."
                            score >= 4 -> "Great job! You can spot most scams."
                            score >= 2 -> "Not bad, but review the topics above to improve."
                            else -> "Scams can be tricky! Review our learning materials."
                        },
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge
                    )

                    Button(onClick = {
                        val shareText = "I scored $score/${questions.size} on the Scam Detection Quiz in DeepFake Shield! Can you beat my score? #DeepfakeShield #StaySafe"
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(Intent.createChooser(intent, "Share Score").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Your Score")
                    }
                    OutlinedButton(onClick = { currentIndex = 0; score = 0; answered = false; selectedAnswer = null; quizComplete = false }, modifier = Modifier.fillMaxWidth()) { Text("Play Again") }
                }
            }
        } else {
            // Progress
            @Suppress("DEPRECATION")
            LinearProgressIndicator(
                progress = (currentIndex + 1).toFloat() / questions.size,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Question ${currentIndex + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge)

            val q = questions[currentIndex]
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Is this a SCAM?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(q.message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // SCAM button: red before answering, green if correct answer is SCAM, red otherwise
                        Button(
                            onClick = { selectedAnswer = true; answered = true; if (q.isScam) score++ },
                            modifier = Modifier.weight(1f), enabled = !answered,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !answered -> MaterialTheme.colorScheme.error
                                    q.isScam -> Color(0xFF4CAF50) // Correct answer IS scam — show green
                                    selectedAnswer == true -> MaterialTheme.colorScheme.error // User picked SCAM but it wasn't — red
                                    else -> MaterialTheme.colorScheme.surfaceVariant // Not selected, not correct — neutral
                                }
                            )
                        ) { Text("SCAM", fontWeight = FontWeight.Bold) }

                        // SAFE button: green before answering, green if correct answer is SAFE, red otherwise
                        Button(
                            onClick = { selectedAnswer = false; answered = true; if (!q.isScam) score++ },
                            modifier = Modifier.weight(1f), enabled = !answered,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !answered -> Color(0xFF4CAF50)
                                    !q.isScam -> Color(0xFF4CAF50) // Correct answer IS safe — show green
                                    selectedAnswer == false -> MaterialTheme.colorScheme.error // User picked SAFE but it's a scam — red
                                    else -> MaterialTheme.colorScheme.surfaceVariant // Not selected, not correct — neutral
                                }
                            )
                        ) { Text("SAFE", fontWeight = FontWeight.Bold) }
                    }

                    if (answered) {
                        val correct = (selectedAnswer == true && q.isScam) || (selectedAnswer == false && !q.isScam)
                        Card(colors = CardDefaults.cardColors(containerColor = if (correct) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(if (correct) "Correct!" else "Wrong!", fontWeight = FontWeight.Bold, color = if (correct) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                                Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Button(
                            onClick = {
                                if (currentIndex < questions.lastIndex) { currentIndex++; answered = false; selectedAnswer = null }
                                else quizComplete = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (currentIndex < questions.lastIndex) "Next Question" else "See Results") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ===== TIPS TAB =====
@Composable
private fun TipsTab() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Daily Safety Tips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        val tips = listOf(
            "Never share your OTP or verification code with anyone, even if they claim to be from your bank.",
            "If a deal sounds too good to be true, it probably is. Always verify independently.",
            "Use two-factor authentication (2FA) on all important accounts.",
            "Be suspicious of any unexpected call asking for personal information.",
            "Check URLs carefully before clicking - scammers use lookalike domains.",
            "Keep your phone's OS and apps updated to patch security vulnerabilities.",
            "Don't download apps from unknown sources or APK files shared via messages.",
            "If a video seems shocking or unbelievable, it might be a deepfake. Verify the source.",
            "Use different passwords for different accounts. Consider a password manager.",
            "Be wary of QR codes in public places - they could redirect to malicious sites."
        )

        tips.forEachIndexed { index, tip ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), lineHeight = 20.sp)
                }
            }
        }

        // Share safety tips button
        Button(
            onClick = {
                val shareText = "10 Safety Tips from DeepFake Shield:\n\n" + tips.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n\n") + "\n\nStay safe! #DeepfakeShield"
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(Intent.createChooser(intent, "Share Tips").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Safety Tips")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ===== Quiz Data =====
private data class QuizQuestion(val message: String, val isScam: Boolean, val explanation: String)

private fun getQuizQuestions() = listOf(
    QuizQuestion(
        "URGENT: Your bank account has been compromised! Click here immediately to verify: http://secure-banklogin.xyz",
        true, "This is a classic phishing scam. Real banks never send links via SMS asking you to verify your account."
    ),
    QuizQuestion(
        "Hi, your Amazon package is scheduled for delivery tomorrow between 2-4 PM. Track at amazon.com/tracking",
        false, "This looks legitimate - it uses the official amazon.com domain and doesn't ask for personal info."
    ),
    QuizQuestion(
        "Congratulations! You've won a \$1000 Walmart gift card! Claim now: http://bit.ly/walmrt-prize",
        true, "Prize scams use urgency and fake shortened links. Note 'walmrt' misspelling. You can't win contests you didn't enter."
    ),
    QuizQuestion(
        "This is the IRS. You owe \$5,847 in back taxes. If you don't pay immediately with gift cards, a warrant will be issued for your arrest.",
        true, "The IRS never calls demanding immediate payment via gift cards, and never threatens arrest over the phone."
    ),
    QuizQuestion(
        "Your appointment with Dr. Smith is confirmed for March 15 at 2:30 PM. Reply Y to confirm or call 555-0123 to reschedule.",
        false, "This is a normal appointment reminder. It doesn't ask for personal info or contain suspicious links."
    ),
    QuizQuestion(
        "Hey, it's your grandson. I'm in jail and need \$2000 for bail. Please send money via Western Union. Don't tell mom!",
        true, "This is the 'grandparent scam'. Always verify by calling the person directly on their known number."
    )
)
