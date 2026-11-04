package com.shreenathji.sales.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenathji.sales.BuildConfig
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnHeader
import com.shreenathji.sales.ui.screens.order_history.OrderHistoryCard
import com.shreenathji.sales.ui.screens.order_history.OrderHistoryEmptyState
import com.shreenathji.sales.ui.screens.order_history.OrderHistoryFilterBar
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.OrderHistoryViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OrderHistoryScreen(
    viewModel: OrderHistoryViewModel,
    onLogout: () -> Unit
) {
    val orders by viewModel.orders.collectAsState()
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val staff = viewModel.currentStaff

    var viewingImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, CONFIRMED, DISPATCHED, DELIVERED, REJECTED, OFFLINE, SYNCED

    val filteredOrders = remember(orders, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> orders.filter { it.order.orderStatus.equals("PENDING", ignoreCase = true) }
            "CONFIRMED" -> orders.filter { it.order.orderStatus.equals("CONFIRMED", ignoreCase = true) }
            "DISPATCHED" -> orders.filter { it.order.orderStatus.equals("DISPATCHED", ignoreCase = true) }
            "DELIVERED" -> orders.filter { it.order.orderStatus.equals("DELIVERED", ignoreCase = true) }
            "REJECTED" -> orders.filter { it.order.orderStatus.equals("REJECTED", ignoreCase = true) }
            "SYNCED" -> orders.filter { it.order.isSynced }
            "OFFLINE" -> orders.filter { !it.order.isSynced }
            else -> orders
        }
    }

    Scaffold(
        topBar = {
            ShadcnHeader(
                title = "Order Bookings",
                subtitle = if (staff != null) "${staff.name} (${staff.prefix}) • ${orders.size} orders • v${BuildConfig.VERSION_NAME}" else "${orders.size} orders • v${BuildConfig.VERSION_NAME}",
                actions = {
                    ShadcnBadge(
                        text = "v${BuildConfig.VERSION_NAME}",
                        variant = BadgeVariant.SECONDARY,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = { viewModel.refreshOrders() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Refresh Orders",
                            tint = Slate600
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Slate500
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate50)
        ) {
            // Filter Pills Row
            OrderHistoryFilterBar(
                orders = orders,
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            if (filteredOrders.isEmpty()) {
                OrderHistoryEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders, key = { it.order.id }) { orderWithItems ->
                        OrderHistoryCard(
                            orderWithItems = orderWithItems,
                            currencyFormatter = currencyFormatter,
                            onImageClick = { uri -> viewingImageUri = uri }
                        )
                    }
                }
            }
        }
    }

    viewingImageUri?.let { uri ->
        ImageViewerDialog(
            imageUri = uri,
            onDismiss = { viewingImageUri = null }
        )
    }
}

// Re-export ShadcnOrderCard for backward compatibility if referenced elsewhere
@Composable
fun ShadcnOrderCard(
    orderWithItems: OrderWithItems,
    currencyFormatter: NumberFormat,
    onImageClick: (Uri) -> Unit
) {
    OrderHistoryCard(
        orderWithItems = orderWithItems,
        currencyFormatter = currencyFormatter,
        onImageClick = onImageClick
    )
}
