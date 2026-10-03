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
        imageUri: Uri
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

            // Automatic header block detection (e.g. "MAINTENANCE: 'G' BLOCK - 20" -> "G")
            val detectedHeaderBlock = TextTableParser.detectHeaderBlock(visionText.text)

            // Strategy 1: Adaptive 2D Spatial Row Clustering (Handles slight camera tilt and multi-column tables)
            val rowsFromClustering = parseByRowClustering(visionText, detectedHeaderBlock)

            // Strategy 2: Column-Aware Proximity Grid Matching (Handles tables where ML Kit separated columns into vertical blocks)
            val rowsFromColumnMatching = parseByColumnProximityMatching(visionText, detectedHeaderBlock)

            // Strategy 3: Direct Raw Text Parsing
            val rowsFromRaw = TextTableParser.parse(visionText.text, detectedHeaderBlock)

            val validClustered = rowsFromClustering.filter { it.amount > 0 && it.flatNo.any { c -> c.isDigit() } }
            val validColumnMatch = rowsFromColumnMatching.filter { it.amount > 0 && it.flatNo.any { c -> c.isDigit() } }
            val validRaw = rowsFromRaw.filter { it.amount > 0 && it.flatNo.any { c -> c.isDigit() } }

            // Pick the strategy that extracted the most complete, valid collection rows
            val candidateLists = listOf(validClustered, validColumnMatch, validRaw)
            val bestList = candidateLists.maxByOrNull { it.size } ?: emptyList()

            val finalRows = if (bestList.isNotEmpty()) {
                bestList
            } else {
                (rowsFromClustering.ifEmpty { rowsFromColumnMatching }.ifEmpty { rowsFromRaw })
                    .filter { it.flatNo.any { c -> c.isDigit() } }
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
                val amtMatch = Regex("""(?:Total|Grand Total|Net Payable|Amount|Bill Amount|Paid)[:\s]*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE).find(clean)
                if (amtMatch != null) {
                    val num = amtMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num > detectedAmount) {
                        detectedAmount = num
                    }
                } else {
                    // Check standalone large numbers
                    val standalone = Regex("""([0-9]+(?:\.[0-9]{2}))""").find(clean)
                    val num = standalone?.groupValues?.get(1)?.toDoubleOrNull()
                    if (num != null && num > detectedAmount) {
                        detectedAmount = num
                    }
                }

                // Check date
                if (detectedDate.isBlank()) {
                    val dateMatch = Regex("""([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4}|[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2})""").find(line)
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
     * Adaptive 2D spatial clustering:
     * Groups lines into horizontal rows using adaptive vertical tolerance based on line heights,
     * maintaining running row vertical bounds to handle perspective distortion and slight tilt.
     */
    private fun parseByRowClustering(visionText: Text, defaultBlock: String? = null): List<ParsedCollectionRow> {
        val lines = visionText.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() && it.boundingBox != null }
        if (lines.isEmpty()) return emptyList()

        val medianH = lines.map { it.boundingBox?.height() ?: 24 }.sorted().let {
            if (it.isNotEmpty()) it[it.size / 2] else 24
        }
        val verticalTolerance = maxOf(medianH * 0.85f, 22f)

        class ClusterRow(first: Text.Line) {
            val items = mutableListOf(first)
            var minY = first.boundingBox?.top ?: 0
            var maxY = first.boundingBox?.bottom ?: 0
            var centerY = first.boundingBox?.centerY()?.toFloat() ?: 0f

            fun matches(line: Text.Line): Boolean {
                val box = line.boundingBox ?: return false
                val lineCenterY = box.centerY().toFloat()
                val diff = abs(centerY - lineCenterY)

                // Vertical overlap check
                val overlap = minOf(maxY, box.bottom) - maxOf(minY, box.top)
                return overlap > 0 || diff <= verticalTolerance
            }

            fun add(line: Text.Line) {
                items.add(line)
                val box = line.boundingBox ?: return
                minY = minOf(minY, box.top)
                maxY = maxOf(maxY, box.bottom)
                centerY = (minY + maxY) / 2f
            }
        }

        val sortedLines = lines.sortedBy { it.boundingBox?.top ?: 0 }
        val clusterRows = mutableListOf<ClusterRow>()

        for (line in sortedLines) {
            val matchedRow = clusterRows.find { it.matches(line) }
            if (matchedRow != null) {
                matchedRow.add(line)
            } else {
                clusterRows.add(ClusterRow(line))
            }
        }

        // For each clustered row, sort items left-to-right (X position)
        val reconstructedText = clusterRows
            .sortedBy { it.centerY }
            .map { row ->
                row.items.sortedBy { it.boundingBox?.left ?: 0 }
                    .joinToString(", ") { it.text.trim() }
            }.joinToString("\n")

        return TextTableParser.parse(reconstructedText, defaultBlock)
    }

    /**
     * Column-Aware Proximity Grid Matching:
     * When ML Kit segments a printed table into vertical column blocks (Column 1: Flats, Column 2: Names, Column 3: Amounts),
     * this strategy identifies candidate elements across columns and aligns them by vertical coordinate proximity.
     */
    private fun parseByColumnProximityMatching(visionText: Text, defaultBlock: String? = null): List<ParsedCollectionRow> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
            .filter { it.text.isNotBlank() && it.boundingBox != null }
        if (allLines.isEmpty()) return emptyList()

        data class ItemWithBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val centerY: Int)

        val items = allLines.map {
            val box = it.boundingBox!!
            ItemWithBox(it.text.trim(), box.left, box.top, box.right, box.bottom, box.centerY())
        }

        // 1. Identify candidate Flat numbers
        data class FlatCandidate(val block: String, val flatNo: String, val item: ItemWithBox)
        val flatCandidates = mutableListOf<FlatCandidate>()

        for (item in items) {
            // Must not be a date
            if (item.text.matches(Regex("""^[0-9]{1,2}[-/][0-9]{1,2}(?:[-/][0-9]{2,4})?$"""))) continue

            val flatPair = TextTableParser.extractFlatFromToken(item.text)
            if (flatPair != null && flatPair.second.any { it.isDigit() }) {
                // Ignore small 1-digit numbers near the very left that are likely Sr No
                val numOnly = flatPair.second.filter { it.isDigit() }
                if (numOnly.length >= 2 || flatPair.first.isNotBlank()) {
                    flatCandidates.add(FlatCandidate(flatPair.first, flatPair.second, item))
                }
            }
        }

        if (flatCandidates.isEmpty()) return emptyList()

        // 2. Identify candidate Amounts
        data class AmountCandidate(val amount: Double, val item: ItemWithBox)
        val amountCandidates = mutableListOf<AmountCandidate>()

        for (item in items) {
            val amt = TextTableParser.extractAmountFromToken(item.text)
            if (amt != null && amt >= 100.0) {
                // Must not be the same item as a flat candidate
                if (flatCandidates.none { it.item == item }) {
                    amountCandidates.add(AmountCandidate(amt, item))
                }
            }
        }

        if (amountCandidates.isEmpty()) return emptyList()

        // 3. Identify candidate Payment Modes
        data class ModeCandidate(val mode: String, val item: ItemWithBox)
        val modeCandidates = mutableListOf<ModeCandidate>()

        for (item in items) {
            val lower = item.text.lowercase()
            val mode = when {
                lower.contains("cash") || lower.contains("cheque") || lower.contains("check") -> "Cash"
                lower.contains("online") || lower.contains("one line") || lower.contains("on line") ||
                        lower.contains("upi") || lower.contains("gpay") || lower.contains("neft") ||
                        lower.contains("rtgs") || lower.contains("bank") || lower.contains("phonepe") ||
                        lower.contains("paytm") -> "Online"
                else -> null
            }
            if (mode != null) {
                modeCandidates.add(ModeCandidate(mode, item))
            }
        }

        // Sort flats by vertical position top to bottom
        val sortedFlats = flatCandidates.sortedBy { it.item.centerY }
        val usedAmounts = mutableSetOf<AmountCandidate>()
        val result = mutableListOf<ParsedCollectionRow>()

        // Estimate typical row spacing
        val medianSpacing = if (sortedFlats.size >= 2) {
            val diffs = sortedFlats.zipWithNext { a, b -> abs(b.item.centerY - a.item.centerY) }
            diffs.sorted()[diffs.size / 2]
        } else {
            50
        }
        val maxVerticalDistance = maxOf(medianSpacing * 1.5, 45.0)

        for (flat in sortedFlats) {
            // Find closest available amount horizontally to the right of flat (or nearest in Y)
            val matchedAmt = amountCandidates
                .filter { it !in usedAmounts }
                .minByOrNull { abs(it.item.centerY - flat.item.centerY) }

            if (matchedAmt != null && abs(matchedAmt.item.centerY - flat.item.centerY) <= maxVerticalDistance) {
                usedAmounts.add(matchedAmt)

                // Find closest payment mode in this row band
                val matchedMode = modeCandidates.minByOrNull { abs(it.item.centerY - flat.item.centerY) }
                val paymentMode = if (matchedMode != null && abs(matchedMode.item.centerY - flat.item.centerY) <= maxVerticalDistance) {
                    matchedMode.mode
                } else {
                    "Online"
                }

                // Find resident name between flat and amount (horizontally) and matching Y
                val minX = minOf(flat.item.right, matchedAmt.item.left)
                val maxX = maxOf(flat.item.right, matchedAmt.item.left)
                val nameItem = items.filter {
                    it != flat.item && it != matchedAmt.item &&
                            it.left >= minX - 20 && it.right <= maxX + 20 &&
                            abs(it.centerY - flat.item.centerY) <= maxVerticalDistance &&
                            !it.text.any { c -> c.isDigit() } &&
                            it.text.length > 2
                }.maxByOrNull { it.text.length }

                val nameStr = nameItem?.text?.trim()?.ifBlank { "Resident" } ?: "Resident"

                // Determine effective block
                val effectiveBlock = when {
                    flat.block.isNotBlank() && flat.block != "General" -> flat.block
                    !defaultBlock.isNullOrBlank() -> defaultBlock.uppercase()
                    flat.flatNo.contains("-") -> flat.flatNo.substringBefore("-").trim().uppercase()
                    else -> "General"
                }

                val cleanFlat = when {
                    effectiveBlock != "General" && flat.flatNo.startsWith("$effectiveBlock-", ignoreCase = true) -> flat.flatNo
                    effectiveBlock != "General" && flat.flatNo.startsWith(effectiveBlock, ignoreCase = true) && flat.flatNo.length > effectiveBlock.length -> {
                        "$effectiveBlock-${flat.flatNo.removePrefix(effectiveBlock).removePrefix("-")}"
                    }
                    effectiveBlock != "General" && !flat.flatNo.contains("-") && flat.flatNo.all { it.isDigit() } -> {
                        "$effectiveBlock-${flat.flatNo}"
                    }
                    else -> flat.flatNo
                }

                result.add(
                    ParsedCollectionRow(
                        block = effectiveBlock,
                        flatNo = cleanFlat,
                        ownerName = nameStr,
                        amount = matchedAmt.amount,
                        paymentMode = paymentMode
                    )
                )
            }
        }

        return result
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
            }
            val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

            // Scale to max dimension 2048 for optimal OCR speed & accuracy
            val maxDim = 2048
            if (rotated.width > maxDim || rotated.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / rotated.width, maxDim.toFloat() / rotated.height)
                val newW = (rotated.width * ratio).toInt()
                val newH = (rotated.height * ratio).toInt()
                Bitmap.createScaledBitmap(rotated, newW, newH, true)
            } else {
                rotated
            }
        } catch (_: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }
}
