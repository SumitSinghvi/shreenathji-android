package com.shreenathji.sales.ui.util.challan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/** Square photo swatch, cropped to fill (same as the web's object-cover). */
internal fun drawSwatchCard(
    canvas: Canvas,
    paint: Paint,
    context: Context,
    imageUrl: String,
    label: String,
    cardRect: RectF,
    lightGrayBorder: Int,
    mutedGrayColor: Int
) {
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(cardRect, 4f, 4f, paint)

    val bmp = loadBitmapSafe(context, imageUrl)
    if (bmp != null) {
        // Center-crop the bitmap to the card's aspect ratio.
        val cardAspect = cardRect.width() / cardRect.height()
        val bmpAspect = bmp.width.toFloat() / bmp.height
        val src = if (bmpAspect > cardAspect) {
            val w = (bmp.height * cardAspect).toInt()
            val x = (bmp.width - w) / 2
            Rect(x, 0, x + w, bmp.height)
        } else {
            val h = (bmp.width / cardAspect).toInt()
            val y = (bmp.height - h) / 2
            Rect(0, y, bmp.width, y + h)
        }
        canvas.drawBitmap(bmp, src, cardRect, paint)
    } else {
        paint.textSize = 8f
        paint.color = mutedGrayColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ $label ]", cardRect.centerX(), cardRect.centerY() + 3f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    paint.style = Paint.Style.STROKE
    paint.color = lightGrayBorder
    paint.strokeWidth = 1f
    canvas.drawRoundRect(cardRect, 4f, 4f, paint)
}
