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

internal fun ChallanLayout.drawRows(canvas: Canvas, slice: List<ChallanPrintRow>, tableTop: Float): Float {
    var tableY = tableTop + headerH
    slice.forEach { row ->
        val hasImg = !row.imageUrl.isNullOrBlank()
        val isMatch = row.isMatching || row.multiplier > 1
        val mult = if (row.multiplier > 1) row.multiplier else 2
        val hasSubtitle = isMatch || row.colorCount > 1 || row.cutMeter != null
        val currentRowH = getRowH(row)
        val totalMeterStr = "${formatMeters(row.meter)} mtrs"

        // Row divider
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawLine(marginX, tableY, tableRight, tableY, paint)

        val textCenterY = tableY + (currentRowH / 2) + 3.5f

        // S.No.
        paint.style = Paint.Style.FILL
        paint.color = mutedGrayColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${row.sNo}", col1X + (colSnoW / 2), textCenterY, paint)

        // Product Name + subtitle metadata
        if (hasSubtitle) {
            val prodBaseY = tableY + 10f
            paint.style = Paint.Style.FILL
            paint.color = darkSlateColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            val prodName = if (row.productName.length > 28) row.productName.substring(0, 25) + "..." else row.productName
            canvas.drawText(prodName, col2X + 8f, prodBaseY, paint)

            var curSubX = col2X + 8f
            val badgeTop = prodBaseY + 2.5f
            val badgeH = 10f

            if (isMatch) {
                val badgeText = "$mult-Matching"
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val badgeTextW = paint.measureText(badgeText)
                val badgePadX = 4f
                val badgeRect = RectF(curSubX, badgeTop, curSubX + badgeTextW + (badgePadX * 2), badgeTop + badgeH)

                val (bgCol, borderCol, txtCol) = if (mult == 4) {
                    Triple(
                        android.graphics.Color.parseColor("#F3E8FF"),
                        android.graphics.Color.parseColor("#D8B4FE"),
                        android.graphics.Color.parseColor("#6B21A8")
                    )
                } else {
                    Triple(
                        android.graphics.Color.parseColor("#FEF3C7"),
                        android.graphics.Color.parseColor("#FCD34D"),
                        android.graphics.Color.parseColor("#92400E")
                    )
                }

                // Pill background
                paint.style = Paint.Style.FILL
                paint.color = bgCol
                canvas.drawRoundRect(badgeRect, 3f, 3f, paint)

                // Pill border
                paint.style = Paint.Style.STROKE
                paint.color = borderCol
                paint.strokeWidth = 0.5f
                canvas.drawRoundRect(badgeRect, 3f, 3f, paint)

                // Pill text
                paint.style = Paint.Style.FILL
                paint.color = txtCol
                canvas.drawText(badgeText, curSubX + badgePadX, badgeTop + 7.5f, paint)

                curSubX += badgeTextW + (badgePadX * 2) + 4f
            }

            // Subtitle metadata parts: colors & cut
            val metaParts = mutableListOf<String>()
            if (row.colorCount > 1) {
                metaParts.add("${row.colorCount} Colors")
            }
            val cut = row.cutMeter ?: (if (isMatch) row.meter / (mult * row.colorCount) else null)
            if (cut != null) {
                val cutVal: Double = cut
                val cutStr = if (cutVal % 1.0 == 0.0) cutVal.toLong().toString() else cutVal.toString()
                if (isMatch) {
                    val setMtr = cutVal * mult
                    val setMtrStr = if (setMtr % 1.0 == 0.0) setMtr.toLong().toString() else setMtr.toString()
                    metaParts.add("Cut: ${cutStr}m × $mult (${setMtrStr}m/color)")
                } else {
                    metaParts.add("Cut: ${cutStr}m")
                }
            }

            if (metaParts.isNotEmpty()) {
                val metaText = if (isMatch) "• " + metaParts.joinToString(" • ") else metaParts.joinToString(" • ")
                paint.style = Paint.Style.FILL
                paint.color = mutedGrayColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val maxMetaLen = 42
                val displayMeta = if (metaText.length > maxMetaLen) metaText.substring(0, maxMetaLen - 3) + "..." else metaText
                canvas.drawText(displayMeta, curSubX, badgeTop + 7.5f, paint)
            }
        } else {
            paint.style = Paint.Style.FILL
            paint.color = darkSlateColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            val prodName = if (row.productName.length > 28) row.productName.substring(0, 25) + "..." else row.productName
            canvas.drawText(prodName, col2X + 8f, textCenterY, paint)
        }

        // Photo or Notes Column
        if (hasImg) {
            val bmp = loadBitmapSafe(context, row.imageUrl!!)
            if (bmp != null) {
                val imgLeft = col3X + ((colPhotoW - photoDim) / 2)
                val imgTop = tableY + ((currentRowH - photoDim) / 2)
                val destRect = RectF(imgLeft, imgTop, imgLeft + photoDim, imgTop + photoDim)

                canvas.drawBitmap(bmp, null, destRect, paint)

                paint.style = Paint.Style.STROKE
                paint.color = lightGrayBorder
                paint.strokeWidth = 1f
                canvas.drawRoundRect(destRect, 4f, 4f, paint)
                paint.style = Paint.Style.FILL
            }
        } else {
            paint.color = mutedGrayColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val rawText = if (!row.notes.isNullOrBlank()) row.notes.trim() else "—"
            var noteText = rawText
            val maxW = colPhotoW - 8f
            if (noteText.isNotEmpty() && paint.measureText(noteText) > maxW) {
                paint.textSize = 7.5f
            }
            while (noteText.length > 4 && paint.measureText("$noteText...") > maxW) {
                noteText = noteText.dropLast(1)
            }
            val displayText = if (noteText != rawText) "$noteText..." else noteText
            canvas.drawText(displayText, col3X + (colPhotoW / 2), textCenterY, paint)
        }

        // Rate
        val rateStr = if (row.rate > 0.0) {
            val rFmt = if (row.rate % 1.0 == 0.0) {
                NumberFormat.getNumberInstance(Locale("en", "IN")).format(row.rate.toLong())
            } else {
                String.format(Locale("en", "IN"), "%.2f", row.rate)
            }
            "₹$rFmt"
        } else "—"
        paint.style = Paint.Style.FILL
        paint.color = darkSlateColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(rateStr, col5X - 8f, textCenterY, paint)

        // Quantity (pure total meters)
        canvas.drawText(totalMeterStr, tableRight - 8f, textCenterY, paint)
        paint.textAlign = Paint.Align.LEFT

        tableY += currentRowH
    }
    return tableY
}
