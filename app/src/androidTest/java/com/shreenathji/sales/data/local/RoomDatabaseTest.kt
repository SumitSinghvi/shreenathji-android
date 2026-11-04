package com.shreenathji.sales.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shreenathji.sales.data.local.dao.DailyVisitPlanDao
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.dao.PartyDao
import com.shreenathji.sales.data.local.dao.ProductDao
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var partyDao: PartyDao
    private lateinit var productDao: ProductDao
    private lateinit var orderDao: OrderDao
    private lateinit var dailyVisitPlanDao: DailyVisitPlanDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        partyDao = db.partyDao()
        productDao = db.productDao()
        orderDao = db.orderDao()
        dailyVisitPlanDao = db.dailyVisitPlanDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndQueryParty() = runBlocking {
        val party = PartyEntity(
            id = "party_1",
            businessName = "Shreenathji Silks",
            contactPerson = "Jayesh Bhai",
            phone = "9825000000",
            addressLine1 = "Shop 101, New Textile Market",
            addressLine2 = "Ring Road, Surat",
            defaultAgent = "Laxmi Agency"
        )
        partyDao.insertParty(party)

        val retrieved = partyDao.getPartyById("party_1")
        assertNotNull(retrieved)
        assertEquals("Shreenathji Silks", retrieved?.businessName)
        assertEquals("9825000000", retrieved?.phone)

        val searchResults = partyDao.searchParties("Silks").first()
        assertEquals(1, searchResults.size)
        assertEquals("Shreenathji Silks", searchResults[0].businessName)
    }

    @Test
    fun insertAndQueryProduct() = runBlocking {
        val product = ProductEntity(
            id = "prod_1",
            name = "Micro Velvet 9000",
            category = "Velvet",
            defaultRate = 220.0
        )
        productDao.
        insertProduct(product)

        val allProducts = productDao.getAllProducts().first()
        assertEquals(1, allProducts.size)
        assertEquals("Micro Velvet 9000", allProducts[0].name)
        assertEquals(220.0, allProducts[0].defaultRate, 0.0001)
    }

    @Test
    fun insertAndRetrieveOrderWithItems() = runBlocking {
        val order = OrderEntity(
            id = "order_100",
            orderNumber = "RG-4521",
            partyId = "party_1",
            partyName = "Shreenathji Silks",
            salesRepId = "staff_1",
            salesRepName = "Rajesh Gupta",
            totalAmount = 22000.0,
            orderStatus = "PENDING",
            isSynced = false
        )
        val items = listOf(
            OrderItemEntity(
                id = "item_1",
                orderId = "order_100",
                productName = "Micro Velvet 9000",
                meter = 100.0,
                price = 220.0,
                totalPrice = 22000.0
            )
        )

        orderDao.insertFullOrder(order, items)

        val orderWithItems = orderDao.getOrderWithItemsById("order_100")
        assertNotNull(orderWithItems)
        assertEquals("RG-4521", orderWithItems?.order?.orderNumber)
        assertEquals(1, orderWithItems?.items?.size)
        assertEquals("Micro Velvet 9000", orderWithItems?.items?.get(0)?.productName)

        // Test unsynced orders query
        val unsynced = orderDao.getUnsyncedOrders()
        assertEquals(1, unsynced.size)
        assertEquals("order_100", unsynced[0].order.id)
    }

    @Test
    fun insertAndQueryDailyVisitPlan() = runBlocking {
        val plan = DailyVisitPlanEntity(
            id = "plan_1",
            partyId = "party_1",
            partyName = "Shreenathji Silks",
            salesRepId = "staff_1",
            salesRepName = "Rajesh Gupta",
            planDate = "2026-09-01",
            status = "PENDING",
            priorityOrder = 1
        )
        dailyVisitPlanDao.insertPlan(plan)

        val plans = dailyVisitPlanDao.getPlansForRepAndDate("2026-09-01", "Rajesh Gupta").first()
        assertEquals(1, plans.size)
        assertEquals("PENDING", plans[0].status)

        // Update status to VISITED
        dailyVisitPlanDao.updatePlanStatus(
            id = "plan_1",
            status = "VISITED",
            visitedAt = System.currentTimeMillis(),
            remarks = null,
            orderId = "order_100",
            isSynced = false
        )

        val updatedPlan = dailyVisitPlanDao.getPlanByPartyAndDate("party_1", "2026-09-01")
        assertEquals("VISITED", updatedPlan?.status)
    }
}
