package com.shreenathji.sales.ui.viewmodels

import com.shreenathji.sales.data.local.entities.OrderItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The total the rep sees on the form (sum of draft.totalPrice) must equal what is
 * persisted and pushed (sum of flattened item totalPrice, which createOrder sums into
 * orders.totalAmount). A mismatch means the challan disagrees with the order screen.
 */
class OrderTotalsConsistencyTest {

    private fun slot(m: String, bm: String = "") = ColorSlotDraft(meterText = m, bottomMeterText = bm)

    private fun persistedTotal(drafts: List<OrderItemDraft>) =
        flattenOrderItemDrafts(drafts).sumOf(OrderItemEntity::totalPrice)

    private fun shownTotal(drafts: List<OrderItemDraft>) = drafts.sumOf { it.totalPrice }

    @Test
    fun `mixed plain, matching and top-bottom drafts persist the same total that is shown`() {
        val drafts = listOf(
            OrderItemDraft(name = "Rayon", priceText = "52.5", colors = listOf(slot("10.5"), slot("7.25"))),
            OrderItemDraft(name = "Satin", priceText = "99.99", multiplier = 2, isMatching = true, colors = listOf(slot("3.3"), slot("0.1"), slot("0.2"))),
            OrderItemDraft(name = "Cotton", priceText = "40", multiplier = 4, isMatching = true, colors = listOf(slot("12"))),
            OrderItemDraft(
                name = "Linen Set", priceText = "120", bottomPriceText = "85.5", isTopBottom = true, multiplier = 2,
                colors = listOf(slot("2.5", "2.25"), slot("3", ""), slot("", "1.75"))
            )
        )

        assertEquals(shownTotal(drafts), persistedTotal(drafts), 0.001)
    }

    @Test
    fun `incomplete drafts are excluded from the persisted total`() {
        val valid = OrderItemDraft(name = "Rayon", priceText = "50", colors = listOf(slot("10")))
        val noName = OrderItemDraft(name = "", priceText = "50", colors = listOf(slot("10")))
        val noRate = OrderItemDraft(name = "Satin", priceText = "", colors = listOf(slot("10")))
        val noMeters = OrderItemDraft(name = "Cotton", priceText = "50", colors = listOf(slot("")))

        assertEquals(500.0, persistedTotal(listOf(valid, noName, noRate, noMeters)), 0.0001)
    }

    @Test
    fun `persisted meters are cut meters times matching multiplier`() {
        val draft = OrderItemDraft(name = "Satin", priceText = "10", multiplier = 4, isMatching = true, colors = listOf(slot("2.5")))
        val row = flattenOrderItemDrafts(listOf(draft)).single()

        assertEquals(2.5, row.cutMeter!!, 0.0)
        assertEquals(10.0, row.meter, 0.0)
        assertEquals(100.0, row.totalPrice, 0.0001)
        assertEquals(4, row.multiplier)
    }
}
