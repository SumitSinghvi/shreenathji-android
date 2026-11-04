package com.shreenathji.sales.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderItemDraftTest {

    @Test
    fun `default values should be zero and empty with one color slot`() {
        val draft = OrderItemDraft()
        assertEquals("", draft.name)
        assertEquals("", draft.priceText)
        assertEquals("", draft.metersPerColorText)
        assertEquals(1, draft.colors.size)
        assertEquals("", draft.colors[0].label)
        assertEquals("", draft.colors[0].meterText)
        assertFalse(draft.colors[0].meterOverridden)
        assertEquals(0.0, draft.price, 0.0001)
        assertEquals(0.0, draft.totalMeters, 0.0001)
        assertEquals(0.0, draft.totalPrice, 0.0001)
    }

    @Test
    fun `totalMeters and totalPrice sum color slots times rate`() {
        val draft = OrderItemDraft(
            name = "Cotton Rayon 58",
            priceText = "45.0",
            metersPerColorText = "250",
            colors = listOf(
                ColorSlotDraft(label = "Navy", meterText = "250"),
                ColorSlotDraft(label = "", meterText = "250"),
                ColorSlotDraft(label = "Olive", meterText = "280", meterOverridden = true),
                ColorSlotDraft(label = "", meterText = "250")
            )
        )
        assertEquals(1030.0, draft.totalMeters, 0.0001)
        assertEquals(45.0, draft.price, 0.0001)
        assertEquals(1030.0 * 45.0, draft.totalPrice, 0.0001)
        assertTrue(draft.colors[2].meterOverridden)
    }

    @Test
    fun `invalid meter strings safely fallback to zero`() {
        val draft = OrderItemDraft(
            name = "Silk Twill",
            priceText = "",
            colors = listOf(
                ColorSlotDraft(meterText = "invalid_number")
            )
        )
        assertEquals(0.0, draft.colors[0].meter, 0.0001)
        assertEquals(0.0, draft.price, 0.0001)
        assertEquals(0.0, draft.totalMeters, 0.0001)
        assertEquals(0.0, draft.totalPrice, 0.0001)
    }

    @Test
    fun `decimal rate computes total with floating point precision`() {
        val draft = OrderItemDraft(
            name = "Polyester 44",
            priceText = "12.75",
            colors = listOf(
                ColorSlotDraft(meterText = "50")
            )
        )
        assertEquals(50.0, draft.totalMeters, 0.0001)
        assertEquals(12.75, draft.price, 0.0001)
        assertEquals(637.5, draft.totalPrice, 0.0001)
    }

    @Test
    fun `flattenOrderItemDrafts creates one row per color`() {
        val rows = flattenOrderItemDrafts(
            listOf(
                OrderItemDraft(
                    name = "Reva",
                    priceText = "85",
                    colors = listOf(
                        ColorSlotDraft(label = "Navy", meterText = "250"),
                        ColorSlotDraft(meterText = "250")
                    )
                )
            )
        )
        assertEquals(2, rows.size)
        assertEquals("Navy", rows[0].notes)
        assertEquals(null, rows[1].notes)
        assertEquals(250.0 * 85.0, rows[0].totalPrice, 0.0001)
    }

    @Test
    fun `2-matching doubles totalMeters and totalPrice`() {
        val draft = OrderItemDraft(
            name = "Reva 2-Matching",
            priceText = "70.0",
            isMatching = true,
            colors = listOf(
                ColorSlotDraft(label = "Design 1", meterText = "120")
            )
        )
        assertEquals(120.0, draft.baseMeters, 0.0001)
        assertEquals(240.0, draft.totalMeters, 0.0001)
        assertEquals(240.0 * 70.0, draft.totalPrice, 0.0001)

        val rows = flattenOrderItemDrafts(listOf(draft))
        assertEquals(1, rows.size)
        assertEquals(240.0, rows[0].meter, 0.0001)
        assertEquals(120.0, rows[0].cutMeter!!, 0.0001)
        assertEquals(2, rows[0].multiplier)
        assertTrue(rows[0].isMatching)
        assertEquals(240.0 * 70.0, rows[0].totalPrice, 0.0001)
    }

    @Test
    fun `4-matching quadruples totalMeters and totalPrice`() {
        val draft = OrderItemDraft(
            name = "Slub 4-Matching",
            priceText = "80.0",
            multiplier = 4,
            isMatching = true,
            colors = listOf(
                ColorSlotDraft(label = "Set A", meterText = "120")
            )
        )
        assertEquals(120.0, draft.baseMeters, 0.0001)
        assertEquals(480.0, draft.totalMeters, 0.0001)
        assertEquals(480.0 * 80.0, draft.totalPrice, 0.0001)

        val rows = flattenOrderItemDrafts(listOf(draft))
        assertEquals(1, rows.size)
        assertEquals(480.0, rows[0].meter, 0.0001)
        assertEquals(120.0, rows[0].cutMeter!!, 0.0001)
        assertEquals(4, rows[0].multiplier)
        assertTrue(rows[0].isMatching)
        assertEquals(480.0 * 80.0, rows[0].totalPrice, 0.0001)
    }

    @Test
    fun `top and bottom calculates distinct top and bottom meters and rates`() {
        val draft = OrderItemDraft(
            name = "Suit Set Top Bottom",
            priceText = "100.0",
            bottomPriceText = "60.0",
            isTopBottom = true,
            colors = listOf(
                ColorSlotDraft(label = "Red", meterText = "2.5", bottomMeterText = "2.0"),
                ColorSlotDraft(label = "Blue", meterText = "2.5", bottomMeterText = "2.0")
            )
        )
        assertEquals(5.0, draft.baseTopMeters, 0.0001)
        assertEquals(4.0, draft.baseBottomMeters, 0.0001)
        assertEquals(9.0, draft.baseMeters, 0.0001)
        assertEquals(9.0, draft.totalMeters, 0.0001)
        // (5 * 100) + (4 * 60) = 500 + 240 = 740
        assertEquals(740.0, draft.totalPrice, 0.0001)
    }

    @Test
    fun `flattenOrderItemDrafts with isTopBottom emits TOP and BOTTOM rows linked by setGroupId`() {
        val draft = OrderItemDraft(
            name = "Cotton Suit",
            priceText = "120.0",
            bottomPriceText = "80.0",
            isTopBottom = true,
            colors = listOf(
                ColorSlotDraft(label = "Color A", meterText = "50", bottomMeterText = "40")
            )
        )
        val rows = flattenOrderItemDrafts(listOf(draft))
        assertEquals(2, rows.size)

        val topRow = rows.find { it.component == "TOP" }!!
        val btmRow = rows.find { it.component == "BOTTOM" }!!

        assertEquals("Cotton Suit (Top)", topRow.productName)
        assertEquals(50.0, topRow.meter, 0.0001)
        assertEquals(120.0, topRow.price, 0.0001)
        assertEquals(6000.0, topRow.totalPrice, 0.0001)

        assertEquals("Cotton Suit (Bottom)", btmRow.productName)
        assertEquals(40.0, btmRow.meter, 0.0001)
        assertEquals(80.0, btmRow.price, 0.0001)
        assertEquals(3200.0, btmRow.totalPrice, 0.0001)

        assertEquals(topRow.setGroupId, btmRow.setGroupId)
        assertTrue(topRow.setGroupId!!.isNotBlank())
    }

    @Test
    fun `top and bottom combined with 2-matching multiplies both top and bottom`() {
        val draft = OrderItemDraft(
            name = "Paired Matching Set",
            priceText = "100.0",
            bottomPriceText = "50.0",
            isTopBottom = true,
            isMatching = true,
            multiplier = 2,
            colors = listOf(
                ColorSlotDraft(label = "Set 1", meterText = "10", bottomMeterText = "10")
            )
        )
        // Base: top=10, btm=10 -> total 20 base. With 2x: top=20, btm=20 -> total 40 mtr.
        assertEquals(40.0, draft.totalMeters, 0.0001)
        // (20 * 100) + (20 * 50) = 2000 + 1000 = 3000
        assertEquals(3000.0, draft.totalPrice, 0.0001)

        val rows = flattenOrderItemDrafts(listOf(draft))
        assertEquals(2, rows.size)
        val top = rows.find { it.component == "TOP" }!!
        val btm = rows.find { it.component == "BOTTOM" }!!
        assertEquals(20.0, top.meter, 0.0001)
        assertEquals(10.0, top.cutMeter!!, 0.0001)
        assertEquals(20.0, btm.meter, 0.0001)
        assertEquals(10.0, btm.cutMeter!!, 0.0001)
    }

    @Test
    fun `negative meters should not deduct from totalMeters`() {
        val draft = OrderItemDraft(
            name = "Silk Twill",
            priceText = "100.0",
            colors = listOf(
                ColorSlotDraft(label = "Red", meterText = "100"),
                ColorSlotDraft(label = "Blue", meterText = "-40")
            )
        )
        // Invariant: negative meter must not deduct from positive meters!
        assertEquals(100.0, draft.totalMeters, 0.0001)
        assertEquals(10000.0, draft.totalPrice, 0.0001)
    }

    @Test
    fun `top and bottom with negative bottom rate should not produce negative item`() {
        val draft = OrderItemDraft(
            name = "Suit Set",
            priceText = "100.0",
            bottomPriceText = "-50.0",
            isTopBottom = true,
            colors = listOf(
                ColorSlotDraft(label = "Set 1", meterText = "10", bottomMeterText = "10")
            )
        )
        val rows = flattenOrderItemDrafts(listOf(draft))
        // Invariant: negative bottom rate must NOT be emitted as a valid line item!
        assertTrue(rows.none { it.price < 0 || it.totalPrice < 0 })
    }
}
