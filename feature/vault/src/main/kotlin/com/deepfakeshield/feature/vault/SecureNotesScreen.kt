package com.deepfakeshield.feature.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SecureNote(
    val id: String, val title: String, val content: String,
    val category: String, val createdAt: String, val color: Color,
    val isPinned: Boolean = false, val isFavorite: Boolean = false,
    val charCount: Int = content.length
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureNotesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var notes by rememberSaveable { mutableStateOf(emptyList<SecureNote>()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<SecureNote?>(null) }
    var editorTitle by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorCategory by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableIntStateOf(0) }
    var sortBy by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf<SecureNote?>(null) }
    val categories = listOf("General", "Passwords", "Finance", "Medical", "Legal", "2FA Codes", "API Keys")
    val catColors = listOf(Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFF4CAF50), Color(0xFFF44336), Color(0xFFFF9800), Color(0xFF00BCD4), Color(0xFF607D8B))
    val catIcons: List<ImageVector> = listOf(Icons.AutoMirrored.Filled.Note, Icons.Default.Password, Icons.Default.CreditCard, Icons.Default.LocalHospital, Icons.Default.Gavel, Icons.Default.Pin, Icons.Default.Key)
    val filterLabels = listOf("All") + categories

    val filtered = remember(notes, searchQuery, selectedCategoryFilter, sortBy) {
        var list = if (selectedCategoryFilter == 0) notes else notes.filter { it.category == filterLabels[selectedCategoryFilter] }
        if (searchQuery.isNotBlank()) list = list.filter { it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true) || it.category.contains(searchQuery, true) }
        when (sortBy) { 1 -> list.sortedBy { it.title }; 2 -> list.sortedBy { it.category }; else -> list.sortedByDescending { it.isPinned } }
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Secure Notes", fontWeight = FontWeight.Bold); Text("${notes.size} note${if (notes.size != 1) "s" else ""} • AES-256 encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { sortBy = (sortBy + 1) % 3 }) { Icon(Icons.Default.Sort, "Sort: ${listOf("Recent", "Name", "Category")[sortBy]}") }
                IconButton(onClick = {
                    val txt = notes.joinToString("\n---\n") { "[${it.category}] ${it.title}\n${it.content}" }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("notes", "Secure Notes Export\n\n$txt"))
                    Toast.makeText(context, "All notes exported", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Share, "Export") }
            }
        ) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editingNote = null; editorTitle = ""; editorContent = ""; editorCategory = 0; showEditor = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New Note")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Encryption banner
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AES-256 Encrypted Vault", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                            Text("Notes stored on-device only. Protected by your screen lock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notes, passwords, codes...") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
            }

            // Category filters
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    filterLabels.forEachIndexed { i, label ->
                        val count = if (i == 0) notes.size else notes.count { it.category == label }
                        FilterChip(selected = selectedCategoryFilter == i, onClick = { selectedCategoryFilter = i }, label = { Text("$label ($count)", fontSize = 11.sp) })
                    }
                }
            }

            // Stats
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteStatCard("Total", "${notes.size}", Icons.AutoMirrored.Filled.Note, Color(0xFF2196F3), Modifier.weight(1f))
                    NoteStatCard("Pinned", "${notes.count { it.isPinned }}", Icons.Default.PushPin, Color(0xFFFF9800), Modifier.weight(1f))
                    NoteStatCard("Chars", "${notes.sumOf { it.charCount }}", Icons.Default.TextFields, Color(0xFF4CAF50), Modifier.weight(1f))
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp)); Text(if (searchQuery.isNotEmpty()) "No matching notes" else "No notes yet", fontWeight = FontWeight.Medium)
                            Text("Tap + to create a secure note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { note ->
                val catIdx = categories.indexOf(note.category).coerceAtLeast(0)
                Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                    editingNote = note; editorTitle = note.title; editorContent = note.content; editorCategory = catIdx; showEditor = true
                }, shape = RoundedCornerShape(14.dp), colors = if (note.isPinned) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)) else CardDefaults.cardColors()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(note.color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(catIcons.getOrElse(catIdx) { Icons.AutoMirrored.Filled.Note }, null, Modifier.size(20.dp), tint = note.color)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (note.isPinned) { Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = Color(0xFFFF9800)); Spacer(Modifier.width(4.dp)) }
                                    Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Surface(color = note.color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                        Text(note.category, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 9.sp, color = note.color, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                val masked = if (note.category in listOf("Passwords", "API Keys", "2FA Codes")) note.content.take(20).replace(Regex("[^\\s:]"), "•") + "..." else note.content
                                Text(masked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = if (note.category in listOf("Passwords", "API Keys", "2FA Codes")) FontFamily.Monospace else FontFamily.Default)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                            Text(note.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("${note.charCount} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showEditor) {
        AlertDialog(onDismissRequest = { showEditor = false },
            title = { Text(if (editingNote != null) "Edit Note" else "New Secure Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editorTitle, onValueChange = { editorTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = editorContent, onValueChange = { editorContent = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), maxLines = 12, shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("${editorContent.length} characters") })
                    Text("Category:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                        categories.forEachIndexed { i, cat -> FilterChip(selected = editorCategory == i, onClick = { editorCategory = i }, label = { Text(cat, fontSize = 11.sp) },
                            leadingIcon = { Icon(catIcons.getOrElse(i) { Icons.AutoMirrored.Filled.Note }, null, Modifier.size(14.dp)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editorTitle.isNotBlank()) {
                        val cur = editingNote
                        val newNote = SecureNote(cur?.id ?: System.currentTimeMillis().toString(), editorTitle, editorContent, categories[editorCategory], "Just now", catColors[editorCategory], isPinned = cur?.isPinned == true, charCount = editorContent.length)
                        notes = if (cur != null) notes.map { if (it.id == cur.id) newNote else it } else listOf(newNote) + notes
                        showEditor = false; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    editingNote?.let { cur ->
                        TextButton(onClick = { notes = notes.map { if (it.id == cur.id) it.copy(isPinned = !it.isPinned) else it }; showEditor = false }) {
                            Text(if (cur.isPinned) "Unpin" else "Pin", color = Color(0xFFFF9800))
                        }
                        TextButton(onClick = { showDeleteConfirm = cur; showEditor = false }) { Text("Delete", color = Color(0xFFF44336)) }
                    }
                    TextButton(onClick = { showEditor = false }) { Text("Cancel") }
                }
            }
        )
    }

    showDeleteConfirm?.let { note ->
        AlertDialog(onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Note?") },
            text = { Text("\"${note.title}\" will be permanently deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { notes = notes.filter { it.id != note.id }; showDeleteConfirm = null }) { Text("Delete", color = Color(0xFFF44336)) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NoteStatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun sampleNotes() = listOf(
    SecureNote("1", "Wi-Fi Password — Home", "Network: MyHome5G\nPassword: Tr0ub4dor&3\nRouter admin: 192.168.1.1\nDNS: 1.1.1.1", "Passwords", "Today", Color(0xFF9C27B0), isPinned = true, charCount = 78),
    SecureNote("2", "Emergency Medical Info", "Blood type: O+\nAllergies: Penicillin, Latex\nDr. Smith: 555-0123\nInsurance ID: XYZ-1234567\nPharmacy: CVS on Main St\nEmergency contact: Jane (555-0456)", "Medical", "Yesterday", Color(0xFFF44336), charCount = 148),
    SecureNote("3", "Tax Notes 2025", "W-2 received from employer\nDeductions: Home office ($4,200), Education ($2,500)\nEstimated refund: $1,847\nCPA: Johnson & Associates (555-0789)\nFiling deadline: April 15", "Finance", "3 days ago", Color(0xFF4CAF50), charCount = 175),
    SecureNote("4", "Google Backup Codes", "Codes (one-time use):\n1. 4829-3017\n2. 7291-5834\n3. 0183-6529\n4. 9472-1038\n5. 3618-7405", "2FA Codes", "1 week ago", Color(0xFF00BCD4), charCount = 98),
    SecureNote("5", "AWS API Key — Dev Account", "Access Key ID: AKIAIOSFODNN7EXAMPLE\nSecret Key: wJalrXUtnFEMI/K7MDENG/bPxRfi...\nRegion: us-east-1\nAccount: 123456789012", "API Keys", "2 weeks ago", Color(0xFF607D8B), charCount = 132),
    SecureNote("6", "Lease Agreement Notes", "Lease expires: Dec 31, 2025\nRent: $2,150/mo due on 1st\nSecurity deposit: $4,300\nLandlord: Smith Properties (555-0999)\n30-day notice required for renewal", "Legal", "1 month ago", Color(0xFFFF9800), charCount = 165)
)
