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
                            orderPrefix = it.order_prefix,
                            canViewAllOrders = it.canViewAllOrders
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

            // 2-4. Master tables (parties, products, designs, folders): delta pull when the
            // server supports it, full pull otherwise. Independent, so run in parallel.
            val deltaOk = isDeltaSupported()
            coroutineScope {
                listOf(
                    async { syncParties(deltaOk) },
                    async { syncProducts(deltaOk) },
                    async { syncDesignFolders(deltaOk) },
                    async { syncDesigns(deltaOk) }
                ).awaitAll()
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

    // ---------------------------------------------------------------------------------
    // Master-table sync: delta (updated_at cursor + tombstones) with full-pull fallback
    // ---------------------------------------------------------------------------------

    private val cursorPrefs by lazy { context.getSharedPreferences("sync_cursors", Context.MODE_PRIVATE) }
    @Volatile private var deltaSupported = false

    /**
     * Delta needs the server migration that adds updated_at triggers + deleted_records.
     * updated_at alone is not enough (without the trigger it is not bumped on edits), so we
     * probe for the tombstone table, which exists only once the migration has run.
     */
    private suspend fun isDeltaSupported(): Boolean {
        if (deltaSupported) return true
        val ok = supabaseClient.masterDataApi.getTombstones("parties", FAR_FUTURE) != null
        deltaSupported = ok
        return ok
    }

    private suspend fun <D> pullTable(
        table: String,
        deltaOk: Boolean,
        fetchFull: suspend () -> List<D>?,
        fetchSince: suspend (String) -> List<D>?,
        updatedAt: (D) -> String?,
        applyFull: suspend (List<D>) -> Unit,
        applyDelta: suspend (List<D>) -> Unit,
        applyDeletes: suspend (List<String>) -> Unit
    ) {
        val updKey = "$table.updated_at"
        val delKey = "$table.deleted_at"
        val cursor = if (deltaOk) cursorPrefs.getString(updKey, null) else null

        if (cursor == null) {
            val rows = fetchFull() ?: return
            applyFull(rows)
            if (deltaOk) {
                SyncCursor.max(rows.map(updatedAt))?.let { cursorPrefs.edit().putString(updKey, it).apply() }
            } else {
                cursorPrefs.edit().remove(updKey).remove(delKey).apply()
            }
            Log.d("SupabaseSync", "$table: full pull ${rows.size} rows")
            return
        }

        val rows = fetchSince(cursor) ?: return
        if (rows.isNotEmpty()) applyDelta(rows)
        SyncCursor.max(rows.map(updatedAt))?.let { cursorPrefs.edit().putString(updKey, it).apply() }

        val tombstones = supabaseClient.masterDataApi.getTombstones(table, cursorPrefs.getString(delKey, null))
        if (!tombstones.isNullOrEmpty()) {
            applyDeletes(tombstones.map { it.record_id })
            SyncCursor.max(tombstones.map { it.deleted_at })?.let { cursorPrefs.edit().putString(delKey, it).apply() }
        }
        Log.d("SupabaseSync", "$table: delta ${rows.size} changed, ${tombstones?.size ?: 0} deleted")
    }

    private suspend fun syncParties(deltaOk: Boolean) {
        val api = supabaseClient.masterDataApi
        pullTable(
            table = "parties", deltaOk = deltaOk,
            fetchFull = { api.getPartiesFull() },
            fetchSince = { api.getPartiesSince(it) },
            updatedAt = { it.updated_at },
            applyFull = { partyDao.syncReplaceParties(it.map(::toPartyEntity)) },
            applyDelta = { partyDao.insertParties(it.map(::toPartyEntity)) },
            applyDeletes = { partyDao.deleteSyncedPartiesByIds(it) }
        )
    }

    private suspend fun syncProducts(deltaOk: Boolean) {
        val api = supabaseClient.masterDataApi
        pullTable(
            table = "products", deltaOk = deltaOk,
            fetchFull = { api.getProductsFull() },
            fetchSince = { api.getProductsSince(it) },
            updatedAt = { it.updated_at },
            applyFull = { productDao.syncReplaceProducts(it.map(::toProductEntity)) },
            applyDelta = { rows ->
                // Delta includes inactive products so they can be removed from the phone.
                val (active, inactive) = rows.partition { it.is_active }
                if (active.isNotEmpty()) productDao.insertProducts(active.map(::toProductEntity))
                if (inactive.isNotEmpty()) productDao.deleteProductsByIds(inactive.map { it.id })
            },
            applyDeletes = { productDao.deleteProductsByIds(it) }
        )
    }

    private suspend fun syncDesignFolders(deltaOk: Boolean) {
        val api = supabaseClient.masterDataApi
        pullTable(
            table = "design_folders", deltaOk = deltaOk,
            fetchFull = { api.getDesignFoldersFull() },
            fetchSince = { api.getDesignFoldersSince(it) },
            updatedAt = { it.updated_at },
            applyFull = { designFolderDao.syncReplaceFolders(it.map(::toFolderEntity)) },
            applyDelta = { designFolderDao.insertFolders(it.map(::toFolderEntity)) },
            applyDeletes = { designFolderDao.deleteFoldersByIds(it) }
        )
    }

    private suspend fun syncDesigns(deltaOk: Boolean) {
        val api = supabaseClient.masterDataApi
        pullTable(
            table = "designs", deltaOk = deltaOk,
            fetchFull = { api.getDesignsFull() },
            fetchSince = { api.getDesignsSince(it) },
            updatedAt = { it.updated_at },
            applyFull = { designDao.syncReplaceDesigns(it.map(::toDesignEntity)) },
            applyDelta = { designDao.insertDesigns(it.map(::toDesignEntity)) },
            applyDeletes = { designDao.deleteDesignsByIds(it) }
        )
    }

    private val orderSync = OrderSync(context, supabaseClient, orderDao, scope)

    suspend fun syncOrdersFromSupabase() = orderSync.syncOrdersFromSupabase()

    suspend fun syncPendingOrders() = orderSync.syncPendingOrders()

    fun pushOrderAsync(order: OrderEntity, items: List<OrderItemEntity>) = orderSync.pushOrderAsync(order, items)


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
                        orderId = plan.orderId,
                        remarks = plan.remarks
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
                    orderId = plan.orderId,
                    remarks = plan.remarks
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

    private val locationSync = LocationSync(supabaseClient, locationDao, authManager, licenseSessionGuard, scope)

    suspend fun syncPendingLocations() = locationSync.syncPendingLocations()

    fun pushLocationAsync(log: LocationLogEntity) = locationSync.pushLocationAsync(log)

    suspend fun pushLocationAndMark(log: LocationLogEntity): Boolean = locationSync.pushLocationAndMark(log)


    private companion object {
        const val MIN_SYNC_INTERVAL_MS = 30_000L
        const val FAR_FUTURE = "9999-12-31T00:00:00+00:00"
    }
}
