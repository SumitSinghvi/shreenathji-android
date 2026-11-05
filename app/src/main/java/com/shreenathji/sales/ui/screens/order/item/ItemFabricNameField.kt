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
internal fun ItemFabricNameField(
    item: OrderItemDraft,
    availableProducts: List<ProductEntity>,
    onProductSelected: (ProductEntity) -> Unit,
    onNameChanged: (String) -> Unit
) {
    var productPickerOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = item.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Fabric name *", fontSize = 14.sp, color = Slate600) },
            placeholder = { Text("Tap to search or select fabric...", color = Slate400) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (item.name.isNotBlank()) BrandOrange else Slate400,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Catalog",
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
                .clickable { productPickerOpen = true }
        )
    }

    if (productPickerOpen) {
        SearchPickerDialog(
            title = "Select fabric",
            placeholder = "Search fabric or category…",
            items = availableProducts,
            matches = { product, q ->
                product.name.contains(q, ignoreCase = true) ||
                    product.category.contains(q, ignoreCase = true)
            },
            onDismiss = { productPickerOpen = false },
            onPick = { product ->
                onProductSelected(product)
                productPickerOpen = false
            },
            onUseTyped = { name ->
                onNameChanged(name)
                productPickerOpen = false
            }
        ) { product ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Slate950
                        )
                        if (product.isMatching || product.matchingMultiplier > 1) {
                            val pMult = if (product.matchingMultiplier > 1) product.matchingMultiplier else 2
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (pMult == 4) Color(0xFFF3E8FF) else AmberWarningBg,
                                border = BorderStroke(1.dp, if (pMult == 4) Color(0xFFC084FC) else AmberWarningText.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (pMult == 4) "4-Matching" else "2-Matching",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pMult == 4) Color(0xFF7E22CE) else AmberWarningText,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (product.isTopBottom) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEEF2FF),
                                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Top & Bottom",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3730A3),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
                Text(
                    text = if (product.isTopBottom && product.defaultRateBottom != null) {
                        "₹${product.defaultRate} / ₹${product.defaultRateBottom}/Mtr"
                    } else {
                        "₹${product.defaultRate}/Mtr"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BrandOrangeDark
                )
            }
        }
    }
}
