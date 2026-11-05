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
internal fun ToDispatchCard(
    order: DispatchOrderDto,
    onOpenForm: () -> Unit,
    onPhotoClick: (String) -> Unit,
    hidePartyName: Boolean = false
) {
    val items = order.order_items.orEmpty()
    val totalMeters = items.sumOf { it.meter }
    val formattedDate = DispatchDateUtils.formatOrderTimestamp(order.order_timestamp)

    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Order Number, Date & Total Meters
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        order.order_number,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = Slate950
                    )
                    formattedDate?.let {
                        Text(it, fontSize = 11.sp, color = Slate500)
                    }
                }
                ShadcnBadge(
                    "${fmtMeter(totalMeters)} m",
                    variant = BadgeVariant.ORANGE
                )
            }

            // Buyer Name & Address
            if (!hidePartyName) {
                Column {
                    Text(order.party_name, color = Slate900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    order.party_address?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Slate500, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            OrderInfoBlock(order)

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // Fabric Items List with Swatches
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    val (cleanName, tag) = cleanItem(item.product_name, item.component)
                    val swatchUrl = item.image_url ?: order.image_urls?.firstOrNull()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate50)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Swatch thumbnail
                        if (!swatchUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = swatchUrl,
                                contentDescription = "Swatch",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                                    .clickable { onPhotoClick(swatchUrl) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Slate200.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Product Name & Tag
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                cleanName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800,
                                maxLines = 1
                            )
                            tag?.let {
                                Text(
                                    it,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (it == "TOP") Slate700 else BrandOrange
                                )
                            }
                            ItemDetails(item)
                        }

                        // Meters
                        Text(
                            "${fmtMeter(item.meter)} m",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate950
                        )
                    }
                }
            }

            // Dispatch Button
            ShadcnPrimaryButton(
                onClick = onOpenForm,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Dispatch Order", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun DispatchedCard(
    d: DispatchRecordDto,
    onPhotoClick: (String) -> Unit,
    hidePartyName: Boolean = false
) {
    val items = d.dispatch_items.orEmpty()

    ShadcnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Order Number & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    d.orders?.order_number.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = Slate950
                )
                ShadcnBadge(
                    d.orders?.order_status ?: "DISPATCHED",
                    variant = if (d.orders?.order_status == "DELIVERED") BadgeVariant.SUCCESS else BadgeVariant.INFO
                )
            }

            // Party Name & Date
            Column {
                if (!hidePartyName) {
                    Text(d.orders?.party_name.orEmpty(), color = Slate900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    d.dispatched_at?.take(16)?.replace('T', ' ').orEmpty(),
                    color = Slate500,
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // Fabric Items (Ordered -> Actual with variance)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    val (cleanName, tag) = cleanItem(item.product_name, item.component)
                    val diff = item.actual_meter - item.ordered_meter

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(cleanName, fontSize = 12.sp, color = Slate700, maxLines = 1)
                            tag?.let {
                                Text(" ($it)", fontSize = 11.sp, color = Slate500)
                            }
                        }
                        Text(
                            "${fmtMeter(item.ordered_meter)} → ${fmtMeter(item.actual_meter)} m" +
                                (if (diff != 0.0) " (${if (diff > 0) "+" else ""}${fmtMeter(diff)})" else ""),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (diff > 0) EmeraldSuccessText else if (diff < 0) RoseDestructiveText else Slate800
                        )
                    }
                }
            }

            // Transport Details
            val transportFields = listOf(
                "Transporter" to d.transporter,
                "LR" to d.lr_number,
                "Vehicle" to d.vehicle_no,
                "Notes" to d.notes
            ).filter { !it.second.isNullOrBlank() }

            if (transportFields.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate50)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    transportFields.forEach { (label, value) ->
                        Row {
                            Text("$label: ", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Slate600)
                            Text(value.orEmpty(), fontSize = 11.sp, color = Slate900)
                        }
                    }
                }
            }

            // Attached Proof Photos
            val photos = d.photo_urls.orEmpty()
            if (photos.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Proof Photos (${photos.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(photos) { photoUrl ->
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Proof Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                                    .clickable { onPhotoClick(photoUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

