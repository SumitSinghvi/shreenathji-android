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

/** The form a dispatch person fills for one approved order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchFormScreen(
    viewModel: DispatchViewModel,
    orderId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val order = viewModel.findOrder(orderId)
    var previewPhotoUri by remember { mutableStateOf<Uri?>(null) }

    if (order == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Text("This order is no longer waiting for dispatch.", color = Slate700)
            Spacer(Modifier.height(12.dp))
            ShadcnOutlineButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val orderItems = order.order_items.orEmpty()
    val actuals = remember(order.id) {
        mutableStateMapOf<String, String>().apply {
            orderItems.forEach { put(it.id, "") }
        }
    }
    val itemNotes = remember(order.id) { mutableStateMapOf<String, String>() }
    var lr by remember { mutableStateOf("") }
    var transporter by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        photos = (photos + uris).distinct()
    }

    // Fill all with ordered quantities in 1 tap
    val fillAllWithOrdered = {
        orderItems.forEach { item ->
            actuals[item.id] = fmtMeter(item.meter)
        }
        Toast.makeText(context, "Filled actual meters with ordered quantities", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(order.order_number, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Slate950)
                Text(order.party_name, fontSize = 12.sp, color = Slate500)
            }
            TextButton(onClick = fillAllWithOrdered) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandOrange)
                Spacer(Modifier.width(4.dp))
                Text("Fill Ordered", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            order.party_address?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = Slate500)
            }
            OrderInfoBlock(order)

            Text("Fabric Items & Actual Meters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate950)

            orderItems.forEach { item ->
                val (cleanName, tag) = cleanItem(item.product_name, item.component)
                val swatchUrl = item.image_url ?: order.image_urls?.firstOrNull()

                ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!swatchUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = swatchUrl,
                                    contentDescription = "Swatch",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                                        .clickable { previewPhotoUri = Uri.parse(swatchUrl) }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cleanName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Ordered: ${fmtMeter(item.meter)} m", fontSize = 12.sp, color = Slate500)
                                    tag?.let {
                                        Text("• $it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                                    }
                                }
                                ItemDetails(item)
                            }
                        }

                        OutlinedTextField(
                            value = actuals[item.id].orEmpty(),
                            onValueChange = { actuals[item.id] = it },
                            label = { Text("Actual measured meters") },
                            placeholder = { Text(fmtMeter(item.meter)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = itemNotes[item.id].orEmpty(),
                            onValueChange = { itemNotes[item.id] = it },
                            label = { Text("Item note (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Text("Transport & Logistics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate950)
            OutlinedTextField(
                lr, { lr = it },
                label = { Text("LR / Bilty number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                transporter, { transporter = it },
                label = { Text("Transporter name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                vehicle, { vehicle = it },
                label = { Text("Vehicle number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                notes, { notes = it },
                label = { Text("Dispatch notes") },
                modifier = Modifier.fillMaxWidth()
            )

            // Photo Upload
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShadcnOutlineButton(onClick = { picker.launch("image/*") }) {
                    Text("Add Proof Photos")
                }
                Spacer(Modifier.width(10.dp))
                if (photos.isNotEmpty()) {
                    Text("${photos.size} photo(s) selected", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }

            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(photos) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                                .clickable { previewPhotoUri = uri }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        ShadcnPrimaryButton(
            onClick = {
                val input = DispatchFormInput(
                    orderId = order.id,
                    items = orderItems.map {
                        DispatchItemInput(it.id, actuals[it.id].orEmpty(), itemNotes[it.id].orEmpty())
                    },
                    lrNumber = lr, transporter = transporter, vehicleNo = vehicle, notes = notes
                )
                viewModel.submit(order, input, photos) { error ->
                    if (error == null) {
                        Toast.makeText(context, "Dispatch saved successfully", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (state.isSubmitting) "Saving Dispatch..." else "Confirm & Submit Dispatch", fontWeight = FontWeight.Bold)
        }
    }

    previewPhotoUri?.let { uri ->
        ImageViewerDialog(imageUri = uri, onDismiss = { previewPhotoUri = null })
    }
}
