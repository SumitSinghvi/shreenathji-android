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

internal fun ChallanLayout.drawFirstPageHeader(canvas: Canvas): Float {
    var currentY = 36f

    // 1. Header: SHREE NATH JI (Left) and ORDER CHALLAN (Right)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 18f
    paint.color = orangeColor
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("SHREE NATH JI", marginX, currentY + 16f, paint)

    paint.textSize = 8.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = mutedGrayColor
    canvas.drawText("Order Management System | Premium Textiles & Fabrics", marginX, currentY + 28f, paint)

    // Right header
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 14f
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("ORDER CHALLAN", pageWidth - marginX, currentY + 14f, paint)

    paint.textSize = 9.5f
    paint.color = orangeColor
    canvas.drawText("Order #: ${order.orderNumber} | Date: $formattedDate", pageWidth - marginX, currentY + 27f, paint)

    paint.textAlign = Paint.Align.LEFT
    currentY += 36f

    // Accent Line
    paint.color = orangeColor
    paint.style = Paint.Style.FILL
    canvas.drawRect(marginX, currentY, pageWidth - marginX, currentY + 2.5f, paint)
    currentY += 10f

    // 2. Section 2: Details Card
    val cardTop = currentY
    val cardRect = RectF(marginX, cardTop, pageWidth - marginX, cardTop + cardHeight)

    // Card background
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(cardRect, 8f, 8f, paint)

    // Card border
    paint.color = lightGrayBorder
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRoundRect(cardRect, 8f, 8f, paint)

    // Vertical divider inside card
    val dividerX = marginX + (contentWidth * 0.60f)
    canvas.drawLine(dividerX, cardTop, dividerX, cardTop + cardHeight, paint)

    // Left Column: Customer & Address
    paint.style = Paint.Style.FILL
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText("CUSTOMER", marginX + 10f, cardTop + 13f, paint)

    paint.textSize = 11.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = darkSlateColor
    val partyName = if (order.partyName.length > 35) order.partyName.substring(0, 32) + "..." else order.partyName
    canvas.drawText(partyName, marginX + 10f, cardTop + 26f, paint)

    // Address divider inside left column
    paint.color = android.graphics.Color.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    canvas.drawLine(marginX + 10f, cardTop + 33f, dividerX - 10f, cardTop + 33f, paint)

    val isDeliveryAddress = !order.deliveryAddress.isNullOrBlank()
    val addressToShow = if (isDeliveryAddress) order.deliveryAddress?.trim() else (order.partyAddress?.trim() ?: "—")
    val addressLabel = if (isDeliveryAddress) "DELIVERY ADDRESS" else "STORE / BILLING ADDRESS"

    paint.style = Paint.Style.FILL
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText(addressLabel, marginX + 10f, cardTop + 44f, paint)

    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = android.graphics.Color.parseColor("#334155")
    val addr = addressToShow ?: "—"
    if (addr.length > 45) {
        canvas.drawText(addr.substring(0, 42) + "...", marginX + 10f, cardTop + 56f, paint)
        val remaining = addr.substring(42)
        val addr2 = if (remaining.length > 45) remaining.substring(0, 42) + "..." else remaining
        canvas.drawText(addr2, marginX + 10f, cardTop + 68f, paint)
    } else {
        canvas.drawText(addr, marginX + 10f, cardTop + 58f, paint)
    }

    // Right Column: Order Date, Order Number, Broker, Sales Rep
    val rightColX = dividerX + 10f
    val rightColEnd = pageWidth - marginX - 10f

    // Row 1: Order Date
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText("ORDER DATE:", rightColX, cardTop + 16f, paint)

    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText(formattedDate, rightColEnd, cardTop + 16f, paint)

    // Row 2: Order Number
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText("ORDER NUMBER:", rightColX, cardTop + 34f, paint)

    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText(order.orderNumber, rightColEnd, cardTop + 34f, paint)

    // Row 3: Broker
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText("BROKER:", rightColX, cardTop + 52f, paint)

    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    val rawBroker = if (!order.agent.isNullOrBlank()) order.agent.trim() else "Direct"
    val brokerDisplay = if (rawBroker.length > 22) rawBroker.substring(0, 20) + "..." else rawBroker
    canvas.drawText(brokerDisplay, rightColEnd, cardTop + 52f, paint)

    // Row 4: Sales Rep
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 7.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = mutedGrayColor
    canvas.drawText("SALES REP:", rightColX, cardTop + 70f, paint)

    paint.textSize = 9.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    val rawRepName = if (!order.salesRepName.isNullOrBlank()) order.salesRepName.trim() else "Direct / Staff"
    val repNameDisplay = if (rawRepName.length > 22) rawRepName.substring(0, 20) + "..." else rawRepName
    canvas.drawText(repNameDisplay, rightColEnd, cardTop + 70f, paint)

    paint.textAlign = Paint.Align.LEFT
    return cardTop + cardHeight + 8f
}

internal fun ChallanLayout.drawContinuationHeader(canvas: Canvas, pageNumber: Int): Float {
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 14f
    paint.color = orangeColor
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("SHREE NATH JI", marginX, 42f, paint)

    paint.textSize = 8.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = mutedGrayColor
    canvas.drawText("Order #: ${order.orderNumber} | Customer: ${order.partyName}", marginX, 54f, paint)

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 11.5f
    paint.color = darkSlateColor
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("ORDER CHALLAN (Contd.)", pageWidth - marginX, 42f, paint)

    paint.textSize = 8.5f
    paint.color = orangeColor
    canvas.drawText("Page $pageNumber of $totalPages", pageWidth - marginX, 54f, paint)

    paint.color = orangeColor
    paint.style = Paint.Style.FILL
    canvas.drawRect(marginX, 62f, pageWidth - marginX, 64f, paint)

    paint.textAlign = Paint.Align.LEFT
    return 74f
}

internal fun ChallanLayout.drawTableHeader(canvas: Canvas, tableTop: Float) {
    paint.style = Paint.Style.FILL
    paint.color = headerBgColor
    canvas.drawRect(marginX, tableTop, tableRight, tableTop + headerH, paint)

    paint.color = android.graphics.Color.parseColor("#334155")
    paint.textSize = 8f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    paint.textAlign = Paint.Align.CENTER
    canvas.drawText("S.No.", col1X + (colSnoW / 2), tableTop + 13f, paint)

    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("Product / Fabric Name", col2X + 8f, tableTop + 13f, paint)

    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(if (hasAnyRowImage) "Photo" else "Color / Notes", col3X + (colPhotoW / 2), tableTop + 13f, paint)

    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Rate", col5X - 8f, tableTop + 13f, paint)
    canvas.drawText("Quantity", tableRight - 8f, tableTop + 13f, paint)
}
