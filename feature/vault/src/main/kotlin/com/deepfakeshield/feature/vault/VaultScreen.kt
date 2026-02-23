package com.deepfakeshield.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepfakeshield.core.ui.components.SeverityBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToAlertDetail: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar then clear state — clearing AFTER ensures the snackbar isn't cancelled
    LaunchedEffect(uiState.exportSuccess) {
        if (uiState.exportSuccess) {
            snackbarHostState.showSnackbar("Export shared successfully!")
            viewModel.clearExportSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Wrap in Column so the search field appears BELOW the app bar,
            // not rendered at the same (0,0) position overlapping it.
            Column {
                TopAppBar(
                    title = { Text("Incident Vault", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) viewModel.clearSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search vault")
                        }
                        if (uiState.entries.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.exportVault() },
                                enabled = !uiState.isExporting
                            ) {
                                Icon(Icons.Filled.FileDownload, "Export")
                            }
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.searchVault(it) },
                        label = { Text("Search by IOC, hash, domain...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.clearSearch(); showSearch = false }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.entries.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { if (!uiState.isExporting) viewModel.exportVault() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Share, "Export & Share")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.entries.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("${uiState.entries.size} incident${if (uiState.entries.size != 1) "s" else ""} • Tap Export to share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Loading state
            if (uiState.isLoading && uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null && uiState.entries.isEmpty()) {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Failed to load vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "No incidents recorded yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Detected threats will automatically appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                val searchResults = uiState.searchResults
                val displayEntries = searchResults ?: uiState.entries
                if (searchResults != null && searchResults.isEmpty() && uiState.searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matches for \"${uiState.searchQuery}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayEntries, key = { it.id }) { entry ->
                        var showDetailDialog by remember { mutableStateOf(false) }
                        @OptIn(ExperimentalMaterial3Api::class)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                entry.alertId?.let { onNavigateToAlertDetail(it) }
                                    ?: run { showDetailDialog = true }
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        entry.type,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SeverityBadge(entry.severity)
                                }

                                Text(
                                    entry.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    dateFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (showDetailDialog) {
                            AlertDialog(
                                onDismissRequest = { showDetailDialog = false },
                                title = { Text(entry.type) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            dateFormat.format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showDetailDialog = false }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }
                    }
                }
                }
            }
        }

        // Export progress dialog
        if (uiState.isExporting) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelExport() },
                title = { Text("Exporting Vault") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Text("Creating export file...")
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelExport() }) { Text("Cancel") }
                }
            )
        }
    }
}
