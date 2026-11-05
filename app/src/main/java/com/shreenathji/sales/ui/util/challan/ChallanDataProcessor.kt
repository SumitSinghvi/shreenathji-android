package com.shreenathji.sales.ui.util.challan

import com.shreenathji.sales.data.local.entities.OrderItemEntity

/*
 * Challan grouping rules. MUST stay identical to the dashboard's
 * `src/components/orders/orderPrintData.ts`. Reference samples:
 * `app/src/test/resources/challan-fixtures/` (copied from the dashboard repo by
 * `scripts/sync-fixtures.sh`).
 */

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
    /** Every photo not shown inside a table row — drawn in the swatch grid. */
    val swatches: List<String>
)

object ChallanDataProcessor {

    private val TOP_BOTTOM_RE = Regex("(?i)\\((top|bottom)\\)")
    private val TOP_RE = Regex("(?i)\\(top\\)")
    private val BOTTOM_RE = Regex("(?i)\\(bottom\\)")

    private fun cleanUrl(url: String?): String? = url?.trim()?.ifEmpty { null }

    private fun cleanNote(note: String?): String? =
        if (!note.isNullOrEmpty() && !note.trim().equals("Photo", ignoreCase = true)) note else null

    private fun multiplierOf(item: OrderItemEntity): Int =
        if (item.multiplier > 1) item.multiplier else if (item.isMatching) 2 else 1

    /** Cut meter 0 counts as missing. */
    private fun cutOf(item: OrderItemEntity): Double? = item.cutMeter?.takeIf { it != 0.0 }

    /** Rule 1: Top/Bottom pairs stay together (set group, else same name + same photo/design). */
    private fun orderTopBottomPairs(items: List<OrderItemEntity>): List<OrderItemEntity> {
        val ordered = mutableListOf<OrderItemEntity>()
        val visited = mutableSetOf<Int>()

        items.forEachIndexed { i, item ->
            if (!visited.add(i)) return@forEachIndexed
            ordered.add(item)

            if (!item.setGroupId.isNullOrBlank()) {
                for (j in (i + 1) until items.size) {
                    if (j !in visited && items[j].setGroupId == item.setGroupId) {
                        visited.add(j)
                        ordered.add(items[j])
                    }
                }
                return@forEachIndexed
            }

            if (!TOP_RE.containsMatchIn(item.productName)) return@forEachIndexed
            val baseName = item.productName.replace(TOP_RE, "").trim().lowercase()
            val img = cleanUrl(item.imageUrl)
            val candidates = ((i + 1) until items.size).filter { j ->
                val other = items[j]
                j !in visited &&
                    BOTTOM_RE.containsMatchIn(other.productName) &&
                    other.productName.replace(BOTTOM_RE, "").trim().lowercase() == baseName
            }
            // Match only on identifiers that are actually set; else the first same-name bottom.
            val match = candidates.firstOrNull { j ->
                (img != null && cleanUrl(items[j].imageUrl) == img) ||
                    (!item.designId.isNullOrEmpty() && items[j].designId == item.designId)
            } ?: if (img == null && item.designId.isNullOrEmpty()) candidates.firstOrNull() else null
            if (match != null) {
                visited.add(match)
                ordered.add(items[match])
            }
        }
        return ordered
    }

    private class Group(
        val productName: String,
        val multiplier: Int,
        val isTopBottom: Boolean,
        val items: MutableList<OrderItemEntity> = mutableListOf()
    )

    fun processOrderItemsForPrint(
        items: List<OrderItemEntity>,
        orderImageUrls: List<String> = emptyList()
    ): ChallanGroupedData {
        // Rule 2: group by base quality + multiplier (+ rate for normal fabrics).
        val groups = mutableListOf<Group>()
        val groupByKey = mutableMapOf<String, Group>()

        for (item in orderTopBottomPairs(items)) {
            val pName = item.productName.trim().ifEmpty { "Fabric" }
            val mult = multiplierOf(item)
            val isTopBottom = TOP_BOTTOM_RE.containsMatchIn(pName)
            val baseName = pName.replace(TOP_BOTTOM_RE, "").trim()
            // Top/Bottom: no rate in key so pairs stay in sequence (each row keeps its own rate).
            val key = if (isTopBottom) {
                "${baseName.lowercase()}::topbottom::$mult"
            } else {
                "${pName.lowercase()}::$mult::${item.price}"
            }
            groupByKey.getOrPut(key) {
                Group(if (isTopBottom) baseName else pName, mult, isTopBottom).also { groups.add(it) }
            }.items.add(item)
        }

        val rows = mutableListOf<ChallanPrintRow>()
        val swatches = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun addSwatch(url: String?) {
            if (url != null && seen.add(url)) swatches.add(url)
        }
        var nextSNo = 1

        for (grp in groups) {
            val grpItems = grp.items
            val multiplier = grp.multiplier
            val isMatching = multiplier > 1
            val firstMeter = grpItems.firstOrNull()?.meter ?: 0.0
            val allMetersEqual = !grp.isTopBottom && grpItems.size > 1 && grpItems.all { it.meter == firstMeter }

            if (allMetersEqual) {
                // Rule 3: one combined row; colour photos go to the swatch grid.
                val grpImages = grpItems.mapNotNull { cleanUrl(it.imageUrl) }
                grpImages.forEach { addSwatch(it) }
                val colorCodes = grpItems.mapNotNull { cleanNote(it.notes?.trim()) }
                val hasPhotos = grpImages.isNotEmpty() || (orderImageUrls.isNotEmpty() && groups.size == 1)

                rows.add(
                    ChallanPrintRow(
                        sNo = nextSNo++,
                        productName = grp.productName,
                        notes = when {
                            hasPhotos -> "${grpItems.size} Swatches Below"
                            colorCodes.isNotEmpty() -> colorCodes.joinToString(", ")
                            else -> null
                        },
                        imageUrl = null,
                        meter = grpItems.sumOf { it.meter },
                        multiplier = multiplier,
                        isMatching = isMatching,
                        cutMeter = cutOf(grpItems.first()) ?: (firstMeter / multiplier),
                        rate = grpItems.first().price,
                        colorCount = grpItems.size
                    )
                )
            } else {
                // Rule 4: one row per item, its own photo in the Photo column.
                for (it in grpItems) {
                    rows.add(
                        ChallanPrintRow(
                            sNo = nextSNo++,
                            productName = it.productName.ifEmpty { grp.productName },
                            notes = cleanNote(it.notes),
                            imageUrl = cleanUrl(it.imageUrl),
                            meter = it.meter,
                            multiplier = multiplier,
                            isMatching = isMatching,
                            cutMeter = cutOf(it) ?: if (multiplier > 1) it.meter / multiplier else null,
                            rate = it.price,
                            colorCount = 1
                        )
                    )
                }
            }
        }

        // Rules 6 + 7: every order photo not already in a row joins the swatch grid.
        val rowUrls = rows.mapNotNull { it.imageUrl }.toSet()
        orderImageUrls.forEach { url ->
            val u = cleanUrl(url)
            if (u != null && u !in rowUrls) addSwatch(u)
        }

        if (rows.size == 1 && rows[0].imageUrl == null && rows[0].notes == null && swatches.isNotEmpty()) {
            rows[0] = rows[0].copy(notes = "${swatches.size} Swatches Below")
        }

        return ChallanGroupedData(rows = rows, swatches = swatches)
    }
}
