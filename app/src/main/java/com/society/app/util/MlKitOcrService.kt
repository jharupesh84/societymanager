package com.society.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * 100% On-Device, Offline OCR Service using Google ML Kit Text Recognition.
 * Requires NO API key, NO network connection, and works completely locally.
 */
object MlKitOcrService {

    suspend fun extractCollectionsFromImage(
        context: Context,
        imageUri: Uri,
        forcedBlock: String? = null
    ): Result<List<ParsedCollectionRow>> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadOrientedBitmap(context, imageUri)
                ?: return@withContext Result.failure(Exception("Failed to decode image from device."))

            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val visionText = suspendCancellableCoroutine<Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWith(kotlin.Result.failure(error))
                    }
            }

            // Auto-detect block from header (e.g. "MAINTENANCE: 'G' BLOCK-") or use user-specified forced block
            val detectedBlock = forcedBlock?.ifBlank { null } ?: TextTableParser.detectHeaderBlock(visionText.text)

            // Strategy 1: Parse visionText.text directly
            val rowsFromRaw = TextTableParser.parse(visionText.text, detectedBlock)

            // Strategy 2: Reconstruct rows by clustering line bounding boxes by vertical Y-center
            val rowsFromClustering = parseByRowClustering(visionText, detectedBlock)

            val finalRows = if (rowsFromClustering.size >= rowsFromRaw.size && rowsFromClustering.isNotEmpty()) {
                rowsFromClustering
            } else {
                rowsFromRaw
            }

            if (finalRows.isEmpty()) {
                Result.failure(
                    Exception("On-device OCR could not detect table rows in this image. Try using Gemini Cloud AI or Paste Text Table.")
                )
            } else {
                Result.success(finalRows)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractExpenseFromImage(
        context: Context,
        imageUri: Uri
    ): Result<ParsedExpenseRow> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadOrientedBitmap(context, imageUri)
                ?: return@withContext Result.failure(Exception("Failed to decode invoice image."))

            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val visionText = suspendCancellableCoroutine<Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWith(kotlin.Result.failure(error))
                    }
            }

            val allLines = visionText.textBlocks.flatMap { it.lines.map { line -> line.text.trim() } }
                .filter { it.isNotBlank() }

            var detectedAmount = 0.0
            var detectedDate = ""
            var detectedDetail = ""

            // Regex for currency amounts
            for (line in allLines) {
                val clean = line.replace("₹", "").replace("Rs", "", ignoreCase = true)
                val amtMatch = Regex("(?:Total|Grand Total|Net Payable|Amount|Bill Amount|Paid)[:\\s]*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE).find(clean)
                if (amtMatch != null) {
                    val num = amtMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num > detectedAmount) {
                        detectedAmount = num
                    }
                } else {
                    // Check standalone large numbers
                    val standalone = Regex("([0-9]+(?:\\.[0-9]{2}))").find(clean)
                    val num = standalone?.groupValues?.get(1)?.toDoubleOrNull()
                    if (num != null && num > detectedAmount) {
                        detectedAmount = num
                    }
                }

                // Check date
                if (detectedDate.isBlank()) {
                    val dateMatch = Regex("([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4}|[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2})").find(line)
                    if (dateMatch != null) {
                        detectedDate = dateMatch.groupValues[1]
                    }
                }
            }

            // Vendor / Title is usually near top
            for (line in allLines.take(4)) {
                if (line.length > 3 && !line.any { it.isDigit() } && !line.contains("invoice", ignoreCase = true) && !line.contains("tax", ignoreCase = true)) {
                    detectedDetail = line
                    break
                }
            }
            if (detectedDetail.isBlank() && allLines.isNotEmpty()) {
                detectedDetail = allLines.first()
            }

            Result.success(
                ParsedExpenseRow(
                    detail = detectedDetail.ifBlank { "Scanned Expense" },
                    amount = detectedAmount,
                    date = detectedDate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconstructs table rows by grouping OCR text elements that share similar vertical Y coordinates.
     */
    private fun parseByRowClustering(visionText: Text, defaultBlock: String? = null): List<ParsedCollectionRow> {
        val lines = visionText.textBlocks.flatMap { it.lines }
        if (lines.isEmpty()) return emptyList()

        // Estimate average line height
        val avgHeight = lines.mapNotNull { it.boundingBox?.height() }.average().takeIf { !it.isNaN() && it > 0 } ?: 30.0
        val tolerance = avgHeight * 0.65

        val rows = mutableListOf<MutableList<Text.Line>>()

        // Sort lines primarily by top Y position
        val sortedLines = lines.sortedBy { it.boundingBox?.top ?: 0 }

        for (line in sortedLines) {
            val lineY = line.boundingBox?.centerY() ?: continue
            val existingRow = rows.find { row ->
                val rowY = row.firstOrNull()?.boundingBox?.centerY() ?: return@find false
                abs(rowY - lineY) <= tolerance
            }
            if (existingRow != null) {
                existingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        // For each clustered row, sort items left-to-right (X position)
        val reconstructedText = rows.map { rowItems ->
            rowItems.sortedBy { it.boundingBox?.left ?: 0 }
                .joinToString(", ") { it.text.trim() }
        }.joinToString("\n")

        return TextTableParser.parse(reconstructedText, defaultBlock)
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream) ?: return null

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return original
            }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } catch (_: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }
}
