package com.shreenathji.sales.ui.screens.dispatch

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shreenathji.sales.data.remote.dto.DispatchOrderDto
import com.shreenathji.sales.data.remote.dto.DispatchRecordDto
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.components.SearchPickerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnCard
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.components.ShadcnPrimaryButton
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.DispatchDateFilter
import com.shreenathji.sales.ui.viewmodels.DispatchDateUtils
import com.shreenathji.sales.ui.viewmodels.DispatchFilters
import com.shreenathji.sales.ui.viewmodels.DispatchFormInput
import com.shreenathji.sales.ui.viewmodels.DispatchItemInput
import com.shreenathji.sales.ui.viewmodels.DispatchViewModel

internal fun fmtMeter(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

/** Cleans item name and separates component tag to avoid '(Top) (TOP)' duplication. */
internal fun cleanItem(name: String, component: String?): Pair<String, String?> {
    val comp = component?.trim()?.ifBlank { null }
    var clean = name.trim()
    if (comp != null && clean.contains("($comp)", ignoreCase = true)) {
        clean = clean.replace(Regex("\\s*\\(${Regex.escape(comp)}\\)\\s*", RegexOption.IGNORE_CASE), " ").trim()
        return clean to comp.uppercase()
    }
    val match = Regex("""^(.*?)\s*\((Top|Bottom|Dupatta|Set)\)\s*$""", RegexOption.IGNORE_CASE).find(clean)
    if (match != null) {
        return match.groupValues[1].trim() to (comp ?: match.groupValues[2]).uppercase()
    }
    return clean to comp?.uppercase()
}

/** Home for dispatch logins: what to dispatch, and what I already dispatched. */
@Composable
internal fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Slate500, fontSize = 13.sp)
    }
}

