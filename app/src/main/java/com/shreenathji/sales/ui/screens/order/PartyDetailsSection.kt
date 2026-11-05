package com.shreenathji.sales.ui.screens.order

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.ui.components.PartyAvatar
import com.shreenathji.sales.ui.components.SearchPickerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.theme.*

@Composable
fun PartyDetailsSection(
    parties: List<PartyEntity>,
    selectedParty: PartyEntity?,
    partyName: String,
    partyAddress: String,
    agent: String,
    poNumber: String,
    deliveryAddress: String,
    currentStaffId: String? = null,
    onPartySelected: (PartyEntity) -> Unit,
    onPartyNameChanged: (String) -> Unit,
    onPartyAddressChanged: (String) -> Unit,
    onAgentChanged: (String) -> Unit,
    onPoNumberChanged: (String) -> Unit,
    onDeliveryAddressChanged: (String) -> Unit
) {
    var partyPickerOpen by remember { mutableStateOf(false) }
    var moreDetailsExpanded by remember {
        mutableStateOf(
            agent.isNotBlank() || poNumber.isNotBlank() || deliveryAddress.isNotBlank()
        )
    }

    val sortedParties = remember(parties, currentStaffId) {
        parties.sortedByDescending { it.isAllottedTo(currentStaffId) }
    }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 1.5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = BrandOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Store details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate950
                    )
                }

                if (selectedParty != null && !selectedParty.defaultAgent.isNullOrBlank()) {
                    ShadcnBadge(
                        text = selectedParty.defaultAgent ?: "",
                        variant = BadgeVariant.INDIGO
                    )
                }
            }

            if (selectedParty != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldSuccessBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccessBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccessText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected store",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccessText
                            )
                            Text(
                                text = selectedParty.businessName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }
                        if (!selectedParty.isSynced) {
                            ShadcnBadge(
                                text = "Not sent yet",
                                variant = BadgeVariant.WARNING
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = partyName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Store name *", fontSize = 14.sp, color = Slate600) },
                    placeholder = { Text("Tap to search or select store...", color = Slate400) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Browse stores",
                            tint = Slate900
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )
                // Transparent tap layer: the field itself is read-only, typing happens in the picker.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { partyPickerOpen = true }
                )
            }

            if (partyPickerOpen) {
                SearchPickerDialog(
                    title = "Select store",
                    placeholder = "Search store name or address…",
                    items = sortedParties,
                    matches = { party, q ->
                        party.businessName.contains(q, ignoreCase = true) ||
                            party.formattedAddress.contains(q, ignoreCase = true) ||
                            (party.contactPerson?.contains(q, ignoreCase = true) == true)
                    },
                    onDismiss = { partyPickerOpen = false },
                    onPick = { party ->
                        onPartySelected(party)
                        partyPickerOpen = false
                    },
                    onUseTyped = { name ->
                        onPartyNameChanged(name)
                        partyPickerOpen = false
                    }
                ) { party ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PartyAvatar(
                            name = party.businessName,
                            size = 38.dp,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = party.businessName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Slate950,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!party.isSynced) {
                                    ShadcnBadge(text = "Not sent yet", variant = BadgeVariant.WARNING)
                                }
                                if (party.isAllottedTo(currentStaffId)) {
                                    ShadcnBadge(text = "My party", variant = BadgeVariant.SUCCESS)
                                }
                            }
                            if (party.formattedAddress.isNotBlank()) {
                                Text(
                                    text = party.formattedAddress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate500,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = partyAddress,
                onValueChange = onPartyAddressChanged,
                label = { Text("Store address *", fontSize = 14.sp, color = Slate600) },
                placeholder = { Text("Shop number, market, city...", color = Slate400) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ShadcnWhite,
                    unfocusedContainerColor = ShadcnWhite,
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate200,
                    focusedTextColor = Slate950,
                    unfocusedTextColor = Slate950
                ),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { moreDetailsExpanded = !moreDetailsExpanded }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More details (optional)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )
                Icon(
                    imageVector = if (moreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (moreDetailsExpanded) "Hide details" else "Show details",
                    tint = Slate500
                )
            }

            AnimatedVisibility(visible = moreDetailsExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = agent,
                            onValueChange = onAgentChanged,
                            label = { Text("Broker", fontSize = 14.sp, color = Slate600) },
                            placeholder = { Text("Broker name", color = Slate400) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ShadcnWhite,
                                unfocusedContainerColor = ShadcnWhite,
                                focusedBorderColor = Slate900,
                                unfocusedBorderColor = Slate200,
                                focusedTextColor = Slate950,
                                unfocusedTextColor = Slate950
                            )
                        )

                        OutlinedTextField(
                            value = poNumber,
                            onValueChange = onPoNumberChanged,
                            label = { Text("PO / Ref", fontSize = 14.sp, color = Slate600) },
                            placeholder = { Text("e.g. PO-8942", color = Slate400) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ShadcnWhite,
                                unfocusedContainerColor = ShadcnWhite,
                                focusedBorderColor = Slate900,
                                unfocusedBorderColor = Slate200,
                                focusedTextColor = Slate950,
                                unfocusedTextColor = Slate950
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = deliveryAddress,
                        onValueChange = onDeliveryAddressChanged,
                        label = { Text("Delivery address (optional)", fontSize = 14.sp, color = Slate600) },
                        placeholder = { Text("Godown or transport address if different", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ShadcnWhite,
                            unfocusedContainerColor = ShadcnWhite,
                            focusedBorderColor = Slate900,
                            unfocusedBorderColor = Slate200,
                            focusedTextColor = Slate950,
                            unfocusedTextColor = Slate950
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }
}
