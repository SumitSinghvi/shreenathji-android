package com.shreenathji.sales.ui.viewmodels

import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.repository.SalesRepository
import com.shreenathji.sales.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: SalesRepository = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)

    private val fieldRep = StaffMember(
        id = "rep_101",
        name = "Kunal Verma",
        role = "Field Sales Rep",
        pinCode = "4321"
    )

    private val adminStaff = StaffMember(
        id = "staff_admin",
        name = "Master Admin",
        role = "System Administrator",
        pinCode = "9999"
    )

    private val sampleParties = listOf(
        PartyEntity(id = "p1", businessName = "Shree Textiles"),
        PartyEntity(id = "p2", businessName = "Krishna Silk Mills")
    )

    private val sampleVisits = listOf(
        DailyVisitPlanEntity(
            id = "v1",
            partyId = "p1",
            partyName = "Shree Textiles",
            planDate = "2026-09-01",
            salesRepId = "rep_101",
            salesRepName = "Kunal Verma",
            status = "PENDING"
        )
    )

    @Before
    fun setUp() {
        every { repository.searchParties(any()) } returns flowOf(sampleParties)
        every { repository.getTodayVisitPlans(any()) } returns flowOf(sampleVisits)
    }

    @Test
    fun `field sales rep defaults to ALLOTTED store filter mode and non-admin`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)

        assertFalse(viewModel.isStaffAdmin)
        assertEquals("ALLOTTED", viewModel.storeFilterMode.value)
    }

    @Test
    fun `admin staff defaults to ALL store filter mode and isStaffAdmin is true`() = runTest {
        every { authManager.getCurrentStaff() } returns adminStaff

        val viewModel = PartyListViewModel(repository, authManager)

        assertTrue(viewModel.isStaffAdmin)
        assertEquals("ALL", viewModel.storeFilterMode.value)
    }

    @Test
    fun `search query update modifies state`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)

        viewModel.onSearchQueryChanged("Krishna")
        assertEquals("Krishna", viewModel.searchQuery.value)
    }

    @Test
    fun `switching tab updates selectedTab and triggers cloud sync`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)

        viewModel.setSelectedTab(1)
        assertEquals(1, viewModel.selectedTab.value)
        verify { repository.syncWithCloud() }
    }

    @Test
    fun `setStoreFilterMode modifies filter mode state`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)

        viewModel.setStoreFilterMode("ALL")
        assertEquals("ALL", viewModel.storeFilterMode.value)
    }

    @Test
    fun `date navigation modifies selectedDate and updates isViewingToday`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)
        assertTrue(viewModel.isViewingToday.value)

        viewModel.selectDate("2026-08-20")
        assertEquals("2026-08-20", viewModel.selectedDate.value)
        assertFalse(viewModel.isViewingToday.value)

        viewModel.selectToday()
        assertTrue(viewModel.isViewingToday.value)
    }

    @Test
    fun `selectPreviousDay and selectNextDay change date by one day`() = runTest {
        every { authManager.getCurrentStaff() } returns fieldRep

        val viewModel = PartyListViewModel(repository, authManager)
        viewModel.selectDate("2026-08-15")

        viewModel.selectPreviousDay()
        assertEquals("2026-08-14", viewModel.selectedDate.value)

        viewModel.selectNextDay()
        assertEquals("2026-08-15", viewModel.selectedDate.value)
    }
}
