package com.deepfakeshield.feature.shield

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MetadataField(val name: String, val value: String, val risk: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataStripperScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var metadata by remember { mutableStateOf<List<MetadataField>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedUri = it
            context.contentResolver.query(it, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) { val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) fileName = c.getString(idx) ?: "image" }
            }
            isAnalyzing = true
        }
    }

    LaunchedEffect(isAnalyzing) {
        if (isAnalyzing && selectedUri != null) {
            try {
                metadata = withContext(Dispatchers.IO) { selectedUri?.let { extractMetadata(context, it) } ?: emptyList() }
            } catch (_: Exception) { metadata = emptyList() }
            isAnalyzing = false
        }
    }

    val riskyFields = metadata.count { it.risk == "HIGH" || it.risk == "CRITICAL" }

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("Metadata Stripper", fontWeight = FontWeight.Bold); Text("Remove hidden data before sharing photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RemoveRedEye, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Photo Metadata Inspector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Photos contain hidden data — GPS location, device model, timestamps, and more. See what your photos reveal before sharing.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(onClick = { imagePicker.launch(arrayOf("image/*")); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (selectedUri != null) "Select Another Photo" else "Select Photo to Inspect", fontWeight = FontWeight.SemiBold)
            }

            if (isAnalyzing) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Reading EXIF metadata...", fontWeight = FontWeight.Medium) }
                        repeat(5) {
                            Card(Modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) { }
                        }
                    }
                }
            }

            if (metadata.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (riskyFields > 0) Color(0xFFF44336).copy(alpha = 0.06f) else Color(0xFF4CAF50).copy(alpha = 0.06f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(fileName, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(if (riskyFields > 0) "$riskyFields sensitive field${if (riskyFields > 1) "s" else ""} found — this photo reveals personal information" else "No high-risk metadata found",
                            style = MaterialTheme.typography.bodySmall, color = if (riskyFields > 0) Color(0xFFF44336) else Color(0xFF4CAF50))
                    }
                }

                Text("${metadata.size} Metadata Fields Found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                metadata.forEach { field ->
                    val riskColor = when (field.risk) { "CRITICAL" -> Color(0xFFF44336); "HIGH" -> Color(0xFFFF9800); "MEDIUM" -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(riskColor, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(field.name, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(field.value, Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text(field.risk, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = riskColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("How to Strip Metadata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                listOf("Screenshot the photo (removes ALL metadata)", "Use 'Share as new photo' on messaging apps", "Disable 'Save location' in camera settings", "Use apps like 'Scrambled Exif' to strip before sharing", "Social media strips most metadata on upload, but not all").forEach { tip ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = Color(0xFFFFC107)); Spacer(Modifier.width(6.dp)); Text(tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun extractMetadata(context: Context, uri: Uri): List<MetadataField> {
    val fields = mutableListOf<MetadataField>()
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = androidx.exifinterface.media.ExifInterface(input)
            fun addIf(tag: String, name: String, risk: String) { exif.getAttribute(tag)?.let { if (it.isNotBlank() && it != "0" && it != "0/0") fields.add(MetadataField(name, it.take(60), risk)) } }

            addIf(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, "GPS Latitude", "CRITICAL")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, "GPS Longitude", "CRITICAL")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, "GPS Altitude", "HIGH")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, "Date Taken", "HIGH")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, "Date Modified", "MEDIUM")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "Camera Make", "MEDIUM")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_MODEL, "Camera Model", "MEDIUM")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE, "Software", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, "Width", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, "Height", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "Orientation", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, "Exposure Time", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, "F-Number", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS, "ISO Speed", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, "Focal Length", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE, "White Balance", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_FLASH, "Flash", "LOW")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_ARTIST, "Artist/Author", "HIGH")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT, "Copyright", "MEDIUM")
            addIf(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT, "User Comment", "HIGH")
        }
    } catch (_: Exception) { }
    if (fields.isEmpty()) fields.add(MetadataField("EXIF Data", "No metadata found — this image may have been stripped", "LOW"))
    return fields.sortedBy { when (it.risk) { "CRITICAL" -> 0; "HIGH" -> 1; "MEDIUM" -> 2; else -> 3 } }
}
