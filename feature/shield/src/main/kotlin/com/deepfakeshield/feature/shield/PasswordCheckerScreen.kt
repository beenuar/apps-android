package com.deepfakeshield.feature.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.log2

data class PasswordAnalysis(
    val strength: String,
    val score: Int,
    val crackTime: String,
    val entropy: Double,
    val checks: List<PasswordCheck>,
    val suggestions: List<String>,
    val breachCount: Int = -1,
    val isCommon: Boolean = false,
    val similarTo: String? = null
)

data class PasswordCheck(
    val name: String,
    val passed: Boolean,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordCheckerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<PasswordAnalysis?>(null) }
    var breachCount by remember { mutableIntStateOf(-1) }
    var isCheckingBreach by remember { mutableStateOf(false) }
    var genLength by remember { mutableIntStateOf(20) }

    LaunchedEffect(password) {
        analysis = try {
            if (password.isNotEmpty()) analyzePassword(password) else null
        } catch (_: Exception) { null }
        breachCount = -1
    }

    val strengthColor by animateColorAsState(
        targetValue = when (analysis?.strength) {
            "Very Strong" -> Color(0xFF4CAF50)
            "Strong" -> Color(0xFF8BC34A)
            "Medium" -> Color(0xFFFFC107)
            "Weak" -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        },
        animationSpec = tween(400),
        label = "strengthColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Checker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Password, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Password Strength Checker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("100% offline — your password never leaves this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Enter a password to test") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    Row {
                        if (password.isNotEmpty()) {
                            IconButton(onClick = {
                                copyToClipboard(context, password)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle visibility")
                        }
                    }
                },
                supportingText = {
                    if (password.isNotEmpty()) {
                        Text("${password.length} characters", style = MaterialTheme.typography.labelSmall)
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp)
            )

            // Generate + length slider
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Password Generator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Length: $genLength", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                        Slider(value = genLength.toFloat(), onValueChange = { genLength = it.toInt() }, valueRange = 8f..40f, steps = 31, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { password = generateStrongPassword(genLength); showPassword = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) { Text("Random", fontWeight = FontWeight.SemiBold) }
                        OutlinedButton(
                            onClick = { password = generatePassphrase(); showPassword = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) { Text("Passphrase", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // Breach check button
            if (password.length >= 4) {
                Button(
                    onClick = {
                        isCheckingBreach = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    enabled = !isCheckingBreach,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    if (isCheckingBreach) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Icon(Icons.Default.Shield, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (breachCount >= 0) "Check Again" else "Check if Breached (HIBP)", fontWeight = FontWeight.SemiBold)
                }
                if (isCheckingBreach) {
                    LaunchedEffect(password) {
                        try {
                            breachCount = withContext(Dispatchers.IO) { checkPasswordBreach(password) }
                        } catch (_: Exception) { breachCount = -2 }
                        isCheckingBreach = false
                    }
                }
                if (breachCount >= 0) {
                    val bColor = if (breachCount == 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bColor.copy(alpha = 0.08f))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (breachCount == 0) Icons.Default.VerifiedUser else Icons.Default.Warning, null, tint = bColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(if (breachCount == 0) "Not Found in Breaches" else "Found in $breachCount Breach${if (breachCount != 1) "es" else ""}!", fontWeight = FontWeight.Bold, color = bColor)
                                Text(if (breachCount == 0) "This password hasn't appeared in known data breaches" else "This password has been exposed — change it immediately!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (breachCount == -2) {
                    Text("Could not check — try again later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Strength meter
            AnimatedVisibility(visible = analysis != null) {
                analysis?.let { a ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Strength bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = strengthColor.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.strength, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = strengthColor)
                                    Spacer(Modifier.weight(1f))
                                    Text("${a.score}/100", style = MaterialTheme.typography.titleMedium, color = strengthColor)
                                }
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { a.score / 100f },
                                    modifier = Modifier.fillMaxWidth().height(10.dp),
                                    color = strengthColor,
                                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text("Crack time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(a.crackTime, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Entropy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${"%.1f".format(a.entropy)} bits", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Character composition bar
                        if (password.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    CharTypeChip("ABC", password.count { it.isUpperCase() }, Color(0xFF2196F3))
                                    CharTypeChip("abc", password.count { it.isLowerCase() }, Color(0xFF4CAF50))
                                    CharTypeChip("123", password.count { it.isDigit() }, Color(0xFFFF9800))
                                    CharTypeChip("#@!", password.count { !it.isLetterOrDigit() }, Color(0xFF9C27B0))
                                }
                            }
                        }

                        // Checks
                        Text("Security Checks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        a.checks.forEach { check ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (check.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    null,
                                    tint = if (check.passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(check.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Suggestions
                        if (a.suggestions.isNotEmpty()) {
                            Text("Suggestions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            a.suggestions.forEach { suggestion ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Best practices
                        Spacer(Modifier.height(8.dp))
                        Text("Password Best Practices", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        listOf(
                            "Use a unique password for every account",
                            "Make it at least 16 characters long",
                            "Use a passphrase: 4+ random words strung together",
                            "Use a password manager to generate and store passwords",
                            "Enable two-factor authentication everywhere"
                        ).forEach { tip ->
                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Circle, null, modifier = Modifier.size(6.dp).padding(top = 6.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(tip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharTypeChip(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.12f), CircleShape)
        ) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("password", text))
}

private fun generateStrongPassword(length: Int = 20): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghjkmnpqrstuvwxyz"
    val digits = "23456789"
    val special = "!@#\$%^&*_-+=?"
    val all = upper + lower + digits + special

    val password = StringBuilder()
    password.append(upper.random())
    password.append(lower.random())
    password.append(digits.random())
    password.append(special.random())
    repeat(length - 4) { password.append(all.random()) }
    return password.toList().shuffled().joinToString("")
}

private val COMMON_PASSWORDS = setOf(
    "password", "123456", "12345678", "qwerty", "abc123", "monkey", "1234567",
    "letmein", "trustno1", "dragon", "baseball", "iloveyou", "master", "sunshine",
    "ashley", "michael", "shadow", "123123", "654321", "superman", "qazwsx",
    "football", "password1", "password123", "welcome", "admin", "login", "princess",
    "hello", "charlie", "donald", "access", "ninja", "mustang", "starwars",
    "passw0rd", "whatever", "freedom", "batman", "summer", "winter", "spring",
    "autumn", "google", "cheese", "soccer", "1234", "12345", "123456789",
    "1234567890", "000000", "111111", "1q2w3e4r", "qwerty123", "password1!",
    "p@ssw0rd", "p@ssword", "pass1234", "changeme", "secret", "test", "demo",
    "temp", "guest", "root", "administrator", "user", "default", "master123",
    "love", "angel", "princess1", "flower", "samantha", "jessica", "diamond",
    "pokemon", "gaming", "minecraft", "roblox", "fortnite", "computer", "internet",
    "money", "killer", "matrix", "hunter", "ranger", "thomas", "robert", "daniel",
    "william", "joshua", "andrew", "james", "john", "david", "richard", "charles",
    "joseph", "george", "banana", "pepper", "orange", "cookie", "chicken", "coffee",
    "abcdef", "abcd1234", "qwer1234", "asdf1234", "zxcv1234", "q1w2e3r4",
    "1q2w3e", "zaq12wsx", "!qaz2wsx", "yankees", "jordan23", "trustno1",
    "arsenal", "liverpool", "chelsea", "barcelona", "madrid", "juventus"
)

private val KEYBOARD_WALKS = listOf("qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "zxcvbnm", "1qaz2wsx", "qazwsx", "1q2w3e", "zaq1xsw2", "!qaz@wsx", "qweasd", "qweasdzxc", "1234qwer", "poiuytrewq", "lkjhgfdsa", "mnbvcxz")
private val DATE_PATTERN = Regex("(19|20)\\d{2}|0[1-9]|1[0-2]\\d{2}|\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])")

private fun analyzePassword(password: String): PasswordAnalysis {
    val checks = mutableListOf<PasswordCheck>()
    val suggestions = mutableListOf<String>()

    val hasLength8 = password.length >= 8
    val hasLength12 = password.length >= 12
    val hasLength16 = password.length >= 16
    val hasUpper = password.any { it.isUpperCase() }
    val hasLower = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }
    val hasNoRepeats = !Regex("(.)\\1{2,}").containsMatchIn(password)
    val hasNoSequence = !listOf("123", "abc", "qwerty", "password", "asdf", "zxcv", "1qaz", "2wsx").any { password.lowercase().contains(it) }
    val isCommon = password.lowercase() in COMMON_PASSWORDS
    val hasNoDict = !isCommon && !listOf("password", "admin", "login", "welcome", "letmein").any { password.lowercase().contains(it) }
    val uniqueChars = password.toSet().size
    val hasGoodUniqueness = uniqueChars.toFloat() / password.length.coerceAtLeast(1) > 0.5f

    checks.add(PasswordCheck("Length", hasLength8, if (hasLength16) "${password.length} chars (excellent)" else if (hasLength12) "${password.length} chars (good)" else if (hasLength8) "${password.length} chars (minimum)" else "${password.length} chars (too short)"))
    checks.add(PasswordCheck("Uppercase Letters", hasUpper, if (hasUpper) "Contains uppercase (${password.count { it.isUpperCase() }})" else "Add uppercase letters (A-Z)"))
    checks.add(PasswordCheck("Lowercase Letters", hasLower, if (hasLower) "Contains lowercase (${password.count { it.isLowerCase() }})" else "Add lowercase letters (a-z)"))
    checks.add(PasswordCheck("Numbers", hasDigit, if (hasDigit) "Contains digits (${password.count { it.isDigit() }})" else "Add numbers (0-9)"))
    checks.add(PasswordCheck("Special Characters", hasSpecial, if (hasSpecial) "Contains symbols (${password.count { !it.isLetterOrDigit() }})" else "Add symbols (!@#\$%^&*)"))
    checks.add(PasswordCheck("No Repeating Chars", hasNoRepeats, if (hasNoRepeats) "No excessive repeating characters" else "Avoid repeating characters (aaa, 111)"))
    checks.add(PasswordCheck("No Common Patterns", hasNoSequence, if (hasNoSequence) "No keyboard/sequential patterns" else "Contains common patterns (123, qwerty)"))
    checks.add(PasswordCheck("Not a Common Password", !isCommon, if (!isCommon) "Not in top 200 most-used passwords" else "This is one of the most commonly used passwords!"))
    checks.add(PasswordCheck("No Dictionary Words", hasNoDict, if (hasNoDict) "No common dictionary words found" else "Contains easily guessable words"))
    checks.add(PasswordCheck("Character Uniqueness", hasGoodUniqueness, "$uniqueChars unique of ${password.length} total — ${if (hasGoodUniqueness) "good variety" else "too repetitive"}"))

    val hasKeyboardWalk = KEYBOARD_WALKS.any { password.lowercase().contains(it) }
    checks.add(PasswordCheck("No Keyboard Walks", !hasKeyboardWalk, if (!hasKeyboardWalk) "No keyboard walking patterns found" else "Contains keyboard walk pattern (qwerty, asdf, zxcv)"))

    val hasDatePattern = DATE_PATTERN.containsMatchIn(password)
    checks.add(PasswordCheck("No Date Patterns", !hasDatePattern, if (!hasDatePattern) "No birth years or dates detected" else "Contains date-like pattern (easily guessable)"))

    val upperRatio = if (password.isNotEmpty()) password.count { it.isUpperCase() }.toFloat() / password.length else 0f
    val digitRatio = if (password.isNotEmpty()) password.count { it.isDigit() }.toFloat() / password.length else 0f
    val symbolRatio = if (password.isNotEmpty()) password.count { !it.isLetterOrDigit() }.toFloat() / password.length else 0f
    val goodMix = upperRatio in 0.1f..0.5f && digitRatio in 0.1f..0.5f
    checks.add(PasswordCheck("Character Mix", goodMix || password.length < 6, "Upper: ${"%.0f".format(upperRatio * 100)}% • Digits: ${"%.0f".format(digitRatio * 100)}% • Symbols: ${"%.0f".format(symbolRatio * 100)}%${if (goodMix) " — well balanced" else ""}"))

    if (!hasLength12) suggestions.add("Make your password at least 12 characters long")
    if (!hasUpper) suggestions.add("Add uppercase letters for more complexity")
    if (!hasSpecial) suggestions.add("Add special characters like !@#\$%^&*")
    if (!hasNoSequence) suggestions.add("Avoid common words and keyboard patterns")
    if (!hasNoRepeats) suggestions.add("Don't repeat the same character 3+ times")
    if (isCommon) suggestions.add("This password is in every hacker's dictionary — change it immediately")
    if (!hasGoodUniqueness) suggestions.add("Use more diverse characters — avoid repeating the same ones")
    if (hasLength8 && !hasLength16) suggestions.add("Consider a passphrase: 4+ random words like 'tiger-piano-rocket-ocean'")

    var charsetSize = 0
    if (hasLower) charsetSize += 26
    if (hasUpper) charsetSize += 26
    if (hasDigit) charsetSize += 10
    if (hasSpecial) charsetSize += 33
    if (charsetSize == 0) charsetSize = 26
    val entropy = password.length * log2(charsetSize.toDouble())

    val passedCount = checks.count { it.passed }
    val lengthBonus = when { hasLength16 -> 30; hasLength12 -> 20; hasLength8 -> 10; else -> 0 }
    val commonPenalty = if (isCommon) -40 else 0
    val score = ((passedCount.toFloat() / checks.size) * 55 + lengthBonus + (if (entropy > 60) 15 else 0) + commonPenalty).toInt().coerceIn(0, 100)

    val strength = when {
        isCommon -> "Very Weak"
        score >= 90 -> "Very Strong"
        score >= 70 -> "Strong"
        score >= 50 -> "Medium"
        score >= 30 -> "Weak"
        else -> "Very Weak"
    }

    val combinations = Math.pow(charsetSize.toDouble(), password.length.toDouble())
    val guessesPerSec = 1e10
    val secondsToCrack = if (isCommon) 0.001 else combinations / guessesPerSec / 2
    val crackTime = when {
        secondsToCrack < 1 -> "Instantly"
        secondsToCrack < 60 -> "${secondsToCrack.toLong()} seconds"
        secondsToCrack < 3600 -> "${(secondsToCrack / 60).toLong()} minutes"
        secondsToCrack < 86400 -> "${(secondsToCrack / 3600).toLong()} hours"
        secondsToCrack < 31536000 -> "${(secondsToCrack / 86400).toLong()} days"
        secondsToCrack < 31536000.0 * 100 -> "${(secondsToCrack / 31536000).toLong()} years"
        secondsToCrack < 31536000.0 * 1e6 -> "${(secondsToCrack / 31536000).toLong() / 1000}K years"
        secondsToCrack < 31536000.0 * 1e9 -> "${(secondsToCrack / 31536000 / 1e6).toLong()}M years"
        else -> "Billions of years"
    }

    return PasswordAnalysis(strength, score, crackTime, entropy, checks, suggestions, isCommon = isCommon)
}

private fun checkPasswordBreach(password: String): Int {
    val sha1 = MessageDigest.getInstance("SHA-1")
        .digest(password.toByteArray())
        .joinToString("") { "%02x".format(it) }.uppercase()
    val prefix = sha1.take(5)
    val suffix = sha1.drop(5)

    var conn: HttpURLConnection? = null
    return try {
        val c = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection(com.deepfakeshield.core.network.TorNetworkModule.getProxy()) as HttpURLConnection
        conn = c
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Cyble-PasswordChecker")
        c.setRequestProperty("Add-Padding", "true")
        c.inputStream.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith(suffix, ignoreCase = true) }
                .map { it.substringAfter(":").trim().toIntOrNull() ?: 0 }
                .firstOrNull() ?: 0
        }
    } catch (_: Exception) { throw Exception("Network error") }
    finally { conn?.disconnect() }
}

private fun generatePassphrase(wordCount: Int = 4): String {
    val words = listOf(
        "tiger", "piano", "rocket", "ocean", "castle", "mirror", "garden", "thunder",
        "crystal", "dragon", "sunset", "mountain", "diamond", "falcon", "harbor",
        "lantern", "velvet", "copper", "silver", "golden", "marble", "forest",
        "valley", "bridge", "island", "comet", "storm", "flame", "frost", "river",
        "shadow", "legend", "beacon", "shield", "armor", "crown", "wizard", "phoenix",
        "arrow", "blade", "coral", "ember", "glyph", "haven", "ivory", "jungle",
        "knight", "lemon", "nexus", "orbit", "prism", "quartz", "raven", "spark"
    )
    return (1..wordCount).map { words.random() }.joinToString("-")
}
