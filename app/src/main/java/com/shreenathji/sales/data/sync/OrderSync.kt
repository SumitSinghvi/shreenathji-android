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

/** Order pull / push (local Room <-> Supabase), split out of [SupabaseSyncManager]. */
internal class OrderSync(
    private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val orderDao: OrderDao,
    private val scope: CoroutineScope
) {
    private val gson = Gson()

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
            when {
                remote.isEmpty() -> item.copy(imageUrl = null) // local file gone
                remote == local -> item
                else -> item.copy(imageUrl = remote)
            }
        }
    }

    /**
     * Uploads every local photo. Returns null if any local photo could not be uploaded, so the
     * order stays unsynced and is retried — pushing it anyway would store device-only file:// URIs.
     */
    private suspend fun uploadAndRemap(
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): Pair<List<String>, List<OrderItemEntity>>? {
        val mapped = linkedMapOf<String, String>()
        var allUploaded = true
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
                            } else {
                                allUploaded = false
                            }
                        } else {
                            allUploaded = false
                        }
                    } catch (gone: java.io.FileNotFoundException) {
                        // File no longer on the device: it can never upload, so drop it rather
                        // than block the order forever.
                        mapped[uriStr] = ""
                        Log.w("SupabaseSync", "Swatch image $uriStr missing on device — dropped")
                    } catch (imgErr: Exception) {
                        allUploaded = false
                        Log.e("SupabaseSync", "Failed to upload swatch image $uriStr: ${imgErr.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Error parsing imageUrisJson: ${e.message}")
            }
        }
        // Item photos are mirrored into imageUrisJson at submit; any item still pointing at a
        // local URI after remap means its upload is missing.
        val remapped = remapItemImageUrls(items, mapped)
        val localLeft = remapped.any { item ->
            val s = item.imageUrl?.let { Uri.parse(it).scheme?.lowercase() }
            item.imageUrl != null && s != "http" && s != "https"
        }
        if (!allUploaded || localLeft) {
            Log.w("SupabaseSync", "Order ${order.orderNumber}: photo upload incomplete — keeping it queued")
            return null
        }
        return mapped.values.filter { it.isNotEmpty() } to remapped
    }

    /** One order push at a time, so the create-time push and background sync never race on the same order. */
    private val pushMutex = Mutex()

    private suspend fun push(order: OrderEntity, items: List<OrderItemEntity>, urls: List<String>): Boolean =
        supabaseClient.pushOrder(order = order, items = items, uploadedImageUrls = urls.ifEmpty { null })

    /** Pushes one order (re-read from Room under the lock). Returns true once it is on the server. */
    private suspend fun pushOneLocked(orderId: String): Boolean {
        val fresh = orderDao.getOrderWithItemsById(orderId) ?: return true
        if (fresh.order.isSynced) return true
        var currentOrder = fresh.order
        val (uploadedUrls, remappedItems) = uploadAndRemap(currentOrder, fresh.items) ?: return false

        suspend fun attempt(): Boolean = try {
            push(currentOrder, remappedItems, uploadedUrls)
        } catch (conflict: com.shreenathji.sales.data.remote.api.OrderConflictException) {
            Log.w("SupabaseSync", "Collision on orderNumber ${currentOrder.orderNumber}, regenerating...")
            val prefix = currentOrder.orderNumber.substringBefore("-", "ST")
            val newNumber = "$prefix-${100000 + kotlin.random.Random.nextInt(900000)}"
            orderDao.updateOrderNumber(currentOrder.id, newNumber)
            currentOrder = currentOrder.copy(orderNumber = newNumber)
            push(currentOrder, remappedItems, uploadedUrls)
        }

        var success = attempt()
        if (!success) {
            kotlinx.coroutines.delay(1000)
            success = attempt()
        }
        if (success) {
            orderDao.markOrderAsSynced(currentOrder.id)
            Log.d("SupabaseSync", "Order ${currentOrder.orderNumber} synced to Supabase with ${uploadedUrls.size} images")
        } else {
            Log.w("SupabaseSync", "Order ${currentOrder.orderNumber} sync failed — will retry on next sync")
        }
        return success
    }

    suspend fun syncPendingOrders() {
        try {
            supabaseClient.ensureAuthenticatedSession()
            pushMutex.withLock {
                val unsynced = orderDao.getUnsyncedOrders()
                if (unsynced.isEmpty()) return
                Log.d("SupabaseSync", "Pushing ${unsynced.size} unsynced orders to Supabase")
                unsynced.forEach { orderWithItems ->
                    // Per-order isolation: one order failing must not skip the rest.
                    try {
                        pushOneLocked(orderWithItems.order.id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("SupabaseSync", "Order ${orderWithItems.order.orderNumber} sync errored — will retry on next sync: ${e.message}")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending orders: ${e.message}", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun pushOrderAsync(order: OrderEntity, items: List<OrderItemEntity>) {
        scope.launch {
            try {
                pushMutex.withLock { pushOneLocked(order.id) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Exception in pushOrderAsync: ${e.message}", e)
            }
        }
    }
}
