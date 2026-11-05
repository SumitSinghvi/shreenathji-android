package com.shreenathji.sales.ui.viewmodels

import com.shreenathji.sales.data.local.entities.ProductEntity

/** Pure transformations on [OrderItemDraft], used by [OrderFormViewModel]. */

private const val MAX_COLORS = 50

private fun ColorSlotDraft.withoutAttachment() = copy(
    attachmentKind = null,
    attachmentDesignId = null,
    attachmentUri = null,
    attachmentName = null
)

private fun categoryOf(product: ProductEntity) = if (product.category == "Print") "Print" else "Dying"

private fun multiplierOf(product: ProductEntity) =
    if (product.matchingMultiplier > 1) product.matchingMultiplier else if (product.isMatching) 2 else 1

/** Print fabrics keep attachments and drop labels; others drop attachments. */
private fun List<ColorSlotDraft>.forCategory(print: Boolean) = map { slot ->
    if (print) slot.copy(label = "") else slot.withoutAttachment()
}

internal fun OrderItemDraft.withProduct(product: ProductEntity): OrderItemDraft {
    val category = categoryOf(product)
    val mult = multiplierOf(product)
    val bp = if (product.isTopBottom) {
        (product.defaultRateBottom ?: product.defaultRate).toString()
    } else if (bottomPriceText.isNotBlank()) {
        bottomPriceText
    } else {
        product.defaultRate.toString()
    }
    return copy(
        name = product.name,
        priceText = product.defaultRate.toString(),
        bottomPriceText = bp,
        category = category,
        isMatching = mult > 1,
        multiplier = mult,
        isTopBottom = product.isTopBottom,
        colors = colors.forCategory(isPrintCategory(category))
    )
}

/** Name typed by hand. [matched] is the catalog product with the same name, if any. */
internal fun OrderItemDraft.withTypedName(name: String, matched: ProductEntity?): OrderItemDraft {
    if (matched == null) {
        return copy(name = name, category = "Dying", colors = colors.map { it.withoutAttachment() })
    }
    val category = categoryOf(matched)
    val mult = multiplierOf(matched)
    val bp = if (matched.isTopBottom) (matched.defaultRateBottom ?: matched.defaultRate).toString() else ""
    return copy(
        name = name,
        priceText = if (priceText.isBlank()) matched.defaultRate.toString() else priceText,
        bottomPriceText = if (bp.isNotBlank()) bp else if (bottomPriceText.isNotBlank()) bottomPriceText else priceText,
        category = category,
        isMatching = mult > 1,
        multiplier = mult,
        isTopBottom = matched.isTopBottom,
        colors = colors.forCategory(isPrintCategory(category))
    )
}

internal fun OrderItemDraft.withNextMatching(): OrderItemDraft {
    val next = when (effectiveMultiplier) {
        1 -> 2
        2 -> 4
        else -> 1
    }
    return copy(isMatching = next > 1, multiplier = next)
}

internal fun OrderItemDraft.withToggledTopBottom(): OrderItemDraft {
    val next = !isTopBottom
    val bp = if (next && bottomPriceText.isBlank()) priceText else bottomPriceText
    val bm = if (next && bottomMetersPerColorText.isBlank()) metersPerColorText else bottomMetersPerColorText
    val newColors = colors.map { slot ->
        if (next && slot.bottomMeterText.isBlank() && !slot.bottomMeterOverridden) {
            slot.copy(bottomMeterText = slot.meterText)
        } else slot
    }
    return copy(isTopBottom = next, bottomPriceText = bp, bottomMetersPerColorText = bm, colors = newColors)
}

internal fun OrderItemDraft.withMetersPerColor(value: String) = copy(
    metersPerColorText = value,
    colors = colors.map { if (!it.meterOverridden) it.copy(meterText = value) else it }
)

internal fun OrderItemDraft.withBottomMetersPerColor(value: String) = copy(
    bottomMetersPerColorText = value,
    colors = colors.map { if (!it.bottomMeterOverridden) it.copy(bottomMeterText = value) else it }
)

private fun OrderItemDraft.newColorSlot() = ColorSlotDraft(
    meterText = metersPerColorText,
    bottomMeterText = bottomMetersPerColorText
)

internal fun OrderItemDraft.withColorCount(count: Int): OrderItemDraft {
    if (count == colors.size) return this
    val next = if (count > colors.size) colors + List(count - colors.size) { newColorSlot() } else colors.take(count)
    return copy(colors = next)
}

internal fun OrderItemDraft.withColorAdded(): OrderItemDraft =
    if (colors.size >= MAX_COLORS) this else copy(colors = colors + newColorSlot())

internal fun OrderItemDraft.withColorRemoved(colorIndex: Int): OrderItemDraft =
    if (colors.size <= 1) this else copy(colors = colors.filterIndexed { i, _ -> i != colorIndex })

internal const val MAX_COLOR_COUNT = MAX_COLORS
