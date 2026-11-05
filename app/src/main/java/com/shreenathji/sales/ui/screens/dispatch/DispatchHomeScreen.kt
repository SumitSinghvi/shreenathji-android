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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchHomeScreen(
    viewModel: DispatchViewModel,
    onOpenForm: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(DispatchDateFilter.ALL) }
    var selectedParty by remember { mutableStateOf<String?>(null) }
    var showPartyPicker by remember { mutableStateOf(false) }
    var groupByParty by remember { mutableStateOf(false) }
    var previewPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Distinct party list for party filter picker
    val partyOptions = remember(state.toDispatch, state.dispatched, tab) {
        if (tab == 0) {
            state.toDispatch.map { it.party_name.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        } else {
            state.dispatched.mapNotNull { it.orders?.party_name?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    // Recomputed on each recomposition so "Today" rolls over at midnight without restarting the app.
    val (todayKey, yesterdayKey, weekAgoKey) = DispatchDateUtils.currentCalendarKeys()

    val filteredToDispatch = remember(state.toDispatch, searchQuery, dateFilter, selectedParty, todayKey, yesterdayKey, weekAgoKey) {
        DispatchFilters.filterOrders(
            orders = state.toDispatch,
            query = searchQuery,
            dateFilter = dateFilter,
            partyFilter = selectedParty,
            todayKey = todayKey,
            yesterdayKey = yesterdayKey,
            weekAgoKey = weekAgoKey
        )
    }

    val filteredDispatched = remember(state.dispatched, searchQuery, dateFilter, selectedParty, todayKey, yesterdayKey, weekAgoKey) {
        DispatchFilters.filterDispatches(
            dispatches = state.dispatched,
            query = searchQuery,
            dateFilter = dateFilter,
            partyFilter = selectedParty,
            todayKey = todayKey,
            yesterdayKey = yesterdayKey,
            weekAgoKey = weekAgoKey
        )
    }

    // Live meter summaries
    val toDispatchTotalMeters = remember(filteredToDispatch) {
        filteredToDispatch.sumOf { o -> o.order_items?.sumOf { it.meter } ?: 0.0 }
    }
    val dispatchedTotalOrdered = remember(filteredDispatched) {
        filteredDispatched.sumOf { d -> d.dispatch_items?.sumOf { it.ordered_meter } ?: 0.0 }
    }
    val dispatchedTotalActual = remember(filteredDispatched) {
        filteredDispatched.sumOf { d -> d.dispatch_items?.sumOf { it.actual_meter } ?: 0.0 }
    }
    val dispatchedVariance = dispatchedTotalActual - dispatchedTotalOrdered

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // App Bar Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dispatch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate950)
                Text(viewModel.staff?.name.orEmpty(), fontSize = 12.sp, color = Slate500)
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Slate700)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out", tint = Slate700)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search order, party, LR...", fontSize = 13.sp, color = Slate400) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate500, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ShadcnWhite,
                unfocusedContainerColor = Slate50,
                focusedBorderColor = Slate950,
                unfocusedBorderColor = Slate200
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Filter Bar (Date Pills + Party Filter + Group by Party)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Date Filter Pills
            DispatchDateFilter.values().forEach { df ->
                val selected = dateFilter == df
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Slate900 else Slate100)
                        .clickable { dateFilter = df }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        df.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) ShadcnWhite else Slate700
                    )
                }
            }

            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Slate200))

            // Party Filter Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selectedParty != null) BrandOrangeLight else Slate100)
                    .border(
                        1.dp,
                        if (selectedParty != null) BrandOrangeBorder else Slate200,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { showPartyPicker = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        selectedParty ?: "All Parties ▾",
                        fontSize = 12.sp,
                        fontWeight = if (selectedParty != null) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedParty != null) BrandOrangeDark else Slate700
                    )
                    if (selectedParty != null) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear party",
                            tint = BrandOrangeDark,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { selectedParty = null }
                        )
                    }
                }
            }

            // Group by Party Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (groupByParty) Slate900 else Slate100)
                    .border(
                        1.dp,
                        if (groupByParty) Slate900 else Slate200,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { groupByParty = !groupByParty }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    "Group by Party",
                    fontSize = 12.sp,
                    fontWeight = if (groupByParty) FontWeight.Bold else FontWeight.Medium,
                    color = if (groupByParty) ShadcnWhite else Slate700
                )
            }
        }

        // Live Meter & Count Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Slate100)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (tab == 0) {
                    "Showing ${filteredToDispatch.size} orders" + (selectedParty?.let { " • $it" } ?: "")
                } else {
                    "Showing ${filteredDispatched.size} dispatches" + (selectedParty?.let { " • $it" } ?: "")
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Slate700
            )
            Text(
                text = if (tab == 0) {
                    "${fmtMeter(toDispatchTotalMeters)} m"
                } else {
                    "${fmtMeter(dispatchedTotalActual)} m" + (if (dispatchedVariance != 0.0) " (${if (dispatchedVariance > 0) "+" else ""}${fmtMeter(dispatchedVariance)}m)" else "")
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (tab == 1 && dispatchedVariance != 0.0) (if (dispatchedVariance > 0) EmeraldSuccessText else RoseDestructiveText) else Slate950
            )
        }

        // Segmented Tabs
        TabRow(
            selectedTabIndex = tab,
            containerColor = ShadcnWhite,
            contentColor = Slate950,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = {
                    Text(
                        "To dispatch (${filteredToDispatch.size})",
                        fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    Text(
                        "Dispatched (${filteredDispatched.size})",
                        fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }

        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandOrange)
        state.loadError?.let {
            Text(it, color = RoseDestructiveText, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }

        if (tab == 0) {
            if (filteredToDispatch.isEmpty() && !state.isLoading) {
                EmptyNote(if (searchQuery.isNotBlank() || selectedParty != null || dateFilter != DispatchDateFilter.ALL) "No orders match selected filters." else "No orders waiting for dispatch.")
            }
            if (groupByParty) {
                val grouped = remember(filteredToDispatch) { filteredToDispatch.groupBy { it.party_name } }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(grouped.keys.toList(), key = { it }) { partyName ->
                        PartyToDispatchGroup(
                            partyName = partyName,
                            orders = grouped[partyName].orEmpty(),
                            onOpenForm = onOpenForm,
                            onPhotoClick = { url -> previewPhotoUri = Uri.parse(url) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredToDispatch, key = { it.id }) { order ->
                        ToDispatchCard(
                            order = order,
                            onOpenForm = { onOpenForm(order.id) },
                            onPhotoClick = { url -> previewPhotoUri = Uri.parse(url) }
                        )
                    }
                }
            }
        } else {
            if (filteredDispatched.isEmpty() && !state.isLoading) {
                EmptyNote(if (searchQuery.isNotBlank() || selectedParty != null || dateFilter != DispatchDateFilter.ALL) "No dispatches match selected filters." else "You have not dispatched anything yet.")
            }
            if (groupByParty) {
                val grouped = remember(filteredDispatched) { filteredDispatched.groupBy { it.orders?.party_name ?: "Other" } }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(grouped.keys.toList(), key = { it }) { partyName ->
                        PartyDispatchedGroup(
                            partyName = partyName,
                            records = grouped[partyName].orEmpty(),
                            onPhotoClick = { url -> previewPhotoUri = Uri.parse(url) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDispatched, key = { it.id }) { record ->
                        DispatchedCard(
                            d = record,
                            onPhotoClick = { url -> previewPhotoUri = Uri.parse(url) }
                        )
                    }
                }
            }
        }
    }

    // Party Picker Dialog
    if (showPartyPicker) {
        SearchPickerDialog(
            title = "Filter by Party",
            placeholder = "Search party name...",
            items = partyOptions,
            matches = { item, query -> item.lowercase().contains(query.lowercase()) },
            onDismiss = { showPartyPicker = false },
            onPick = { picked ->
                selectedParty = picked
                showPartyPicker = false
            },
            onUseTyped = { typed ->
                selectedParty = typed.trim().ifBlank { null }
                showPartyPicker = false
            }
        ) { party ->
            Text(
                party,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Slate900,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }

    // Fullscreen Swatch / Proof Photo Lightbox
    previewPhotoUri?.let { uri ->
        ImageViewerDialog(imageUri = uri, onDismiss = { previewPhotoUri = null })
    }
}

