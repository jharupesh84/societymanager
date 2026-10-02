package com.society.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points
    private const val MARGIN = 36f

    /**
     * Resolves the target directory to save the PDF.
     * Prefers the public Downloads directory on the device, falling back to app external files or cache.
     */
    private fun getDownloadsDirectory(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
            return publicDownloads
        }
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appDownloads != null && (appDownloads.exists() || appDownloads.mkdirs())) {
            return appDownloads
        }
        val fallback = File(context.cacheDir, "reports")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    /**
     * 1. Export Collections PDF (All or Block-wise, filtered by Category and Month)
     */
    fun exportCollectionsPdf(
        context: Context,
        collections: List<CollectionEntity>,
        category: String,
        monthFilter: String? = null,
        blockFilter: String? = null
    ): File? {
        val document = PdfDocument()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayDate = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())

        val sanitizedCategory = category.replace(" ", "_")
        val sanitizedMonth = monthFilter?.replace(" ", "_") ?: "AllMonths"
        val sanitizedBlock = if (blockFilter != null) "Block_${blockFilter}" else "AllBlocks"
        val fileName = "${sanitizedCategory}_Collection_${sanitizedMonth}_${sanitizedBlock}_$timestamp.pdf"

        val titleText = "$category Collection Report"
        val scopeText = buildString {
            if (monthFilter != null) append("Month: $monthFilter | ")
            if (blockFilter != null) append("Block: $blockFilter | ") else append("All Blocks | ")
            append("Total Records: ${collections.size}")
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(27, 94, 32)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val categoryPaint = Paint().apply {
            color = Color.rgb(21, 101, 192)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(232, 245, 233)
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeader(c: Canvas) {
            c.drawText("SOCIETY RESIDENTS WELFARE ASSOCIATION", MARGIN, 42f, titlePaint)
            c.drawText(titleText, MARGIN, 58f, categoryPaint)
            c.drawText("$scopeText  |  Generated: $displayDate", MARGIN, 73f, subtitlePaint)
            c.drawLine(MARGIN, 82f, PAGE_WIDTH - MARGIN, 82f, linePaint)
        }

        drawHeader(canvas)

        var y = 102f
        val colFlat = MARGIN
        val colOwner = MARGIN + 70f
        val colMonth = MARGIN + 230f
        val colMode = MARGIN + 320f
        val colAmount = MARGIN + 395f
        val colDate = MARGIN + 465f

        // Table Header
        canvas.drawRect(MARGIN, y - 12f, PAGE_WIDTH - MARGIN, y + 6f, headerBgPaint)
        canvas.drawText("Flat No", colFlat, y, headerPaint)
        canvas.drawText("Resident Name", colOwner, y, headerPaint)
        canvas.drawText("Period/Month", colMonth, y, headerPaint)
        canvas.drawText("Mode", colMode, y, headerPaint)
        canvas.drawText("Amount (₹)", colAmount, y, headerPaint)
        canvas.drawText("Date", colDate, y, headerPaint)
        y += 18f

        var totalAmount = 0.0
        var totalCash = 0.0
        var totalOnline = 0.0

        for (item in collections) {
            if (y > PAGE_HEIGHT - 60f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 102f
            }

            totalAmount += item.amount
            if (item.paymentMode.equals("Cash", ignoreCase = true)) totalCash += item.amount else totalOnline += item.amount

            val displayFlat = when {
                item.flatNo.startsWith("${item.block}-", ignoreCase = true) -> item.flatNo
                item.flatNo.startsWith(item.block, ignoreCase = true) && item.flatNo.length > item.block.length -> item.flatNo
                item.block.isNotBlank() && item.block != "General" -> "${item.block}-${item.flatNo}"
                else -> item.flatNo
            }
            canvas.drawText(displayFlat, colFlat, y, rowPaint)
            canvas.drawText(item.ownerName.take(24), colOwner, y, rowPaint)
            canvas.drawText(if (item.monthYear.isNotBlank()) item.monthYear.take(14) else "-", colMonth, y, rowPaint)
            canvas.drawText(item.paymentMode, colMode, y, rowPaint)
            canvas.drawText("₹%.2f".format(item.amount), colAmount, y, rowPaint)
            canvas.drawText(item.date, colDate, y, rowPaint)
            canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, linePaint)
            y += 16f
        }

        // Summary at end of table
        y += 10f
        if (y > PAGE_HEIGHT - 70f) {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeader(canvas)
            y = 102f
        }

        canvas.drawRect(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y + 36f, headerBgPaint)
        canvas.drawText("TOTAL COLLECTION: ₹%.2f".format(totalAmount), MARGIN + 10f, y + 6f, headerPaint)
        canvas.drawText("Online (UPI/Bank): ₹%.2f   |   Cash: ₹%.2f".format(totalOnline, totalCash), MARGIN + 10f, y + 24f, subtitlePaint)

        document.finishPage(page)

        return saveAndOpenFile(context, document, fileName, titleText)
    }

    /**
     * 2. Export Expenses PDF (Filtered by Category and Month)
     */
    fun exportExpensesPdf(
        context: Context,
        expenses: List<ExpenseEntity>,
        category: String,
        monthFilter: String? = null
    ): File? {
        val document = PdfDocument()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayDate = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())

        val sanitizedCategory = category.replace(" ", "_")
        val sanitizedMonth = monthFilter?.replace(" ", "_") ?: "AllMonths"
        val fileName = "${sanitizedCategory}_Expenses_${sanitizedMonth}_$timestamp.pdf"

        val titleText = "$category Expense Report"
        val scopeText = buildString {
            if (monthFilter != null) append("Month: $monthFilter | ")
            append("Total Entries: ${expenses.size}")
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(198, 40, 40)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val categoryPaint = Paint().apply {
            color = Color.rgb(21, 101, 192)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 9f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(255, 235, 238)
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeader(c: Canvas) {
            c.drawText("SOCIETY RESIDENTS WELFARE ASSOCIATION", MARGIN, 42f, titlePaint)
            c.drawText(titleText, MARGIN, 58f, categoryPaint)
            c.drawText("$scopeText  |  Generated: $displayDate", MARGIN, 73f, subtitlePaint)
            c.drawLine(MARGIN, 82f, PAGE_WIDTH - MARGIN, 82f, linePaint)
        }

        drawHeader(canvas)

        var y = 102f
        val colSr = MARGIN
        val colDetail = MARGIN + 35f
        val colPeriod = MARGIN + 320f
        val colAmount = MARGIN + 410f
        val colDate = MARGIN + 480f

        // Table Header
        canvas.drawRect(MARGIN, y - 12f, PAGE_WIDTH - MARGIN, y + 6f, headerBgPaint)
        canvas.drawText("#", colSr, y, headerPaint)
        canvas.drawText("Expense Detail / Purpose", colDetail, y, headerPaint)
        canvas.drawText("Period", colPeriod, y, headerPaint)
        canvas.drawText("Amount (₹)", colAmount, y, headerPaint)
        canvas.drawText("Date", colDate, y, headerPaint)
        y += 18f

        var totalExpense = 0.0
        var sr = 1

        for (item in expenses) {
            if (y > PAGE_HEIGHT - 60f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 102f
            }

            totalExpense += item.amount
            canvas.drawText("$sr", colSr, y, rowPaint)
            canvas.drawText(item.detail.take(40), colDetail, y, rowPaint)
            canvas.drawText(if (item.monthYear.isNotBlank()) item.monthYear.take(13) else "-", colPeriod, y, rowPaint)
            canvas.drawText("₹%.2f".format(item.amount), colAmount, y, rowPaint)
            canvas.drawText(item.date, colDate, y, rowPaint)
            canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, linePaint)
            y += 16f
            sr++
        }

        // Summary footer
        y += 10f
        if (y > PAGE_HEIGHT - 60f) {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeader(canvas)
            y = 102f
        }

        canvas.drawRect(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y + 26f, headerBgPaint)
        canvas.drawText("TOTAL EXPENSE: ₹%.2f".format(totalExpense), MARGIN + 10f, y + 10f, headerPaint)

        document.finishPage(page)

        return saveAndOpenFile(context, document, fileName, titleText)
    }

    /**
     * 3. Export Consolidated Financial Summary PDF (with Collections, Block breakdown & Expenses)
     */
    fun exportConsolidatedSummaryPdf(
        context: Context,
        blockSummaries: List<BlockSummary>,
        totalCollection: Double,
        totalExpense: Double,
        netBalance: Double,
        category: String,
        monthFilter: String? = null
    ): File? {
        val document = PdfDocument()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayDate = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())

        val sanitizedCategory = category.replace(" ", "_")
        val sanitizedMonth = monthFilter?.replace(" ", "_") ?: "AllMonths"
        val fileName = "${sanitizedCategory}_Consolidated_Statement_${sanitizedMonth}_$timestamp.pdf"

        val titleText = "$category Consolidated Statement"

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(21, 101, 192)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val categoryPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }
        val cardPaint = Paint().apply {
            color = Color.rgb(245, 245, 245)
        }

        // Header
        canvas.drawText("SOCIETY RESIDENTS WELFARE ASSOCIATION", MARGIN, 42f, titlePaint)
        canvas.drawText(titleText, MARGIN, 58f, categoryPaint)
        val periodText = if (monthFilter != null) "Period: $monthFilter | Statement As On: $displayDate" else "Period: All Recorded Months | Statement As On: $displayDate"
        canvas.drawText(periodText, MARGIN, 73f, Paint().apply { color = Color.GRAY; textSize = 9f })
        canvas.drawLine(MARGIN, 84f, PAGE_WIDTH - MARGIN, 84f, linePaint)

        // KPI Metric Cards
        val cardY = 100f
        val cardW = (PAGE_WIDTH - 2 * MARGIN - 20f) / 3f

        // Card 1: Total Collection
        canvas.drawRect(MARGIN, cardY, MARGIN + cardW, cardY + 60f, cardPaint)
        canvas.drawText("TOTAL COLLECTION", MARGIN + 10f, cardY + 20f, Paint().apply { color = Color.rgb(46, 125, 50); textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText("₹%.2f".format(totalCollection), MARGIN + 10f, cardY + 45f, Paint().apply { color = Color.rgb(46, 125, 50); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

        // Card 2: Total Expense
        val card2X = MARGIN + cardW + 10f
        canvas.drawRect(card2X, cardY, card2X + cardW, cardY + 60f, cardPaint)
        canvas.drawText("TOTAL EXPENSE", card2X + 10f, cardY + 20f, Paint().apply { color = Color.rgb(198, 40, 40); textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText("₹%.2f".format(totalExpense), card2X + 10f, cardY + 45f, Paint().apply { color = Color.rgb(198, 40, 40); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

        // Card 3: Net Balance
        val card3X = card2X + cardW + 10f
        canvas.drawRect(card3X, cardY, card3X + cardW, cardY + 60f, cardPaint)
        val balColor = if (netBalance >= 0) Color.rgb(21, 101, 192) else Color.rgb(198, 40, 40)
        canvas.drawText("NET BALANCE", card3X + 10f, cardY + 20f, Paint().apply { color = balColor; textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
        canvas.drawText("₹%.2f".format(netBalance), card3X + 10f, cardY + 45f, Paint().apply { color = balColor; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

        // Block-wise Summary Table
        var y = 195f
        canvas.drawText("BLOCK-WISE COLLECTION BREAKDOWN", MARGIN, y, headerPaint)
        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        canvas.drawText("Block Name", MARGIN, y, headerPaint)
        canvas.drawText("Flats Contributed", MARGIN + 180f, y, headerPaint)
        canvas.drawText("Total Collection (₹)", MARGIN + 360f, y, headerPaint)
        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        if (blockSummaries.isEmpty()) {
            canvas.drawText("No block contributions recorded for this period.", MARGIN, y, rowPaint)
            y += 20f
        } else {
            for (bs in blockSummaries) {
                canvas.drawText("Block ${bs.block}", MARGIN, y, rowPaint)
                canvas.drawText("${bs.flatCount} flats", MARGIN + 180f, y, rowPaint)
                canvas.drawText("₹%.2f".format(bs.totalAmount), MARGIN + 360f, y, rowPaint)
                y += 18f
            }
        }

        // Summary Note
        y += 25f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f
        val note = "Note: Funds collected for $category are strictly ring-fenced for this purpose."
        canvas.drawText(note, MARGIN, y, Paint().apply { color = Color.GRAY; textSize = 9f })

        document.finishPage(page)

        return saveAndOpenFile(context, document, fileName, titleText)
    }

    /**
     * Saves document to phone's public Download directory and cache, then launches view/share chooser.
     */
    private fun saveAndOpenFile(context: Context, document: PdfDocument, fileName: String, subject: String): File? {
        return try {
            val downloadDir = getDownloadsDirectory(context)
            val targetFile = File(downloadDir, fileName)

            FileOutputStream(targetFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            // Also keep a copy in cache directory for FileProvider sharing
            val cacheReports = File(context.cacheDir, "reports")
            if (!cacheReports.exists()) cacheReports.mkdirs()
            val shareableFile = File(cacheReports, fileName)
            targetFile.copyTo(shareableFile, overwrite = true)

            Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

            // Launch viewer or share intent
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", shareableFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open or Share Report PDF"))

            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            document.close()
            null
        }
    }
}
