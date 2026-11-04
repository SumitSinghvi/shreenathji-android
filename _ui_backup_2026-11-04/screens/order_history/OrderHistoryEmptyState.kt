package com.shreenathji.sales.ui.screens.order_history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenathji.sales.ui.theme.*

@Composable
fun OrderHistoryEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = Slate300
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No orders found",
                style = MaterialTheme.typography.titleMedium,
                color = Slate700,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Punched orders will appear here for dispatch tracking",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
            )
        }
    }
}
