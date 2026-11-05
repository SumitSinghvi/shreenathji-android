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

internal class ChallanLayout(val context: Context, orderWithItems: OrderWithItems) {
    val order = orderWithItems.order
    val items = orderWithItems.items
    val allImages = PdfChallanGenerator.extractImages(orderWithItems)
    val groupedData = ChallanDataProcessor.processOrderItemsForPrint(items, allImages)
    val rows = groupedData.rows
    val bottomImages = groupedData.bottomImages
    val remainingImages = groupedData.remainingImages

    // Standard A4 width and height in PostScript points: 595 x 842
    val pageWidth = 595
    val pageHeight = 842

    // Palette
    val orangeColor = android.graphics.Color.parseColor("#EA580C")
    val darkSlateColor = android.graphics.Color.parseColor("#0F172A")
    val mutedGrayColor = android.graphics.Color.parseColor("#64748B")
    val lightGrayBorder = android.graphics.Color.parseColor("#CBD5E1")
    val headerBgColor = android.graphics.Color.parseColor("#F1F5F9")
    val softBgColor = android.graphics.Color.parseColor("#F8FAFC")

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val marginX = 28f
    val contentWidth = pageWidth - (marginX * 2)

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN"))
    val formattedDate = dateFormatter.format(Date(order.orderTimestamp))

    // Table column widths & row heights
    val rowCount = rows.size
    val hasAnyRowImage = rows.any { !it.imageUrl.isNullOrBlank() }

    private val dims = when {
        !hasAnyRowImage -> Triple(0f, 100f, 22f)
        rowCount <= 3 -> Triple(64f, 80f, 72f)
        rowCount <= 5 -> Triple(48f, 64f, 54f)
        else -> Triple(32f, 48f, 38f)
    }
    val photoDim = dims.first
    val colPhotoW = dims.second
    val defaultRowH = dims.third

    val colSnoW = 30f
    val colRateW = 55f
    val colQtyW = 95f
    val colProdW = contentWidth - colSnoW - colQtyW - colRateW - colPhotoW

    val col1X = marginX
    val col2X = col1X + colSnoW
    val col3X = col2X + colProdW
    val col4X = col3X + colPhotoW
    val col5X = col4X + colRateW
    val tableRight = pageWidth - marginX

    fun getRowH(row: ChallanPrintRow): Float {
        val hasImg = !row.imageUrl.isNullOrBlank()
        val isMatch = row.isMatching || row.multiplier > 1
        val hasSubtitle = isMatch || row.colorCount > 1 || row.cutMeter != null
        return if (hasImg) defaultRowH else if (hasSubtitle) 26f else defaultRowH
    }

    val headerH = 20f
    val cardHeight = 84f
    val page1TableTop = 36f + 36f + 10f + cardHeight + 8f // 174f
    val page1RowsStartY = page1TableTop + headerH // 194f

    val challanFooterBlockH = 34f + (if (!order.notes.isNullOrBlank()) 26f else 8f) + 48f + 26f

    val challanSlices: List<List<ChallanPrintRow>> = sliceRows()

    val totalChallanPages = challanSlices.size

    // All non-row images to be displayed as swatches
    val rowImageUrls = rows.mapNotNull { it.imageUrl?.trim() }.toSet()
    val allSwatches = (bottomImages + remainingImages + allImages.filter { !rowImageUrls.contains(it.trim()) }).distinct()

    // Calculate if swatches fit on the last challan page below footer
    val isFirstChallan = totalChallanPages == 1
    val lastPageStartY = if (isFirstChallan) page1RowsStartY else (74f + headerH)
    val lastSliceRowsH = challanSlices.last().sumOf { getRowH(it).toDouble() }.toFloat()
    val lastTableBottomY = lastPageStartY + lastSliceRowsH
    val lastChallanFooterEndY = lastTableBottomY + challanFooterBlockH
    val spaceRemainingOnLastPage = (pageHeight - 20f) - lastChallanFooterEndY

    val swatchCols = 4
    val swatchCardW = 127f
    val swatchCardH = 117f
    val swatchGapY = 10f
    val swatchRowCount = (allSwatches.size + swatchCols - 1) / swatchCols
    val swatchesTotalH = if (allSwatches.isEmpty()) 0f else (24f + (swatchRowCount * swatchCardH) + ((swatchRowCount - 1) * swatchGapY))

    val swatchesFitOnLastPage = allSwatches.isNotEmpty() && (swatchesTotalH <= spaceRemainingOnLastPage)
    val maxSwatchesPerPage = 20
    val swatchPagesCount = if (allSwatches.isEmpty() || swatchesFitOnLastPage) 0 else ((allSwatches.size + maxSwatchesPerPage - 1) / maxSwatchesPerPage)

    val totalPages = totalChallanPages + swatchPagesCount

    private fun sliceRows(): List<List<ChallanPrintRow>> {
        val challanSlices = mutableListOf<List<ChallanPrintRow>>()
        var remainingRows = rows
        var currChallanPage = 0

        if (remainingRows.isEmpty()) {
            challanSlices.add(emptyList())
        } else {
            while (remainingRows.isNotEmpty()) {
                currChallanPage++
                val isFirst = currChallanPage == 1
                val startY = if (isFirst) page1RowsStartY else (74f + headerH)

                val totalH = remainingRows.sumOf { getRowH(it).toDouble() }.toFloat()
                val availAsLast = pageHeight - 20f - challanFooterBlockH - startY

                if (totalH <= availAsLast) {
                    challanSlices.add(remainingRows)
                    break
                }

                val availAsCont = pageHeight - 44f - startY
                var acc = 0f
                val slice = mutableListOf<ChallanPrintRow>()
                for (r in remainingRows) {
                    val rH = getRowH(r)
                    if (acc + rH <= availAsCont) {
                        slice.add(r)
                        acc += rH
                    } else {
                        break
                    }
                }
                if (slice.isEmpty()) {
                    slice.add(remainingRows.first())
                }
                challanSlices.add(slice)
                remainingRows = remainingRows.drop(slice.size)
            }
        }
        return challanSlices
    }
}

internal fun formatMeters(meter: Double): String {
    val numFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return if (meter % 1.0 == 0.0) {
        numFormat.format(meter.toLong())
    } else {
        numFormat.format(meter)
    }
}
