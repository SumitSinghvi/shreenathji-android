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

internal fun ChallanLayout.drawLastPageFooter(canvas: Canvas, tableTop: Float, tableYStart: Float, itemsTableBottom: Float, pageNumber: Int) {
    var tableY = tableYStart
    paint.color = lightGrayBorder
    paint.style = Paint.Style.STROKE
    canvas.drawLine(marginX, tableY, tableRight, tableY, paint)

    paint.style = Paint.Style.FILL
    paint.color = mutedGrayColor
    paint.textSize = 8f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("TOTAL ITEMS", col5X - 8f, tableY + 12f, paint)

    paint.color = darkSlateColor
    paint.textSize = 9f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("${rows.size} ${if (rows.size == 1) "Item" else "Items"}", tableRight - 8f, tableY + 12f, paint)
    tableY += 16f

    // Summary Row 2: Total Fabric Quantity
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    canvas.drawLine(marginX, tableY, tableRight, tableY, paint)

    paint.style = Paint.Style.FILL
    paint.color = orangeColor
    paint.textSize = 8.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("TOTAL FABRIC QUANTITY", col5X - 8f, tableY + 13f, paint)

    val totalMeters = items.sumOf { it.meter }
    val totalMetersStr = "${formatMeters(totalMeters)} mtrs"
    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    canvas.drawText(totalMetersStr, tableRight - 8f, tableY + 13f, paint)
    tableY += 18f

    // Table Borders
    paint.style = Paint.Style.STROKE
    paint.color = lightGrayBorder
    paint.strokeWidth = 1f
    canvas.drawRect(marginX, tableTop, tableRight, tableY, paint)

    // Vertical dividers
    canvas.drawLine(col2X, tableTop, col2X, itemsTableBottom, paint)
    canvas.drawLine(col3X, tableTop, col3X, itemsTableBottom, paint)
    canvas.drawLine(col4X, tableTop, col4X, itemsTableBottom, paint)
    canvas.drawLine(col5X, tableTop, col5X, tableY, paint)

    var currentY = tableY + 6f

    // Order Notes (if any)
    if (!order.notes.isNullOrBlank()) {
        val notesRect = RectF(marginX, currentY, tableRight, currentY + 20f)
        paint.style = Paint.Style.FILL
        paint.color = softBgColor
        canvas.drawRoundRect(notesRect, 4f, 4f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(notesRect, 4f, 4f, paint)

        paint.style = Paint.Style.FILL
        paint.color = darkSlateColor
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Notes: ", marginX + 8f, currentY + 13f, paint)

        val notesW = paint.measureText("Notes: ")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = android.graphics.Color.parseColor("#334155")
        val noteDisp = if (order.notes.length > 60) order.notes.substring(0, 57) + "..." else order.notes
        canvas.drawText(noteDisp, marginX + 8f + notesW, currentY + 13f, paint)

        currentY += 26f
    } else {
        currentY += 8f
    }

    // 4. Section 4: Signatures
    val sigY = currentY + 18f
    val sigColW = (contentWidth - 32f) / 3f

    val sig1X = marginX
    val sig2X = sig1X + sigColW + 16f
    val sig3X = sig2X + sigColW + 16f

    paint.color = android.graphics.Color.parseColor("#94A3B8")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f

    canvas.drawLine(sig1X, sigY, sig1X + sigColW, sigY, paint)
    canvas.drawLine(sig2X, sigY, sig2X + sigColW, sigY, paint)
    canvas.drawLine(sig3X, sigY, sig3X + sigColW, sigY, paint)

    paint.style = Paint.Style.FILL
    paint.textSize = 8.5f
    paint.color = android.graphics.Color.parseColor("#334155")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textAlign = Paint.Align.CENTER

    canvas.drawText("Prepared By", sig1X + (sigColW / 2), sigY + 11f, paint)
    canvas.drawText("Authorized Signatory", sig2X + (sigColW / 2), sigY + 11f, paint)
    canvas.drawText("Customer's Acknowledgment", sig3X + (sigColW / 2), sigY + 11f, paint)

    // 5. Footer Notice
    val footerY = sigY + 28f
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawLine(marginX, footerY, tableRight, footerY, paint)

    paint.style = Paint.Style.FILL
    paint.textSize = 7.5f
    paint.color = mutedGrayColor
    paint.textAlign = Paint.Align.LEFT
    val footerNoticeText = if (swatchesContinue) {
        "Computer generated order challan • Swatches continued on next page"
    } else {
        "Computer generated order challan"
    }
    canvas.drawText(footerNoticeText, marginX, footerY + 10f, paint)

    if (totalPages > 1) {
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNumber of $totalPages", tableRight, footerY + 10f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}

internal fun ChallanLayout.drawContinuationFooter(canvas: Canvas, tableTop: Float, itemsTableBottom: Float, pageNumber: Int) {
    paint.style = Paint.Style.STROKE
    paint.color = lightGrayBorder
    paint.strokeWidth = 1f
    canvas.drawRect(marginX, tableTop, tableRight, itemsTableBottom, paint)

    canvas.drawLine(col2X, tableTop, col2X, itemsTableBottom, paint)
    canvas.drawLine(col3X, tableTop, col3X, itemsTableBottom, paint)
    canvas.drawLine(col4X, tableTop, col4X, itemsTableBottom, paint)
    canvas.drawLine(col5X, tableTop, col5X, itemsTableBottom, paint)

    val footerY = pageHeight - 28f
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawLine(marginX, footerY, tableRight, footerY, paint)

    paint.style = Paint.Style.FILL
    paint.textSize = 7.5f
    paint.color = mutedGrayColor
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("Computer generated order challan • Continued on next page", marginX, footerY + 10f, paint)

    if (totalPages > 1) {
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNumber of $totalPages", tableRight, footerY + 10f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
