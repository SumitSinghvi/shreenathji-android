package com.shreenathji.sales.ui.screens.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*
import java.text.NumberFormat

@Composable
fun OrderSubmitSection(
    grandTotal: Double,
    qualityCount: Int,
    colorCount: Int,
    currencyFormatter: NumberFormat,
    isSubmitting: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OrderTotalSummary(
                grandTotal = grandTotal,
                qualityCount = qualityCount,
                colorCount = colorCount,
                currencyFormatter = currencyFormatter
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = Slate200)
            Spacer(modifier = Modifier.height(14.dp))

            SaveOrderButton(
                isSubmitting = isSubmitting,
                enabled = enabled,
                onSubmit = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Compact sticky bar pinned above bottom navigation so Save stays visible while scrolling.
 */
@Composable
fun OrderStickySubmitBar(
    grandTotal: Double,
    qualityCount: Int,
    colorCount: Int,
    currencyFormatter: NumberFormat,
    isSubmitting: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ShadcnWhite,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OrderTotalSummary(
                grandTotal = grandTotal,
                qualityCount = qualityCount,
                colorCount = colorCount,
                currencyFormatter = currencyFormatter,
                compact = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            SaveOrderButton(
                isSubmitting = isSubmitting,
                enabled = enabled,
                onSubmit = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OrderTotalSummary(
    grandTotal: Double,
    qualityCount: Int,
    colorCount: Int,
    currencyFormatter: NumberFormat,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TOTAL",
                style = MaterialTheme.typography.labelSmall,
                color = Slate500,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currencyFormatter.format(grandTotal),
                style = if (compact) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                color = Slate950
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BrandOrangeLight,
            border = BorderStroke(1.dp, BrandOrangeBorder),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = "$qualityCount fabric${if (qualityCount != 1) "s" else ""} · $colorCount color${if (colorCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = BrandOrangeDark,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SaveOrderButton(
    isSubmitting: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShadcnPrimaryButton(
        onClick = onSubmit,
        enabled = enabled && !isSubmitting,
        containerColor = if (enabled && !isSubmitting) BrandOrange else Slate200,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Saving order...",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        } else {
            Icon(
                imageVector = if (enabled) Icons.Default.ShoppingCartCheckout else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save order",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
