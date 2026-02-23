package com.deepfakeshield.feature.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.deepfakeshield.data.entity.AlertEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EXPORT & SHARING MANAGER
 * - Export safety reports (PDF, CSV, JSON)
 * - Share protection summary
 * - Generate shareable cards
 * - Email/SMS export
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Export safety report as CSV
     */
    suspend fun exportToCsv(alerts: List<AlertEntity>): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "safety_report_${System.currentTimeMillis()}.csv")
        
        file.bufferedWriter().use { writer ->
            // Header
            writer.write("Date,Time,Type,Severity,Score,Source,Summary\n")
            
            // Data rows
            alerts.forEach { alert ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(alert.timestamp))
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(alert.timestamp))
                
                writer.write("${csvEscape(date)},")
                writer.write("${csvEscape(time)},")
                writer.write("${csvEscape(alert.threatType.name)},")
                writer.write("${csvEscape(alert.severity.name)},")
                writer.write("${alert.score},")
                writer.write("${csvEscape(alert.source.name)},")
                writer.write("${csvEscape(alert.summary)}\n")
            }
        }
        
        file
    }
    
    /**
     * Export as JSON for programmatic access
     */
    suspend fun exportToJson(alerts: List<AlertEntity>): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "safety_report_${System.currentTimeMillis()}.json")
        
        val json = buildString {
            append("{\n")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            append("  \"export_date\": \"${sdf.format(Date())}\",\n")
            append("  \"total_threats\": ${alerts.size},\n")
            append("  \"alerts\": [\n")
            
            alerts.forEachIndexed { index, alert ->
                append("    {\n")
                append("      \"timestamp\": ${alert.timestamp},\n")
                append("      \"type\": \"${alert.threatType.name}\",\n")
                append("      \"severity\": \"${alert.severity.name}\",\n")
                append("      \"score\": ${alert.score},\n")
                append("      \"confidence\": ${alert.confidence},\n")
                append("      \"source\": \"${alert.source.name}\",\n")
                append("      \"summary\": \"${jsonEscape(alert.summary)}\"\n")
                append("    }${if (index < alerts.lastIndex) "," else ""}\n")
            }
            
            append("  ]\n")
            append("}\n")
        }
        
        file.writeText(json)
        file
    }
    
    /**
     * Generate shareable protection summary text
     */
    fun generateSharableSummary(
        totalThreats: Int,
        safetyScore: Int,
        activeDays: Int
    ): String {
        return """
            🛡️ My Safety Report - Deepfake Shield
            
            📊 Protection Statistics:
            • Threats Blocked: $totalThreats
            • Safety Score: $safetyScore%
            • Active Protection: $activeDays days
            
            ✅ I'm protected from:
            • SMS/Message scams
            • Suspicious phone calls
            • Phishing notifications
            • Deepfake videos
            
            Get Deepfake Shield to protect yourself too!
            #DeepfakeShield #CyberSecurity #StaySafe
        """.trimIndent()
    }
    
    /**
     * Share file via Android share sheet
     */
    fun shareFile(file: File, mimeType: String = "text/csv") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Safety Report - Deepfake Shield")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Safety Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("ExportManager", "No app available to share file", e)
        }
    }
    
    /**
     * Share text via Android share sheet
     */
    fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Protection Summary")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("ExportManager", "No app available to share text", e)
        }
    }
    
    private fun csvEscape(value: String): String {
        // Step 1: Prevent formula injection FIRST on the raw value (before CSV quoting wraps in ")
        val safe = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$value"
        } else {
            value
        }
        // Step 2: CSV quoting SECOND (includes \r per RFC 4180)
        return if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }
    
    private fun jsonEscape(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace(Regex("[\\u0000-\\u001F]")) { match ->
                String.format("\\u%04x", match.value[0].code)
            }
    }
}
