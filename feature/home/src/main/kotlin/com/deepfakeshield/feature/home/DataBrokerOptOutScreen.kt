package com.deepfakeshield.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DataBroker(val name: String, val url: String, val dataTypes: String, val difficulty: String, val timeToProcess: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBrokerOptOutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var completedBrokers by remember { mutableStateOf(setOf<String>()) }
    var visibleCount by remember { mutableIntStateOf(0) }

    val brokers = remember { listOf(
        DataBroker("Spokeo", "https://www.spokeo.com/optout", "Name, Phone, Address, Email, Social", "Easy", "24-48 hours", "One of the largest people-search sites. Enter your profile URL and email to opt out."),
        DataBroker("WhitePages", "https://www.whitepages.com/suppression-requests", "Name, Phone, Address, Relatives", "Easy", "24 hours", "Major directory service. Find your listing and submit a removal request."),
        DataBroker("BeenVerified", "https://www.beenverified.com/app/optout/search", "Name, Phone, Address, Email, Criminal", "Medium", "24 hours", "Background check service. Search for yourself and submit opt-out for each listing."),
        DataBroker("Intelius", "https://www.intelius.com/opt-out", "Name, Address, Phone, Age, Relatives", "Medium", "72 hours", "People search engine. Requires finding your record, then submitting removal."),
        DataBroker("TruePeopleSearch", "https://www.truepeoplesearch.com/removal", "Name, Address, Phone, Email", "Easy", "24-48 hours", "Free people-search site. Click the 'Remove Record' link on your listing."),
        DataBroker("FastPeopleSearch", "https://www.fastpeoplesearch.com/removal", "Name, Address, Phone, Email", "Easy", "24 hours", "Another free directory. Find your listing and click 'Remove This Record'."),
        DataBroker("Radaris", "https://radaris.com/control/privacy", "Name, Address, Phone, Employment, Education", "Hard", "24-48 hours", "Aggregates public records. Requires creating an account to manage and remove listings."),
        DataBroker("MyLife", "https://www.mylife.com/privacy-policy#opt-out", "Name, Reputation Score, Background", "Hard", "30 days", "Assigns 'reputation scores'. Send written opt-out request or call 1-888-704-1900."),
        DataBroker("PeopleFinder", "https://www.peoplefinder.com/optout", "Name, Phone, Address, Email", "Easy", "48 hours", "Standard people-search. Submit email with links to the records you want removed."),
        DataBroker("USA People Search", "https://www.usa-people-search.com/manage/default.aspx", "Name, Address, Phone", "Medium", "48-72 hours", "Public records aggregator. Find your listing and request removal."),
        DataBroker("Acxiom", "https://isapps.acxiom.com/optout/optout.aspx", "Name, Address, Demographics, Purchase History", "Medium", "2 weeks", "Major marketing data broker. One of the biggest — sells your data to advertisers."),
        DataBroker("Oracle Data Cloud", "https://datacloudoptout.oracle.com/", "Browsing, Purchase, Demographics", "Medium", "30 days", "Oracle's ad targeting platform. Opt out of online interest-based advertising."),
        DataBroker("Epsilon", "https://www.epsilon.com/consumer-information", "Name, Address, Purchase, Demographics", "Hard", "10 weeks", "Email marketing giant. Opt out via mail or online form for marketing lists."),
        DataBroker("Google Activity", "https://myactivity.google.com/", "Search, Location, YouTube, Voice", "Easy", "Immediate", "Not a broker, but Google stores massive amounts of your activity. Delete and disable tracking.")
    ) }

    val progress = completedBrokers.size.toFloat() / brokers.size

    LaunchedEffect(Unit) { for (i in 1..brokers.size) { delay(50); visibleCount = i } }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Data Broker Opt-Out", fontWeight = FontWeight.Bold); Text("${completedBrokers.size}/${brokers.size} completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Remove Your Data from the Internet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Data brokers collect and sell your personal information. Opt out of each one below to remove your data. This process takes 30-60 minutes but dramatically reduces your digital exposure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        Text("${(progress * 100).toInt()}% complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                    }
                }
            }

            itemsIndexed(brokers, key = { _, b -> b.name }) { index, broker ->
                AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 4 }) {
                val isDone = broker.name in completedBrokers
                var expanded by remember { mutableStateOf(false) }
                val diffColor = when (broker.difficulty) { "Easy" -> Color(0xFF4CAF50); "Medium" -> Color(0xFFFF9800); else -> Color(0xFFF44336) }
                Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(), shape = RoundedCornerShape(14.dp),
                    colors = if (isDone) CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.04f)) else CardDefaults.cardColors()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isDone, onCheckedChange = { completedBrokers = if (isDone) completedBrokers - broker.name else completedBrokers + broker.name; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                            Column(Modifier.weight(1f)) {
                                Text(broker.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Text(broker.dataTypes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(color = diffColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                    Text(broker.difficulty, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = diffColor, fontWeight = FontWeight.Bold)
                                }
                                Text(broker.timeToProcess, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                            Text(broker.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(broker.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show() }
                            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open Opt-Out Page") }
                        }
                    }
                }
                }
            }
        }
    }
}
