package com.deepfakeshield.core.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates shareable visual safety report cards as images.
 * Instagram/WhatsApp-story ready format (1080x1920).
 */
object ShareableReportCard {

    fun generateWeeklyReport(
        context: Context,
        threatsBlocked: Int,
        safetyScore: Int,
        topThreatType: String,
        streakDays: Int
    ): File? {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#1976D2"), Color.parseColor("#004BA0"), Color.parseColor("#001E3C")),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative circles
        val circlePaint = Paint().apply { color = Color.parseColor("#20FFFFFF"); isAntiAlias = true }
        canvas.drawCircle(900f, 200f, 150f, circlePaint)
        canvas.drawCircle(100f, 1600f, 200f, circlePaint)

        // Title
        val titlePaint = Paint().apply { color = Color.WHITE; textSize = 64f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("WEEKLY SAFETY REPORT", width / 2f, 220f, titlePaint)

        // Date
        val datePaint = Paint().apply { color = Color.parseColor("#80FFFFFF"); textSize = 36f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date()), width / 2f, 280f, datePaint)

        // Shield icon area
        val shieldPaint = Paint().apply { color = Color.parseColor("#30FFFFFF"); isAntiAlias = true }
        canvas.drawCircle(width / 2f, 500f, 120f, shieldPaint)
        val checkPaint = Paint().apply { color = Color.parseColor("#4CAF50"); textSize = 120f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("✓", width / 2f, 540f, checkPaint)

        // Safety score
        val scorePaint = Paint().apply { color = Color.WHITE; textSize = 140f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("$safetyScore%", width / 2f, 800f, scorePaint)
        val scoreLabelPaint = Paint().apply { color = Color.parseColor("#B0FFFFFF"); textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("SAFETY SCORE", width / 2f, 860f, scoreLabelPaint)

        // Stats cards
        drawStatCard(canvas, 80f, 940f, 450f, 200f, "$threatsBlocked", "Threats Blocked", "#F44336")
        drawStatCard(canvas, 550f, 940f, 450f, 200f, "$streakDays", "Day Streak", "#FF9800")
        drawStatCard(canvas, 80f, 1170f, 920f, 160f, topThreatType, "Top Threat Blocked", "#9C27B0")

        // Footer
        val footerPaint = Paint().apply { color = Color.WHITE; textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("DeepFake Shield", width / 2f, 1600f, footerPaint)
        val tagPaint = Paint().apply { color = Color.parseColor("#80FFFFFF"); textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("Get protected: deepfakeshield.app", width / 2f, 1660f, tagPaint)
        canvas.drawText("#DeepfakeShield #StaySafe", width / 2f, 1710f, tagPaint)

        // Save to file
        return saveBitmap(context, bitmap, "safety_report_${System.currentTimeMillis()}")
    }

    fun generateScanResultCard(
        context: Context,
        scanType: String,
        riskScore: Int,
        threatType: String,
        findings: Int,
        recommendation: String
    ): File? {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = when { riskScore >= 70 -> "#D32F2F"; riskScore >= 40 -> "#F57F17"; else -> "#2E7D32" }
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor(bgColor), Color.parseColor("#1A1A1A")),
                floatArrayOf(0f, 0.5f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val titlePaint = Paint().apply { color = Color.WHITE; textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("SCAN RESULT", width / 2f, 120f, titlePaint)

        val typePaint = Paint().apply { color = Color.parseColor("#B0FFFFFF"); textSize = 36f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(scanType.uppercase(), width / 2f, 175f, typePaint)

        val scoreBigPaint = Paint().apply { color = Color.WHITE; textSize = 200f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("$riskScore", width / 2f, 450f, scoreBigPaint)
        canvas.drawText(when { riskScore >= 70 -> "DANGEROUS"; riskScore >= 40 -> "SUSPICIOUS"; else -> "SAFE" }, width / 2f, 530f, titlePaint)

        drawStatCard(canvas, 80f, 600f, 920f, 140f, threatType, "Threat Type", bgColor)
        drawStatCard(canvas, 80f, 770f, 920f, 140f, "$findings findings", "Analysis Depth", bgColor)

        // Recommendation
        val recPaint = Paint().apply { color = Color.parseColor("#E0FFFFFF"); textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val recLines = wrapText(recommendation, recPaint, 900f)
        var yPos = 1000f
        recLines.forEach { line -> canvas.drawText(line, width / 2f, yPos, recPaint); yPos += 42f }

        val footerPaint = Paint().apply { color = Color.parseColor("#80FFFFFF"); textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("Scanned with DeepFake Shield", width / 2f, height - 60f, footerPaint)

        return saveBitmap(context, bitmap, "scan_result_${System.currentTimeMillis()}")
    }

    fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Safety Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawStatCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, value: String, label: String, _accentColor: String) {
        val cardPaint = Paint().apply { color = Color.parseColor("#20FFFFFF"); isAntiAlias = true }
        canvas.drawRoundRect(x, y, x + w, y + h, 24f, 24f, cardPaint)
        val valuePaint = Paint().apply { color = Color.WHITE; textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(value, x + w / 2, y + h / 2 + 5f, valuePaint)
        val labelPaint = Paint().apply { color = Color.parseColor("#80FFFFFF"); textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(label, x + w / 2, y + h / 2 + 45f, labelPaint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) { currentLine = testLine }
            else { if (currentLine.isNotEmpty()) lines.add(currentLine); currentLine = word }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, name: String): File? {
        val file = File(context.cacheDir, "$name.png")
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
            file
        } catch (e: Exception) {
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }
}
