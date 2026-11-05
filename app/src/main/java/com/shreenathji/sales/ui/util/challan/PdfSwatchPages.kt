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

internal fun ChallanLayout.drawSwatchPages(pdfDocument: PdfDocument) {
    val swatchChunks = allSwatches.chunked(maxSwatchesPerPage)

    swatchChunks.forEachIndexed { sChunkIdx, chunk ->
        val pageNumber = totalChallanPages + sChunkIdx + 1
        val swatchPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val swatchPage = pdfDocument.startPage(swatchPageInfo)
        val swatchCanvas = swatchPage.canvas

        // Header Left: Brand title & order details
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.color = orangeColor
        paint.textAlign = Paint.Align.LEFT
        swatchCanvas.drawText("SHREE NATH JI", marginX, 48f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = mutedGrayColor
        swatchCanvas.drawText("Order #: ${order.orderNumber} | Customer: ${order.partyName}", marginX, 62f, paint)

        // Header Right: COLOR & DESIGN SWATCHES
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        paint.color = darkSlateColor
        paint.textAlign = Paint.Align.RIGHT
        swatchCanvas.drawText("COLOR & DESIGN SWATCHES", pageWidth - marginX, 46f, paint)

        paint.textSize = 9f
        paint.color = orangeColor
        val swatchBadge = if (swatchPagesCount > 1) {
            "(${allSwatches.size} Swatches) • Page $pageNumber of $totalPages"
        } else {
            "(${allSwatches.size} Swatches)"
        }
        swatchCanvas.drawText(swatchBadge, pageWidth - marginX, 60f, paint)

        // Accent Line
        paint.color = orangeColor
        paint.style = Paint.Style.FILL
        swatchCanvas.drawRect(marginX, 70f, pageWidth - marginX, 72.5f, paint)

        // Swatch Grid
        val gridTop = 84f
        val swatchGapX = (contentWidth - (swatchCols * swatchCardW)) / (swatchCols - 1)
        val swatchGapDedicatedY = 12f

        chunk.forEachIndexed { itemIdx, sUrl ->
            val absIdx = (sChunkIdx * maxSwatchesPerPage) + itemIdx + 1
            val rowIdx = itemIdx / swatchCols
            val colIdx = itemIdx % swatchCols
            val sLeft = marginX + (colIdx * (swatchCardW + swatchGapX))
            val sTop = gridTop + (rowIdx * (swatchCardH + swatchGapDedicatedY))
            val cardRect = RectF(sLeft, sTop, sLeft + swatchCardW, sTop + swatchCardH)

            drawSwatchCard(
                canvas = swatchCanvas,
                paint = paint,
                context = context,
                imageUrl = sUrl,
                label = "Design #$absIdx",
                cardRect = cardRect,
                softBgColor = softBgColor,
                lightGrayBorder = lightGrayBorder,
                darkSlateColor = darkSlateColor,
                mutedGrayColor = mutedGrayColor
            )
        }

        // Footer Notice
        val swatchFooterY = 812f
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        swatchCanvas.drawLine(marginX, swatchFooterY - 8f, pageWidth - marginX, swatchFooterY - 8f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 7.5f
        paint.color = mutedGrayColor
        paint.textAlign = Paint.Align.LEFT
        swatchCanvas.drawText("Computer generated order challan • Color & design swatches", marginX, swatchFooterY + 3f, paint)

        paint.textAlign = Paint.Align.RIGHT
        swatchCanvas.drawText("Page $pageNumber of $totalPages", pageWidth - marginX, swatchFooterY + 3f, paint)
        paint.textAlign = Paint.Align.LEFT

        pdfDocument.finishPage(swatchPage)
    }
}
