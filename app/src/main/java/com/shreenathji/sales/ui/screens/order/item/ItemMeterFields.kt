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
internal fun ItemMeterFields(
    item: OrderItemDraft,
    colorCountText: String,
    onColorCountTextChange: (String) -> Unit,
    onMetersPerColorChanged: (String) -> Unit,
    onBottomMetersPerColorChanged: (String) -> Unit,
    onColorCountChanged: (String) -> Unit
) {
    val meterPresets = listOf(60, 100, 120, 240)

    if (item.isTopBottom) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = item.metersPerColorText,
                onValueChange = onMetersPerColorChanged,
                label = { Text("Top Mtr / Color *", fontSize = 13.sp, color = Slate600) },
                placeholder = { Text("250", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                value = item.bottomMetersPerColorText,
                onValueChange = onBottomMetersPerColorChanged,
                label = { Text("Bottom Mtr / Color *", fontSize = 13.sp, color = Slate600) },
                placeholder = { Text("200", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            value = colorCountText,
            onValueChange = { raw ->
                onColorCountTextChange(raw)
                if (raw.isNotBlank()) {
                    onColorCountChanged(raw)
                }
            },
            label = { Text("How many colors? *", fontSize = 14.sp, color = Slate600) },
            placeholder = { Text("1", color = Slate400) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        val parsed = colorCountText.toIntOrNull()
                        if (colorCountText.isBlank() || parsed == null || parsed < 1) {
                            onColorCountTextChange(item.colors.size.toString())
                        } else {
                            val count = parsed.coerceIn(1, 50)
                            onColorCountTextChange(count.toString())
                            onColorCountChanged(count.toString())
                        }
                    }
                },
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
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = item.metersPerColorText,
                onValueChange = onMetersPerColorChanged,
                label = { Text("Meters per color *", fontSize = 14.sp, color = Slate600) },
                placeholder = { Text("e.g. 250", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                value = colorCountText,
                onValueChange = { raw ->
                    onColorCountTextChange(raw)
                    if (raw.isNotBlank()) {
                        onColorCountChanged(raw)
                    }
                },
                label = { Text("How many colors?", fontSize = 14.sp, color = Slate600) },
                placeholder = { Text("1", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            val parsed = colorCountText.toIntOrNull()
                            if (colorCountText.isBlank() || parsed == null || parsed < 1) {
                                onColorCountTextChange(item.colors.size.toString())
                            } else {
                                val count = parsed.coerceIn(1, 50)
                                onColorCountTextChange(count.toString())
                                onColorCountChanged(count.toString())
                            }
                        }
                    },
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
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Same meters for every color — change one color if needed",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Slate500
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Quick set:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate400
        )
        meterPresets.forEach { preset ->
            val isSelected = item.metersPerColorText == preset.toString()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) BrandOrangeLight else Slate100,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) BrandOrangeBorder else Slate200
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onMetersPerColorChanged(preset.toString())
                        if (item.isTopBottom) {
                            onBottomMetersPerColorChanged(preset.toString())
                        }
                    }
            ) {
                Text(
                    text = "$preset",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) BrandOrangeDark else Slate700,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
