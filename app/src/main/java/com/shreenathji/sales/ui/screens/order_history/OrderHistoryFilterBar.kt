package com.shreenathji.sales.ui.screens.order_history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.ui.theme.*

@Composable
fun OrderHistoryFilterBar(
    orders: List<OrderWithItems>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingCount = orders.count { it.order.orderStatus.equals("PENDING", ignoreCase = true) }
    val confirmedCount = orders.count { it.order.orderStatus.equals("CONFIRMED", ignoreCase = true) }
    val dispatchedCount = orders.count { it.order.orderStatus.equals("DISPATCHED", ignoreCase = true) }
    val deliveredCount = orders.count { it.order.orderStatus.equals("DELIVERED", ignoreCase = true) }
    val rejectedCount = orders.count { it.order.orderStatus.equals("REJECTED", ignoreCase = true) }
    val offlineCount = orders.count { !it.order.isSynced }

    val filterOptions = mutableListOf(
        "ALL" to "All (${orders.size})",
        "PENDING" to "Pending ($pendingCount)",
        "CONFIRMED" to "Confirmed ($confirmedCount)"
    )

    if (dispatchedCount > 0) {
        filterOptions.add("DISPATCHED" to "Dispatched ($dispatchedCount)")
    }
    if (deliveredCount > 0) {
        filterOptions.add("DELIVERED" to "Delivered ($deliveredCount)")
    }
    if (rejectedCount > 0) {
        filterOptions.add("REJECTED" to "Rejected ($rejectedCount)")
    }
    if (offlineCount > 0) {
        filterOptions.add("OFFLINE" to "Offline Cache ($offlineCount)")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterOptions.forEach { (filterKey, label) ->
            val isSelected = selectedFilter == filterKey
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) BrandOrange else ShadcnWhite,
                border = BorderStroke(1.dp, if (isSelected) BrandOrange else Slate200),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onFilterSelected(filterKey) }
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Slate700,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
