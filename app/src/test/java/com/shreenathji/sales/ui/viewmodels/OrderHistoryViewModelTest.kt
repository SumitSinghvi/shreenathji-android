package com.shreenathji.sales.ui.viewmodels

import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.data.repository.SalesRepository
import com.shreenathji.sales.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: SalesRepository = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)

    private val sampleStaff = StaffMember(
        id = "staff_42",
        name = "Vikram Patel",
        role = "Field Sales Rep",
        pinCode = "5555"
    )

    private val sampleOrderWithItems = listOf(
        OrderWithItems(
            order = OrderEntity(
                id = "order_1",
                orderNumber = "VP-1001",
                partyId = "p1",
                partyName = "Balaji Cottons",
                salesRepId = "staff_42",
                salesRepName = "Vikram Patel",
                totalAmount = 25000.0,
                orderStatus = "PENDING",
                isSynced = false
            ),
            items = emptyList()
        )
    )

    @Before
    fun setUp() {
        every { authManager.getCurrentStaff() } returns sampleStaff
        every { repository.getOrdersByRep("staff_42") } returns flowOf(sampleOrderWithItems)
    }

    @Test
    fun `initialization triggers cloud sync and queries orders by current staff ID`() = runTest {
        val viewModel = OrderHistoryViewModel(repository, authManager)

        verify { repository.syncWithCloud() }
        verify { repository.getOrdersByRep("staff_42") }
        assertEquals(sampleStaff, viewModel.currentStaff)
    }

    @Test
    fun `refreshOrders triggers cloud sync`() = runTest {
        val viewModel = OrderHistoryViewModel(repository, authManager)

        viewModel.refreshOrders()

        verify(atLeast = 2) { repository.syncWithCloud() }
    }

    @Test
    fun `logout delegates to AuthManager`() = runTest {
        val viewModel = OrderHistoryViewModel(repository, authManager)

        viewModel.logout()

        verify { authManager.logout() }
    }
}
