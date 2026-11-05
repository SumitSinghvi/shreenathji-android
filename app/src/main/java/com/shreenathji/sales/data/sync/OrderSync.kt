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
              // Per-order isolation: one order failing (e.g. repeated number conflict) must not skip the rest.
              try {
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
              } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
              } catch (e: Exception) {
                Log.w("SupabaseSync", "Order ${orderWithItems.order.orderNumber} sync errored — will retry on next sync: ${e.message}")
              }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending orders: ${e.message}", e)
        }
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
}
