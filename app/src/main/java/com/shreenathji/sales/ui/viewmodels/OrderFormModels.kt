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
