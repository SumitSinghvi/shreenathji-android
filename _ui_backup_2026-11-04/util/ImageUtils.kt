package com.shreenathji.sales.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    fun createTempImageUri(context: Context): Uri {
        val tempDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val tempFile = File(tempDir, "IMG_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    /**
     * WhatsApp-style smart image compression:
     * 1. Auto-orients image based on camera EXIF data.
     * 2. Scales down to max 1280px (preserves full fabric swatch clarity & aspect ratio).
     * 3. Compresses to JPEG 78% quality.
     * Result: ~100KB - 250KB crisp image (down from 5MB - 12MB).
     */
    fun compressImage(context: Context, sourceUri: Uri): Uri {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds to compute inSampleSize
            var inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            val maxDimension = 1280

            var sampleSize = 1
            if (srcWidth > maxDimension || srcHeight > maxDimension) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // 2. Decode bitmap with sample size & RGB_565 (50% memory saving)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            inputStream = contentResolver.openInputStream(sourceUri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap == null) return sourceUri

            // 3. Handle EXIF rotation (so portrait photos don't rotate sideways)
            val orientation = getExifOrientation(context, sourceUri)
            if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }

            // 4. Exact scale down to max 1280px
            val currentMax = max(bitmap.width, bitmap.height)
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax
                val targetW = (bitmap.width * scale).roundToInt()
                val targetH = (bitmap.height * scale).roundToInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // 5. Compress to file (JPEG 78% quality)
            val compressedDir = File(context.cacheDir, "compressed_swatches").apply { mkdirs() }
            val compressedFile = File(compressedDir, "SWATCH_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(compressedFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, outputStream)
            outputStream.flush()
            outputStream.close()
            bitmap.recycle()

            Uri.fromFile(compressedFile)
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
