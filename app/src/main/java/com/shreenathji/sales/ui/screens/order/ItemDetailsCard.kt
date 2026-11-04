package com.shreenathji.sales.ui.screens.order

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
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.OrderItemDraft
import java.text.NumberFormat

@Composable
fun ItemDetailsCard(
    itemIndex: Int,
    item: OrderItemDraft,
    availableProducts: List<ProductEntity>,
    currencyFormatter: NumberFormat,
    canDelete: Boolean,
    onProductSelected: (ProductEntity) -> Unit,
    onToggleMatching: () -> Unit,
    onToggleTopBottom: () -> Unit = {},
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onBottomPriceChanged: (String) -> Unit = {},
    onMetersPerColorChanged: (String) -> Unit,
    onBottomMetersPerColorChanged: (String) -> Unit = {},
    onColorCountChanged: (String) -> Unit,
    onAddColor: () -> Unit,
    onRemoveColor: (Int) -> Unit,
    onColorLabelChanged: (Int, String) -> Unit,
    onColorMeterChanged: (Int, String) -> Unit,
    onColorBottomMeterChanged: (Int, String) -> Unit = { _, _ -> },
    onChooseDesignForColor: (Int) -> Unit,
    onPhotoForColor: (Int) -> Unit,
    onClearColorAttachment: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var productDropdownExpanded by remember { mutableStateOf(false) }
    var colorCountText by remember(item.id) { mutableStateOf(item.colors.size.toString()) }

    LaunchedEffect(item.colors.size) {
        colorCountText = item.colors.size.toString()
    }

    val filteredProducts = remember(item.name, availableProducts) {
        if (item.name.isBlank()) {
            availableProducts
        } else {
            availableProducts.filter {
                it.name.contains(item.name, ignoreCase = true) ||
                    it.category.contains(item.name, ignoreCase = true)
            }
        }
    }

    val meterPresets = listOf(60, 100, 120, 240)

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 1.5.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate900,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${itemIndex + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fabric ${itemIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700,
                        letterSpacing = 0.3.sp,
                        fontSize = 13.sp
                    )
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove fabric",
                            tint = RoseDestructive,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Options toggles row (Matching & Top/Bottom)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val mult = item.effectiveMultiplier
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (mult) {
                        4 -> Color(0xFFF3E8FF)
                        2 -> AmberWarningBg
                        else -> Slate100
                    },
                    border = BorderStroke(1.dp, when (mult) {
                        4 -> Color(0xFFC084FC)
                        2 -> AmberWarningText.copy(alpha = 0.5f)
                        else -> Slate200
                    }),
                    modifier = Modifier.clickable { onToggleMatching() }
                ) {
                    Text(
                        text = when (mult) {
                            4 -> "✓ 4-Matching (Set: 4×)"
                            2 -> "✓ 2-Matching (Pair: 2×)"
                            else -> "+ Matching (1×)"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (mult) {
                            4 -> Color(0xFF7E22CE)
                            2 -> AmberWarningText
                            else -> Slate600
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isTopBottom) Color(0xFFEEF2FF) else Slate100,
                    border = BorderStroke(1.dp, if (item.isTopBottom) Color(0xFF818CF8) else Slate200),
                    modifier = Modifier.clickable { onToggleTopBottom() }
                ) {
                    Text(
                        text = if (item.isTopBottom) "✓ Top & Bottom (Paired)" else "+ Top & Bottom",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isTopBottom) Color(0xFF3730A3) else Slate600,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { query ->
                        onNameChanged(query)
                        productDropdownExpanded = true
                    },
                    label = { Text("Fabric name *", fontSize = 14.sp, color = Slate600) },
                    placeholder = { Text("e.g. Rayon, Reva, Cotton Slub...", color = Slate400) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (item.name.isNotBlank()) BrandOrange else Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { productDropdownExpanded = !productDropdownExpanded }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Catalog",
                                tint = Slate900
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = BrandOrange,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )

                if (filteredProducts.isNotEmpty()) {
                    DropdownMenu(
                        expanded = productDropdownExpanded,
                        onDismissRequest = { productDropdownExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        filteredProducts.forEach { product ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = product.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
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
                                },
                                onClick = {
                                    onProductSelected(product)
                                    productDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            focusedBorderColor = BrandOrange,
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
                            focusedBorderColor = BrandOrange,
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
                        focusedBorderColor = BrandOrange,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            focusedBorderColor = BrandOrange,
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
                            focusedBorderColor = BrandOrange,
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
                        colorCountText = raw
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
                                    colorCountText = item.colors.size.toString()
                                } else {
                                    val count = parsed.coerceIn(1, 50)
                                    colorCountText = count.toString()
                                    onColorCountChanged(count.toString())
                                }
                            }
                        },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = BrandOrange,
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
                            focusedBorderColor = BrandOrange,
                            unfocusedBorderColor = Slate200,
                            focusedTextColor = Slate950,
                            unfocusedTextColor = Slate950
                        )
                    )

                    OutlinedTextField(
                        value = colorCountText,
                        onValueChange = { raw ->
                            colorCountText = raw
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
                                        colorCountText = item.colors.size.toString()
                                    } else {
                                        val count = parsed.coerceIn(1, 50)
                                        colorCountText = count.toString()
                                        onColorCountChanged(count.toString())
                                    }
                                }
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ShadcnWhite,
                            unfocusedContainerColor = ShadcnWhite,
                            focusedBorderColor = BrandOrange,
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

            Spacer(modifier = Modifier.height(12.dp))

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
                                                focusedBorderColor = BrandOrange,
                                                unfocusedBorderColor = Slate200,
                                                focusedTextColor = Slate950,
                                                unfocusedTextColor = Slate950
                                            )
                                        )
                                    }

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
                                                focusedBorderColor = BrandOrange,
                                                unfocusedBorderColor = Slate200,
                                                focusedTextColor = Slate950,
                                                unfocusedTextColor = Slate950
                                            )
                                        )
                                    }

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

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val mult = item.effectiveMultiplier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colorCountStr = "${item.colors.size} color${if (item.colors.size != 1) "s" else ""}"
                        val totalMtrFormatted = "${NumberFormat.getNumberInstance().format(item.totalMeters)} Mtr"
                        Text(
                            text = "$colorCountStr · $totalMtrFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                        Text(
                            text = currencyFormatter.format(item.totalPrice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate950
                        )
                    }

                    if (item.isTopBottom || mult > 1) {
                        val subText = if (item.isTopBottom) {
                            val topM = NumberFormat.getNumberInstance().format(item.baseTopMeters)
                            val btmM = NumberFormat.getNumberInstance().format(item.baseBottomMeters)
                            val multSuffix = if (mult > 1) " (×$mult ${if (mult == 4) "set" else "pair"})" else ""
                            "Top: ${topM}m @ ₹${item.price} · Btm: ${btmM}m @ ₹${item.bottomPrice}$multSuffix"
                        } else {
                            val unitWord = if (mult == 4) "set" else "pair"
                            "${NumberFormat.getNumberInstance().format(item.baseMeters)}m × $mult $unitWord"
                        }
                        Text(
                            text = subText,
                            fontSize = 11.sp,
                            color = Slate500,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
