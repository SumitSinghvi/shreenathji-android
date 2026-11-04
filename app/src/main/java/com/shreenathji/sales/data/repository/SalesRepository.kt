package com.shreenathji.sales.data.repository

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
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.data.sync.SupabaseSyncManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class PendingDesignAttachment(
    val designId: String,
    val imageUrl: String
)

class SalesRepository(
    private val partyDao: PartyDao,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val locationDao: LocationDao,
    private val dailyVisitPlanDao: DailyVisitPlanDao,
    private val designDao: DesignDao,
    private val designFolderDao: DesignFolderDao,
    var syncManager: SupabaseSyncManager? = null
) {
    private val pendingDesignAttachments = AtomicReference<List<PendingDesignAttachment>>(emptyList())

    fun setPendingDesignAttachments(items: List<PendingDesignAttachment>) {
        pendingDesignAttachments.set(items)
    }

    fun consumePendingDesignAttachments(): List<PendingDesignAttachment> {
        return pendingDesignAttachments.getAndSet(emptyList())
    }

    // Parties
    val allParties: Flow<List<PartyEntity>> = partyDao.getAllParties()

    fun searchParties(query: String): Flow<List<PartyEntity>> {
        return if (query.isBlank()) {
            partyDao.getAllParties()
        } else {
            partyDao.searchParties(query.trim())
        }
    }

    suspend fun getPartyById(id: String): PartyEntity? = partyDao.getPartyById(id)

    suspend fun addParty(party: PartyEntity) {
        val localParty = party.copy(isSynced = false)
        partyDao.insertParty(localParty)
        syncManager?.pushPartyAsync(localParty)
    }

    // Products
    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    // Designs
    val allDesigns: Flow<List<DesignEntity>> = designDao.getAllDesigns()
    val activeDesigns: Flow<List<DesignEntity>> = designDao.getActiveDesigns()
    val allDesignFolders: Flow<List<DesignFolderEntity>> = designFolderDao.getAllFolders()

    // Orders
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()

    fun getOrdersByRep(repId: String?): Flow<List<OrderWithItems>> {
        return if (repId.isNullOrBlank() || repId == "staff_admin") {
            orderDao.getAllOrdersWithItems()
        } else {
            orderDao.getOrdersByRep(repId)
        }
    }

    suspend fun getOrderById(id: String): OrderWithItems? = orderDao.getOrderWithItemsById(id)

    suspend fun createOrder(
        partyId: String,
        partyName: String,
        partyAddress: String?,
        agent: String?,
        poNumber: String?,
        deliveryAddress: String?,
        imageUris: List<String>,
        designIds: List<String> = emptyList(),
        items: List<OrderItemEntity>,
        notes: String?,
        salesRepId: String?,
        salesRepName: String?,
        latitude: Double?,
        longitude: Double?,
        orderPrefix: String? = null
    ): String {
        val totalAmount = items.sumOf { it.totalPrice }
        val repPrefix = if (!orderPrefix.isNullOrBlank()) {
            orderPrefix.trim().uppercase()
        } else {
            val words = (salesRepName ?: "").trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size >= 2) {
                "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
            } else {
                (salesRepName?.take(3)?.ifBlank { "ST" } ?: "ST").uppercase()
            }
        }
        val random6Digit = 100000 + kotlin.random.Random.nextInt(900000)
        val orderNumber = "$repPrefix-$random6Digit"
        val orderId = UUID.randomUUID().toString()

        val imageJson = if (imageUris.isNotEmpty()) {
            com.google.gson.Gson().toJson(imageUris)
        } else null

        val designIdsJson = if (designIds.isNotEmpty()) {
            com.google.gson.Gson().toJson(designIds)
        } else null

        val order = OrderEntity(
            id = orderId,
            orderNumber = orderNumber,
            partyId = partyId,
            partyName = partyName,
            partyAddress = partyAddress,
            agent = agent,
            poNumber = poNumber,
            deliveryAddress = deliveryAddress,
            imageUrisJson = imageJson,
            designIdsJson = designIdsJson,
            totalAmount = totalAmount,
            notes = notes,
            salesRepId = salesRepId,
            salesRepName = salesRepName,
            orderTimestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            isSynced = false
        )

        val itemsWithOrderId = items.map {
            it.copy(id = UUID.randomUUID().toString(), orderId = orderId)
        }

        orderDao.insertFullOrder(order, itemsWithOrderId)

        // Asynchronously push live to Supabase cloud
        syncManager?.pushOrderAsync(order, itemsWithOrderId)

        return orderId
    }

    // Location Tracking (runs silently in background)
    suspend fun logLocation(latitude: Double, longitude: Double, accuracy: Float, speed: Float, batteryLevel: Int? = null) {
        val log = LocationLogEntity(
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            speed = speed,
            batteryLevel = batteryLevel,
            isSynced = false
        )
        val insertedId = locationDao.insertLocation(log)
        val logWithId = log.copy(id = insertedId)

        // Push this ping, then flush any older failed uploads only if online/valid
        val pushed = syncManager?.pushLocationAndMark(logWithId) ?: false
        if (pushed) {
            syncManager?.syncPendingLocations()
        }
    }

    val recentLocations: Flow<List<LocationLogEntity>> = locationDao.getRecentLocations()

    suspend fun getLatestLocation(): LocationLogEntity? = locationDao.getLatestLocation()

    // Daily Beat / Visit Plans
    fun getTodayVisitPlans(repName: String?): Flow<List<DailyVisitPlanEntity>> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return getVisitPlansForDate(todayStr, repName)
    }

    fun getVisitPlansForDate(date: String, repName: String?): Flow<List<DailyVisitPlanEntity>> {
        return if (repName.isNullOrBlank() || repName.contains("Admin", ignoreCase = true) || repName == "staff_admin") {
            dailyVisitPlanDao.getAllPlansForDate(date)
        } else {
            dailyVisitPlanDao.getPlansForRepAndDate(date, repName)
        }
    }

    suspend fun getPlanById(id: String): DailyVisitPlanEntity? = dailyVisitPlanDao.getPlanById(id)

    suspend fun completeVisit(
        planId: String,
        remarks: String? = null,
        orderId: String? = null
    ) {
        val existing = dailyVisitPlanDao.getPlanById(planId)
        val visitedAt = existing?.visitedAt ?: System.currentTimeMillis()
        val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(visitedAt))

        val updatedRemarks = if (!remarks.isNullOrBlank()) {
            val trimmed = remarks.trim()
            if (existing?.remarks.isNullOrBlank()) {
                "[$timeStr] $trimmed"
            } else if (existing?.remarks?.contains(trimmed) == true) {
                existing.remarks
            } else {
                "${existing?.remarks?.trim()}\n[$timeStr] $trimmed"
            }
        } else {
            existing?.remarks
        }

        dailyVisitPlanDao.updatePlanStatus(
            id = planId,
            status = "VISITED",
            visitedAt = visitedAt,
            remarks = updatedRemarks,
            orderId = orderId ?: existing?.orderId,
            isSynced = false
        )
        syncManager?.pushVisitPlanStatusAsync(
            planId = planId,
            status = "VISITED",
            visitedAt = visitedAt,
            orderId = orderId ?: existing?.orderId,
            remarks = updatedRemarks
        )
    }

    suspend fun sendStoreChatMessage(
        planId: String,
        message: String
    ) {
        val plan = dailyVisitPlanDao.getPlanById(planId) ?: return
        val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedEntry = "[$timeStr] ${message.trim()}"
        val updatedRemarks = if (!plan.remarks.isNullOrBlank()) {
            "${plan.remarks.trim()}\n$formattedEntry"
        } else {
            formattedEntry
        }

        dailyVisitPlanDao.updateRemarks(planId, updatedRemarks)
        syncManager?.pushVisitPlanStatusAsync(
            planId = planId,
            status = plan.status,
            visitedAt = plan.visitedAt,
            orderId = plan.orderId,
            remarks = updatedRemarks
        )
    }

    /**
     * Uploads a local voice note and appends it to visit chat remarks as [[voice]]url.
     * @return true if uploaded and saved locally
     */
    suspend fun sendStoreVoiceMessage(planId: String, audioFile: java.io.File): Boolean {
        val bytes = try {
            audioFile.readBytes()
        } catch (_: Exception) {
            return false
        }
        if (bytes.isEmpty()) return false
        val url = syncManager?.uploadVisitVoiceNote(bytes, planId) ?: return false
        sendStoreChatMessage(planId, "[[voice]]$url")
        return true
    }

    suspend fun markVisitUnvisited(planId: String) {
        dailyVisitPlanDao.markPlanUnvisited(planId)
        syncManager?.pushVisitPlanStatusAsync(
            planId = planId,
            status = "PENDING",
            visitedAt = null,
            orderId = null,
            remarks = null
        )
    }

    suspend fun markVisitStatus(planId: String, status: String, orderId: String? = null, remarks: String? = null) {
        if (status == "VISITED") {
            completeVisit(planId, remarks, orderId)
        } else {
            markVisitUnvisited(planId)
        }
    }

    suspend fun recordStoreVisit(
        party: PartyEntity,
        salesRepId: String?,
        salesRepName: String?
    ): Boolean {
        return recordStoreVisitWithRemarks(party, null, salesRepId, salesRepName) != null
    }

    suspend fun recordStoreVisitWithRemarks(
        party: PartyEntity,
        remarks: String?,
        salesRepId: String?,
        salesRepName: String?
    ): DailyVisitPlanEntity? {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val repId = salesRepId ?: "staff_admin"
        val repName = salesRepName ?: "Admin"
        val existingPlan = dailyVisitPlanDao.getPlanByPartyAndDate(party.id, todayStr)

        return if (existingPlan != null) {
            val cleanRemarks = remarks?.trim()?.ifBlank { null } ?: existingPlan.remarks
            completeVisit(existingPlan.id, cleanRemarks, existingPlan.orderId)
            existingPlan.copy(
                status = "VISITED",
                visitedAt = System.currentTimeMillis(),
                remarks = cleanRemarks
            )
        } else {
            val fullAddress = listOfNotNull(party.addressLine1, party.addressLine2).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            val newPlan = DailyVisitPlanEntity(
                id = UUID.randomUUID().toString(),
                planDate = todayStr,
                salesRepId = repId,
                salesRepName = repName,
                partyId = party.id,
                partyName = party.businessName,
                partyAddress = fullAddress,
                partyPhone = party.phone,
                priorityOrder = 999,
                instructions = "Ad-hoc field visit",
                visitPurpose = "GENERAL",
                status = "VISITED",
                visitedAt = System.currentTimeMillis(),
                orderId = null,
                remarks = remarks?.trim()?.ifBlank { null },
                isSynced = false
            )
            dailyVisitPlanDao.insertPlan(newPlan)
            syncManager?.pushNewVisitPlanAsync(newPlan)
            newPlan
        }
    }

    fun syncWithCloud() {
        syncManager?.syncAll()
    }
}
