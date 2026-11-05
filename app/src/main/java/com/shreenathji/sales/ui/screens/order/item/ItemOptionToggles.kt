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
internal fun ItemOptionToggles(
    item: OrderItemDraft,
    onToggleMatching: () -> Unit,
    onToggleTopBottom: () -> Unit
) {
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
}
