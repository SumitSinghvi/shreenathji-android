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
internal fun ItemTotalsSummary(
    item: OrderItemDraft,
    currencyFormatter: NumberFormat
) {
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
