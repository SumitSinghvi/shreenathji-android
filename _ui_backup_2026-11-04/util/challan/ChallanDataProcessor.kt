package com.shreenathji.sales.ui.util.challan

import com.shreenathji.sales.data.local.entities.OrderItemEntity

data class ChallanPrintRow(
    val sNo: Int,
    val productName: String,
    val notes: String? = null,
    val imageUrl: String? = null,
    val meter: Double,
    val multiplier: Int = 1,
    val isMatching: Boolean = false,
    val cutMeter: Double? = null,
    val rate: Double = 0.0,
    val colorCount: Int = 1
)

data class ChallanGroupedData(
    val rows: List<ChallanPrintRow>,
    val bottomImages: List<String>,
    val remainingImages: List<String>
)

object ChallanDataProcessor {

    fun processOrderItemsForPrint(
        items: List<OrderItemEntity>,
        orderImageUrls: List<String> = emptyList()
    ): ChallanGroupedData {
        // 1. Sort items so Top & Bottom pairs stay together:
        val orderedItems = mutableListOf<OrderItemEntity>()
        val visited = mutableSetOf<String>()

        for (i in items.indices) {
            val item = items[i]
            val itemId = item.id.ifEmpty { "idx-$i" }
            if (visited.contains(itemId)) continue

            visited.add(itemId)
            orderedItems.add(item)

            if (!item.setGroupId.isNullOrBlank()) {
                for (j in (i + 1) until items.size) {
                    val other = items[j]
                    val otherId = other.id.ifEmpty { "idx-$j" }
                    if (!visited.contains(otherId) && other.setGroupId == item.setGroupId) {
                        visited.add(otherId)
                        orderedItems.add(other)
                    }
                }
            } else {
                val pLower = item.productName.lowercase()
                if (pLower.contains("(top)")) {
                    val baseName = item.productName.replace(Regex("(?i)\\(top\\)"), "").trim().lowercase()
                    for (j in (i + 1) until items.size) {
                        val other = items[j]
                        val otherId = other.id.ifEmpty { "idx-$j" }
                        val otherLower = other.productName.lowercase()
                        val otherBase = other.productName.replace(Regex("(?i)\\(bottom\\)"), "").trim().lowercase()
                        if (!visited.contains(otherId) &&
                            otherLower.contains("(bottom)") &&
                            otherBase == baseName &&
                            (other.imageUrl == item.imageUrl || other.designId == item.designId)
                        ) {
                            visited.add(otherId)
                            orderedItems.add(other)
                            break
                        }
                    }
                }
            }
        }

        // 2. Group by base product quality + multiplier
        data class TempGroup(
            val key: String,
            val productName: String,
            val multiplier: Int,
            val isMatching: Boolean,
            val items: MutableList<OrderItemEntity>
        )

        val groups = mutableListOf<TempGroup>()
        val groupByKey = mutableMapOf<String, TempGroup>()

        for (item in orderedItems) {
            val pName = item.productName.trim().ifEmpty { "Fabric" }
            val mult = if (item.multiplier > 1) item.multiplier else (if (item.isMatching) 2 else 1)
            val isTopOrBottom = Regex("(?i)\\((top|bottom)\\)").containsMatchIn(pName)
            val baseFabricName = pName.replace(Regex("(?i)\\((top|bottom)\\)"), "").trim()
            val key = if (isTopOrBottom) "${baseFabricName.lowercase()}::topbottom::$mult" else "${pName.lowercase()}::$mult"

            var grp = groupByKey[key]
            if (grp == null) {
                grp = TempGroup(
                    key = key,
                    productName = if (isTopOrBottom) baseFabricName else pName,
                    multiplier = mult,
                    isMatching = mult > 1,
                    items = mutableListOf()
                )
                groupByKey[key] = grp
                groups.add(grp)
            }
            grp.items.add(item)
        }

        val rows = mutableListOf<ChallanPrintRow>()
        val bottomImages = mutableListOf<String>()
        val seenBottomUrls = mutableSetOf<String>()
        var nextSNo = 1

        for (grp in groups) {
            val grpItems = grp.items
            val productName = grp.productName
            val multiplier = grp.multiplier
            val isMatching = grp.isMatching

            // Condition 1: Multiple colors with the exact same meter
            val hasMultipleColors = grpItems.size > 1
            val firstMeter = grpItems.firstOrNull()?.meter ?: 0.0
            val allMetersEqual = hasMultipleColors && grpItems.all { it.meter == firstMeter }

            if (allMetersEqual) {
                val totalGrpMeters = grpItems.sumOf { it.meter }
                val colorCount = grpItems.size

                val grpImages = mutableListOf<String>()
                grpItems.forEach { it ->
                    val url = it.imageUrl?.trim()
                    if (!url.isNullOrEmpty()) {
                        if (!seenBottomUrls.contains(url)) {
                            seenBottomUrls.add(url)
                            bottomImages.add(url)
                        }
                        if (!grpImages.contains(url)) {
                            grpImages.add(url)
                        }
                    }
                }

                // Filter out automated placeholder "Photo"
                val colorCodes = grpItems
                    .mapNotNull { it.notes?.trim() }
                    .filter { it.isNotEmpty() && !it.equals("Photo", ignoreCase = true) }
                val hasPhotos = grpImages.isNotEmpty() || (orderImageUrls.isNotEmpty() && groups.size == 1)
                val noteText = if (hasPhotos) {
                    "$colorCount Swatches Below"
                } else if (colorCodes.isNotEmpty()) {
                    colorCodes.joinToString(", ")
                } else {
                    null
                }

                rows.add(
                    ChallanPrintRow(
                        sNo = nextSNo++,
                        productName = productName,
                        notes = noteText,
                        imageUrl = null,
                        meter = totalGrpMeters,
                        multiplier = multiplier,
                        isMatching = isMatching,
                        cutMeter = grpItems.firstOrNull()?.cutMeter ?: (firstMeter / multiplier),
                        rate = grpItems.firstOrNull()?.price ?: 0.0,
                        colorCount = colorCount
                    )
                )
            } else {
                // Condition 2: Different meters or single color (Top/Bottom paired sequence)
                for (it in grpItems) {
                    val cleanNote = if (!it.notes.isNullOrBlank() && !it.notes.equals("Photo", ignoreCase = true)) it.notes else null
                    rows.add(
                        ChallanPrintRow(
                            sNo = nextSNo++,
                            productName = it.productName.ifEmpty { productName },
                            notes = cleanNote,
                            imageUrl = it.imageUrl?.trim()?.ifEmpty { null },
                            meter = it.meter,
                            multiplier = multiplier,
                            isMatching = isMatching,
                            cutMeter = it.cutMeter ?: if (multiplier > 1) (it.meter / multiplier) else null,
                            rate = it.price,
                            colorCount = 1
                        )
                    )
                }
            }
        }

        // If items didn't have individual image_url set, fallback to orderImageUrls (matching web app OrderPrintSlip)
        if (bottomImages.isEmpty() && orderImageUrls.isNotEmpty()) {
            val rowUrls = rows.mapNotNull { it.imageUrl?.trim() }.toSet()
            orderImageUrls.forEach { url ->
                val trimmed = url.trim()
                if (trimmed.isNotEmpty() && !seenBottomUrls.contains(trimmed) && !rowUrls.contains(trimmed)) {
                    seenBottomUrls.add(trimmed)
                    bottomImages.add(trimmed)
                }
            }
            if (rows.size == 1 && rows[0].imageUrl == null && rows[0].notes == null) {
                rows[0] = rows[0].copy(notes = "${orderImageUrls.size} Swatches Below")
            }
        }

        // Remaining attachments not shown in table rows or bottom swatches
        val displayedUrls = mutableSetOf<String>()
        displayedUrls.addAll(bottomImages)
        rows.forEach { r -> r.imageUrl?.let { displayedUrls.add(it) } }

        val remainingImages = orderImageUrls.filter { !displayedUrls.contains(it.trim()) }

        return ChallanGroupedData(
            rows = rows,
            bottomImages = bottomImages,
            remainingImages = remainingImages
        )
    }
}
