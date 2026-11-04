package com.shreenathji.sales.ui.viewmodels

import android.content.Context
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.data.repository.SalesRepository
import com.shreenathji.sales.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: SalesRepository = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val sampleStaff = StaffMember(
        id = "staff_1",
        name = "Rajesh Gupta",
        role = "Field Sales Rep",
        pinCode = "1234",
        orderPrefix = "RG"
    )

    private val sampleParty = PartyEntity(
        id = "party_1",
        businessName = "Surat Textiles Ltd",
        contactPerson = "Mahesh Bhai",
        phone = "9876543210",
        addressLine1 = "Ring Road",
        addressLine2 = "Surat",
        defaultAgent = "Kailash Agency"
    )

    private val sampleProduct = ProductEntity(
        id = "prod_1",
        name = "Heavy Satin 60",
        category = "Satin",
        defaultRate = 85.0
    )

    @Before
    fun setUp() {
        every { authManager.getCurrentStaff() } returns sampleStaff
        every { repository.allParties } returns flowOf(listOf(sampleParty))
        every { repository.allProducts } returns flowOf(listOf(sampleProduct))
    }

    @Test
    fun `initial state has one empty item draft and empty fields`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)

        assertEquals(1, viewModel.items.value.size)
        assertEquals(1, viewModel.items.value[0].colors.size)
        assertEquals("", viewModel.partyName.value)
        assertEquals("", viewModel.partyAddress.value)
        assertEquals("", viewModel.agent.value)
        assertFalse(viewModel.isSubmitting.value)
    }

    @Test
    fun `initialPartyId loads party and pre-fills party fields`() = runTest {
        coEvery { repository.getPartyById("party_1") } returns sampleParty

        val viewModel = OrderFormViewModel(repository, authManager, initialPartyId = "party_1")

        assertEquals("Surat Textiles Ltd", viewModel.partyName.value)
        assertEquals("Ring Road, Surat", viewModel.partyAddress.value)
        assertEquals("Kailash Agency", viewModel.agent.value)
        assertEquals(sampleParty, viewModel.selectedParty.value)
    }

    @Test
    fun `selectParty updates party fields correctly`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)

        viewModel.selectParty(sampleParty)

        assertEquals("Surat Textiles Ltd", viewModel.partyName.value)
        assertEquals("Ring Road, Surat", viewModel.partyAddress.value)
        assertEquals("Kailash Agency", viewModel.agent.value)
        assertEquals(sampleParty, viewModel.selectedParty.value)
    }

    @Test
    fun `addItem and removeItem update items list properly`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)

        viewModel.addItem()
        assertEquals(2, viewModel.items.value.size)

        viewModel.removeItem(0)
        assertEquals(1, viewModel.items.value.size)

        viewModel.removeItem(0)
        assertEquals(1, viewModel.items.value.size)
    }

    @Test
    fun `selectProductForItem updates item name and default rate`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)

        viewModel.selectProductForItem(0, sampleProduct)

        val firstItem = viewModel.items.value[0]
        assertEquals("Heavy Satin 60", firstItem.name)
        assertEquals("85.0", firstItem.priceText)
        assertEquals(85.0, firstItem.price, 0.0001)
    }

    @Test
    fun `updating meters per color applies to non-overridden slots`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)

        viewModel.updateItemName(0, "Pure Linen")
        viewModel.updateItemPrice(0, "150.50")
        viewModel.setColorCount(0, "3")
        viewModel.updateMetersPerColor(0, "250")
        viewModel.updateColorMeter(0, 1, "280")
        viewModel.updateMetersPerColor(0, "260")
        viewModel.updateColorLabel(0, 0, "Navy")

        val item = viewModel.items.value[0]
        assertEquals("Pure Linen", item.name)
        assertEquals("260", item.metersPerColorText)
        assertEquals(3, item.colors.size)
        assertEquals("260", item.colors[0].meterText)
        assertEquals("280", item.colors[1].meterText)
        assertTrue(item.colors[1].meterOverridden)
        assertEquals("260", item.colors[2].meterText)
        assertEquals("Navy", item.colors[0].label)
        assertEquals(800.0, item.totalMeters, 0.0001)
        assertEquals(800.0 * 150.50, item.totalPrice, 0.0001)
    }

    @Test
    fun `setColorCount ignores empty input for backspace UX`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)
        viewModel.setColorCount(0, "4")
        assertEquals(4, viewModel.items.value[0].colors.size)

        viewModel.setColorCount(0, "")
        assertEquals(4, viewModel.items.value[0].colors.size)

        viewModel.setColorCount(0, "2")
        assertEquals(2, viewModel.items.value[0].colors.size)
    }

    @Test
    fun `submitOrder without party name emits error message`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)
        var capturedError: String? = null

        val job = launch(UnconfinedTestDispatcher()) {
            capturedError = viewModel.errorMessage.first()
        }

        viewModel.submitOrder(context)

        assertEquals("Enter or select a store name", capturedError)
        job.cancel()
    }

    @Test
    fun `submitOrder without valid items emits error message`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)
        viewModel.onPartyNameChanged("Test Party")
        var capturedError: String? = null

        val job = launch(UnconfinedTestDispatcher()) {
            capturedError = viewModel.errorMessage.first()
        }

        viewModel.submitOrder(context)

        assertEquals(
            "Add fabric name, rate, and meters for at least one color",
            capturedError
        )
        job.cancel()
    }

    @Test
    fun `submitOrder with unsynced party emits error message`() = runTest {
        val unsyncedParty = sampleParty.copy(isSynced = false)
        val viewModel = OrderFormViewModel(repository, authManager)
        viewModel.selectParty(unsyncedParty)
        viewModel.updateItemName(0, "Cotton")
        viewModel.updateItemPrice(0, "80")
        viewModel.updateMetersPerColor(0, "50")
        var capturedError: String? = null

        val job = launch(UnconfinedTestDispatcher()) {
            capturedError = viewModel.errorMessage.first()
        }

        viewModel.submitOrder(context)

        assertEquals(
            "This store is not online yet. Wait a moment, then try again.",
            capturedError
        )
        job.cancel()
    }

    @Test
    fun `canSubmit is true only when store and valid fabric lines are set`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)
        assertFalse(viewModel.canSubmit.value)
        assertEquals("Enter or select a store name", viewModel.submitBlockReason.value?.message)

        viewModel.selectParty(sampleParty)
        assertFalse(viewModel.canSubmit.value)
        assertEquals(
            "Add fabric name, rate, and meters for at least one color",
            viewModel.submitBlockReason.value?.message
        )

        viewModel.updateItemName(0, "Cotton")
        viewModel.updateItemPrice(0, "80")
        viewModel.updateMetersPerColor(0, "50")
        assertTrue(viewModel.canSubmit.value)
        assertNull(viewModel.submitBlockReason.value)
    }

    @Test
    fun `flattenOrderItemDrafts expands colors into rows with labels in notes`() {
        val drafts = listOf(
            OrderItemDraft(
                name = "Reva",
                priceText = "85",
                metersPerColorText = "250",
                colors = listOf(
                    ColorSlotDraft(label = "Navy", meterText = "250"),
                    ColorSlotDraft(label = "", meterText = "250"),
                    ColorSlotDraft(label = "Olive", meterText = "280", meterOverridden = true)
                )
            )
        )

        val saved = flattenOrderItemDrafts(drafts)
        assertEquals(3, saved.size)
        assertTrue(saved.all { it.productName == "Reva" && it.price == 85.0 })
        assertEquals("Navy", saved[0].notes)
        assertEquals(250.0, saved[0].meter, 0.0001)
        assertNull(saved[1].notes)
        assertEquals(250.0, saved[1].meter, 0.0001)
        assertEquals("Olive", saved[2].notes)
        assertEquals(280.0, saved[2].meter, 0.0001)
        assertEquals(280.0 * 85.0, saved[2].totalPrice, 0.0001)
    }

    @Test
    fun `resetForm resets all fields to default blank state`() = runTest {
        val viewModel = OrderFormViewModel(repository, authManager)
        viewModel.selectParty(sampleParty)
        viewModel.onPoNumberChanged("PO-999")
        viewModel.onOrderNotesChanged("Deliver immediately")
        viewModel.addItem()

        viewModel.resetForm()

        assertNull(viewModel.selectedParty.value)
        assertEquals("", viewModel.partyName.value)
        assertEquals("", viewModel.partyAddress.value)
        assertEquals("", viewModel.poNumber.value)
        assertEquals("", viewModel.orderNotes.value)
        assertEquals(1, viewModel.items.value.size)
        assertEquals(1, viewModel.items.value[0].colors.size)
    }
}
