package com.shreenathji.sales.ui.screens.order.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.ui.components.SearchPickerDialog
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.ColorSlotDraft
import com.shreenathji.sales.ui.viewmodels.OrderItemDraft
import java.text.NumberFormat

@Composable
internal fun ItemColorsList(
    item: OrderItemDraft,
    onAddColor: () -> Unit,
    onRemoveColor: (Int) -> Unit,
    onColorLabelChanged: (Int, String) -> Unit,
    onColorMeterChanged: (Int, String) -> Unit,
    onColorBottomMeterChanged: (Int, String) -> Unit,
    onChooseDesignForColor: (Int) -> Unit,
    onPhotoForColor: (Int) -> Unit,
    onClearColorAttachment: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COLORS (${item.colors.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate500,
                    letterSpacing = 0.4.sp
                )
                TextButton(
                    onClick = onAddColor,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = BrandOrange
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Add color",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Slate200)

            item.colors.forEachIndexed { colorIdx, color ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.isTopBottom) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorAttachmentOrLabel(item, color, colorIdx, onClearColorAttachment, onChooseDesignForColor, onPhotoForColor, onColorLabelChanged)

                            IconButton(
                                onClick = { onRemoveColor(colorIdx) },
                                enabled = item.colors.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove color",
                                    tint = if (item.colors.size > 1) RoseDestructive else Slate200,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = color.meterText,
                                onValueChange = { onColorMeterChanged(colorIdx, it) },
                                label = { Text("Top Mtr", fontSize = 11.sp) },
                                placeholder = { Text("Top Mtr", color = Slate400, fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (color.meterOverridden) AmberWarningBg else ShadcnWhite,
                                    unfocusedContainerColor = if (color.meterOverridden) AmberWarningBg else ShadcnWhite,
                                    focusedBorderColor = if (color.meterOverridden) AmberWarningText else Slate900,
                                    unfocusedBorderColor = if (color.meterOverridden) AmberWarningText.copy(alpha = 0.4f) else Slate200,
                                    focusedTextColor = Slate950,
                                    unfocusedTextColor = Slate950
                                )
                            )

                            OutlinedTextField(
                                value = color.bottomMeterText,
                                onValueChange = { onColorBottomMeterChanged(colorIdx, it) },
                                label = { Text("Bottom Mtr", fontSize = 11.sp) },
                                placeholder = { Text("Bottom Mtr", color = Slate400, fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (color.bottomMeterOverridden) Color(0xFFEEF2FF) else ShadcnWhite,
                                    unfocusedContainerColor = if (color.bottomMeterOverridden) Color(0xFFEEF2FF) else ShadcnWhite,
                                    focusedBorderColor = if (color.bottomMeterOverridden) Color(0xFF818CF8) else Slate900,
                                    unfocusedBorderColor = if (color.bottomMeterOverridden) Color(0xFF818CF8).copy(alpha = 0.5f) else Slate200,
                                    focusedTextColor = Slate950,
                                    unfocusedTextColor = Slate950
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorAttachmentOrLabel(item, color, colorIdx, onClearColorAttachment, onChooseDesignForColor, onPhotoForColor, onColorLabelChanged)

                            OutlinedTextField(
                                value = color.meterText,
                                onValueChange = { onColorMeterChanged(colorIdx, it) },
                                placeholder = { Text("Mtr", color = Slate400, fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(88.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (color.meterOverridden) AmberWarningBg else ShadcnWhite,
                                    unfocusedContainerColor = if (color.meterOverridden) AmberWarningBg else ShadcnWhite,
                                    focusedBorderColor = if (color.meterOverridden) AmberWarningText else Slate900,
                                    unfocusedBorderColor = if (color.meterOverridden) AmberWarningText.copy(alpha = 0.4f) else Slate200,
                                    focusedTextColor = Slate950,
                                    unfocusedTextColor = Slate950
                                )
                            )

                            IconButton(
                                onClick = { onRemoveColor(colorIdx) },
                                enabled = item.colors.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove color",
                                    tint = if (item.colors.size > 1) RoseDestructive else Slate200,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (colorIdx < item.colors.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Slate100,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ColorAttachmentOrLabel(
    item: OrderItemDraft,
    color: ColorSlotDraft,
    colorIdx: Int,
    onClearColorAttachment: (Int) -> Unit,
    onChooseDesignForColor: (Int) -> Unit,
    onPhotoForColor: (Int) -> Unit,
    onColorLabelChanged: (Int, String) -> Unit
) {
        if (item.isPrint) {
            if (color.hasAttachment) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                        model = color.attachmentUri,
                        contentDescription = color.attachmentName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Slate200, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = color.attachmentName ?: "Attached",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800,
                            maxLines = 1
                        )
                        Text(
                            text = if (color.attachmentKind == "design") "Design" else "Photo",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }
                    IconButton(
                        onClick = { onClearColorAttachment(colorIdx) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = RoseDestructive,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onChooseDesignForColor(colorIdx) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Slate700
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Design", fontSize = 11.sp, color = Slate800, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onPhotoForColor(colorIdx) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Slate700
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Photo", fontSize = 11.sp, color = Slate800)
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = color.label,
                onValueChange = { onColorLabelChanged(colorIdx, it) },
                placeholder = {
                    Text("Color ${colorIdx + 1} (optional)", color = Slate400, fontSize = 12.sp)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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
}

