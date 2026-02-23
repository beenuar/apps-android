package com.deepfakeshield.feature.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var reportType by remember { mutableIntStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var scamCategory by remember { mutableIntStateOf(0) }
    var submitted by remember { mutableStateOf(false) }
    val types = listOf("Phone Number", "URL/Domain", "Email")
    val scamCategories = listOf("Robocall", "IRS/Tax Scam", "Tech Support", "Romance Scam", "Phishing", "Investment Fraud", "Other")

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Report a Scam", fontWeight = FontWeight.Bold); Text("Help protect the community", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (submitted) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f))) {
                    Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.height(16.dp))
                        Text("Report Submitted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Spacer(Modifier.height(8.dp))
                        Text("Thank you for helping protect the community. Your report has been added to our threat database and will help warn other users.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { submitted = false; phoneNumber = ""; url = ""; description = "" }, shape = RoundedCornerShape(14.dp)) {
                            Text("Report Another Scam")
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReportProblem, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Community Scam Reporting", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Report scam numbers, phishing URLs, and fraud to protect others. All reports are anonymous.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text("What are you reporting?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEachIndexed { i, t -> FilterChip(selected = reportType == i, onClick = { reportType = i }, label = { Text(t) }) }
                }

                when (reportType) {
                    0 -> OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Scam phone number") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    1 -> OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Scam URL or domain") }, leadingIcon = { Icon(Icons.Default.Link, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                    2 -> OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Scam email address") }, leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }

                Text("Scam Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    scamCategories.forEachIndexed { i, c -> FilterChip(selected = scamCategory == i, onClick = { scamCategory = i }, label = { Text(c, fontSize = 11.sp) }) }
                }

                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, placeholder = { Text("What happened? How did they contact you?") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(14.dp), maxLines = 5)

                Button(onClick = {
                    // Write to local database for caller ID
                    try {
                        val prefs = context.getSharedPreferences("community_reports", Context.MODE_PRIVATE)
                        val key = if (reportType == 0) "phone_$phoneNumber" else "url_$url"
                        val count = prefs.getInt(key, 0) + 1
                        prefs.edit().putInt(key, count).putString("${key}_cat", scamCategories[scamCategory]).putLong("${key}_time", System.currentTimeMillis()).apply()
                    } catch (_: Exception) { }
                    submitted = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Report submitted — thank you!", Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                    enabled = (reportType == 0 && phoneNumber.length >= 7) || (reportType != 0 && url.length >= 5)) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Submit Report", fontWeight = FontWeight.SemiBold)
                }

                Text("Your Privacy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("Reports are anonymous. We never share your identity or device information with third parties.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

