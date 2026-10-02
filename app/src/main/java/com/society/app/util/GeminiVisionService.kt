package com.society.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ParsedCollectionRow(
    val block: String,
    val flatNo: String,
    val ownerName: String,
    val amount: Double,
    val paymentMode: String = "Online"
)

data class ParsedExpenseRow(
    val detail: String,
    val amount: Double,
    val date: String
)

object GeminiVisionService {

    private val CANDIDATE_MODELS = listOf(
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-flash-latest",
        "gemini-1.5-flash",
        "gemini-2.0-flash-lite"
    )
    private var cachedWorkingModel: String? = null
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun extractCollectionsFromImage(
        context: Context,
        imageUri: Uri,
        apiKey: String
    ): Result<List<ParsedCollectionRow>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is missing. Please set your API key in AI Settings."))
            }

            val base64Image = uriToBase64Jpeg(context, imageUri)
                ?: return@withContext Result.failure(Exception("Failed to decode image from device."))

            val prompt = """
                You are an expert OCR and financial data extraction assistant for a housing society in India.
                Examine this image, which contains a table, ledger, screenshot, or list of maintenance or festival collections from residents/flats.
                Extract every collection row into a clean JSON array.
                
                The table structure may vary. The image may have columns in any order such as:
                (Block, Flat, Name, Amount, Mode) or (Flat, Resident, Amount, Paid Via) or (Unit, Owner, Amount), etc.
                
                For each row, provide a JSON object with strictly these keys:
                - "block": string (e.g. "B", "G", "A". If block is part of the flat number like "B-504", extract "B". If no block is indicated, use "")
                - "flatNo": string (e.g. "B-504", "G-302", "101", "A-501")
                - "ownerName": string (e.g. "Rupesh Jha", "Kamlesh Agrawal", "Sanotsh Mishra". If absent, use "Resident")
                - "amount": number (e.g. 2100.0, 1500.0, 1200.0. Clean up any ₹, Rs, commas)
                - "paymentMode": string (strictly either "Online" or "Cash". Convert "online", "upi", "gpay", "neft" to "Online"; convert "cash", "cheque" to "Cash". Default to "Online")
                
                Ignore header rows like "Block", "Flat", "Name", "Amount", "Mode".
                Return ONLY the valid JSON array.
            """.trimIndent()

            val rawJson = callGeminiVisionApi(apiKey, prompt, base64Image)
            val rows = parseCollectionsJson(rawJson)
            if (rows.isEmpty()) {
                Result.failure(Exception("No collection records could be identified in the image."))
            } else {
                Result.success(rows)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractExpenseFromImage(
        context: Context,
        imageUri: Uri,
        apiKey: String
    ): Result<ParsedExpenseRow> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key is missing. Please set your API key in AI Settings."))
            }

            val base64Image = uriToBase64Jpeg(context, imageUri)
                ?: return@withContext Result.failure(Exception("Failed to decode image from device."))

            val prompt = """
                You are an expert expense and invoice extractor for a housing society management app in India.
                Examine this bill, invoice, receipt, cash voucher, or payment slip.
                Extract the core expense details into a single JSON object with these keys:
                - "detail": string (the vendor, company name, or description of goods/services, e.g. "Torrent Power Electricity Bill", "Plumbing Supplies & PVC Pipes", "Security Agency Fees", "Diwali Lightings & Decoration")
                - "amount": number (the total or grand total amount, e.g. 1500.0. Clean up ₹, Rs, commas)
                - "date": string (bill date in YYYY-MM-DD format if present, otherwise empty string "")
                
                Return ONLY the valid JSON object.
            """.trimIndent()

            val rawJson = callGeminiVisionApi(apiKey, prompt, base64Image)
            val expense = parseExpenseJson(rawJson)
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGeminiVisionApi(apiKey: String, prompt: String, base64Jpeg: String): String {
        cachedWorkingModel?.let { model ->
            try {
                return executeGenerateContent(apiKey, model, prompt, base64Jpeg)
            } catch (e: Exception) {
                if (!e.message.orEmpty().contains("not found", ignoreCase = true)) {
                    throw e
                }
                cachedWorkingModel = null
            }
        }

        var lastError: Exception? = null
        for (model in CANDIDATE_MODELS) {
            try {
                val res = executeGenerateContent(apiKey, model, prompt, base64Jpeg)
                cachedWorkingModel = model
                return res
            } catch (e: Exception) {
                lastError = e
                val msg = e.message.orEmpty()
                if (msg.contains("not found", ignoreCase = true) || msg.contains("404")) {
                    continue
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: Exception("Unable to connect to Gemini vision API.")
    }

    private fun executeGenerateContent(apiKey: String, modelName: String, prompt: String, base64Jpeg: String): String {
        val endpoint = "$BASE_URL/$modelName:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject().apply {
                val partsArray = JSONArray()
                // Text part
                partsArray.put(JSONObject().apply {
                    put("text", prompt)
                })
                // Inline Image part
                partsArray.put(JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Jpeg)
                    }
                    put("inline_data", inlineData)
                })
                put("parts", partsArray)
            }
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            // Structured JSON output config
            val genConfig = JSONObject().apply {
                put("response_mime_type", "application/json")
            }
            put("generationConfig", genConfig)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            return extractTextFromGeminiResponse(responseText)
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw Exception(parseGeminiErrorMessage(errorText))
        }
    }

    private fun extractTextFromGeminiResponse(responseJsonStr: String): String {
        val root = JSONObject(responseJsonStr)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() > 0) {
                return parts.getJSONObject(0).optString("text", "")
            }
        }
        return ""
    }

    private fun parseGeminiErrorMessage(errorJsonStr: String): String {
        return try {
            val root = JSONObject(errorJsonStr)
            val errorObj = root.optJSONObject("error")
            val message = errorObj?.optString("message") ?: errorJsonStr
            "AI Error: $message"
        } catch (e: Exception) {
            "AI Error: $errorJsonStr"
        }
    }

    private fun parseCollectionsJson(jsonStr: String): List<ParsedCollectionRow> {
        val cleanStr = jsonStr.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val list = mutableListOf<ParsedCollectionRow>()
        try {
            val array = JSONArray(cleanStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val flatNo = obj.optString("flatNo", "").trim()
                var block = obj.optString("block", "").trim()
                val ownerName = obj.optString("ownerName", "Resident").trim()
                val amount = obj.optDouble("amount", 0.0)
                val rawMode = obj.optString("paymentMode", "Online").trim()
                val mode = if (rawMode.equals("Cash", ignoreCase = true)) "Cash" else "Online"

                // Auto-infer block from flatNo if block is missing (e.g. "B-504" -> "B")
                if (block.isBlank() && flatNo.contains("-")) {
                    block = flatNo.substringBefore("-").trim()
                }

                if (flatNo.isNotBlank() || amount > 0) {
                    list.add(
                        ParsedCollectionRow(
                            block = if (block.isNotBlank()) block else "A",
                            flatNo = flatNo,
                            ownerName = if (ownerName.isNotBlank()) ownerName else "Resident",
                            amount = amount,
                            paymentMode = mode
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Check if it returned an object wrapping an array e.g. {"collections": [...]}
            try {
                val root = JSONObject(cleanStr)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val subArray = root.optJSONArray(key)
                    if (subArray != null) {
                        return parseCollectionsJson(subArray.toString())
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }

    private fun parseExpenseJson(jsonStr: String): ParsedExpenseRow {
        val cleanStr = jsonStr.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            val obj = JSONObject(cleanStr)
            val detail = obj.optString("detail", "Expense").trim()
            val amount = obj.optDouble("amount", 0.0)
            val date = obj.optString("date", "").trim()
            return ParsedExpenseRow(
                detail = if (detail.isNotBlank()) detail else "Expense",
                amount = amount,
                date = date
            )
        } catch (e: Exception) {
            // In case it returned an array with one element
            try {
                val array = JSONArray(cleanStr)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    return ParsedExpenseRow(
                        detail = obj.optString("detail", "Expense"),
                        amount = obj.optDouble("amount", 0.0),
                        date = obj.optString("date", "")
                    )
                }
            } catch (_: Exception) {}
            return ParsedExpenseRow(detail = "Scanned Expense", amount = 0.0, date = "")
        }
    }

    private fun uriToBase64Jpeg(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            // Correct orientation from EXIF
            val rotatedBitmap = correctOrientation(context, uri, originalBitmap)

            // Resize down to max 1600px width/height for fast transmission & high accuracy
            val maxDimension = 1600
            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val newWidth = (width * ratio).toInt()
                val newHeight = (height * ratio).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true)
            } else {
                rotatedBitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun correctOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }
}
