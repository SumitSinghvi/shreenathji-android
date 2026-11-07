package com.shreenathji.sales.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.remote.api.OrderConflictException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Offline queue push: retries, order-number conflicts, one bad order not blocking the rest. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class OrderSyncTest {

    private val client = mockk<SupabaseClient>()
    private val dao = mockk<OrderDao>(relaxed = true)
    private lateinit var sync: OrderSync

    /** Orders by id, so the per-order re-read in OrderSync finds what the queue returned. */
    private val byId = mutableMapOf<String, OrderWithItems>()

    private fun order(id: String, number: String = "NS-123456") = OrderWithItems(
        order = OrderEntity(id = id, orderNumber = number, partyId = "p1", partyName = "Party", totalAmount = 500.0),
        items = listOf(OrderItemEntity(id = "$id-i1", orderId = id, productName = "Rayon", meter = 10.0, price = 50.0, totalPrice = 500.0))
    ).also { byId[id] = it }

    @Before
    fun setUp() {
        coEvery { client.ensureAuthenticatedSession() } returns true
        coEvery { dao.getOrderWithItemsById(any()) } answers { byId[firstArg()] }
        sync = OrderSync(ApplicationProvider.getApplicationContext(), client, dao, TestScope())
    }

    @Test
    fun `successful push marks order synced`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("o1"))
        coEvery { client.pushOrder(any(), any(), any()) } returns true

        sync.syncPendingOrders()

        coVerify(exactly = 1) { client.pushOrder(any(), any(), any()) }
        coVerify(exactly = 1) { dao.markOrderAsSynced("o1") }
    }

    @Test
    fun `transient failure is retried once then marked synced`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("o1"))
        coEvery { client.pushOrder(any(), any(), any()) } returnsMany listOf(false, true)

        sync.syncPendingOrders()

        coVerify(exactly = 2) { client.pushOrder(any(), any(), any()) }
        coVerify(exactly = 1) { dao.markOrderAsSynced("o1") }
    }

    @Test
    fun `persistent failure keeps order queued for next sync`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("o1"))
        coEvery { client.pushOrder(any(), any(), any()) } returns false

        sync.syncPendingOrders()

        coVerify(exactly = 0) { dao.markOrderAsSynced(any()) }
    }

    @Test
    fun `order number conflict regenerates number with same prefix and pushes again`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("o1", "NS-111111"))
        val pushed = mutableListOf<String>()
        coEvery { client.pushOrder(any(), any(), any()) } answers {
            val o = firstArg<OrderEntity>()
            pushed += o.orderNumber
            if (o.orderNumber == "NS-111111") throw OrderConflictException("dup") else true
        }
        val newNumber = slot<String>()
        coEvery { dao.updateOrderNumber("o1", capture(newNumber)) } returns Unit

        sync.syncPendingOrders()

        assertThat(newNumber.captured).matches("NS-\\d{6}")
        assertThat(newNumber.captured).isNotEqualTo("NS-111111")
        assertThat(pushed).containsExactly("NS-111111", newNumber.captured).inOrder()
        coVerify(exactly = 1) { dao.markOrderAsSynced("o1") }
    }

    @Test
    fun `failed order does not block later orders in the queue`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("bad"), order("good", "NS-222222"))
        coEvery { client.pushOrder(match { it.id == "bad" }, any(), any()) } returns false
        coEvery { client.pushOrder(match { it.id == "good" }, any(), any()) } returns true

        sync.syncPendingOrders()

        coVerify(exactly = 0) { dao.markOrderAsSynced("bad") }
        coVerify(exactly = 1) { dao.markOrderAsSynced("good") }
    }

    @Test
    fun `repeated conflict on one order does not abort the rest of the queue`() = runTest {
        coEvery { dao.getUnsyncedOrders() } returns listOf(order("bad"), order("good", "NS-222222"))
        coEvery { client.pushOrder(match { it.id == "bad" }, any(), any()) } throws OrderConflictException("dup")
        coEvery { client.pushOrder(match { it.id == "good" }, any(), any()) } returns true

        sync.syncPendingOrders()

        coVerify(exactly = 0) { dao.markOrderAsSynced("bad") }
        coVerify(exactly = 1) { dao.markOrderAsSynced("good") }
    }
}
