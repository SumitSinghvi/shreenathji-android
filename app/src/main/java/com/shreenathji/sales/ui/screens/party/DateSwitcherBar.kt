package com.shreenathji.sales.ui.screens.party

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DateSwitcherBar(
    selectedDate: String, // "yyyy-MM-dd"
    isViewingToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    val context = LocalContext.current
    val isoSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displaySdf = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val todayDateStr = remember { isoSdf.format(Date()) }

    val parsedDate = remember(selectedDate) {
        try {
            isoSdf.parse(selectedDate) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    val yesterdayDateStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        isoSdf.format(cal.time)
    }

    val dateLabel = remember(selectedDate) {
        when (selectedDate) {
            todayDateStr -> "Today • " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(parsedDate)
            yesterdayDateStr -> "Yesterday • " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(parsedDate)
            else -> displaySdf.format(parsedDate)
        }
    }

    val isFutureDateBlocked = remember(selectedDate) {
        selectedDate >= todayDateStr
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ShadcnWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
            shadowElevation = 0.5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Day Button
                IconButton(
                    onClick = onPreviousDay,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Day",
                        tint = Slate700,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Date Display & Picker trigger
                Row(
                    modifier = Modifier
                        .clickable {
                            val cal = Calendar.getInstance().apply { time = parsedDate }
                            val dialog = DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth)
                                    }
                                    onSelectDate(isoSdf.format(picked.time))
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            // Restrict to past / today
                            dialog.datePicker.maxDate = System.currentTimeMillis()
                            dialog.show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick Date",
                        tint = if (isViewingToday) Slate900 else BrandOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = dateLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isViewingToday) Slate950 else BrandOrange
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Next Day Button (disabled if viewing today or beyond)
                    IconButton(
                        onClick = onNextDay,
                        enabled = !isFutureDateBlocked,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Day",
                            tint = if (!isFutureDateBlocked) Slate700 else Slate300,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Jump to Today Pill (if on past date)
                    if (!isViewingToday) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate900,
                            modifier = Modifier.clickable { onSelectToday() }
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Read-only notice banner for past dates
        if (!isViewingToday) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = AmberWarningBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarningBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AmberWarningText,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Historical Record • Read Only (Visit status locked)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AmberWarningText
                        )
                    }

                    Text(
                        text = "Go to Today",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarningText,
                        modifier = Modifier
                            .clickable { onSelectToday() }
                            .padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
