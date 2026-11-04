package com.shreenathji.sales.ui.screens.party

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*

@Composable
fun RecordVisitNotesDialog(
    storeName: String,
    storeAddress: String?,
    initialRemarks: String? = null,
    isAlreadyVisited: Boolean = false,
    onDismiss: () -> Unit,
    onSaveVisit: (remarks: String?) -> Unit,
    onMarkUnvisited: (() -> Unit)? = null
) {
    var remarksText by remember { mutableStateOf(initialRemarks ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = ShadcnWhite,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAlreadyVisited) EmeraldSuccessBg else Slate100,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAlreadyVisited) Icons.Default.CheckCircle else Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = if (isAlreadyVisited) EmeraldSuccessText else BrandOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isAlreadyVisited) "Visit Completed" else "Record Store Visit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate950
                            )
                            Text(
                                text = storeName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate600,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!storeAddress.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = storeAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 1.dp, color = Slate200)
                Spacer(modifier = Modifier.height(14.dp))

                // Notes / Remarks Input Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Visit Notes / Remarks (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarksText,
                    onValueChange = { remarksText = it },
                    placeholder = {
                        Text(
                            "e.g. Owner reviewed catalog, follow up on Friday with sample swatches...",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary Action: Mark Visited / Update Notes
                    ShadcnPrimaryButton(
                        onClick = {
                            onSaveVisit(remarksText.trim().ifBlank { null })
                        },
                        containerColor = if (isAlreadyVisited) Slate900 else EmeraldSuccess,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isAlreadyVisited) "Update Visit Notes" else "Confirm & Mark Visited",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Undo Visit Option (only shown if currently visited)
                    if (isAlreadyVisited && onMarkUnvisited != null) {
                        OutlinedButton(
                            onClick = onMarkUnvisited,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RoseDestructiveBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = RoseDestructiveBg,
                                contentColor = RoseDestructiveText
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = RoseDestructiveText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mark as Unvisited (Undo)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseDestructiveText
                            )
                        }
                    }

                    // Cancel button
                    ShadcnOutlineButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                    }
                }
            }
        }
    }
}
