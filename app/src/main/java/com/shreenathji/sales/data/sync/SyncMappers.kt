package com.shreenathji.sales.data.sync

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.LicenseSessionGuard
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.dao.DailyVisitPlanDao
import com.shreenathji.sales.data.local.dao.DesignDao
import com.shreenathji.sales.data.local.dao.DesignFolderDao
import com.shreenathji.sales.data.local.dao.LocationDao
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.dao.PartyDao
import com.shreenathji.sales.data.local.dao.ProductDao
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.DesignEntity
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.remote.dto.DesignDto
import com.shreenathji.sales.data.remote.dto.DesignFolderDto
import com.shreenathji.sales.data.remote.dto.PartyDto
import com.shreenathji.sales.data.remote.dto.ProductDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val gson = Gson()

internal fun toPartyEntity(it: PartyDto) = PartyEntity(
    id = it.id,
    businessName = it.business_name,
    contactPerson = it.contact_person,
    phone = it.phone,
    addressLine1 = it.address_line1,
    addressLine2 = it.address_line2,
    defaultAgent = it.default_agent,
    latitude = it.latitude,
    longitude = it.longitude,
    assignedRepIdsJson = if (it.assigned_rep_ids != null) gson.toJson(it.assigned_rep_ids) else null
)

internal fun toProductEntity(it: ProductDto): ProductEntity {
    val mult = it.matching_multiplier ?: if (it.is_matching) 2 else 1
    return ProductEntity(
        id = it.id,
        name = it.name,
        category = it.category ?: "Dying",
        defaultRate = it.default_rate,
        unit = it.unit,
        isMatching = mult > 1 || it.is_matching,
        matchingMultiplier = mult,
        isTopBottom = it.is_top_bottom == true,
        defaultRateBottom = it.default_rate_bottom
    )
}

internal fun toFolderEntity(it: DesignFolderDto) = DesignFolderEntity(
    id = it.id,
    name = it.name,
    parentId = it.parent_id,
    sortOrder = it.sort_order,
    coverDesignId = it.cover_design_id,
    createdAt = it.created_at
)

internal fun toDesignEntity(it: DesignDto) = DesignEntity(
    id = it.id,
    imageUrl = it.image_url,
    thumbUrl = it.thumb_url,
    originalFilename = it.original_filename,
    notes = it.notes,
    uploadedByStaffId = it.uploaded_by_staff_id,
    uploadedByName = it.uploaded_by_name,
    capturedOn = it.captured_on,
    isArchived = it.is_archived,
    folderId = it.folder_id,
    createdAt = it.created_at
)

internal fun formatIsoUtcDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(timestamp))
}
