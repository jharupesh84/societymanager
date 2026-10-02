package com.society.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object SampleImageHelper {

    /**
     * Extracts the sample collection table image into cache and returns a content Uri.
     * Allows 1-tap immediate testing without relying on the device photo picker.
     */
    fun getSampleTableImageUri(context: Context): Uri? {
        return try {
            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val cacheFile = File(imagesDir, "sample_collection_table.png")
            context.assets.open("sample_collection_table.png").use { input ->
                FileOutputStream(cacheFile).use { out ->
                    input.copyTo(out)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves the bundled sample collection table image into the device's public Gallery / Pictures
     * and triggers the media scanner so it immediately appears in Google Photos / Gallery.
     */
    fun saveSampleImageToDeviceGallery(context: Context): Uri? {
        return try {
            val fileName = "society_table_test_sample_${System.currentTimeMillis() % 10000}.png"

            // 1. Save using MediaStore for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SocietyApp")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    context.assets.open("sample_collection_table.png").use { input ->
                        input.copyTo(out)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                Toast.makeText(context, "Test table image saved to your Gallery!", Toast.LENGTH_SHORT).show()
                uri
            } else {
                // Fallback for Android 9 and lower
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "SocietyApp").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                context.assets.open("sample_collection_table.png").use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )

                Toast.makeText(context, "Test table image saved to Gallery!", Toast.LENGTH_SHORT).show()
                FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
            }
        } catch (e: Exception) {
            // Also try inserting via MediaStore bitmap insert
            try {
                val bitmap = context.assets.open("sample_collection_table.png").use {
                    BitmapFactory.decodeStream(it)
                }
                if (bitmap != null) {
                    val path = MediaStore.Images.Media.insertImage(
                        context.contentResolver,
                        bitmap,
                        "society_table_sample",
                        "Sample Table Image"
                    )
                    if (path != null) {
                        Toast.makeText(context, "Test table image added to Gallery!", Toast.LENGTH_SHORT).show()
                        return Uri.parse(path)
                    }
                }
            } catch (_: Exception) {}

            Toast.makeText(context, "Saved to cache. Use 1-Tap test button!", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
