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
internal fun ItemRateFields(
    item: OrderItemDraft,
    onPriceChanged: (String) -> Unit,
    onBottomPriceChanged: (String) -> Unit
) {
    if (item.isTopBottom) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = item.priceText,
                onValueChange = onPriceChanged,
                label = { Text("Top Rate (₹/Mtr) *", fontSize = 13.sp, color = Slate600) },
                placeholder = { Text("68.00", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                value = item.bottomPriceText,
                onValueChange = onBottomPriceChanged,
                label = { Text("Bottom Rate (₹/Mtr) *", fontSize = 13.sp, color = Slate600) },
                placeholder = { Text("55.00", color = Slate400) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
    } else {
        OutlinedTextField(
            value = item.priceText,
            onValueChange = onPriceChanged,
            label = { Text("Rate (₹/Mtr) *", fontSize = 14.sp, color = Slate600) },
            placeholder = { Text("68.00", color = Slate400) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
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
