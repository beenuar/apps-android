package com.deepfakeshield.feature.quickactions

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * QUICK ACTIONS BOTTOM SHEET
 * - Fast access to common actions
 * - Scan video/message
 * - View recent alerts
 * - Toggle protection
 * - Export reports
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    onDismiss: () -> Unit,
    onScanVideo: () -> Unit,
    onScanMessage: () -> Unit,
    onViewAlerts: () -> Unit,
    onExportReport: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "⚡ Quick Actions",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                QuickActionItem(
                    icon = Icons.Filled.VideoLibrary,
                    title = "Scan Video",
                    subtitle = "Check video for deepfakes",
                    onClick = {
                        onScanVideo()
                        onDismiss()
                    }
                )
                
                QuickActionItem(
                    icon = Icons.Filled.Message,
                    title = "Scan Message",
                    subtitle = "Analyze text for scams",
                    onClick = {
                        onScanMessage()
                        onDismiss()
                    }
                )
                
                QuickActionItem(
                    icon = Icons.Filled.Notifications,
                    title = "View Alerts",
                    subtitle = "See recent threats",
                    onClick = {
                        onViewAlerts()
                        onDismiss()
                    }
                )
                
                QuickActionItem(
                    icon = Icons.Filled.Share,
                    title = "Export Report",
                    subtitle = "Share safety summary",
                    onClick = {
                        onExportReport()
                        onDismiss()
                    }
                )
                
                Spacer(Modifier.height(8.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
