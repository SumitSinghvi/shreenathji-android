package com.shreenathji.sales.ui.screens.dispatch

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shreenathji.sales.data.remote.dto.DispatchOrderDto
import com.shreenathji.sales.data.remote.dto.DispatchRecordDto
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.components.SearchPickerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.DispatchDateFilter
import com.shreenathji.sales.ui.viewmodels.DispatchDateUtils
import com.shreenathji.sales.ui.viewmodels.DispatchFilters
import com.shreenathji.sales.ui.viewmodels.DispatchFormInput
import com.shreenathji.sales.ui.viewmodels.DispatchItemInput
import com.shreenathji.sales.ui.viewmodels.DispatchViewModel

@Composable
internal fun PartyToDispatchGroup(
    partyName: String,
    orders: List<DispatchOrderDto>,
    onOpenForm: (String) -> Unit,
    onPhotoClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val totalMeters = orders.sumOf { o -> o.order_items?.sumOf { it.meter } ?: 0.0 }
    val firstAddress = orders.firstOrNull { !it.party_address.isNullOrBlank() }?.party_address

    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            partyName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate950
                        )
                        if (orders.size > 1) {
                            ShadcnBadge(
                                "${orders.size} orders",
                                variant = BadgeVariant.INDIGO
                            )
                        }
                    }
                    firstAddress?.let {
                        Text(it, fontSize = 11.sp, color = Slate500, maxLines = 1)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShadcnBadge(
                        "${fmtMeter(totalMeters)} m",
                        variant = BadgeVariant.ORANGE
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Slate600,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                if (orders.size > 1) {
                    Text(
                        "Combined dispatch: ${orders.size} orders can be packed and dispatched in one consignment",
                        fontSize = 11.sp,
                        color = BrandOrangeDark,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(color = Slate100, thickness = 1.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    orders.forEach { order ->
                        ToDispatchCard(
                            order = order,
                            onOpenForm = { onOpenForm(order.id) },
                            onPhotoClick = onPhotoClick,
                            hidePartyName = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PartyDispatchedGroup(
    partyName: String,
    records: List<DispatchRecordDto>,
    onPhotoClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val totalActual = records.sumOf { r -> r.dispatch_items?.sumOf { it.actual_meter } ?: 0.0 }
    val totalOrdered = records.sumOf { r -> r.dispatch_items?.sumOf { it.ordered_meter } ?: 0.0 }
    val diff = totalActual - totalOrdered

    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            partyName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate950
                        )
                        if (records.size > 1) {
                            ShadcnBadge(
                                "${records.size} dispatches",
                                variant = BadgeVariant.INDIGO
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShadcnBadge(
                        "${fmtMeter(totalActual)} m" + (if (diff != 0.0) " (${if (diff > 0) "+" else ""}${fmtMeter(diff)})" else ""),
                        variant = if (diff > 0) BadgeVariant.SUCCESS else if (diff < 0) BadgeVariant.DESTRUCTIVE else BadgeVariant.SECONDARY
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Slate600,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = Slate100, thickness = 1.dp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { record ->
                        DispatchedCard(
                            d = record,
                            onPhotoClick = onPhotoClick,
                            hidePartyName = true
                        )
                    }
                }
            }
        }
    }
}

