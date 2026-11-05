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
import coil.compose.AsyncImage
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.ui.components.SearchPickerDialog
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.screens.order.item.*
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
    var colorCountText by remember(item.id) { mutableStateOf(item.colors.size.toString()) }

    LaunchedEffect(item.colors.size) {
        colorCountText = item.colors.size.toString()
    }


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

            ItemOptionToggles(item, onToggleMatching, onToggleTopBottom)

            Spacer(modifier = Modifier.height(10.dp))

            ItemFabricNameField(item, availableProducts, onProductSelected, onNameChanged)

            Spacer(modifier = Modifier.height(10.dp))

            ItemRateFields(item, onPriceChanged, onBottomPriceChanged)

            Spacer(modifier = Modifier.height(10.dp))

            ItemMeterFields(
                item = item,
                colorCountText = colorCountText,
                onColorCountTextChange = { colorCountText = it },
                onMetersPerColorChanged = onMetersPerColorChanged,
                onBottomMetersPerColorChanged = onBottomMetersPerColorChanged,
                onColorCountChanged = onColorCountChanged
            )

            Spacer(modifier = Modifier.height(12.dp))

            ItemColorsList(
                item, onAddColor, onRemoveColor, onColorLabelChanged, onColorMeterChanged,
                onColorBottomMeterChanged, onChooseDesignForColor, onPhotoForColor, onClearColorAttachment
            )

            Spacer(modifier = Modifier.height(12.dp))

            ItemTotalsSummary(item, currencyFormatter)
        }
    }
}
