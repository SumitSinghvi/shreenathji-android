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
import com.shreenathji.sales.data.local.entities.OrderEntity
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

object PdfChallanGenerator {

    /**
     * Native vector PDF Document rendering using Android PdfDocument
     */
    fun generatePdf(context: Context, orderWithItems: OrderWithItems): File {
        val layout = ChallanLayout(context, orderWithItems)
        val outputDir = File(context.cacheDir, "order_challans").apply { mkdirs() }
        val pdfFile = File(outputDir, "Order_Challan_${orderWithItems.order.orderNumber}.pdf")
        val pdfDocument = PdfDocument()

        with(layout) {
            pages.forEachIndexed { idx, challanPage ->
                val pageNumber = idx + 1
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val top = if (idx == 0) drawFirstPageHeader(canvas) else drawContinuationHeader(canvas, pageNumber)
                var swatchTop = top
                if (challanPage.kind == ChallanPageKind.CHALLAN) {
                    drawTableHeader(canvas, top)
                    val itemsTableBottom = drawRows(canvas, challanPage.rows, top)
                    if (challanPage.isLastChallan) {
                        drawLastPageFooter(canvas, top, itemsTableBottom, itemsTableBottom, pageNumber)
                        swatchTop = itemsTableBottom + ChallanPagination.footerHeight(hasNotes)
                    } else {
                        drawContinuationFooter(canvas, top, itemsTableBottom, pageNumber)
                    }
                } else {
                    drawSwatchPageFooter(canvas, pageNumber)
                }
                drawSwatchGrid(canvas, challanPage.swatches, swatchTop, continued = challanPage.kind == ChallanPageKind.SWATCHES)

                pdfDocument.finishPage(page)
            }
        }

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        return pdfFile
    }

    fun extractImages(orderWithItems: OrderWithItems): List<String> {
        val images = extractOrderImages(orderWithItems.order).toMutableList()
        for (item in orderWithItems.items) {
            val u = item.imageUrl?.trim()
            if (!u.isNullOrEmpty() && !images.contains(u)) {
                images.add(u)
            }
        }
        return images
    }

    /** Photos attached to the order itself (same input the web passes as `image_urls`). */
    fun extractOrderImages(order: OrderEntity): List<String> {
        val images = mutableListOf<String>()

        if (!order.imageUrisJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String>? = Gson().fromJson(order.imageUrisJson, type)
                if (list != null) {
                    for (u in list) {
                        val trimmed = u.trim()
                        if (trimmed.isNotEmpty() && !images.contains(trimmed)) {
                            images.add(trimmed)
                        }
                    }
                }
            } catch (e: Exception) {
                val trimmed = order.imageUrisJson.trim()
                if (trimmed.startsWith("http") || trimmed.startsWith("content:") || trimmed.startsWith("file:")) {
                    images.add(trimmed)
                }
            }
        }

        return images
    }
}
