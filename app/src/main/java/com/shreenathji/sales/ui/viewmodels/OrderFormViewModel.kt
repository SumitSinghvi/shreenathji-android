package com.shreenathji.sales.ui.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.data.repository.SalesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Why Save order is disabled, or null when the form can be submitted. */
data class OrderSubmitBlockReason(
    val message: String,
    val isWarning: Boolean = false
)

/** Snapshot shown on the post-submit success dialog (form is reset before the dialog). */
data class OrderSavedSummary(
    val orderId: String,
    val partyName: String,
    val totalMeters: Double,
    val rates: List<Double>
)

data class ColorSlotDraft(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val meterText: String = "",
    val bottomMeterText: String = "",
    val meterOverridden: Boolean = false,
    val bottomMeterOverridden: Boolean = false,
    /** design | file — for Print qualities */
    val attachmentKind: String? = null,
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val attachmentDesignId: String? = null
) {
    val meter: Double get() = (meterText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val bottomMeter: Double get() = (bottomMeterText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val hasAttachment: Boolean get() = !attachmentUri.isNullOrBlank()
}

fun isPrintCategory(category: String?): Boolean = category == "Print"

data class OrderItemDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val priceText: String = "",
    val bottomPriceText: String = "",
    val metersPerColorText: String = "",
    val bottomMetersPerColorText: String = "",
    /** Dying | Print — copied from product; custom typed qualities treated as Dying. */
    val category: String = "Dying",
    val isMatching: Boolean = false,
    val multiplier: Int = 1,
    val isTopBottom: Boolean = false,
    val colors: List<ColorSlotDraft> = listOf(ColorSlotDraft())
) {
    val price: Double get() = priceText.toDoubleOrNull() ?: 0.0
    val bottomPrice: Double get() = bottomPriceText.toDoubleOrNull() ?: 0.0
    val metersPerColor: Double get() = metersPerColorText.toDoubleOrNull() ?: 0.0
    val bottomMetersPerColor: Double get() = bottomMetersPerColorText.toDoubleOrNull() ?: 0.0
    val baseMeters: Double get() = if (isTopBottom) {
        colors.sumOf { it.meter + it.bottomMeter }
    } else {
        colors.sumOf { it.meter }
    }
    val baseTopMeters: Double get() = colors.sumOf { it.meter }
    val baseBottomMeters: Double get() = colors.sumOf { it.bottomMeter }
    val effectiveMultiplier: Int get() = if (multiplier > 1) multiplier else if (isMatching) 2 else 1
    val totalMeters: Double get() = baseMeters * effectiveMultiplier
    val totalPrice: Double get() = if (isTopBottom) {
        (baseTopMeters * effectiveMultiplier * price) + (baseBottomMeters * effectiveMultiplier * bottomPrice)
    } else {
        totalMeters * price
    }
    val isPrint: Boolean get() = isPrintCategory(category)
}

data class PendingColorAttachTarget(val itemIndex: Int, val colorIndex: Int)

/** Expand quality drafts into one persisted order item per color slot. */
fun flattenOrderItemDrafts(items: List<OrderItemDraft>): List<OrderItemEntity> {
    return items
        .filter { draft ->
            if (draft.isTopBottom) {
                draft.name.isNotBlank() && draft.price > 0 && draft.bottomPrice > 0 &&
                        draft.colors.any { c -> c.meter > 0 || c.bottomMeter > 0 }
            } else {
                draft.name.isNotBlank() && draft.price > 0 && draft.colors.any { c -> c.meter > 0 }
            }
        }
        .flatMap { draft ->
            val print = draft.isPrint
            val mult = draft.effectiveMultiplier
            if (draft.isTopBottom) {
                draft.colors.filter { it.meter > 0 || it.bottomMeter > 0 }.flatMap { slot ->
                    val notes = when {
                        print && slot.hasAttachment -> {
                            if (slot.attachmentKind == "design") {
                                slot.attachmentName?.ifBlank { null } ?: "Design"
                            } else {
                                "Photo"
                            }
                        }
                        !print -> slot.label.trim().ifBlank { null }
                        else -> null
                    }
                    val setGroupId = UUID.randomUUID().toString()
                    val pairList = mutableListOf<OrderItemEntity>()
                    if (slot.meter > 0) {
                        val cutTop = slot.meter
                        val totalTop = cutTop * mult
                        pairList.add(
                            OrderItemEntity(
                                id = UUID.randomUUID().toString(),
                                orderId = "",
                                productName = "${draft.name.trim()} (Top)",
                                meter = totalTop,
                                price = draft.price,
                                totalPrice = totalTop * draft.price,
                                notes = notes,
                                designId = if (print) slot.attachmentDesignId else null,
                                imageUrl = if (print) slot.attachmentUri else null,
                                isMatching = mult > 1,
                                multiplier = mult,
                                cutMeter = cutTop,
                                component = "TOP",
                                setGroupId = setGroupId
                            )
                        )
                    }
                    if (slot.bottomMeter > 0) {
                        val cutBottom = slot.bottomMeter
                        val totalBottom = cutBottom * mult
                        pairList.add(
                            OrderItemEntity(
                                id = UUID.randomUUID().toString(),
                                orderId = "",
                                productName = "${draft.name.trim()} (Bottom)",
                                meter = totalBottom,
                                price = draft.bottomPrice,
                                totalPrice = totalBottom * draft.bottomPrice,
                                notes = notes,
                                designId = if (print) slot.attachmentDesignId else null,
                                imageUrl = if (print) slot.attachmentUri else null,
                                isMatching = mult > 1,
                                multiplier = mult,
                                cutMeter = cutBottom,
                                component = "BOTTOM",
                                setGroupId = setGroupId
                            )
                        )
                    }
                    pairList
                }
            } else {
                draft.colors.filter { it.meter > 0 }.map { slot ->
                    val notes = when {
                        print && slot.hasAttachment -> {
                            if (slot.attachmentKind == "design") {
                                slot.attachmentName?.ifBlank { null } ?: "Design"
                            } else {
                                "Photo"
                            }
                        }
                        !print -> slot.label.trim().ifBlank { null }
                        else -> null
                    }
                    val cutMeter = slot.meter
                    val totalMeter = cutMeter * mult
                    OrderItemEntity(
                        id = UUID.randomUUID().toString(),
                        orderId = "",
                        productName = draft.name.trim(),
                        meter = totalMeter,
                        price = draft.price,
                        totalPrice = totalMeter * draft.price,
                        notes = notes,
                        designId = if (print) slot.attachmentDesignId else null,
                        imageUrl = if (print) slot.attachmentUri else null,
                        isMatching = mult > 1,
                        multiplier = mult,
                        cutMeter = cutMeter,
                        component = null,
                        setGroupId = null
                    )
                }
            }
        }
}

class OrderFormViewModel(
    private val repository: SalesRepository,
    private val authManager: AuthManager,
    initialPartyId: String? = null
) : ViewModel() {

    val currentStaff: StaffMember? = authManager.getCurrentStaff()

    val parties: StateFlow<List<PartyEntity>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedParty = MutableStateFlow<PartyEntity?>(null)
    val selectedParty: StateFlow<PartyEntity?> = _selectedParty.asStateFlow()

    private val _partyName = MutableStateFlow("")
    val partyName: StateFlow<String> = _partyName.asStateFlow()

    private val _partyAddress = MutableStateFlow("")
    val partyAddress: StateFlow<String> = _partyAddress.asStateFlow()

    private val _agent = MutableStateFlow("")
    val agent: StateFlow<String> = _agent.asStateFlow()

    private val _poNumber = MutableStateFlow("")
    val poNumber: StateFlow<String> = _poNumber.asStateFlow()

    private val _deliveryAddress = MutableStateFlow("")
    val deliveryAddress: StateFlow<String> = _deliveryAddress.asStateFlow()

    private val _imageUris = MutableStateFlow<List<Uri>>(emptyList())
    val imageUris: StateFlow<List<Uri>> = _imageUris.asStateFlow()

    private val _designIds = MutableStateFlow<List<String>>(emptyList())
    val designIds: StateFlow<List<String>> = _designIds.asStateFlow()

    private val _pendingColorAttach = MutableStateFlow<PendingColorAttachTarget?>(null)
    val pendingColorAttach: StateFlow<PendingColorAttachTarget?> = _pendingColorAttach.asStateFlow()

    private val _items = MutableStateFlow<List<OrderItemDraft>>(listOf(OrderItemDraft()))
    val items: StateFlow<List<OrderItemDraft>> = _items.asStateFlow()

    private val _orderNotes = MutableStateFlow("")
    val orderNotes: StateFlow<String> = _orderNotes.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _orderSubmittedEvent = MutableSharedFlow<OrderSavedSummary>()
    val orderSubmittedEvent: SharedFlow<OrderSavedSummary> = _orderSubmittedEvent.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    /**
     * Human-readable reason Save is blocked. Null when [canSubmit] is true.
     * Matches [submitOrder] / [flattenOrderItemDrafts] rules.
     */
    val submitBlockReason: StateFlow<OrderSubmitBlockReason?> = combine(
        _partyName,
        _selectedParty,
        _items
    ) { name, party, items ->
        when {
            name.trim().isBlank() -> OrderSubmitBlockReason("Enter or select a store name")
            party?.isSynced == false -> OrderSubmitBlockReason(
                message = "This store is not online yet. Wait a moment, then try again.",
                isWarning = true
            )
            flattenOrderItemDrafts(items).isEmpty() -> OrderSubmitBlockReason(
                "Add fabric name, rate, and meters for at least one color"
            )
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OrderSubmitBlockReason("Enter or select a store name"))

    val canSubmit: StateFlow<Boolean> = submitBlockReason
        .combine(_isSubmitting) { reason, submitting -> reason == null && !submitting }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        initialPartyId?.let { id ->
            viewModelScope.launch {
                val party = repository.getPartyById(id)
                party?.let { selectParty(it) }
            }
        }
        consumePendingDesigns()
    }

    fun consumePendingDesigns() {
        val pending = repository.consumePendingDesignAttachments()
        if (pending.isEmpty()) return
        val target = _pendingColorAttach.value
        if (target != null) {
            val first = pending.first()
            setColorDesignAttachment(
                target.itemIndex,
                target.colorIndex,
                first.designId,
                first.imageUrl,
                "Design"
            )
            // Also mirror extras to order-level if multiple selected
            if (pending.size > 1) {
                val rest = pending.drop(1)
                _imageUris.value = _imageUris.value + rest.map { Uri.parse(it.imageUrl) }
                _designIds.value = (_designIds.value + rest.map { it.designId }).distinct()
            }
            _pendingColorAttach.value = null
            return
        }
        val urls = pending.map { Uri.parse(it.imageUrl) }
        val ids = pending.map { it.designId }
        _imageUris.value = _imageUris.value + urls
        _designIds.value = (_designIds.value + ids).distinct()
    }

    fun beginColorDesignAttach(itemIndex: Int, colorIndex: Int) {
        _pendingColorAttach.value = PendingColorAttachTarget(itemIndex, colorIndex)
    }

    fun beginColorPhotoAttach(itemIndex: Int, colorIndex: Int) {
        _pendingColorAttach.value = PendingColorAttachTarget(itemIndex, colorIndex)
    }

    fun clearPendingColorAttach() {
        _pendingColorAttach.value = null
    }

    fun setColorDesignAttachment(
        itemIndex: Int,
        colorIndex: Int,
        designId: String,
        imageUrl: String,
        name: String
    ) {
        updateColorAt(itemIndex, colorIndex) {
            it.copy(
                attachmentKind = "design",
                attachmentDesignId = designId,
                attachmentUri = imageUrl,
                attachmentName = name,
                label = ""
            )
        }
        _designIds.value = (_designIds.value + designId).distinct()
        val uri = Uri.parse(imageUrl)
        if (_imageUris.value.none { it.toString() == imageUrl }) {
            _imageUris.value = _imageUris.value + uri
        }
    }

    fun setColorPhotoAttachment(itemIndex: Int, colorIndex: Int, uri: Uri) {
        updateColorAt(itemIndex, colorIndex) {
            it.copy(
                attachmentKind = "file",
                attachmentDesignId = null,
                attachmentUri = uri.toString(),
                attachmentName = "Photo",
                label = ""
            )
        }
        if (_imageUris.value.none { it.toString() == uri.toString() }) {
            _imageUris.value = _imageUris.value + uri
        }
        _pendingColorAttach.value = null
    }

    fun clearColorAttachment(itemIndex: Int, colorIndex: Int) {
        updateColorAt(itemIndex, colorIndex) {
            it.copy(
                attachmentKind = null,
                attachmentDesignId = null,
                attachmentUri = null,
                attachmentName = null
            )
        }
    }

    fun selectParty(party: PartyEntity) {
        _selectedParty.value = party
        _partyName.value = party.businessName
        _partyAddress.value = party.formattedAddress
        _agent.value = party.defaultAgent ?: ""
    }

    fun onPartyNameChanged(name: String) { _partyName.value = name }
    fun onPartyAddressChanged(addr: String) { _partyAddress.value = addr }
    fun onAgentChanged(ag: String) { _agent.value = ag }
    fun onPoNumberChanged(po: String) { _poNumber.value = po }
    fun onDeliveryAddressChanged(del: String) { _deliveryAddress.value = del }
    fun onOrderNotesChanged(notes: String) { _orderNotes.value = notes }

    fun addImageUris(newUris: List<Uri>) {
        _imageUris.value = _imageUris.value + newUris
    }

    fun addDesignAttachments(designIdToUrl: List<Pair<String, String>>) {
        if (designIdToUrl.isEmpty()) return
        val urls = designIdToUrl.map { Uri.parse(it.second) }
        val ids = designIdToUrl.map { it.first }
        _imageUris.value = _imageUris.value + urls
        _designIds.value = (_designIds.value + ids).distinct()
    }

    fun removeImageUri(index: Int) {
        val list = _imageUris.value.toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            _imageUris.value = list
            val removedUrl = removed.toString()
            // Best-effort: drop matching design id if URL came from design library
            // (ids stay if URL still present elsewhere)
            val stillHasUrl = list.any { it.toString() == removedUrl }
            if (!stillHasUrl) {
                // no-op on ids list size mismatch — keep ids as submitted set of attached designs
            }
        }
    }

    fun addItem() {
        _items.value = _items.value + OrderItemDraft()
    }

    fun removeItem(index: Int) {
        if (_items.value.size > 1) {
            _items.value = _items.value.toMutableList().also { it.removeAt(index) }
        }
    }

    fun selectProductForItem(index: Int, product: ProductEntity) {
        val category = if (product.category == "Print") "Print" else "Dying"
        val print = isPrintCategory(category)
        val mult = if (product.matchingMultiplier > 1) product.matchingMultiplier else if (product.isMatching) 2 else 1
        val isTopBottom = product.isTopBottom
        updateItemAt(index) { item ->
            val bp = if (isTopBottom) {
                (product.defaultRateBottom ?: product.defaultRate).toString()
            } else if (item.bottomPriceText.isNotBlank()) {
                item.bottomPriceText
            } else {
                product.defaultRate.toString()
            }
            item.copy(
                name = product.name,
                priceText = product.defaultRate.toString(),
                bottomPriceText = bp,
                category = category,
                isMatching = mult > 1,
                multiplier = mult,
                isTopBottom = isTopBottom,
                colors = item.colors.map { slot ->
                    if (print) {
                        slot.copy(label = "")
                    } else {
                        slot.copy(
                            attachmentKind = null,
                            attachmentDesignId = null,
                            attachmentUri = null,
                            attachmentName = null
                        )
                    }
                }
            )
        }
    }

    fun toggleMatching(index: Int) {
        updateItemAt(index) { item ->
            val curr = item.effectiveMultiplier
            val next = when (curr) {
                1 -> 2
                2 -> 4
                else -> 1
            }
            item.copy(
                isMatching = next > 1,
                multiplier = next
            )
        }
    }

    fun toggleTopBottom(index: Int) {
        updateItemAt(index) { item ->
            val next = !item.isTopBottom
            val bp = if (next && item.bottomPriceText.isBlank()) item.priceText else item.bottomPriceText
            val bm = if (next && item.bottomMetersPerColorText.isBlank()) item.metersPerColorText else item.bottomMetersPerColorText
            val colors = item.colors.map { slot ->
                if (next && slot.bottomMeterText.isBlank() && !slot.bottomMeterOverridden) {
                    slot.copy(bottomMeterText = slot.meterText)
                } else slot
            }
            item.copy(
                isTopBottom = next,
                bottomPriceText = bp,
                bottomMetersPerColorText = bm,
                colors = colors
            )
        }
    }

    fun updateItemName(index: Int, name: String) {
        val matched = products.value.find {
            it.name.equals(name.trim(), ignoreCase = true)
        }
        if (matched != null) {
            val category = if (matched.category == "Print") "Print" else "Dying"
            val print = isPrintCategory(category)
            val mult = if (matched.matchingMultiplier > 1) matched.matchingMultiplier else if (matched.isMatching) 2 else 1
            val isTopBottom = matched.isTopBottom
            val bp = if (isTopBottom) {
                (matched.defaultRateBottom ?: matched.defaultRate).toString()
            } else ""
            updateItemAt(index) { item ->
                item.copy(
                    name = name,
                    priceText = if (item.priceText.isBlank()) matched.defaultRate.toString() else item.priceText,
                    bottomPriceText = if (bp.isNotBlank()) bp else if (item.bottomPriceText.isNotBlank()) item.bottomPriceText else item.priceText,
                    category = category,
                    isMatching = mult > 1,
                    multiplier = mult,
                    isTopBottom = isTopBottom,
                    colors = item.colors.map { slot ->
                        if (print) {
                            slot.copy(label = "")
                        } else {
                            slot.copy(
                                attachmentKind = null,
                                attachmentDesignId = null,
                                attachmentUri = null,
                                attachmentName = null
                            )
                        }
                    }
                )
            }
            return
        }
        updateItemAt(index) { item ->
            item.copy(
                name = name,
                category = "Dying",
                colors = item.colors.map { slot ->
                    slot.copy(
                        attachmentKind = null,
                        attachmentDesignId = null,
                        attachmentUri = null,
                        attachmentName = null
                    )
                }
            )
        }
    }

    fun updateItemPrice(index: Int, price: String) {
        updateItemAt(index) { it.copy(priceText = price) }
    }

    fun updateItemBottomPrice(index: Int, price: String) {
        updateItemAt(index) { it.copy(bottomPriceText = price) }
    }

    fun updateMetersPerColor(index: Int, value: String) {
        updateItemAt(index) { item ->
            val colors = item.colors.map { slot ->
                if (!slot.meterOverridden) {
                    slot.copy(meterText = value)
                } else {
                    slot
                }
            }
            item.copy(metersPerColorText = value, colors = colors)
        }
    }

    fun updateBottomMetersPerColor(index: Int, value: String) {
        updateItemAt(index) { item ->
            val colors = item.colors.map { slot ->
                if (!slot.bottomMeterOverridden) {
                    slot.copy(bottomMeterText = value)
                } else {
                    slot
                }
            }
            item.copy(bottomMetersPerColorText = value, colors = colors)
        }
    }

    /**
     * Resize color slots. Empty / invalid [countRaw] is ignored so the UI can allow backspace.
     */
    fun setColorCount(index: Int, countRaw: String) {
        if (countRaw.isBlank()) return
        val parsed = countRaw.toIntOrNull() ?: return
        if (parsed < 1) return
        val count = parsed.coerceAtMost(50)

        updateItemAt(index) { item ->
            if (count == item.colors.size) return@updateItemAt item
            val next = if (count > item.colors.size) {
                item.colors + List(count - item.colors.size) {
                    ColorSlotDraft(
                        meterText = item.metersPerColorText,
                        bottomMeterText = item.bottomMetersPerColorText
                    )
                }
            } else {
                item.colors.take(count)
            }
            item.copy(colors = next)
        }
    }

    fun addColor(index: Int) {
        updateItemAt(index) { item ->
            if (item.colors.size >= 50) return@updateItemAt item
            item.copy(
                colors = item.colors + ColorSlotDraft(
                    meterText = item.metersPerColorText,
                    bottomMeterText = item.bottomMetersPerColorText
                )
            )
        }
    }

    fun removeColor(itemIndex: Int, colorIndex: Int) {
        updateItemAt(itemIndex) { item ->
            if (item.colors.size <= 1) return@updateItemAt item
            item.copy(colors = item.colors.filterIndexed { i, _ -> i != colorIndex })
        }
    }

    fun updateColorLabel(itemIndex: Int, colorIndex: Int, label: String) {
        updateColorAt(itemIndex, colorIndex) { it.copy(label = label) }
    }

    fun updateColorMeter(itemIndex: Int, colorIndex: Int, meter: String) {
        updateColorAt(itemIndex, colorIndex) {
            it.copy(meterText = meter, meterOverridden = true)
        }
    }

    fun updateColorBottomMeter(itemIndex: Int, colorIndex: Int, meter: String) {
        updateColorAt(itemIndex, colorIndex) {
            it.copy(bottomMeterText = meter, bottomMeterOverridden = true)
        }
    }

    private fun updateItemAt(index: Int, transform: (OrderItemDraft) -> OrderItemDraft) {
        val current = _items.value.toMutableList()
        if (index in current.indices) {
            current[index] = transform(current[index])
            _items.value = current
        }
    }

    private fun updateColorAt(
        itemIndex: Int,
        colorIndex: Int,
        transform: (ColorSlotDraft) -> ColorSlotDraft
    ) {
        updateItemAt(itemIndex) { item ->
            if (colorIndex !in item.colors.indices) return@updateItemAt item
            val colors = item.colors.toMutableList()
            colors[colorIndex] = transform(colors[colorIndex])
            item.copy(colors = colors)
        }
    }

    @SuppressLint("MissingPermission")
    fun submitOrder(context: Context) {
        if (_isSubmitting.value) return

        val party = _partyName.value.trim()
        if (party.isBlank()) {
            viewModelScope.launch { _errorMessage.emit("Enter or select a store name") }
            return
        }

        if (_selectedParty.value?.isSynced == false) {
            viewModelScope.launch {
                _errorMessage.emit("This store is not online yet. Wait a moment, then try again.")
            }
            return
        }

        val orderEntities = flattenOrderItemDrafts(_items.value)

        if (orderEntities.isEmpty()) {
            viewModelScope.launch {
                _errorMessage.emit("Add fabric name, rate, and meters for at least one color")
            }
            return
        }

        _isSubmitting.value = true

        viewModelScope.launch {
            try {
                var lat: Double? = null
                var lng: Double? = null
                try {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val cts = CancellationTokenSource()
                    val loc = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                    lat = loc?.latitude
                    lng = loc?.longitude
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val currentStaff = authManager.getCurrentStaff()
                val partyId = _selectedParty.value?.id ?: UUID.randomUUID().toString()

                // Mirror print-color attachments into order-level galleries
                val mirroredDesignIds = (
                    _designIds.value + orderEntities.mapNotNull { it.designId }
                ).distinct()
                val mirroredImageUris = (
                    _imageUris.value.map { it.toString() } + orderEntities.mapNotNull { it.imageUrl }
                ).distinct()

                val orderId = repository.createOrder(
                    partyId = partyId,
                    partyName = party,
                    partyAddress = _partyAddress.value.ifBlank { null },
                    agent = _agent.value.ifBlank { null },
                    poNumber = _poNumber.value.ifBlank { null },
                    deliveryAddress = _deliveryAddress.value.ifBlank { null },
                    imageUris = mirroredImageUris,
                    designIds = mirroredDesignIds,
                    items = orderEntities,
                    notes = _orderNotes.value.ifBlank { null },
                    salesRepId = currentStaff?.id,
                    salesRepName = currentStaff?.name,
                    latitude = lat,
                    longitude = lng,
                    orderPrefix = currentStaff?.prefix
                )

                val summary = OrderSavedSummary(
                    orderId = orderId,
                    partyName = party,
                    totalMeters = orderEntities.sumOf { it.meter },
                    rates = orderEntities.map { it.price }.distinct()
                )
                resetForm()
                _orderSubmittedEvent.emit(summary)

            } catch (e: Exception) {
                _isSubmitting.value = false
                _errorMessage.emit(
                    "Could not save order. ${e.localizedMessage ?: "Please try again."}"
                )
            }
        }
    }

    fun resetForm() {
        _selectedParty.value = null
        _partyName.value = ""
        _partyAddress.value = ""
        _agent.value = ""
        _poNumber.value = ""
        _deliveryAddress.value = ""
        _imageUris.value = emptyList()
        _designIds.value = emptyList()
        _pendingColorAttach.value = null
        _items.value = listOf(OrderItemDraft())
        _orderNotes.value = ""
        _isSubmitting.value = false
    }
}

class OrderFormViewModelFactory(
    private val repository: SalesRepository,
    private val authManager: AuthManager,
    private val initialPartyId: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrderFormViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrderFormViewModel(repository, authManager, initialPartyId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
