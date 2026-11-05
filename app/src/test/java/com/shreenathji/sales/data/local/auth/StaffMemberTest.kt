package com.shreenathji.sales.data.local.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffMemberTest {

    @Test
    fun `StaffDto reads All Orders App permission from Supabase json`() {
        val gson = com.google.gson.Gson()
        fun parse(json: String) =
            gson.fromJson(json, com.shreenathji.sales.data.remote.dto.StaffDto::class.java)
        val base = "\"id\":\"s1\",\"name\":\"SUMIT\",\"role\":\"Sales\""
        assertEquals(true, parse("{$base,\"permissions\":{\"view_permissions\":{\"all_orders_app\":true}}}").canViewAllOrders)
        assertEquals(true, parse("{$base,\"permissions\":{\"full_admin_access\":true}}").canViewAllOrders)
        assertEquals(false, parse("{$base,\"permissions\":{\"view_permissions\":{\"orders\":true}}}").canViewAllOrders)
        assertEquals(false, parse("{$base,\"permissions\":null}").canViewAllOrders)
    }

    @Test
    fun `prefix should use custom orderPrefix when provided`() {
        val staff = StaffMember(
            id = "1",
            name = "Ramesh Kumar",
            pinCode = "1234",
            orderPrefix = "rkx"
        )
        assertEquals("RKX", staff.prefix)
    }

    @Test
    fun `prefix should extract initials for multi-word name when no custom prefix`() {
        val staff1 = StaffMember(
            id = "2",
            name = "Amit Sharma",
            pinCode = "1111"
        )
        assertEquals("AS", staff1.prefix)

        val staff2 = StaffMember(
            id = "3",
            name = "Suresh  Chandra  Verma",
            pinCode = "2222"
        )
        assertEquals("SC", staff2.prefix)
    }

    @Test
    fun `prefix should take first 3 chars for single word name`() {
        val staff = StaffMember(
            id = "4",
            name = "Ramesh",
            pinCode = "3333"
        )
        assertEquals("RAM", staff.prefix)
    }

    @Test
    fun `prefix should fallback to ST when name is empty`() {
        val staff = StaffMember(
            id = "5",
            name = "   ",
            pinCode = "4444"
        )
        assertEquals("ST", staff.prefix)
    }

    @Test
    fun `accessCode should match pinCode`() {
        val staff = StaffMember(
            id = "6",
            name = "Test Rep",
            pinCode = "9876"
        )
        assertEquals("9876", staff.accessCode)
    }
}
