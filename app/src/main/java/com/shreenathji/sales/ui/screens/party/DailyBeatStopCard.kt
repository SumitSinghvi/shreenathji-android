package com.shreenathji.sales.ui.screens.party

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyBeatStopCard(
    plan: DailyVisitPlanEntity,
    stopNumber: Int,
    isReadOnly: Boolean = false,
    onOpenVisitDialog: () -> Unit,
    onOpenChat: () -> Unit = {},
    onBookOrder: () -> Unit,
    onCall: () -> Unit
) {
    val isVisited = plan.status == "VISITED"

    val visitedTimeFormatted = remember(plan.visitedAt) {
        plan.visitedAt?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
        }
    }

    val visitedLabel = if (visitedTimeFormatted != null) {
        "Visited · $visitedTimeFormatted"
    } else {
        "Visited"
    }

    val cardBgColor by animateColorAsState(
        targetValue = if (isVisited) EmeraldSuccessBg.copy(alpha = 0.4f) else ShadcnWhite,
        label = "beat_card_bg"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isVisited) EmeraldSuccessBorder else Slate200,
        label = "beat_card_border"
    )

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBgColor,
        borderColor = cardBorderColor,
        elevation = if (isVisited) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isVisited) EmeraldSuccess else Slate900,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isVisited) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Visited",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        } else {
                            Text(
                                text = "$stopNumber",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.partyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isVisited) Slate600 else Slate950,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    plan.partyAddress?.let { addr ->
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = addr,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!plan.instructions.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberWarningBg,
                    border = BorderStroke(1.dp, AmberWarningBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 1.dp),
                            tint = AmberWarningText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatChatPreviewText(plan.instructions),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AmberWarningText,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!plan.remarks.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 1.dp),
                            tint = Slate700
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatChatPreviewText(plan.remarks),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Slate800,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = if (isVisited) EmeraldSuccessBorder.copy(alpha = 0.5f) else Slate200
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Primary visit action
            if (isReadOnly) {
                if (isVisited) {
                    VisitedStatusChip(label = visitedLabel, editable = false, onClick = {})
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate300),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pending",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate600,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            } else if (isVisited) {
                VisitedStatusChip(
                    label = visitedLabel,
                    editable = true,
                    onClick = onOpenVisitDialog
                )
            } else {
                ShadcnPrimaryButton(
                    onClick = onOpenVisitDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark visited",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary: Order + Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShadcnOutlineButton(
                    onClick = onBookOrder,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BrandOrange
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Order",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                if (!plan.partyPhone.isNullOrBlank()) {
                    ShadcnOutlineButton(
                        onClick = onCall,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call",
                            modifier = Modifier.size(16.dp),
                            tint = Slate900
                        )
                    }
                }
            }

            // Demoted chat
            TextButton(
                onClick = onOpenChat,
                modifier = Modifier.padding(top = 2.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Slate500
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Notes / Chat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500
                )
            }
        }
    }
}

@Composable
private fun VisitedStatusChip(
    label: String,
    editable: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = EmeraldSuccessBg,
        border = BorderStroke(1.dp, EmeraldSuccessBorder),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (editable) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = EmeraldSuccessText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldSuccessText,
                modifier = Modifier.weight(1f)
            )
            if (editable) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit visit notes",
                    modifier = Modifier.size(14.dp),
                    tint = EmeraldSuccessText
                )
            }
        }
    }
}
