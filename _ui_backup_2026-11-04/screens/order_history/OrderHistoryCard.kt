package com.shreenathji.sales.ui.screens.order_history

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.PartyAvatar
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.util.OrderChallanPrintHelper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderHistoryCard(
    orderWithItems: OrderWithItems,
    currencyFormatter: NumberFormat,
    onImageClick: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val order = orderWithItems.order
    val items = orderWithItems.items
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val totalMeters = remember(items) { items.sumOf { it.meter } }

    // Parse image URIs if available
    val imageList: List<String> = remember(order.imageUrisJson) {
        if (!order.imageUrisJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(order.imageUrisJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 1.5.dp,
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Order Number & Status Badges
            val (statusText, statusVariant) = when (order.orderStatus.uppercase().trim()) {
                "CONFIRMED" -> "Confirmed" to BadgeVariant.INFO
                "DISPATCHED" -> "Dispatched" to BadgeVariant.PURPLE
                "DELIVERED" -> "Delivered" to BadgeVariant.SUCCESS
                "REJECTED" -> "Rejected" to BadgeVariant.DESTRUCTIVE
                "CANCELLED" -> "Cancelled" to BadgeVariant.SECONDARY
                else -> "Pending Approval" to BadgeVariant.WARNING
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate950
                    )
                    if (imageList.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ShadcnBadge(
                            text = "📷 ${imageList.size}",
                            variant = BadgeVariant.SECONDARY
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { OrderChallanPrintHelper.printChallan(context, orderWithItems) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Challan",
                            tint = Slate600,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    ShadcnBadge(
                        text = statusText,
                        variant = statusVariant
                    )

                    if (!order.isSynced) {
                        ShadcnBadge(
                            text = "Offline",
                            variant = BadgeVariant.WARNING
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Party Info Row with Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PartyAvatar(
                    name = order.partyName,
                    size = 38.dp,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.partyName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate950
                    )
                    order.partyAddress?.let { addr ->
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = addr,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            maxLines = 1
                        )
                    }
                }
            }

            // Rejection Alert Banner if Rejected by Office
            if (order.orderStatus.equals("REJECTED", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoseDestructiveBg,
                    border = BorderStroke(1.dp, RoseDestructiveBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "✕ Order Rejected by Office",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseDestructiveText
                        )
                        if (!order.rejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Reason: ${order.rejectionReason}",
                                fontSize = 11.sp,
                                color = RoseDestructiveText
                            )
                        }
                    }
                }
            }

            // Date & Time
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dateFormatter.format(Date(order.orderTimestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = Slate200)
            Spacer(modifier = Modifier.height(10.dp))

            // Summary Row: Meters Count & Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${items.size} item(s) • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${totalMeters} Mtrs",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOrangeDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currencyFormatter.format(order.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate950
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Items & Details Breakdown
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Slate50, RoundedCornerShape(12.dp))
                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    // Optional Header Fields
                    if (!order.agent.isNullOrBlank() || !order.poNumber.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!order.agent.isNullOrBlank()) {
                                Text(
                                    text = "Broker: ${order.agent}",
                                    fontSize = 12.sp,
                                    color = Slate700,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (!order.poNumber.isNullOrBlank()) {
                                Text(
                                    text = "PO: ${order.poNumber}",
                                    fontSize = 12.sp,
                                    color = Slate700,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (!order.deliveryAddress.isNullOrBlank()) {
                        Text(
                            text = "Delivery: ${order.deliveryAddress}",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (!order.agent.isNullOrBlank() || !order.poNumber.isNullOrBlank() || !order.deliveryAddress.isNullOrBlank()) {
                        HorizontalDivider(thickness = 1.dp, color = Slate200)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Line Items
                    items.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.productName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate900
                                        )
                                        if (item.isMatching || item.multiplier > 1) {
                                            val m = if (item.multiplier > 1) item.multiplier else 2
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (m == 4) Color(0xFFF3E8FF) else AmberWarningBg,
                                                border = BorderStroke(1.dp, if (m == 4) Color(0xFFC084FC) else AmberWarningText.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = if (m == 4) "4-Matching" else "2-Matching",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (m == 4) Color(0xFF7E22CE) else AmberWarningText,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    val isMatch = item.isMatching || item.multiplier > 1
                                    val m = if (item.multiplier > 1) item.multiplier else 2
                                    val cut = item.cutMeter ?: if (isMatch) (item.meter / m) else null
                                    val meterDetail = if (isMatch && cut != null) {
                                        val cutVal: Double = cut
                                        val cutStr = if (cutVal % 1.0 == 0.0) cutVal.toLong().toString() else cutVal.toString()
                                        "Cut: ${cutStr}m × $m • ${item.meter} Mtr × ₹${item.price}"
                                    } else {
                                        "${item.meter} Mtr × ₹${item.price}"
                                    }
                                    Text(
                                        text = meterDetail,
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                                Text(
                                    text = currencyFormatter.format(item.totalPrice),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate950
                                )
                            }
                            if (!item.notes.isNullOrBlank()) {
                                Text(
                                    text = "↳ Note: ${item.notes}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate600,
                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                                )
                            }
                        }
                    }

                    // Attached Images Preview (Tap to open full-screen viewer)
                    if (imageList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 1.dp, color = Slate200)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Attached Photos (${imageList.size}) • Tap to zoom",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(imageList) { _, uriString ->
                                val uri = Uri.parse(uriString)
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Order photo",
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(uri) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    order.notes?.let { notes ->
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 1.dp, color = Slate200)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Order Notes: $notes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                    }

                    // Action Buttons: Print Challan & Share
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 1.dp, color = Slate200)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandOrange,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    OrderChallanPrintHelper.printChallan(context, orderWithItems)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Print Challan",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Print Challan (A5)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    OrderChallanPrintHelper.shareChallanPdf(context, orderWithItems)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share PDF",
                                    tint = Slate700,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Share PDF",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
