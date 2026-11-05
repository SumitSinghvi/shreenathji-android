package com.shreenathji.sales.ui.util.challan

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Swatch grid: title (24pt) + rows of 5 square cards (100pt, 10pt row gap) from [top].
 * Which swatches land on which page is decided by [ChallanPagination.paginate].
 */
internal fun ChallanLayout.drawSwatchGrid(canvas: Canvas, urls: List<String>, top: Float, continued: Boolean) {
    if (urls.isEmpty()) return
    val startIdx = swatches.indexOf(urls.first())

    paint.style = Paint.Style.FILL
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 9f
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.LEFT
    val title = "COLOR & DESIGN SWATCHES (${swatches.size})" + if (continued) " (cont.)" else ""
    canvas.drawText(title, marginX, top + 16f, paint)

    val gridTop = top + ChallanPagination.SWATCH_TITLE_H
    urls.forEachIndexed { i, url ->
        val left = marginX + (i % swatchCols) * (swatchSize + swatchGapX)
        val cardTop = gridTop + (i / swatchCols) * (swatchSize + swatchGapY)
        drawSwatchCard(
            canvas = canvas,
            paint = paint,
            context = context,
            imageUrl = url,
            label = "Design #${startIdx + i + 1}",
            cardRect = RectF(left, cardTop, left + swatchSize, cardTop + swatchSize),
            lightGrayBorder = lightGrayBorder,
            mutedGrayColor = mutedGrayColor
        )
    }
}

/** Footer for pages that only continue the swatch grid. */
internal fun ChallanLayout.drawSwatchPageFooter(canvas: Canvas, pageNumber: Int) {
    val footerY = pageHeight - 28f
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawLine(marginX, footerY, tableRight, footerY, paint)

    paint.style = Paint.Style.FILL
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = mutedGrayColor
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("Computer generated order challan • Color & design swatches", marginX, footerY + 10f, paint)
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Page $pageNumber of $totalPages", tableRight, footerY + 10f, paint)
    paint.textAlign = Paint.Align.LEFT
}
