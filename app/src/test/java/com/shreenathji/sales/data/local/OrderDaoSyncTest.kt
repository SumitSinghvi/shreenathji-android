package com.shreenathji.sales.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pull-from-cloud conflict rules on the real Room DB: a pull must never drop unsynced local orders. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class OrderDaoSyncTest {

    private lateinit var db: AppDatabase

    private fun order(id: String, synced: Boolean, amount: Double = 100.0) =
        OrderEntity(id = id, orderNumber = "NS-$id", partyId = "p1", partyName = "Party", totalAmount = amount, isSynced = synced)

    private fun item(id: String, orderId: String) =
        OrderItemEntity(id = id, orderId = orderId, productName = "Rayon", meter = 1.0, price = 100.0, totalPrice = 100.0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `pull replaces synced orders but keeps unsynced offline orders and their items`() = runBlocking<Unit> {
        val dao = db.orderDao()
        dao.insertFullOrder(order("offline", synced = false), listOf(item("offline-i", "offline")))
        dao.insertFullOrder(order("stale", synced = true), listOf(item("stale-i", "stale")))

        dao.syncReplaceOrders(listOf(order("remote", synced = true)), listOf(item("remote-i", "remote")))

        val all = dao.getAllOrdersWithItems().first().associateBy { it.order.id }
        assertThat(all.keys).containsExactly("offline", "remote")
        assertThat(all.getValue("offline").order.isSynced).isFalse()
        assertThat(all.getValue("offline").items.map { it.id }).containsExactly("offline-i")
        assertThat(dao.getUnsyncedOrders().map { it.order.id }).containsExactly("offline")
    }

    @Test
    fun `empty pull keeps unsynced orders`() = runBlocking<Unit> {
        val dao = db.orderDao()
        dao.insertFullOrder(order("offline", synced = false), listOf(item("offline-i", "offline")))

        dao.syncReplaceOrders(emptyList(), emptyList())

        assertThat(dao.getUnsyncedOrders().map { it.order.id }).containsExactly("offline")
    }

    @Test
    fun `remote copy wins when server already has the same order id`() = runBlocking<Unit> {
        // Push reached the server but markOrderAsSynced had not run yet; server row is authoritative.
        val dao = db.orderDao()
        dao.insertFullOrder(order("o1", synced = false, amount = 100.0), listOf(item("o1-i", "o1")))

        dao.syncReplaceOrders(listOf(order("o1", synced = true, amount = 120.0)), listOf(item("o1-i", "o1")))

        val o1 = dao.getOrderWithItemsById("o1")!!
        assertThat(o1.order.isSynced).isTrue()
        assertThat(o1.order.totalAmount).isEqualTo(120.0)
        assertThat(dao.getUnsyncedOrders()).isEmpty()
    }
}
