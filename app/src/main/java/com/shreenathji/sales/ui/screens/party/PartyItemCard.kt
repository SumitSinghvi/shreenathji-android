package com.shreenathji.sales.ui.screens.party

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.PartyAvatar
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*

@Composable
fun PartyItemCard(
    party: PartyEntity,
    isVisited: Boolean,
    isAllotted: Boolean = false,
    onMarkVisited: () -> Unit,
    onBookOrder: () -> Unit,
    onCall: () -> Unit
) {
    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 1.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PartyAvatar(
                        name = party.businessName,
                        size = 42.dp,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = party.businessName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate950,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!party.contactPerson.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Contact: ${party.contactPerson}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!party.isSynced) {
                        ShadcnBadge(
                            text = "Not sent yet",
                            variant = BadgeVariant.WARNING
                        )
                    }
                    if (isAllotted) {
                        ShadcnBadge(
                            text = "My party",
                            variant = BadgeVariant.SUCCESS
                        )
                    }
                    if (!party.defaultAgent.isNullOrBlank()) {
                        ShadcnBadge(
                            text = "Broker: ${party.defaultAgent}",
                            variant = BadgeVariant.INDIGO
                        )
                    }
                }
            }

            if (!party.addressLine1.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val fullAddress = listOfNotNull(party.addressLine1, party.addressLine2).joinToString(", ")
                Text(
                    text = fullAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = Slate600,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = Slate200)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!party.phone.isNullOrBlank()) {
                        ShadcnOutlineButton(
                            onClick = onCall,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call",
                                modifier = Modifier.size(14.dp),
                                tint = Slate900
                            )
                        }
                    }

                    // Always clickable so unsynced stores can show an explanation toast
                    ShadcnOutlineButton(
                        onClick = onBookOrder,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (party.isSynced) BrandOrange else Slate400
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Order",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (party.isSynced) Slate900 else Slate400
                        )
                    }
                }

                if (isVisited) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldSuccessBg,
                        border = BorderStroke(1.dp, EmeraldSuccessBorder),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = EmeraldSuccessText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Visited",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccessText
                            )
                        }
                    }
                } else {
                    ShadcnPrimaryButton(
                        onClick = onMarkVisited,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mark visited",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
