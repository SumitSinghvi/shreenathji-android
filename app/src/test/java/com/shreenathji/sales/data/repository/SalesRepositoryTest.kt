package com.shreenathji.sales.data.repository

import com.shreenathji.sales.data.local.dao.DailyVisitPlanDao
import com.shreenathji.sales.data.local.dao.DesignDao
import com.shreenathji.sales.data.local.dao.DesignFolderDao
import com.shreenathji.sales.data.local.dao.LocationDao
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.dao.PartyDao
import com.shreenathji.sales.data.local.dao.ProductDao
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.sync.SupabaseSyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesRepositoryTest {

    private val partyDao: PartyDao = mockk(relaxed = true)
    private val productDao: ProductDao = mockk(relaxed = true)
    private val orderDao: OrderDao = mockk(relaxed = true)
    private val locationDao: LocationDao = mockk(relaxed = true)
    private val dailyVisitPlanDao: DailyVisitPlanDao = mockk(relaxed = true)
    private val designDao: DesignDao = mockk(relaxed = true)
    private val designFolderDao: DesignFolderDao = mockk(relaxed = true)
    private val syncManager: SupabaseSyncManager = mockk(relaxed = true)

    private lateinit var repository: SalesRepository

    @Before
    fun setUp() {
        repository = SalesRepository(
            partyDao = partyDao,
            productDao = productDao,
            orderDao = orderDao,
            locationDao = locationDao,
            dailyVisitPlanDao = dailyVisitPlanDao,
            designDao = designDao,
            designFolderDao = designFolderDao,
            syncManager = syncManager
        )
    }

    @Test
    fun `addParty inserts into local DB and pushes to sync manager`() = runTest {
        val party = PartyEntity(id = "p1", businessName = "Shree Krishna Textiles")
        val expectedLocalParty = party.copy(isSynced = false)

        repository.addParty(party)

        coVerify { partyDao.insertParty(expectedLocalParty) }
        verify { syncManager.pushPartyAsync(expectedLocalParty) }
    }

    @Test
    fun `searchParties calls getAllParties when query is blank`() {
        repository.searchParties("   ")
        verify { partyDao.getAllParties() }
    }

    @Test
    fun `searchParties calls searchParties when query is not blank`() {
        repository.searchParties("Cotton")
        verify { partyDao.searchParties("Cotton") }
    }

    @Test
    fun `createOrder computes total amount and uses order prefix`() = runTest {
        val items = listOf(
            OrderItemEntity(
                id = "item_1",
                orderId = "",
                productName = "Rayon",
                meter = 100.0,
                price = 50.0,
                totalPrice = 5000.0
            ),
            OrderItemEntity(
                id = "item_2",
                orderId = "",
                productName = "Satin",
                meter = 20.0,
                price = 100.0,
                totalPrice = 2000.0
            )
        )

        val orderSlot = slot<OrderEntity>()
        coEvery { orderDao.insertFullOrder(capture(orderSlot), any()) } returns Unit

        val orderId = repository.createOrder(
            partyId = "party_1",
            partyName = "Ambaji Traders",
            partyAddress = "Market 1",
            agent = "Agent X",
            poNumber = "PO-101",
            deliveryAddress = null,
            imageUris = emptyList(),
            items = items,
            notes = "Handle with care",
            salesRepId = "rep_1",
            salesRepName = "Nilesh Shah",
            latitude = 21.1702,
            longitude = 72.8311,
            orderPrefix = "NS"
        )

        assertNotNull(orderId)
        val capturedOrder = orderSlot.captured
        assertEquals(7000.0, capturedOrder.totalAmount, 0.0001)
        assertTrue(capturedOrder.orderNumber.startsWith("NS-"))
        val numericPart = capturedOrder.orderNumber.substringAfter('-')
        assertEquals(6, numericPart.length)
        assertTrue(numericPart.all { it.isDigit() })
        assertEquals("PENDING", capturedOrder.orderStatus)
        assertEquals(false, capturedOrder.isSynced)
        assertEquals("Ambaji Traders", capturedOrder.partyName)
        assertEquals(21.1702, capturedOrder.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `getOrdersByRep returns all orders if staff is staff_admin`() {
        repository.getOrdersByRep("staff_admin")
        verify { orderDao.getAllOrdersWithItems() }
    }

    @Test
    fun `getOrdersByRep filters by repId if staff is not admin`() {
        repository.getOrdersByRep("rep_123")
        verify { orderDao.getOrdersByRep("rep_123") }
    }
}
