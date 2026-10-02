package com.society.app.util

import android.content.ContentValues
import android.content.Context
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
     * Saves the bundled sample collection table image into the device's public Gallery / Pictures
     * so it immediately appears in Google Photos / Gallery and the photo picker.
     */
    fun saveSampleImageToDeviceGallery(context: Context): Uri? {
        return try {
            val fileName = "society_table_test_sample.png"

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
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "SocietyApp").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                context.assets.open("sample_collection_table.png").use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "Test table image saved to Pictures!", Toast.LENGTH_SHORT).show()
                FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save to gallery: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
