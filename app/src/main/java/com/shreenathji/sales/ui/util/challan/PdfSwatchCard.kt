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

internal fun drawSwatchCard(
    canvas: Canvas,
    paint: Paint,
    context: Context,
    imageUrl: String,
    label: String,
    cardRect: RectF,
    softBgColor: Int,
    lightGrayBorder: Int,
    darkSlateColor: Int,
    mutedGrayColor: Int
) {
    // Card Background
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(cardRect, 4f, 4f, paint)

    // Card Border
    paint.style = Paint.Style.STROKE
    paint.color = lightGrayBorder
    paint.strokeWidth = 1f
    canvas.drawRoundRect(cardRect, 4f, 4f, paint)

    // Image area inside card
    val pad = 4f
    val imgLeft = cardRect.left + pad
    val imgTop = cardRect.top + pad
    val imgW = cardRect.width() - (pad * 2)
    val imgH = cardRect.height() - (pad * 2) - 18f
    val labelTop = cardRect.bottom - 18f

    val bmp = loadBitmapSafe(context, imageUrl)
    if (bmp != null) {
        val scale = minOf(imgW / bmp.width.toFloat(), imgH / bmp.height.toFloat())
        val dstW = bmp.width * scale
        val dstH = bmp.height * scale
        val dstX = imgLeft + (imgW - dstW) / 2f
        val dstY = imgTop + (imgH - dstH) / 2f
        val dstRect = RectF(dstX, dstY, dstX + dstW, dstY + dstH)

        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bmp, null, dstRect, paint)

        paint.style = Paint.Style.STROKE
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.strokeWidth = 0.5f
        canvas.drawRoundRect(dstRect, 2f, 2f, paint)
    } else {
        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.color = mutedGrayColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ $label ]", cardRect.centerX(), imgTop + (imgH / 2f) + 3f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    // Sub-divider above label
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawLine(cardRect.left, labelTop, cardRect.right, labelTop, paint)

    // Label Background
    val labelRect = RectF(cardRect.left, labelTop, cardRect.right, cardRect.bottom)
    paint.style = Paint.Style.FILL
    paint.color = softBgColor
    canvas.drawRect(labelRect, paint)

    // Label Text
    paint.style = Paint.Style.FILL
    paint.color = darkSlateColor
    paint.textSize = 8f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, cardRect.centerX(), labelTop + 12f, paint)
    paint.textAlign = Paint.Align.LEFT

    // Re-stroke card border to keep outer frame & rounded corners clean
    paint.style = Paint.Style.STROKE
    paint.color = lightGrayBorder
    paint.strokeWidth = 1f
    canvas.drawRoundRect(cardRect, 4f, 4f, paint)
}
