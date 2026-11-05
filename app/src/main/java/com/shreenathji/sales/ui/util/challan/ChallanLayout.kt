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
    val groupedData = ChallanDataProcessor.processOrderItemsForPrint(
        items,
        PdfChallanGenerator.extractOrderImages(order)
    )
    val rows = groupedData.rows
    val swatches = groupedData.swatches

    // Standard A4 width and height in PostScript points: 595 x 842
    val pageWidth = ChallanPagination.PAGE_W
    val pageHeight = ChallanPagination.PAGE_H

    // Palette
    val orangeColor = android.graphics.Color.parseColor("#EA580C")
    val darkSlateColor = android.graphics.Color.parseColor("#0F172A")
    val mutedGrayColor = android.graphics.Color.parseColor("#64748B")
    val lightGrayBorder = android.graphics.Color.parseColor("#CBD5E1")
    val headerBgColor = android.graphics.Color.parseColor("#F1F5F9")
    val softBgColor = android.graphics.Color.parseColor("#F8FAFC")

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val marginX = ChallanPagination.MARGIN_X
    val contentWidth = pageWidth - (marginX * 2)

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN"))
    val formattedDate = dateFormatter.format(Date(order.orderTimestamp))

    // Table column widths & row heights (shared rule: ChallanPagination)
    private val dims = ChallanPagination.photoDims(rows)
    val photoDim = dims.photo
    val colPhotoW = dims.colW

    val colSnoW = ChallanPagination.COL_SNO_W
    val colRateW = ChallanPagination.COL_RATE_W
    val colQtyW = ChallanPagination.COL_QTY_W
    val colProdW = contentWidth - colSnoW - colQtyW - colRateW - colPhotoW

    val col1X = marginX
    val col2X = col1X + colSnoW
    val col3X = col2X + colProdW
    val col4X = col3X + colPhotoW
    val col5X = col4X + colRateW
    val tableRight = pageWidth - marginX

    fun getRowH(row: ChallanPrintRow): Float = ChallanPagination.rowHeight(row, dims)

    val headerH = ChallanPagination.TABLE_HEADER_H
    val cardHeight = 84f

    val hasNotes = !order.notes.isNullOrBlank()
    val pages: List<ChallanPage> = ChallanPagination.paginate(rows, swatches, hasNotes)
    val totalPages = pages.size
    val swatchesContinue = pages.any { it.kind == ChallanPageKind.SWATCHES }

    val swatchCols = ChallanPagination.SWATCH_COLS
    val swatchSize = ChallanPagination.SWATCH_SIZE
    val swatchGapY = ChallanPagination.SWATCH_GAP_Y
    val swatchGapX = (contentWidth - (swatchCols * swatchSize)) / (swatchCols - 1)
}

internal fun formatMeters(meter: Double): String {
    val numFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return if (meter % 1.0 == 0.0) {
        numFormat.format(meter.toLong())
    } else {
        numFormat.format(meter)
    }
}
