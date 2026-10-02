package com.society.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Generates a block-specific CSV report and launches the Android share sheet.
     */
    fun exportBlockReport(
        context: Context,
        blockName: String,
        collections: List<CollectionEntity>,
        category: String,
        monthFilter: String? = null
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedCat = category.replace(" ", "_")
        val sanitizedMonth = monthFilter?.replace(" ", "_") ?: "AllMonths"
        val fileName = "${sanitizedCat}_Report_Block_${blockName.replace(" ", "_")}_${sanitizedMonth}_$timestamp.csv"
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val file = File(reportsDir, fileName)
        FileWriter(file).use { writer ->
            writer.append("=== SOCIETY COLLECTION REPORT: $category (BLOCK $blockName) ===\n")
            if (monthFilter != null) writer.append("Period/Month,$monthFilter\n")
            writer.append("Generated On,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

            writer.append("Block,Flat No,Owner Name,Month/Period,Amount (INR),Payment Mode,Date\n")
            var total = 0.0
            for (item in collections) {
                total += item.amount
                writer.append("${escapeCsv(item.block)},${escapeCsv(item.flatNo)},${escapeCsv(item.ownerName)},${escapeCsv(item.monthYear)},${item.amount},${escapeCsv(item.paymentMode)},${escapeCsv(item.date)}\n")
            }
            writer.append("\nTOTAL COLLECTION FOR BLOCK $blockName,,,$total,\n")
        }

        shareFile(context, file, "$category Block $blockName Collection Report")
    }

    /**
     * Generates a comprehensive consolidated master CSV report (Collections, Block Breakdown, Expenses, Net Balance).
     */
    fun exportConsolidatedReport(
        context: Context,
        blockSummaries: List<BlockSummary>,
        collections: List<CollectionEntity>,
        expenses: List<ExpenseEntity>,
        totalCollection: Double,
        totalExpense: Double,
        netBalance: Double,
        category: String,
        monthFilter: String? = null
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedCat = category.replace(" ", "_")
        val sanitizedMonth = monthFilter?.replace(" ", "_") ?: "AllMonths"
        val fileName = "${sanitizedCat}_Master_Report_${sanitizedMonth}_$timestamp.csv"
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val file = File(reportsDir, fileName)
        FileWriter(file).use { writer ->
            writer.append("=== SOCIETY FINANCIAL REPORT: $category ===\n")
            if (monthFilter != null) writer.append("Period/Month,$monthFilter\n")
            writer.append("Generated On,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

            // 1. Executive Summary
            writer.append("--- FINANCIAL OVERVIEW ---\n")
            writer.append("Metric,Amount (INR)\n")
            writer.append("Total Collection,$totalCollection\n")
            writer.append("Total Expense,$totalExpense\n")
            writer.append("Net Balance,$netBalance\n\n")

            // 2. Block-wise Breakdown
            writer.append("--- BLOCK-WISE COLLECTION SUMMARY ---\n")
            writer.append("Block Name,Number of Flats Contributed,Total Collection (INR)\n")
            for (bs in blockSummaries) {
                writer.append("${escapeCsv(bs.block)},${bs.flatCount},${bs.totalAmount}\n")
            }
            writer.append("\n")

            // 3. All Collections Detail
            writer.append("--- DETAILED COLLECTION ENTRIES ---\n")
            writer.append("Block,Flat No,Owner Name,Month/Period,Amount (INR),Payment Mode,Date\n")
            for (c in collections) {
                writer.append("${escapeCsv(c.block)},${escapeCsv(c.flatNo)},${escapeCsv(c.ownerName)},${escapeCsv(c.monthYear)},${c.amount},${escapeCsv(c.paymentMode)},${escapeCsv(c.date)}\n")
            }
            writer.append("\n")

            // 4. All Expenses Detail
            writer.append("--- DETAILED EXPENSES ---\n")
            writer.append("Expense Detail,Month/Period,Amount (INR),Date\n")
            for (e in expenses) {
                writer.append("${escapeCsv(e.detail)},${escapeCsv(e.monthYear)},${e.amount},${escapeCsv(e.date)}\n")
            }
        }

        shareFile(context, file, "$category Consolidated Master Report")
    }

    private fun shareFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Report via..."))
    }
}
