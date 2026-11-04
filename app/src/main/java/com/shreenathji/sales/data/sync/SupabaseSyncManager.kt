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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SupabaseSyncManager(
    private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val partyDao: PartyDao,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val locationDao: LocationDao,
    private val dailyVisitPlanDao: DailyVisitPlanDao,
    private val designDao: DesignDao,
    private val designFolderDao: DesignFolderDao,
    private val authManager: AuthManager,
    private val licenseSessionGuard: LicenseSessionGuard? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val gson = Gson()
    private var consecutiveLocationFailures = 0

    private val syncMutex = Mutex()
    @Volatile private var lastSyncFinishedAt = 0L

    /**
     * Coalesced full sync. App start, tab switches and screen inits all call this;
     * a run already in flight, or one that finished < [MIN_SYNC_INTERVAL_MS] ago, is skipped.
     * Immediate pushes (new order/party) use the syncPending* functions directly.
     */
    fun syncAll() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncFinishedAt < MIN_SYNC_INTERVAL_MS) return
        scope.launch {
            if (!syncMutex.tryLock()) return@launch
            try {
                if (licenseSessionGuard != null && !licenseSessionGuard.validateOrWipe()) {
                    Log.w("SupabaseSync", "Skipping sync — license session invalidated")
                    return@launch
                }
                supabaseClient.ensureAuthenticatedSession()
                // Push local-only creates first so they exist on the server before pull/replace
                syncPendingParties()
                syncPendingOrders()
                syncPendingVisitPlans()
                syncPendingLocations()
                syncMasterData()
                lastSyncFinishedAt = SystemClock.elapsedRealtime()
            } finally {
                syncMutex.unlock()
            }
        }
    }

    /**
     * Retry offline-created parties. Create-time [pushPartyAsync] is best-effort;
     * without this, stores stay "Not sent yet" forever after reconnect/restart.
     */
    suspend fun syncPendingParties() {
        try {
            val unsynced = partyDao.getUnsyncedParties()
            if (unsynced.isEmpty()) return
            Log.d("SupabaseSync", "Pushing ${unsynced.size} unsynced local parties")
            unsynced.forEach { party ->
                val success = supabaseClient.pushParty(party)
                if (success) {
                    partyDao.markPartySynced(party.id)
                    Log.d("SupabaseSync", "Party ${party.businessName} synced to Supabase")
                } else {
                    Log.w("SupabaseSync", "Party ${party.businessName} push failed — will retry later")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending parties: ${e.message}", e)
        }
    }

    suspend fun syncMasterData() {
        try {
            // 1. Sync Staff — null pin_code must not abort parties/products sync
            try {
                val remoteStaff = supabaseClient.getStaff()
                if (remoteStaff != null) {
                    val staffList = remoteStaff.map {
                        StaffMember(
                            id = it.id,
                            name = it.name,
                            role = it.role,
                            pinCode = it.pin_code ?: "0000",
                            phone = it.phone,
                            orderPrefix = it.order_prefix
                        )
                    }
                    authManager.updateStaffList(staffList)
                    Log.d("SupabaseSync", "Synced ${staffList.size} staff members from Supabase")
                } else {
                    Log.w("SupabaseSync", "getStaff returned null — skipping staff cache update")
                }
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Staff sync failed (continuing): ${e.message}", e)
            }

            // 2. Sync Parties
            val remoteParties = supabaseClient.getParties()
            if (remoteParties != null) {
                val partyEntities = remoteParties.map {
                    PartyEntity(
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
                }
                partyDao.syncReplaceParties(partyEntities)
                val allottedCount = partyEntities.count { !it.assignedRepIdsJson.isNullOrBlank() }
                Log.d("SupabaseSync", "Synced and replaced ${partyEntities.size} parties from Supabase ($allottedCount have assigned_rep_ids)")
                if (partyEntities.isEmpty()) {
                    Log.w("SupabaseSync", "Parties sync returned 0 rows — check license headers / RLS")
                }
                if (allottedCount > 0) {
                    val sample = partyEntities.filter { !it.assignedRepIdsJson.isNullOrBlank() }.take(3)
                    sample.forEach { p ->
                        Log.d("SupabaseSync", "  Party '${p.businessName}' assigned_rep_ids = ${p.assignedRepIdsJson}")
                    }
                }
            } else {
                Log.w("SupabaseSync", "getParties returned null")
            }

            // 3. Sync Products / Qualities
            val remoteProducts = supabaseClient.getProducts()
            if (remoteProducts != null) {
                val productEntities = remoteProducts.map {
                    val mult = it.matching_multiplier ?: if (it.is_matching) 2 else 1
                    ProductEntity(
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
                productDao.syncReplaceProducts(productEntities)
                Log.d("SupabaseSync", "Synced and replaced ${productEntities.size} products from Supabase")
            }

            // 4. Sync Designs + folders
            val remoteFolders = supabaseClient.getDesignFolders()
            if (remoteFolders != null) {
                val folderEntities = remoteFolders.map {
                    DesignFolderEntity(
                        id = it.id,
                        name = it.name,
                        parentId = it.parent_id,
                        sortOrder = it.sort_order,
                        coverDesignId = it.cover_design_id,
                        createdAt = it.created_at
                    )
                }
                designFolderDao.syncReplaceFolders(folderEntities)
                Log.d("SupabaseSync", "Synced and replaced ${folderEntities.size} design folders from Supabase")
            }

            val remoteDesigns = supabaseClient.getDesigns()
            if (remoteDesigns != null) {
                val designEntities = remoteDesigns.map {
                    DesignEntity(
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
                }
                designDao.syncReplaceDesigns(designEntities)
                Log.d("SupabaseSync", "Synced and replaced ${designEntities.size} designs from Supabase")
            }

            val remotePlans = supabaseClient.getVisitPlans()
            if (remotePlans != null) {
                val isoFormats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                )
                val planEntities = remotePlans.map {
                    val visitedTimestamp = if (!it.visited_at.isNullOrBlank()) {
                        try {
                            val raw = it.visited_at
                            val isUtc = raw.endsWith("Z", ignoreCase = true) || raw.contains("+00")
                            val clean = raw.replace("Z", "").take(23)
                            var parsedTime: Long? = null
                            for (fmt in isoFormats) {
                                try {
                                    fmt.timeZone = if (isUtc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                                    parsedTime = fmt.parse(clean)?.time
                                    if (parsedTime != null) break
                                } catch (_: Exception) {}
                            }
                            parsedTime ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            null
                        }
                    } else null

                    DailyVisitPlanEntity(
                        id = it.id,
                        planDate = it.plan_date,
                        salesRepId = it.sales_rep_id,
                        salesRepName = it.sales_rep_name,
                        partyId = it.party_id,
                        partyName = it.party_name,
                        partyAddress = it.party_address,
                        priorityOrder = it.priority_order,
                        instructions = it.instructions,
                        visitPurpose = it.visit_purpose,
                        status = it.status,
                        visitedAt = visitedTimestamp,
                        orderId = it.order_id,
                        remarks = it.remarks,
                        isSynced = true
                    )
                }
                dailyVisitPlanDao.syncReplacePlans(planEntities)
                Log.d("SupabaseSync", "Synced and replaced ${planEntities.size} daily visit plans")
            }

            // 5. Sync Past Orders & Line Items from Supabase
            syncOrdersFromSupabase()
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing master data: ${e.message}", e)
        }
    }

    suspend fun syncOrdersFromSupabase() {
        try {
            val remoteOrders = supabaseClient.getOrdersWithItems()
            if (remoteOrders != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val orderEntities = mutableListOf<OrderEntity>()
                val allItemEntities = mutableListOf<OrderItemEntity>()

                remoteOrders.forEach { remote ->
                    val timestamp = try {
                        val dateStr = remote.created_at ?: remote.order_timestamp
                        if (!dateStr.isNullOrBlank()) {
                            val cleanStr = dateStr.take(19)
                            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
                        } else {
                            System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }

                    val imageUrisJson = if (!remote.image_urls.isNullOrEmpty()) {
                        gson.toJson(remote.image_urls)
                    } else null

                    val designIdsJson = if (!remote.design_ids.isNullOrEmpty()) {
                        gson.toJson(remote.design_ids)
                    } else null

                    val orderEntity = OrderEntity(
                        id = remote.id,
                        orderNumber = remote.order_number,
                        partyId = remote.party_id ?: "",
                        partyName = remote.party_name,
                        partyAddress = remote.party_address,
                        agent = remote.agent,
                        poNumber = remote.po_number,
                        deliveryAddress = remote.delivery_address,
                        imageUrisJson = imageUrisJson,
                        designIdsJson = designIdsJson,
                        totalAmount = remote.total_amount,
                        orderStatus = remote.order_status,
                        rejectionReason = remote.rejection_reason,
                        notes = remote.notes,
                        salesRepId = remote.sales_rep_id,
                        salesRepName = remote.sales_rep_name,
                        orderTimestamp = timestamp,
                        latitude = remote.latitude,
                        longitude = remote.longitude,
                        isSynced = true
                    )
                    orderEntities.add(orderEntity)

                    val itemEntities = remote.order_items?.map { item ->
                        OrderItemEntity(
                            id = item.id,
                            orderId = remote.id,
                            productName = item.product_name,
                            meter = item.meter,
                            price = item.price,
                            totalPrice = item.total_price,
                            notes = item.notes,
                            designId = item.design_id,
                            imageUrl = item.image_url,
                            isMatching = item.is_matching,
                            multiplier = item.multiplier,
                            cutMeter = item.cut_meter,
                            component = item.component,
                            setGroupId = item.set_group_id
                        )
                    } ?: emptyList()
                    allItemEntities.addAll(itemEntities)
                }

                orderDao.syncReplaceOrders(orderEntities, allItemEntities)
                Log.d("SupabaseSync", "Synced and replaced ${orderEntities.size} past orders with ${allItemEntities.size} items from cloud")
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing orders from cloud: ${e.message}")
        }
    }

    /** Upload local order images; returns map of original uri string → remote (or already-remote) URL. */
    private fun remapItemImageUrls(
        items: List<OrderItemEntity>,
        uriToRemote: Map<String, String>
    ): List<OrderItemEntity> {
        if (uriToRemote.isEmpty()) return items
        return items.map { item ->
            val local = item.imageUrl ?: return@map item
            val remote = uriToRemote[local] ?: return@map item
            if (remote == local) item else item.copy(imageUrl = remote)
        }
    }

    private suspend fun uploadAndRemap(
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): Pair<List<String>, List<OrderItemEntity>> {
        val mapped = linkedMapOf<String, String>()
        if (!order.imageUrisJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val uriStrings: List<String> = gson.fromJson(order.imageUrisJson, type)
                uriStrings.forEach { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") {
                            mapped[uriStr] = uriStr
                            return@forEach
                        }
                        val inputStream = if (scheme == "content" || scheme == "file") {
                            context.contentResolver.openInputStream(uri)
                        } else {
                            File(uri.path ?: uriStr).inputStream()
                        }
                        val bytes = inputStream?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val uploadedUrl = supabaseClient.uploadOrderImage(bytes, order.orderNumber)
                            if (!uploadedUrl.isNullOrBlank()) {
                                mapped[uriStr] = uploadedUrl
                            }
                        }
                    } catch (imgErr: Exception) {
                        Log.e("SupabaseSync", "Failed to upload swatch image $uriStr: ${imgErr.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error parsing imageUrisJson: ${e.message}")
            }
        }
        return mapped.values.toList() to remapItemImageUrls(items, mapped)
    }

    suspend fun syncPendingOrders() {
        try {
            supabaseClient.ensureAuthenticatedSession()
            val unsynced = orderDao.getUnsyncedOrders()
            if (unsynced.isEmpty()) return
            Log.d("SupabaseSync", "Pushing ${unsynced.size} unsynced orders to Supabase")
            unsynced.forEach { orderWithItems ->
                var currentOrder = orderWithItems.order
                val (uploadedUrls, remappedItems) = uploadAndRemap(currentOrder, orderWithItems.items)

                var success: Boolean
                try {
                    success = supabaseClient.pushOrder(
                        order = currentOrder,
                        items = remappedItems,
                        uploadedImageUrls = uploadedUrls.ifEmpty { null }
                    )
                } catch (conflict: com.shreenathji.sales.data.remote.api.OrderConflictException) {
                    Log.w("SupabaseSync", "Collision on orderNumber ${currentOrder.orderNumber}, regenerating...")
                    val prefix = currentOrder.orderNumber.substringBefore("-", "ST")
                    val newNumber = "$prefix-${100000 + kotlin.random.Random.nextInt(900000)}"
                    orderDao.updateOrderNumber(currentOrder.id, newNumber)
                    currentOrder = currentOrder.copy(orderNumber = newNumber)
                    success = supabaseClient.pushOrder(
                        order = currentOrder,
                        items = remappedItems,
                        uploadedImageUrls = uploadedUrls.ifEmpty { null }
                    )
                }
                if (!success) {
                    kotlinx.coroutines.delay(1000)
                    try {
                        success = supabaseClient.pushOrder(
                            order = currentOrder,
                            items = remappedItems,
                            uploadedImageUrls = uploadedUrls.ifEmpty { null }
                        )
                    } catch (conflict: com.shreenathji.sales.data.remote.api.OrderConflictException) {
                        val prefix = currentOrder.orderNumber.substringBefore("-", "ST")
                        val newNumber = "$prefix-${100000 + kotlin.random.Random.nextInt(900000)}"
                        orderDao.updateOrderNumber(currentOrder.id, newNumber)
                        currentOrder = currentOrder.copy(orderNumber = newNumber)
                        success = supabaseClient.pushOrder(
                            order = currentOrder,
                            items = remappedItems,
                            uploadedImageUrls = uploadedUrls.ifEmpty { null }
                        )
                    }
                }
                if (success) {
                    orderDao.markOrderAsSynced(currentOrder.id)
                    Log.d("SupabaseSync", "Order ${currentOrder.orderNumber} synced to Supabase with ${uploadedUrls.size} images")
                } else {
                    Log.w("SupabaseSync", "Order ${currentOrder.orderNumber} sync failed — will retry on next sync")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending orders: ${e.message}", e)
        }
    }

    private fun formatIsoUtcDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(timestamp))
    }

    suspend fun syncPendingVisitPlans() {
        try {
            val unsynced = dailyVisitPlanDao.getUnsyncedPlans()
            unsynced.forEach { plan ->
                val success = supabaseClient.pushVisitPlan(plan)
                if (success) {
                    dailyVisitPlanDao.markPlanAsSynced(plan.id)
                } else {
                    val isoDate = plan.visitedAt?.let { formatIsoUtcDate(it) }
                    val updateSuccess = supabaseClient.updateVisitPlanStatus(
                        planId = plan.id,
                        status = plan.status,
                        visitedAt = isoDate,
                        orderId = plan.orderId
                    )
                    if (updateSuccess) {
                        dailyVisitPlanDao.markPlanAsSynced(plan.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing visit plan updates: ${e.message}")
        }
    }

    fun pushPartyAsync(party: PartyEntity) {
        scope.launch {
            val success = supabaseClient.pushParty(party)
            if (success) {
                partyDao.markPartySynced(party.id)
                Log.d("SupabaseSync", "Party ${party.businessName} pushed to Supabase")
            } else {
                Log.w(
                    "SupabaseSync",
                    "Failed to push party ${party.businessName} — saved locally; will retry on next sync"
                )
            }
        }
    }

    suspend fun uploadVisitVoiceNote(audioBytes: ByteArray, planId: String): String? {
        val url = supabaseClient.uploadVisitVoiceNote(audioBytes, planId)
        if (url == null) {
            Log.w("SupabaseSync", "Voice note upload returned null for plan $planId (${audioBytes.size} bytes)")
        }
        return url
    }

    fun pushOrderAsync(order: OrderEntity, items: List<OrderItemEntity>) {
        scope.launch {
            try {
                val (uploadedUrls, remappedItems) = uploadAndRemap(order, items)
                var success = supabaseClient.pushOrder(
                    order = order,
                    items = remappedItems,
                    uploadedImageUrls = uploadedUrls.ifEmpty { null }
                )
                if (!success) {
                    kotlinx.coroutines.delay(1000)
                    success = supabaseClient.pushOrder(
                        order = order,
                        items = remappedItems,
                        uploadedImageUrls = uploadedUrls.ifEmpty { null }
                    )
                }
                if (success) {
                    orderDao.markOrderAsSynced(order.id)
                    Log.d("SupabaseSync", "Order ${order.orderNumber} pushed live to Supabase with ${uploadedUrls.size} images")
                } else {
                    Log.w("SupabaseSync", "Order ${order.orderNumber} live push failed — retained locally (isSynced=0) for background sync")
                }
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Exception in pushOrderAsync: ${e.message}", e)
            }
        }
    }

    fun pushNewVisitPlanAsync(plan: DailyVisitPlanEntity) {
        scope.launch {
            val success = supabaseClient.pushVisitPlan(plan)
            if (success) {
                dailyVisitPlanDao.markPlanAsSynced(plan.id)
                Log.d("SupabaseSync", "Visit plan ${plan.id} for party '${plan.partyName}' pushed to Supabase")
            } else {
                val isoDate = plan.visitedAt?.let { formatIsoUtcDate(it) }
                val updateSuccess = supabaseClient.updateVisitPlanStatus(
                    planId = plan.id,
                    status = plan.status,
                    visitedAt = isoDate,
                    orderId = plan.orderId
                )
                if (updateSuccess) {
                    dailyVisitPlanDao.markPlanAsSynced(plan.id)
                }
            }
        }
    }

    fun deleteVisitPlanAsync(planId: String) {
        scope.launch {
            dailyVisitPlanDao.deletePlan(planId)
            supabaseClient.deleteVisitPlan(planId)
        }
    }

    fun pushVisitPlanStatusAsync(planId: String, status: String, visitedAt: Long?, orderId: String?, remarks: String? = null) {
        scope.launch {
            val isoDate = visitedAt?.let { formatIsoUtcDate(it) }
            val success = supabaseClient.updateVisitPlanStatus(
                planId = planId,
                status = status,
                visitedAt = isoDate,
                orderId = orderId,
                remarks = remarks
            )
            if (success) {
                dailyVisitPlanDao.updatePlanStatus(
                    id = planId,
                    status = status,
                    visitedAt = visitedAt,
                    remarks = remarks,
                    orderId = orderId,
                    isSynced = true
                )
            }
        }
    }

    suspend fun syncPendingLocations() {
        try {
            // Prune old logs (>48h) to prevent unbounded local DB growth
            val cutoff = System.currentTimeMillis() - (48 * 3600 * 1000L)
            locationDao.pruneOldLocations(cutoff)

            val unsynced = locationDao.getUnsyncedLocations()
            if (unsynced.isEmpty()) return

            val staff = authManager.getCurrentStaff()
            val success = supabaseClient.pushLocationLogsBatch(
                logs = unsynced,
                salesRepId = staff?.id,
                salesRepName = staff?.name
            )

            if (success) {
                consecutiveLocationFailures = 0
                locationDao.markLocationsSynced(unsynced.map { it.id })
                Log.d("SupabaseSync", "Backfilled ${unsynced.size} offline location logs to Supabase in 1 batch")
            } else {
                consecutiveLocationFailures++
                Log.w("SupabaseSync", "Location batch sync failed (attempt $consecutiveLocationFailures)")
                if (consecutiveLocationFailures >= 5) {
                    licenseSessionGuard?.validateOrWipe()
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending locations: ${e.message}")
        }
    }

    fun pushLocationAsync(log: LocationLogEntity) {
        scope.launch {
            pushLocationAndMark(log)
        }
    }

    suspend fun pushLocationAndMark(log: LocationLogEntity): Boolean {
        val staff = authManager.getCurrentStaff()
        val success = supabaseClient.pushLocationLog(
            log = log,
            salesRepId = staff?.id,
            salesRepName = staff?.name
        )
        if (success) {
            consecutiveLocationFailures = 0
            if (log.id > 0) {
                locationDao.markLocationsSynced(listOf(log.id))
            }
        } else {
            consecutiveLocationFailures++
            if (consecutiveLocationFailures >= 5) {
                licenseSessionGuard?.validateOrWipe()
            }
        }
        return success
    }

    private companion object {
        const val MIN_SYNC_INTERVAL_MS = 30_000L
    }
}
