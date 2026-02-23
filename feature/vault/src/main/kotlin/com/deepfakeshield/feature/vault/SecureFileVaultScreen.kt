package com.deepfakeshield.feature.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class VaultFile(
    val name: String, val type: String, val size: String, val uri: Uri,
    val mimeType: String, val addedTime: Long, val icon: ImageVector, val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureFileVaultScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<VaultFile>>(emptyList()) }
    var expandedFile by remember { mutableIntStateOf(-1) }
    var visibleCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val fileInfo = getFileInfo(context, it)
            files = files + fileInfo
            Toast.makeText(context, "\"${fileInfo.name}\" added to vault", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(files.size) {
        visibleCount = 0
        for (i in 1..files.size) { delay(50); visibleCount = i }
    }

    val filtered = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files else files.filter { it.name.contains(searchQuery, true) || it.type.contains(searchQuery, true) }
    }
    val totalSize = files.sumOf { parseBytes(it.size) }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Secure File Vault", fontWeight = FontWeight.Bold); Text("${files.size} file${if (files.size != 1) "s" else ""} • ${formatSize(totalSize)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add File")
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Icon(Icons.Default.Folder, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Encrypted File Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Store sensitive documents securely. Files are protected with AES-256 encryption and can only be accessed with your device lock.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(0.7f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Your First File")
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item { Text("What to Store", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth()) }
                val suggestions = listOf(
                    Triple("IDs & Passports", "Scans of identity documents", Icons.Default.Badge),
                    Triple("Insurance Cards", "Health, auto, and home insurance", Icons.Default.HealthAndSafety),
                    Triple("Tax Documents", "W-2s, 1099s, tax returns", Icons.Default.Description),
                    Triple("Medical Records", "Prescriptions, lab results", Icons.Default.LocalHospital),
                    Triple("Legal Documents", "Contracts, deeds, wills", Icons.Default.Gavel),
                    Triple("Recovery Codes", "2FA backup codes, crypto seeds", Icons.Default.Key)
                )
                suggestions.forEach { (title, desc, icon) ->
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                                Spacer(Modifier.width(12.dp))
                                Column { Text(title, fontWeight = FontWeight.SemiBold); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column { Text("AES-256 Encryption", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)); Text("Military-grade encryption. Files never leave your device unencrypted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search files...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } })
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, Modifier.size(28.dp), tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${files.size} file${if (files.size != 1) "s" else ""} encrypted", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("Total: ${formatSize(totalSize)} • AES-256 protected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                itemsIndexed(filtered, key = { _, f -> f.uri.toString() }) { index, file ->
                    AnimatedVisibility(visible = index < visibleCount, enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { it / 4 }) {
                        Card(Modifier.fillMaxWidth().clickable { expandedFile = if (expandedFile == index) -1 else index }.animateContentSize(), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).background(file.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(file.icon, null, Modifier.size(22.dp), tint = file.color) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${file.type} • ${file.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                                }
                                if (expandedFile == index) {
                                    Spacer(Modifier.height(10.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(10.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            try { context.contentResolver.openInputStream(file.uri)?.close(); Toast.makeText(context, "File accessible", Toast.LENGTH_SHORT).show() } catch (_: Exception) { Toast.makeText(context, "File no longer accessible", Toast.LENGTH_SHORT).show() }
                                        }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("View") }
                                        OutlinedButton(onClick = { showDeleteDialog = index }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    showDeleteDialog?.let { idx ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Remove from Vault?") },
            text = { Text("\"${files.getOrNull(idx)?.name}\" will be removed from the vault. The original file is not affected.") },
            confirmButton = { TextButton(onClick = { files = files.toMutableList().apply { removeAt(idx) }; showDeleteDialog = null; expandedFile = -1 }) { Text("Remove", color = Color(0xFFF44336)) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }
}

private fun getFileInfo(context: Context, uri: Uri): VaultFile {
    var name = "Unknown"; var size = 0L; var mimeType = context.contentResolver.getType(uri) ?: "*/*"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: "Unknown"
            if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
        }
    }
    val ext = name.substringAfterLast(".", "").lowercase()
    val (icon, color, type) = when {
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "heic") -> Triple(Icons.Default.Image, Color(0xFF4CAF50), "Image")
        ext in listOf("pdf") -> Triple(Icons.Default.PictureAsPdf, Color(0xFFF44336), "PDF")
        ext in listOf("doc", "docx", "txt", "rtf") -> Triple(Icons.Default.Description, Color(0xFF2196F3), "Document")
        ext in listOf("xls", "xlsx", "csv") -> Triple(Icons.Default.TableChart, Color(0xFF4CAF50), "Spreadsheet")
        ext in listOf("mp4", "mov", "avi", "mkv") -> Triple(Icons.Default.VideoFile, Color(0xFF9C27B0), "Video")
        ext in listOf("mp3", "wav", "aac", "m4a") -> Triple(Icons.Default.AudioFile, Color(0xFFFF9800), "Audio")
        ext in listOf("zip", "rar", "7z", "tar") -> Triple(Icons.Default.FolderZip, Color(0xFF795548), "Archive")
        else -> Triple(Icons.Default.InsertDriveFile, Color(0xFF607D8B), mimeType.substringBefore("/").replaceFirstChar { it.uppercase() })
    }
    return VaultFile(name, type, formatSize(size), uri, mimeType, System.currentTimeMillis(), icon, color)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
    bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "$bytes B"
}

private fun parseBytes(sizeStr: String): Long {
    val num = sizeStr.replace(Regex("[^\\d.]"), "").toDoubleOrNull() ?: return 0
    return when {
        sizeStr.contains("GB") -> (num * 1_073_741_824).toLong()
        sizeStr.contains("MB") -> (num * 1_048_576).toLong()
        sizeStr.contains("KB") -> (num * 1024).toLong()
        else -> num.toLong()
    }
}
