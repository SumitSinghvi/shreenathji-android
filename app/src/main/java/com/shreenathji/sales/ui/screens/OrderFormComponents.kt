package com.shreenathji.sales.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shreenathji.sales.BuildConfig
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnHeader
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.screens.order.ImageSourceBottomSheet
import com.shreenathji.sales.ui.screens.order.ImageUploadSection
import com.shreenathji.sales.ui.screens.order.ItemDetailsCard
import com.shreenathji.sales.ui.screens.order.OrderStickySubmitBar
import com.shreenathji.sales.ui.screens.order.PartyDetailsSection
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.util.ImageUtils
import com.shreenathji.sales.ui.viewmodels.OrderFormViewModel
import com.shreenathji.sales.ui.viewmodels.OrderSavedSummary
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun OrderSavedSummaryBlock(
    partyName: String,
    totalMeters: Double,
    rates: List<Double>,
    currencyFormatter: NumberFormat
) {
    val metersText = if (totalMeters == totalMeters.toLong().toDouble()) {
        "${totalMeters.toLong()} Mtrs"
    } else {
        "$totalMeters Mtrs"
    }
    val rateText = rates.joinToString(" · ") { currencyFormatter.format(it) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderSummaryRow(label = "Party", value = partyName)
            OrderSummaryRow(label = "Meters", value = metersText)
            if (rateText.isNotBlank()) {
                OrderSummaryRow(label = "Rate", value = rateText)
            }
        }
    }
}

@Composable
internal fun OrderSummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Slate950,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
internal fun OrderFormStatusBanner(
    message: String,
    isWarning: Boolean
) {
    val bg = if (isWarning) AmberWarningBg else SkyInfoBg
    val border = if (isWarning) AmberWarningBorder else SkyInfoBorder
    val tint = if (isWarning) AmberWarningText else SkyInfoText
    val icon = if (isWarning) Icons.Default.Warning else Icons.Default.Info

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}

@Composable
internal fun OrderSavedDialog(
    summary: OrderSavedSummary?,
    currencyFormatter: NumberFormat,
    onDone: () -> Unit
) {
    Dialog(onDismissRequest = { /* require Done */ }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ShadcnWhite,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Order saved",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate950
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You can find it under Orders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OrderSavedSummaryBlock(
                        partyName = summary.partyName,
                        totalMeters = summary.totalMeters,
                        rates = summary.rates,
                        currencyFormatter = currencyFormatter
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
