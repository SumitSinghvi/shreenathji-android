package com.shreenathji.sales.ui.viewmodels

import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.remote.dto.DispatchOrderDto
import com.shreenathji.sales.data.remote.dto.DispatchOrderItemDto
import com.shreenathji.sales.data.remote.dto.DispatchOrderRefDto
import com.shreenathji.sales.data.remote.dto.DispatchRecordDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchFormTest {

    private fun input(vararg meters: String) = DispatchFormInput(
        orderId = "o1",
        items = meters.mapIndexed { i, m -> DispatchItemInput("i$i", m) },
        lrNumber = " LR-9 ",
        transporter = "  "
    )

    @Test
    fun `accepts actual meters that differ from ordered`() {
        assertNull(DispatchForm.validate(input("121", "60.5")))
    }

    @Test
    fun `rejects blank, negative and non-numeric meters`() {
        assertNotNull(DispatchForm.validate(input("121", "")))
        assertNotNull(DispatchForm.validate(input("-1")))
        assertNotNull(DispatchForm.validate(input("abc")))
        assertNotNull(DispatchForm.validate(input("NaN")))
    }

    @Test
    fun `rejects an order with no items`() {
        assertNotNull(DispatchForm.validate(DispatchFormInput("o1", emptyList())))
    }

    @Test
    fun `payload trims text, nulls blanks and carries parsed meters`() {
        val p = DispatchForm.payload(input("121"), "s1", "Ravi", listOf("u1"))
        assertEquals("o1", p["p_order_id"])
        assertEquals("LR-9", p["p_lr_number"])
        assertNull(p["p_transporter"])
        assertEquals(listOf("u1"), p["p_photo_urls"])
        @Suppress("UNCHECKED_CAST")
        val items = p["p_items"] as List<Map<String, Any?>>
        assertEquals(121.0, items.single()["actual_meter"])
    }

    @Test
    fun `dispatch role is detected case-insensitively`() {
        assertTrue(StaffMember("1", "A", role = " Dispatch ").isDispatch)
        assertFalse(StaffMember("2", "B", role = "Field Sales Rep").isDispatch)
    }

    @Test
    fun `kolkataDayKey shifts UTC evening timestamps into next Kolkata calendar day`() {
        // 19:30 UTC + 5:30 = 01:00 next day (Nov 5)
        assertEquals("2026-11-05", DispatchDateUtils.kolkataDayKey("2026-11-04T19:30:00Z"))
        // 05:00 UTC + 5:30 = 10:30 same day (Nov 4)
        assertEquals("2026-11-04", DispatchDateUtils.kolkataDayKey("2026-11-04T05:00:00Z"))
        // Plain date string
        assertEquals("2026-11-04", DispatchDateUtils.kolkataDayKey("2026-11-04"))
        assertNull(DispatchDateUtils.kolkataDayKey(null))
    }

    @Test
    fun `filterOrders applies party filter, date filter, and text query`() {
        val oToday = DispatchOrderDto(
            id = "o1",
            order_number = "TM-101",
            party_name = "mj export",
            order_timestamp = "2026-11-05T04:00:00Z"
        )
        val oYesterday = DispatchOrderDto(
            id = "o2",
            order_number = "TM-102",
            party_name = "mj export",
            order_timestamp = "2026-11-04T04:00:00Z"
        )
        val oOtherParty = DispatchOrderDto(
            id = "o3",
            order_number = "RB-201",
            party_name = "pearl creation",
            order_timestamp = "2026-11-05T04:00:00Z"
        )
        val list = listOf(oToday, oYesterday, oOtherParty)

        // Date filter TODAY
        val todayOnly = DispatchFilters.filterOrders(
            list, query = "", dateFilter = DispatchDateFilter.TODAY, partyFilter = null,
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(oToday, oOtherParty), todayOnly)

        // Party filter
        val partyOnly = DispatchFilters.filterOrders(
            list, query = "", dateFilter = DispatchDateFilter.ALL, partyFilter = "mj export",
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(oToday, oYesterday), partyOnly)

        // Combined Date + Party
        val todayAndParty = DispatchFilters.filterOrders(
            list, query = "", dateFilter = DispatchDateFilter.TODAY, partyFilter = "mj export",
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(oToday), todayAndParty)

        // Search query
        val search = DispatchFilters.filterOrders(
            list, query = "RB-201", dateFilter = DispatchDateFilter.ALL, partyFilter = null,
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(oOtherParty), search)
    }

    @Test
    fun `filterDispatches applies transporter, lr and party filters`() {
        val d1 = DispatchRecordDto(
            id = "d1",
            order_id = "o1",
            lr_number = "LR-991",
            transporter = "Navkar",
            dispatched_at = "2026-11-05T06:00:00Z",
            orders = DispatchOrderRefDto("TM-101", "mj export")
        )
        val d2 = DispatchRecordDto(
            id = "d2",
            order_id = "o2",
            lr_number = "LR-992",
            transporter = "VRL",
            dispatched_at = "2026-11-01T06:00:00Z",
            orders = DispatchOrderRefDto("RB-201", "pearl creation")
        )
        val list = listOf(d1, d2)

        val navkarMatch = DispatchFilters.filterDispatches(
            list, query = "navkar", dateFilter = DispatchDateFilter.ALL, partyFilter = null,
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(d1), navkarMatch)

        val weekMatch = DispatchFilters.filterDispatches(
            list, query = "", dateFilter = DispatchDateFilter.THIS_WEEK, partyFilter = null,
            todayKey = "2026-11-05", yesterdayKey = "2026-11-04", weekAgoKey = "2026-10-29"
        )
        assertEquals(listOf(d1, d2), weekMatch)
    }
}
