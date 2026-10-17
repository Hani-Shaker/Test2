package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.TaskEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportHelper {

    fun generateAndShareTaskReport(
        context: Context,
        tasks: List<TaskEntity>,
        timeframeTitle: String // "يومي", "أسبوعي", "شهري"
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard size: 595 x 842 points
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val currentDateStr = dateFormat.format(Date())

        // 1. Header Banner
        paint.color = Color.parseColor("#0F172A") // Slate Dark
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Title
        paint.color = Color.parseColor("#14B8A6") // Teal
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TaskFlow - تقرير المهام والإنجاز ($timeframeTitle)", 30f, 45f, paint)

        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("تاريخ التصدير: $currentDateStr", 30f, 70f, paint)

        // 2. Statistics Summary Cards
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val pending = total - completed
        val rate = if (total > 0) (completed * 100 / total) else 0

        // Background for stats box
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(30f, 110f, 565f, 180f, 8f, 8f, paint)

        paint.color = Color.parseColor("#334155")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ملخص الإنتاجية:", 45f, 135f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("• إجمالي المهام: $total", 45f, 160f, paint)
        canvas.drawText("• المنجزة: $completed (${rate}%)", 180f, 160f, paint)
        canvas.drawText("• المعلقة: $pending", 330f, 160f, paint)
        canvas.drawText("• معدل النجاح: ${rate}%", 460f, 160f, paint)

        // 3. Task Table Header
        var yPos = 215f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRect(30f, yPos, 565f, yPos + 25f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("#", 40f, yPos + 17f, paint)
        canvas.drawText("عنوان المهمة", 70f, yPos + 17f, paint)
        canvas.drawText("التصنيف", 300f, yPos + 17f, paint)
        canvas.drawText("الأولوية", 410f, yPos + 17f, paint)
        canvas.drawText("الحالة", 490f, yPos + 17f, paint)

        yPos += 25f

        // 4. Task Table Rows
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f

        for ((index, task) in tasks.take(22).withIndex()) {
            val isEven = index % 2 == 0
            paint.color = if (isEven) Color.parseColor("#F8FAFC") else Color.WHITE
            canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

            paint.color = Color.parseColor("#1E293B")
            canvas.drawText("${index + 1}", 40f, yPos + 15f, paint)

            val displayTitle = if (task.title.length > 32) task.title.take(30) + "..." else task.title
            canvas.drawText(displayTitle, 70f, yPos + 15f, paint)
            canvas.drawText(task.category, 300f, yPos + 15f, paint)

            // Priority text and color
            val priorityText = when (task.priority) {
                "HIGH" -> "عاجل"
                "MEDIUM" -> "متوسط"
                else -> "عادي"
            }
            canvas.drawText(priorityText, 410f, yPos + 15f, paint)

            // Status
            paint.color = if (task.isCompleted) Color.parseColor("#10B981") else Color.parseColor("#F59E0B")
            canvas.drawText(if (task.isCompleted) "منجز ✔" else "قيد الانتظار", 490f, yPos + 15f, paint)

            yPos += 22f
        }

        // 5. Footer
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 9f
        canvas.drawText("تم استخراج هذا التقرير تلقائياً بواسطة تطبيق TaskFlow لإدارة المهام واليوميات.", 30f, 820f, paint)

        pdfDocument.finishPage(page)

        // Write to file
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val fileName = "TaskReport_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(reportsDir, fileName)

        try {
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            return pdfFile
        } catch (e: Exception) {
            pdfDocument.close()
            return null
        }
    }

    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "تقرير إنجاز المهام - TaskFlow")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(shareIntent, "مشاركة تقرير PDF")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooser)
    }
}
