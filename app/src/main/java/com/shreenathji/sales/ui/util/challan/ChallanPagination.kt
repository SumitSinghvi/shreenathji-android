package com.shreenathji.sales.ui.util.challan

/*
 * Challan page-break rule. MUST stay identical to the dashboard's
 * `src/components/orders/challanPagination.ts`. Units are PDF points; A4 = 595 x 842.
 * Every block on the page has a fixed height so both apps break at the same rows.
 */

data class PhotoDims(
    /** Photo edge inside a row (0 = no photo column). */
    val photo: Float,
    /** Width of the Photo / Notes column. */
    val colW: Float,
    /** Height of a row that carries a photo. */
    val photoRowH: Float
)

enum class ChallanPageKind { CHALLAN, SWATCHES }

data class ChallanPage(
    val kind: ChallanPageKind,
    val rows: List<ChallanPrintRow>,
    val swatches: List<String>,
    /** True on the challan page that carries totals + signatures. */
    val isLastChallan: Boolean
)

object ChallanPagination {
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN_X = 28f
    /** Page 1: brand header + customer card, up to the table. */
    const val PAGE1_TABLE_TOP = 174f
    /** Later pages: compact continuation header, up to the table / swatches. */
    const val CONT_TABLE_TOP = 74f
    const val TABLE_HEADER_H = 20f
    /** Space kept free below the footer on the last challan page. */
    const val LAST_PAGE_BOTTOM_GAP = 20f
    /** Continuation footer ("Continued… Page n of N") on other pages. */
    const val CONT_FOOTER_H = 44f
    const val COL_SNO_W = 30f
    const val COL_RATE_W = 55f
    const val COL_QTY_W = 95f
    const val SWATCH_COLS = 5
    const val SWATCH_SIZE = 100f
    const val SWATCH_GAP_Y = 10f
    const val SWATCH_TITLE_H = 24f

    /** Rule 5: photo size depends on row count. */
    fun photoDims(rows: List<ChallanPrintRow>): PhotoDims = when {
        rows.none { it.imageUrl != null } -> PhotoDims(0f, 100f, 0f)
        rows.size <= 3 -> PhotoDims(96f, 108f, 104f)
        rows.size <= 5 -> PhotoDims(72f, 84f, 80f)
        else -> PhotoDims(48f, 60f, 56f)
    }

    fun hasSubtitle(row: ChallanPrintRow): Boolean =
        row.multiplier > 1 || row.isMatching || row.colorCount > 1 || row.cutMeter != null

    fun rowHeight(row: ChallanPrintRow, dims: PhotoDims): Float = when {
        row.imageUrl != null -> dims.photoRowH
        hasSubtitle(row) -> 26f
        else -> 22f
    }

    /** Totals (34) + notes (26, or 8 gap) + signatures (48) + generated-by line (26). */
    fun footerHeight(hasNotes: Boolean): Float = 34f + (if (hasNotes) 26f else 8f) + 48f + 26f

    private fun swatchRowsThatFit(space: Float): Int =
        if (space < SWATCH_TITLE_H + SWATCH_SIZE) 0
        else ((space - SWATCH_TITLE_H + SWATCH_GAP_Y) / (SWATCH_SIZE + SWATCH_GAP_Y)).toInt()

    fun paginate(rows: List<ChallanPrintRow>, swatches: List<String>, hasNotes: Boolean): List<ChallanPage> {
        val dims = photoDims(rows)
        val footerH = footerHeight(hasNotes)
        val pages = mutableListOf<ChallanPage>()

        var rest = rows
        var lastRowsBottom: Float
        while (true) {
            val startY = (if (pages.isEmpty()) PAGE1_TABLE_TOP else CONT_TABLE_TOP) + TABLE_HEADER_H
            val total = rest.sumOf { rowHeight(it, dims).toDouble() }.toFloat()
            // Everything left (plus the footer) fits: this is the last challan page.
            if (total <= PAGE_H - LAST_PAGE_BOTTOM_GAP - footerH - startY) {
                pages.add(ChallanPage(ChallanPageKind.CHALLAN, rest, emptyList(), isLastChallan = true))
                lastRowsBottom = startY + total
                break
            }
            val avail = PAGE_H - CONT_FOOTER_H - startY
            var acc = 0f
            var n = 0
            while (n < rest.size && acc + rowHeight(rest[n], dims) <= avail) acc += rowHeight(rest[n++], dims)
            n = maxOf(n, 1)
            pages.add(ChallanPage(ChallanPageKind.CHALLAN, rest.take(n), emptyList(), isLastChallan = false))
            rest = rest.drop(n)
        }

        // Swatches: full rows of 5 that fit under the footer stay; the rest continue on new pages.
        val space = PAGE_H - LAST_PAGE_BOTTOM_GAP - (lastRowsBottom + footerH)
        val onLast = minOf(swatches.size, swatchRowsThatFit(space) * SWATCH_COLS)
        pages[pages.lastIndex] = pages.last().copy(swatches = swatches.take(onLast))

        val perPage = swatchRowsThatFit(PAGE_H - CONT_FOOTER_H - CONT_TABLE_TOP) * SWATCH_COLS
        swatches.drop(onLast).chunked(perPage).forEach { chunk ->
            pages.add(ChallanPage(ChallanPageKind.SWATCHES, emptyList(), chunk, isLastChallan = false))
        }
        return pages
    }
}
