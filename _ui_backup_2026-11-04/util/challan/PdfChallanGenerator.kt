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

object PdfChallanGenerator {

    /**
     * Native vector PDF Document rendering using Android PdfDocument
     */
    fun generatePdf(context: Context, orderWithItems: OrderWithItems): File {
        val order = orderWithItems.order
        val items = orderWithItems.items
        val allImages = extractImages(orderWithItems)
        val groupedData = ChallanDataProcessor.processOrderItemsForPrint(items, allImages)
        val rows = groupedData.rows
        val bottomImages = groupedData.bottomImages
        val remainingImages = groupedData.remainingImages

        val outputDir = File(context.cacheDir, "order_challans").apply { mkdirs() }
        val pdfFile = File(outputDir, "Order_Challan_${order.orderNumber}.pdf")

        val pdfDocument = PdfDocument()
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

        val (photoDim, colPhotoW, defaultRowH) = when {
            !hasAnyRowImage -> Triple(0f, 100f, 22f)
            rowCount <= 3 -> Triple(64f, 80f, 72f)
            rowCount <= 5 -> Triple(48f, 64f, 54f)
            else -> Triple(32f, 48f, 38f)
        }

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

        // Split table rows across pages if they exceed page capacity
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

        // Render Challan Pages
        challanSlices.forEachIndexed { cIdx, slice ->
            val pageNumber = cIdx + 1
            val isFirstChallanPage = cIdx == 0
            val isLastChallanPage = cIdx == totalChallanPages - 1

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val tableTop: Float

            if (isFirstChallanPage) {
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
                tableTop = cardTop + cardHeight + 8f
            } else {
                // Continuation page header
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
                tableTop = 74f
            }

            // Draw Table Header
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

            var tableY = tableTop + headerH

            // Draw Slice Rows
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

            val itemsTableBottom = tableY

            if (isLastChallanPage) {
                // Summary Row 1: Total Items
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
                val footerNoticeText = if (!swatchesFitOnLastPage && allSwatches.isNotEmpty()) {
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

                // 6. Bottom Swatches (if space permits on this page)
                if (swatchesFitOnLastPage) {
                    var swatchY = footerY + 18f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9f
                    paint.color = darkSlateColor
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("COLOR & DESIGN SWATCHES (${allSwatches.size})", marginX, swatchY, paint)
                    swatchY += 8f

                    val swatchGapX = (contentWidth - (swatchCols * swatchCardW)) / (swatchCols - 1)

                    allSwatches.forEachIndexed { sIdx, sUrl ->
                        val rowIdx = sIdx / swatchCols
                        val colIdx = sIdx % swatchCols
                        val sLeft = marginX + (colIdx * (swatchCardW + swatchGapX))
                        val sTop = swatchY + (rowIdx * (swatchCardH + swatchGapY))
                        val cardRect = RectF(sLeft, sTop, sLeft + swatchCardW, sTop + swatchCardH)

                        drawSwatchCard(
                            canvas = canvas,
                            paint = paint,
                            context = context,
                            imageUrl = sUrl,
                            label = "Design #${sIdx + 1}",
                            cardRect = cardRect,
                            softBgColor = softBgColor,
                            lightGrayBorder = lightGrayBorder,
                            darkSlateColor = darkSlateColor,
                            mutedGrayColor = mutedGrayColor
                        )
                    }
                }
            } else {
                // Continuation page table borders & footer
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

            pdfDocument.finishPage(page)
        }

        // Render Dedicated Swatch Pages if swatches did not fit on the challan page
        if (!swatchesFitOnLastPage && allSwatches.isNotEmpty()) {
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

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        return pdfFile
    }

    private fun drawSwatchCard(
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

    fun extractImages(orderWithItems: OrderWithItems): List<String> {
        val images = mutableListOf<String>()
        val order = orderWithItems.order

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

        for (item in orderWithItems.items) {
            val u = item.imageUrl?.trim()
            if (!u.isNullOrEmpty() && !images.contains(u)) {
                images.add(u)
            }
        }

        return images
    }

    private fun loadBitmapSafe(context: Context, source: String): Bitmap? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try Coil first (handles HTTP URLs, content://, file://, caching, rotation, and downsampling)
        try {
            val request = ImageRequest.Builder(context)
                .data(trimmed)
                .allowHardware(false) // Required for PdfDocument Canvas
                .size(1600) // Prevent OOM
                .build()

            val result = runBlocking(Dispatchers.IO) {
                context.imageLoader.execute(request)
            }
            if (result is SuccessResult) {
                val bmp = drawableToBitmap(result.drawable)
                if (bmp != null) return bmp
            }
        } catch (e: Exception) {
            // Coil failed, proceed to local fallbacks
        }

        // 2. Direct ContentResolver fallback for content:// URIs
        try {
            val uri = android.net.Uri.parse(trimmed)
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    return decodeSampledBitmap(stream)
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // 3. Direct File fallback for file:// or absolute path
        try {
            val file = if (trimmed.startsWith("file://")) {
                File(android.net.Uri.parse(trimmed).path ?: "")
            } else {
                File(trimmed)
            }
            if (file.exists()) {
                file.inputStream().use { stream ->
                    return decodeSampledBitmap(stream)
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (!bmp.isRecycled) {
                return if (bmp.config == Bitmap.Config.HARDWARE) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bmp
                }
            }
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 600
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 800
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun decodeSampledBitmap(inputStream: InputStream): Bitmap? {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return null

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

        var sampleSize = 1
        val maxDim = 1600
        while ((boundsOpts.outWidth / sampleSize) > maxDim || (boundsOpts.outHeight / sampleSize) > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    private fun formatMeters(meter: Double): String {
        val numFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
        return if (meter % 1.0 == 0.0) {
            numFormat.format(meter.toLong())
        } else {
            numFormat.format(meter)
        }
    }
}
