package com.shreenathji.sales.ui.util.challan


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.entities.OrderWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun loadBitmapSafe(context: Context, source: String): Bitmap? {
    val trimmed = source.trim()
    if (trimmed.isEmpty()) return null

    // 1. Try Coil first (handles HTTP URLs, content://, file://, caching, rotation, and downsampling)
    try {
        val request = ImageRequest.Builder(context)
            .data(trimmed)
            .allowHardware(false) // Required for PdfDocument Canvas
            .size(1600) // Prevent OOM
            .build()

        val result = runBlocking(Dispatchers.IO) {
            context.imageLoader.execute(request)
        }
        if (result is SuccessResult) {
            val bmp = drawableToBitmap(result.drawable)
            if (bmp != null) return bmp
        }
    } catch (e: Exception) {
        // Coil failed, proceed to local fallbacks
    }

    // 2. Direct ContentResolver fallback for content:// URIs
    try {
        val uri = android.net.Uri.parse(trimmed)
        if (uri.scheme == "content") {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return decodeSampledBitmap(stream)
            }
        }
    } catch (e: Exception) {
        // fallback
    }

    // 3. Direct File fallback for file:// or absolute path
    try {
        val file = if (trimmed.startsWith("file://")) {
            File(android.net.Uri.parse(trimmed).path ?: "")
        } else {
            File(trimmed)
        }
        if (file.exists()) {
            file.inputStream().use { stream ->
                return decodeSampledBitmap(stream)
            }
        }
    } catch (e: Exception) {
        // fallback
    }

    return null
}

internal fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val bmp = drawable.bitmap
        if (!bmp.isRecycled) {
            return if (bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bmp
            }
        }
    }
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 600
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 800
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

internal fun decodeSampledBitmap(inputStream: InputStream): Bitmap? {
    val bytes = inputStream.readBytes()
    if (bytes.isEmpty()) return null

    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

    var sampleSize = 1
    val maxDim = 1600
    while ((boundsOpts.outWidth / sampleSize) > maxDim || (boundsOpts.outHeight / sampleSize) > maxDim) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
}

