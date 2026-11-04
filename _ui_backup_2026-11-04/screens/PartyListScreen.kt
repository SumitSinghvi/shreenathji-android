package com.shreenathji.sales.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.ui.components.AddPartyDialog
import com.shreenathji.sales.ui.components.ShadcnHeader
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.components.ShadcnSearchInput
import com.shreenathji.sales.ui.screens.party.DailyBeatStopCard
import com.shreenathji.sales.ui.screens.party.DateSwitcherBar
import com.shreenathji.sales.ui.screens.party.PartyItemCard
import com.shreenathji.sales.ui.screens.party.RecordVisitNotesDialog
import com.shreenathji.sales.ui.screens.party.StoreChatDialog
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.PartyListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class VisitDialogState(
    val storeName: String,
    val storeAddress: String?,
    val planId: String? = null,
    val party: PartyEntity? = null,
    val initialRemarks: String? = null,
    val isAlreadyVisited: Boolean = false
)

@Composable
fun PartyListScreen(
    viewModel: PartyListViewModel,
    onBookOrderForParty: (String) -> Unit
) {
    val parties by viewModel.parties.collectAsState()
    val beatPlans by viewModel.currentBeatPlans.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isViewingToday by viewModel.isViewingToday.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val context = LocalContext.current

    var showAddPartyDialog by remember { mutableStateOf(false) }
    var activeVisitDialog by remember { mutableStateOf<VisitDialogState?>(null) }
    var activeChatPlanId by remember { mutableStateOf<String?>(null) }
    val activeChatPlan = remember(beatPlans, activeChatPlanId) {
        beatPlans.find { it.id == activeChatPlanId }
    }

    val todayDateFormatted = remember {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    val routeDateLabel = remember(selectedDate, isViewingToday) {
        if (isViewingToday) {
            "Today's route"
        } else {
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
                val display = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(parsed ?: Date())
                "Route for $display"
            } catch (_: Exception) {
                "Route for $selectedDate"
            }
        }
    }

    val staffId = viewModel.currentStaff?.id

    val visitedPartyIdsOnDate = remember(beatPlans) {
        beatPlans.filter { it.status == "VISITED" }.map { it.partyId }.toSet()
    }

    // Sales reps only ever see shops allotted to them
    val myShops = remember(parties, staffId) {
        parties.filter { it.isAllottedTo(staffId) }
    }

    val filteredPlans = remember(beatPlans, searchQuery) {
        val baseList = if (searchQuery.isBlank()) beatPlans
        else beatPlans.filter {
            it.partyName.contains(searchQuery, ignoreCase = true) ||
            it.partyAddress?.contains(searchQuery, ignoreCase = true) == true ||
            it.instructions?.contains(searchQuery, ignoreCase = true) == true ||
            it.remarks?.contains(searchQuery, ignoreCase = true) == true
        }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<DailyVisitPlanEntity>()
        baseList.forEach { plan ->
            val noteKey = (plan.instructions ?: "").trim().lowercase()
            val dedupeKey = "${plan.partyId}_${plan.visitPurpose}_$noteKey"
            if (seen.add(dedupeKey)) {
                result.add(plan)
            }
        }
        result
    }

    val filteredMyShops = remember(myShops, searchQuery) {
        if (searchQuery.isBlank()) myShops
        else myShops.filter {
            it.businessName.contains(searchQuery, ignoreCase = true) ||
            it.addressLine1?.contains(searchQuery, ignoreCase = true) == true ||
            it.addressLine2?.contains(searchQuery, ignoreCase = true) == true ||
            it.contactPerson?.contains(searchQuery, ignoreCase = true) == true ||
            it.defaultAgent?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    // On route tab while searching: also show allotted shops that match but are not on this day's route
    val offRouteSearchMatches = remember(filteredMyShops, beatPlans, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val onRouteIds = beatPlans.map { it.partyId }.toSet()
            filteredMyShops.filter { it.id !in onRouteIds }
        }
    }
    Scaffold(
        topBar = {
            ShadcnHeader(
                title = "Parties",
                subtitle = "${viewModel.currentStaff?.name ?: "Sales Staff"} • $todayDateFormatted",
                actions = {
                    ShadcnPrimaryButton(
                        onClick = { showAddPartyDialog = true },
                        containerColor = Slate950,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add party",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
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
            PartiesSegmentTabs(
                selectedTab = selectedTab,
                onTabSelected = viewModel::setSelectedTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ShadcnSearchInput(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    placeholder = "Search party name or address…"
                )
            }

            if (selectedTab == 0) {
                DateSwitcherBar(
                    selectedDate = selectedDate,
                    isViewingToday = isViewingToday,
                    onPreviousDay = viewModel::selectPreviousDay,
                    onNextDay = viewModel::selectNextDay,
                    onSelectToday = viewModel::selectToday,
                    onSelectDate = viewModel::selectDate
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = routeDateLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate950,
                                letterSpacing = (-0.2).sp
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Slate200
                            ) {
                                Text(
                                    text = "${filteredPlans.size} stop${if (filteredPlans.size != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (filteredPlans.isEmpty() && offRouteSearchMatches.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Storefront,
                                message = if (searchQuery.isNotBlank()) {
                                    "No parties matching '$searchQuery'"
                                } else {
                                    "No parties on your route for this day"
                                }
                            )
                        }
                    } else {
                        itemsIndexed(filteredPlans, key = { _, plan -> "beat_${plan.id}" }) { index, plan ->
                            DailyBeatStopCard(
                                plan = plan,
                                stopNumber = index + 1,
                                isReadOnly = !isViewingToday,
                                onOpenVisitDialog = {
                                    if (isViewingToday) {
                                        activeVisitDialog = VisitDialogState(
                                            storeName = plan.partyName,
                                            storeAddress = plan.partyAddress,
                                            planId = plan.id,
                                            initialRemarks = plan.remarks,
                                            isAlreadyVisited = plan.status == "VISITED"
                                        )
                                    }
                                },
                                onOpenChat = {
                                    activeChatPlanId = plan.id
                                },
                                onBookOrder = { onBookOrderForParty(plan.partyId) },
                                onCall = {
                                    plan.partyPhone?.let { phone ->
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }

                        if (offRouteSearchMatches.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Also in your parties",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate950,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(offRouteSearchMatches, key = { "offroute_${it.id}" }) { party ->
                                val matchingPlan = beatPlans.find { it.partyId == party.id }
                                PartyItemCard(
                                    party = party,
                                    isVisited = visitedPartyIdsOnDate.contains(party.id),
                                    isAllotted = false,
                                    onMarkVisited = {
                                        if (isViewingToday) {
                                            activeVisitDialog = VisitDialogState(
                                                storeName = party.businessName,
                                                storeAddress = party.formattedAddress,
                                                planId = matchingPlan?.id,
                                                party = party,
                                                initialRemarks = matchingPlan?.remarks,
                                                isAlreadyVisited = visitedPartyIdsOnDate.contains(party.id)
                                            )
                                        }
                                    },
                                    onBookOrder = {
                                        if (party.isSynced) {
                                            onBookOrderForParty(party.id)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Store not uploaded yet — wait for network",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    onCall = {
                                        party.phone?.let { phone ->
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "My parties (${filteredMyShops.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate950,
                            letterSpacing = (-0.2).sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }

                    if (filteredMyShops.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Store,
                                message = when {
                                    searchQuery.isNotBlank() -> "No parties matching '$searchQuery'"
                                    myShops.isEmpty() -> "No parties assigned to you yet"
                                    else -> "No parties registered yet"
                                }
                            )
                        }
                    } else {
                        items(filteredMyShops, key = { "store_${it.id}" }) { party ->
                            val matchingPlan = beatPlans.find { it.partyId == party.id }
                            PartyItemCard(
                                party = party,
                                isVisited = visitedPartyIdsOnDate.contains(party.id),
                                isAllotted = false,
                                onMarkVisited = {
                                    if (isViewingToday) {
                                        activeVisitDialog = VisitDialogState(
                                            storeName = party.businessName,
                                            storeAddress = party.formattedAddress,
                                            planId = matchingPlan?.id,
                                            party = party,
                                            initialRemarks = matchingPlan?.remarks,
                                            isAlreadyVisited = visitedPartyIdsOnDate.contains(party.id)
                                        )
                                    }
                                },
                                onBookOrder = {
                                    if (party.isSynced) {
                                        onBookOrderForParty(party.id)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Store not uploaded yet — wait for network",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                onCall = {
                                    party.phone?.let { phone ->
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    activeVisitDialog?.let { dialogState ->
        RecordVisitNotesDialog(
            storeName = dialogState.storeName,
            storeAddress = dialogState.storeAddress,
            initialRemarks = dialogState.initialRemarks,
            isAlreadyVisited = dialogState.isAlreadyVisited,
            onDismiss = { activeVisitDialog = null },
            onSaveVisit = { remarks ->
                viewModel.completeVisit(
                    context = context,
                    planId = dialogState.planId,
                    party = dialogState.party,
                    remarks = remarks,
                    onDone = { activeVisitDialog = null }
                )
            },
            onMarkUnvisited = if (dialogState.isAlreadyVisited && dialogState.planId != null) {
                {
                    viewModel.markVisitUnvisited(
                        context = context,
                        planId = dialogState.planId,
                        onDone = { activeVisitDialog = null }
                    )
                }
            } else null
        )
    }

    if (showAddPartyDialog) {
        AddPartyDialog(
            onDismiss = { showAddPartyDialog = false },
            onSave = { name, contact, phone, addr1, addr2, agent ->
                viewModel.createParty(
                    context = context,
                    businessName = name,
                    contactPerson = contact,
                    phone = phone,
                    addressLine1 = addr1,
                    addressLine2 = addr2,
                    defaultAgent = agent,
                    onSuccess = { showAddPartyDialog = false }
                )
            }
        )
    }

    if (activeChatPlan != null) {
        StoreChatDialog(
            plan = activeChatPlan,
            onDismiss = { activeChatPlanId = null },
            onSendMessage = { text ->
                viewModel.sendStoreChatMessage(activeChatPlan.id, text)
            },
            onSendVoiceFile = { file, onResult ->
                viewModel.sendStoreVoiceMessage(activeChatPlan.id, file, onResult)
            }
        )
    }
}

@Composable
private fun PartiesSegmentTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Slate100,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PartiesSegmentTab(
                label = "Today's route",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )
            PartiesSegmentTab(
                label = "My parties",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PartiesSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) ShadcnWhite else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Slate950 else Slate500
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Slate300
            )
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate700
            )
        }
    }
}
