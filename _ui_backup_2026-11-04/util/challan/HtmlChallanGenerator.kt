package com.shreenathji.sales.ui.util.challan

import com.shreenathji.sales.data.local.entities.OrderWithItems
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlChallanGenerator {

    fun generateChallanHtml(orderWithItems: OrderWithItems): String {
        val order = orderWithItems.order
        val items = orderWithItems.items
        val allImages = PdfChallanGenerator.extractImages(orderWithItems)
        val groupedData = ChallanDataProcessor.processOrderItemsForPrint(items, allImages)
        val rows = groupedData.rows
        val bottomImages = groupedData.bottomImages
        val remainingImages = groupedData.remainingImages

        val inLocale = Locale("en", "IN")
        val numFormat = NumberFormat.getNumberInstance(inLocale)
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", inLocale)
        val formattedDate = dateFormatter.format(Date(order.orderTimestamp))
        val totalMeters = items.sumOf { it.meter }
        val totalMetersFmt = if (totalMeters % 1.0 == 0.0) numFormat.format(totalMeters.toLong()) else numFormat.format(totalMeters)
        val totalMetersStr = "$totalMetersFmt mtrs"

        val isDeliveryAddress = !order.deliveryAddress.isNullOrBlank()
        val addressToShow = if (isDeliveryAddress) order.deliveryAddress?.trim() else (order.partyAddress?.trim() ?: "—")
        val broker = if (!order.agent.isNullOrBlank()) order.agent else "Direct"
        val salesRep = if (!order.salesRepName.isNullOrBlank()) order.salesRepName else "Direct / Staff"

        val rowCount = rows.size
        val hasAnyRowImage = rows.any { !it.imageUrl.isNullOrBlank() }
        val imgSizePx = when {
            rowCount <= 3 -> 120
            rowCount <= 5 -> 90
            else -> 64
        }
        val colPhotoWidthPx = when {
            !hasAnyRowImage -> 140
            rowCount <= 3 -> 136
            rowCount <= 5 -> 106
            else -> 80
        }
        val photoHeaderTitle = if (hasAnyRowImage) "Photo" else "Color / Notes"

        val itemRowsHtml = rows.map { row ->
            val rowMeterFmt = if (row.meter % 1.0 == 0.0) numFormat.format(row.meter.toLong()) else numFormat.format(row.meter)
            val totalRowMeterStr = "$rowMeterFmt mtrs"
            val isMatch = row.isMatching || row.multiplier > 1
            val mult = if (row.multiplier > 1) row.multiplier else 2
            val hasSubtitle = isMatch || row.colorCount > 1 || row.cutMeter != null
            val cut = row.cutMeter ?: (if (isMatch) row.meter / (mult * row.colorCount) else null)

            val rateDisplay = if (row.rate > 0.0) {
                val rFmt = if (row.rate % 1.0 == 0.0) numFormat.format(row.rate.toLong()) else String.format(inLocale, "%.2f", row.rate)
                "₹$rFmt"
            } else "—"

            val subtitleHtml = if (hasSubtitle) {
                val badgeColor = if (mult == 4) "color: #6b21a8; background: #f3e8ff; border: 1px solid #d8b4fe;" else "color: #92400e; background: #fef3c7; border: 1px solid #fde68a;"
                val badgeHtml = if (isMatch) """<span style="display: inline-block; font-size: 9px; font-weight: 700; $badgeColor padding: 1px 4px; border-radius: 3px;">${mult}-Matching</span>""" else ""
                val colorHtml = if (row.colorCount > 1) """<span style="font-weight: 700; color: #334155;">${if (isMatch) " • " else ""}${row.colorCount} Colors</span>""" else ""
                val cutHtml = if (cut != null) {
                    val cutVal: Double = cut
                    val cutStr = if (cutVal % 1.0 == 0.0) cutVal.toLong().toString() else cutVal.toString()
                    val setStr = if (isMatch) {
                        val setMtr = cutVal * mult
                        val setMtrStr = if (setMtr % 1.0 == 0.0) setMtr.toLong().toString() else setMtr.toString()
                        " (${setMtrStr}m/color)"
                    } else ""
                    """<span style="color: #64748b;">• Cut: <strong style="color: #334155;">${cutStr}m${if (isMatch) " × $mult" else ""}</strong>$setStr</span>"""
                } else ""
                """<div style="margin-top: 3px; font-size: 9.5px; display: flex; align-items: center; gap: 4px; flex-wrap: wrap;">$badgeHtml$colorHtml$cutHtml</div>"""
            } else ""

            """
            <tr>
                <td style="text-align: center; border-right: 1px solid #cbd5e1; padding: 6px 6px; font-size: 11px; color: #475569; vertical-align: middle;">${row.sNo}</td>
                <td style="border-right: 1px solid #cbd5e1; padding: 6px 10px; vertical-align: middle;">
                    <div style="font-weight: 700; color: #0f172a; font-size: 12px;">${escapeHtml(row.productName)}</div>
                    $subtitleHtml
                </td>
                <td style="border-right: 1px solid #cbd5e1; padding: 4px; text-align: center; vertical-align: middle;">
                    ${if (!row.imageUrl.isNullOrBlank()) {
                        """<div style="display: flex; justify-content: center; align-items: center;">
                            <img src="${escapeHtml(row.imageUrl)}" alt="Swatch" style="width: ${imgSizePx}px; height: ${imgSizePx}px; object-fit: cover; border-radius: 6px; border: 1px solid #cbd5e1; display: block;" />
                        </div>"""
                    } else {
                        """<span style="font-weight: 600; font-size: 10.5px; color: #334155;">${escapeHtml(row.notes ?: "—")}</span>"""
                    }}
                </td>
                <td style="text-align: right; border-right: 1px solid #cbd5e1; padding: 6px 8px; font-weight: 700; font-family: monospace; font-size: 11.5px; color: #0f172a; vertical-align: middle; white-space: nowrap;">
                    $rateDisplay
                </td>
                <td style="text-align: right; padding: 6px 10px; font-weight: 700; font-family: monospace; font-size: 12px; color: #0f172a; vertical-align: middle; white-space: nowrap;">
                    $totalRowMeterStr
                </td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val rowImageUrls = rows.mapNotNull { it.imageUrl?.trim() }.toSet()
        val allSwatches = (bottomImages + remainingImages + allImages.filter { !rowImageUrls.contains(it.trim()) }).distinct()

        val bottomSwatchesHtml = if (allSwatches.isNotEmpty()) {
            """
            <div style="margin-top: 24px; padding-top: 12px; border-top: 2px dashed #e2e8f0; page-break-inside: avoid; break-inside: avoid;">
                <h3 style="font-size: 11px; font-weight: 800; text-transform: uppercase; color: #1e293b; margin-bottom: 8px;">
                    Color &amp; Design Swatches (${allSwatches.size})
                </h3>
                <div style="display: flex; flex-wrap: wrap; gap: 10px;">
                    ${allSwatches.mapIndexed { idx, url ->
                        """
                        <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; padding: 4px; text-align: center; width: 112px;">
                            <img src="${escapeHtml(url)}" alt="Design ${idx + 1}" style="width: 104px; height: 104px; object-fit: cover; border-radius: 4px; display: block; margin: 0 auto;" />
                            <div style="margin-top: 4px; font-size: 9px; font-weight: 700; color: #334155;">Design #${idx + 1}</div>
                        </div>
                        """.trimIndent()
                    }.joinToString("\n")}
                </div>
            </div>
            """.trimIndent()
        } else ""

        val notesSectionHtml = if (!order.notes.isNullOrBlank()) {
            """
            <div style="margin-top: 8px; padding: 8px 10px; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 10.5px; color: #334155;">
                <strong style="color: #0f172a;">Notes:</strong> <strong style="color: #0f172a;">${escapeHtml(order.notes)}</strong>
            </div>
            """.trimIndent()
        } else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Order Challan - ${escapeHtml(order.orderNumber)}</title>
            <style>
                @page {
                    size: A4 portrait;
                    margin: 10mm;
                }
                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    color: #0f172a;
                    background: #ffffff;
                    padding: 16px 20px;
                    font-size: 11px;
                    line-height: 1.35;
                    -webkit-print-color-adjust: exact;
                    print-color-adjust: exact;
                }
                .header-flex {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                }
                .brand-title {
                    font-size: 20px;
                    font-weight: 900;
                    text-transform: uppercase;
                    letter-spacing: -0.5px;
                    color: #ea580c;
                    line-height: 1.1;
                }
                .brand-sub {
                    font-size: 10px;
                    color: #64748b;
                    font-weight: 500;
                    margin-top: 2px;
                }
                .challan-title {
                    font-size: 15px;
                    font-weight: 800;
                    text-transform: uppercase;
                    letter-spacing: -0.3px;
                    color: #0f172a;
                    text-align: right;
                    line-height: 1.1;
                }
                .challan-meta {
                    font-size: 11px;
                    font-weight: 700;
                    color: #ea580c;
                    text-align: right;
                    margin-top: 2px;
                }
                .accent-line {
                    height: 2.5px;
                    background-color: #ea580c;
                    width: 100%;
                    margin-top: 8px;
                    margin-bottom: 10px;
                }
                .card-grid {
                    border: 1px solid #cbd5e1;
                    border-radius: 10px;
                    padding: 10px 12px;
                    display: flex;
                    background: #ffffff;
                    margin-bottom: 10px;
                }
                .col-left {
                    flex: 7;
                    padding-right: 12px;
                }
                .col-right {
                    flex: 5;
                    padding-left: 12px;
                    border-left: 1px solid #e2e8f0;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    gap: 6px;
                }
                .micro-label {
                    font-size: 9px;
                    font-weight: 700;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                    color: #94a3b8;
                    display: block;
                }
                .customer-name {
                    font-size: 13px;
                    font-weight: 700;
                    color: #0f172a;
                    margin-top: 2px;
                }
                .address-text {
                    font-size: 11px;
                    color: #334155;
                    margin-top: 2px;
                    line-height: 1.3;
                }
                .key-value-row {
                    display: flex;
                    justify-content: space-between;
                    align-items: baseline;
                    font-size: 11px;
                }
                .table-container {
                    border: 1px solid #cbd5e1;
                    border-radius: 8px;
                    overflow: hidden;
                    margin-top: 10px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    text-align: left;
                    font-size: 11px;
                }
                thead tr {
                    background-color: #f1f5f9;
                    color: #334155;
                    font-weight: 700;
                    border-bottom: 1px solid #cbd5e1;
                    font-size: 10px;
                    text-transform: uppercase;
                    letter-spacing: 0.4px;
                }
                thead th {
                    padding: 7px 10px;
                }
                tbody tr {
                    border-bottom: 1px solid #e2e8f0;
                }
                tfoot tr.unique-row {
                    border-top: 1px solid #cbd5e1;
                    background: #ffffff;
                }
                tfoot tr.total-row {
                    border-top: 1px solid #e2e8f0;
                    background: #ffffff;
                }
                .sig-grid {
                    margin-top: 26px;
                    display: flex;
                    justify-content: space-between;
                    text-align: center;
                    gap: 16px;
                }
                .sig-col {
                    flex: 1;
                }
                .sig-line {
                    border-top: 2px solid #94a3b8;
                    width: 100%;
                    margin-bottom: 4px;
                }
                .sig-text {
                    font-size: 10px;
                    font-weight: 600;
                    color: #334155;
                }
                .footer-note {
                    margin-top: 16px;
                    padding-top: 4px;
                    border-top: 1px solid #e2e8f0;
                    font-size: 8.5px;
                    font-style: italic;
                    color: #94a3b8;
                }
                .printable-attachments-page {
                    page-break-before: always;
                    break-before: page;
                    page-break-inside: avoid;
                    break-inside: avoid;
                    padding-top: 10px;
                }
                .printable-attachments-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 10px;
                    width: 100%;
                    margin-top: 8px;
                }
                .printable-attachments-grid.single-photo {
                    grid-template-columns: 1fr;
                }
                .printable-attachment-cell {
                    border: 1px solid #cbd5e1;
                    border-radius: 6px;
                    overflow: hidden;
                    background: #f8fafc;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    padding: 8px;
                    box-sizing: border-box;
                    min-height: 240px;
                    page-break-inside: avoid;
                    break-inside: avoid;
                }
                .printable-attachment-cell img {
                    max-width: 100%;
                    max-height: 440px;
                    object-fit: contain;
                    display: block;
                    border-radius: 4px;
                }
                .attachment-badge {
                    font-size: 10px;
                    font-weight: 700;
                    color: #64748b;
                    margin-bottom: 6px;
                    align-self: flex-start;
                }
            </style>
        </head>
        <body>
            <!-- Section 1: Header -->
            <div class="header-flex">
                <div>
                    <h1 class="brand-title">SHREE NATH JI</h1>
                    <p class="brand-sub">Order Management System | Premium Textiles & Fabrics</p>
                </div>
                <div>
                    <h2 class="challan-title">ORDER CHALLAN</h2>
                    <p class="challan-meta">Order #: ${escapeHtml(order.orderNumber)} | Date: $formattedDate</p>
                </div>
            </div>

            <!-- Solid Orange Accent Line -->
            <div class="accent-line"></div>

            <!-- Section 2: Two-Column Info Card -->
            <div class="card-grid">
                <div class="col-left">
                    <span class="micro-label">CUSTOMER</span>
                    <div class="customer-name">${escapeHtml(order.partyName)}</div>
                    <p class="address-text" style="margin-top: 4px; color: #475569; font-size: 11px;">
                        ${if (isDeliveryAddress) """<strong style="color: #334155;">Delivery: </strong>""" else ""}${escapeHtml(addressToShow ?: "—")}
                    </p>
                </div>

                <div class="col-right">
                    <div class="key-value-row">
                        <span class="micro-label">ORDER DATE:</span>
                        <strong style="color: #0f172a;">$formattedDate</strong>
                    </div>
                    <div class="key-value-row">
                        <span class="micro-label">ORDER NUMBER:</span>
                        <strong style="font-family: monospace; color: #0f172a;">${escapeHtml(order.orderNumber)}</strong>
                    </div>
                    <div class="key-value-row">
                        <span class="micro-label">BROKER:</span>
                        <strong style="color: #0f172a;">${escapeHtml(broker)}</strong>
                    </div>
                    <div class="key-value-row">
                        <span class="micro-label">SALES REP:</span>
                        <strong style="color: #0f172a;">${escapeHtml(salesRep)}</strong>
                    </div>
                </div>
            </div>

            <!-- Section 3: Line Items Table -->
            <div class="table-container">
                <table>
                    <thead>
                        <tr>
                            <th style="width: 38px; text-align: center; border-right: 1px solid #cbd5e1;">S.No.</th>
                            <th style="border-right: 1px solid #cbd5e1;">Product / Fabric Name</th>
                            <th style="width: ${colPhotoWidthPx}px; text-align: center; border-right: 1px solid #cbd5e1;">$photoHeaderTitle</th>
                            <th style="width: 70px; text-align: right; border-right: 1px solid #cbd5e1;">Rate</th>
                            <th style="width: 105px; text-align: right;">Quantity</th>
                        </tr>
                    </thead>
                    <tbody>
                        $itemRowsHtml
                    </tbody>
                    <tfoot>
                        <tr class="unique-row">
                            <td colspan="4" style="padding: 6px 10px; text-align: right; font-weight: 700; font-size: 9.5px; text-transform: uppercase; color: #475569; border-right: 1px solid #cbd5e1;">
                                TOTAL ITEMS
                            </td>
                            <td style="padding: 6px 10px; text-align: right; font-weight: 700; color: #0f172a; font-size: 11px;">
                                ${rows.size} ${if (rows.size == 1) "Item" else "Items"}
                            </td>
                        </tr>
                        <tr class="total-row">
                            <td colspan="4" style="padding: 7px 10px; text-align: right; font-weight: 800; font-size: 10.5px; text-transform: uppercase; color: #ea580c; border-right: 1px solid #cbd5e1;">
                                TOTAL FABRIC QUANTITY
                            </td>
                            <td style="padding: 7px 10px; text-align: right; font-weight: 900; color: #ea580c; font-family: monospace; font-size: 12.5px;">
                                $totalMetersStr mtrs
                            </td>
                        </tr>
                    </tfoot>
                </table>
            </div>

            $notesSectionHtml

            <!-- Section 4: Three Signatures -->
            <div class="sig-grid">
                <div class="sig-col">
                    <div class="sig-line"></div>
                    <p class="sig-text">Prepared By</p>
                </div>
                <div class="sig-col">
                    <div class="sig-line"></div>
                    <p class="sig-text">Authorized Signatory</p>
                </div>
                <div class="sig-col">
                    <div class="sig-line"></div>
                    <p class="sig-text">Customer's Acknowledgment</p>
                </div>
            </div>

            <!-- Footer -->
            <div class="footer-note">
                Computer generated order challan
            </div>

            $bottomSwatchesHtml
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
