package com.shreenathji.sales.data.remote.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.remote.SupabaseConfig
import com.shreenathji.sales.data.remote.dto.OrderDto
import com.shreenathji.sales.data.remote.dto.OrderItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OrderConflictException(message: String) : Exception(message)

class OrdersApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val requestBuilder: (String, String, String?, Boolean) -> Request,
    private val tokenProvider: (() -> String?)? = null
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getOrdersWithItems(): List<OrderDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("orders?select=*,order_items(*)&order=created_at.desc", "GET", null, false)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<OrderDto>>() {}.type
                return@withContext gson.fromJson<List<OrderDto>>(body, type)
            } else {
                Log.e("OrdersApi", "Failed to fetch orders with items HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("OrdersApi", "Error fetching orders with items: ${e.message}")
        }
        null
    }

    suspend fun uploadOrderImage(imageBytes: ByteArray, orderNumber: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanNum = orderNumber.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val fileName = "${cleanNum}_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val url = "${SupabaseConfig.BASE_URL}/storage/v1/object/order-attachments/orders/$fileName"
            val bearerToken = tokenProvider?.invoke()?.takeIf { it.isNotBlank() }
                ?: SupabaseConfig.PUBLISHABLE_KEY

            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val publicUrl = "${SupabaseConfig.BASE_URL}/storage/v1/object/public/order-attachments/orders/$fileName"
                Log.d("OrdersApi", "Uploaded order swatch image: $publicUrl")
                return@withContext publicUrl
            } else {
                Log.e("OrdersApi", "Failed to upload swatch image: ${res.code} - ${res.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("OrdersApi", "Exception uploading swatch image: ${e.message}")
        }
        null
    }

    suspend fun uploadVisitVoiceNote(audioBytes: ByteArray, planId: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanId = planId.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "visit" }
            val fileName = "${cleanId}_${System.currentTimeMillis()}_${(1000..9999).random()}.m4a"
            val url = "${SupabaseConfig.BASE_URL}/storage/v1/object/order-attachments/visit-chat/$fileName"
            val bearerToken = tokenProvider?.invoke()?.takeIf { it.isNotBlank() }
                ?: SupabaseConfig.PUBLISHABLE_KEY

            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("Content-Type", "audio/mp4")
                .addHeader("x-upsert", "true")
                .post(audioBytes.toRequestBody("audio/mp4".toMediaType()))
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val publicUrl = "${SupabaseConfig.BASE_URL}/storage/v1/object/public/order-attachments/visit-chat/$fileName"
                Log.d("OrdersApi", "Uploaded visit voice note: $publicUrl")
                return@withContext publicUrl
            } else {
                Log.e("OrdersApi", "Failed to upload voice note: ${res.code} - ${res.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("OrdersApi", "Exception uploading voice note: ${e.message}")
        }
        null
    }

    suspend fun pushOrder(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        uploadedImageUrls: List<String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var headerCommitted = false
        // Empty items: header alone is a complete push. Non-empty: false until items upsert succeeds,
        // so exception-path rollback does not leave an orphan header.
        var itemsSuccessful = items.isEmpty()
        try {
            // 1. Insert/Upsert Order
            val orderDto = OrderDto(
                id = order.id,
                order_number = order.orderNumber,
                party_id = order.partyId,
                party_name = order.partyName,
                party_address = order.partyAddress,
                agent = order.agent,
                po_number = order.poNumber,
                delivery_address = order.deliveryAddress,
                total_amount = order.totalAmount,
                order_status = order.orderStatus,
                notes = order.notes,
                sales_rep_id = order.salesRepId,
                sales_rep_name = order.salesRepName,
                latitude = order.latitude,
                longitude = order.longitude,
                image_urls = uploadedImageUrls?.ifEmpty { null },
                design_ids = run {
                    if (order.designIdsJson.isNullOrBlank()) null
                    else {
                        try {
                            val type = object : TypeToken<List<String>>() {}.type
                            gson.fromJson<List<String>>(order.designIdsJson, type)?.ifEmpty { null }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            )
            val orderJson = gson.toJson(orderDto)
            // Insert-or-ignore: a re-push (e.g. the first reply was lost) must never overwrite an
            // order the office has since confirmed/edited/dispatched.
            val orderReq = ignoreDuplicates(requestBuilder("orders?on_conflict=id", "POST", orderJson, false))
            val orderRes = client.newCall(orderReq).execute()

            if (orderRes.isSuccessful) {
                val inserted = orderRes.body?.string().orEmpty().trim()
                if (inserted == "[]") {
                    // Order already on the server. Complete if it has items; otherwise fill them in,
                    // but never delete a header this call did not create.
                    if (items.isEmpty() || serverHasItems(order.id)) return@withContext true
                    return@withContext insertItems(order.id, items)
                }
            }

            if (!orderRes.isSuccessful) {
                val errorBody = orderRes.body?.string().orEmpty()
                Log.e("OrdersApi", "Failed to insert order: ${orderRes.code} - $errorBody")
                if (orderRes.code == 409 || errorBody.contains("orders_order_number_key") || errorBody.contains("duplicate key")) {
                    throw OrderConflictException("Duplicate order number: ${order.orderNumber}")
                }
                return@withContext false
            }
            headerCommitted = true

            // 2. Insert Order Items
            if (items.isNotEmpty()) {
                itemsSuccessful = insertItems(order.id, items)
                if (!itemsSuccessful) {
                    // Avoid orphan orders (header saved, items missing) — this call created the header.
                    deleteOrphanOrderHeader(order.id)
                    return@withContext false
                }
            }

            // 3. Auto-tag Party / Store location in Supabase ONLY IF party has NO verified coordinates yet
            if (order.latitude != null && order.longitude != null && !order.partyId.isNullOrBlank()) {
                try {
                    val checkPartyReq = requestBuilder("parties?id=eq.${order.partyId}&select=latitude,longitude", "GET", null, false)
                    val checkRes = client.newCall(checkPartyReq).execute()
                    if (checkRes.isSuccessful) {
                        val body = checkRes.body?.string() ?: "[]"
                        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                        val list: List<Map<String, Any?>> = gson.fromJson(body, type)
                        val existingParty = list.firstOrNull()
                        val hasLat = existingParty?.get("latitude") != null
                        val hasLng = existingParty?.get("longitude") != null

                        if (!hasLat || !hasLng) {
                            val geoJson = "{\"latitude\":${order.latitude},\"longitude\":${order.longitude},\"geofence_radius_meters\":60}"
                            val geoReq = requestBuilder("parties?id=eq.${order.partyId}", "PATCH", geoJson, false)
                            val patchRes = client.newCall(geoReq).execute()
                            if (patchRes.isSuccessful) {
                                Log.d("OrdersApi", "Auto-tagged initial candidate GPS for party ${order.partyId}")
                            } else {
                                Log.w("OrdersApi", "Party GPS auto-tag failed: ${patchRes.code}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("OrdersApi", "Could not auto-tag party location: ${e.message}")
                }
            }

            return@withContext itemsSuccessful
        } catch (e: OrderConflictException) {
            throw e
        } catch (e: Exception) {
            Log.e("OrdersApi", "Failed to push order to Supabase: ${e.message}")
            if (headerCommitted && !itemsSuccessful) {
                deleteOrphanOrderHeader(order.id)
            }
            return@withContext false
        }
    }

    private fun ignoreDuplicates(req: Request): Request =
        req.newBuilder().header("Prefer", "return=representation,resolution=ignore-duplicates").build()

    private fun serverHasItems(orderId: String): Boolean {
        val res = client.newCall(requestBuilder("order_items?order_id=eq.$orderId&select=id&limit=1", "GET", null, false)).execute()
        if (!res.isSuccessful) throw java.io.IOException("order_items check failed: ${res.code}")
        return res.body?.string().orEmpty().trim() != "[]"
    }

    /** Insert-or-ignore line items (safe to repeat). */
    private fun insertItems(orderId: String, items: List<OrderItemEntity>): Boolean {
        val itemDtos = items.map { item ->
            OrderItemDto(
                id = item.id,
                order_id = orderId,
                product_name = item.productName,
                meter = item.meter,
                price = item.price,
                total_price = item.totalPrice,
                notes = item.notes,
                design_id = item.designId,
                image_url = item.imageUrl,
                is_matching = item.isMatching,
                multiplier = item.multiplier,
                cut_meter = item.cutMeter,
                component = item.component,
                set_group_id = item.setGroupId
            )
        }
        val itemsRes = client.newCall(
            ignoreDuplicates(requestBuilder("order_items?on_conflict=id", "POST", gson.toJson(itemDtos), false))
        ).execute()
        if (itemsRes.isSuccessful) return true
        val itemsErrorBody = itemsRes.body?.string().orEmpty()

        // PostgREST schema cache can lag after adding design_id/image_url — retry without them.
        if (isSchemaLagItemsError(itemsErrorBody)) {
            Log.w("OrdersApi", "order_items insert failed with new columns; retrying without design_id/image_url: ${itemsRes.code}")
            val fallbackDtos = itemDtos.map { it.copy(design_id = null, image_url = null) }
            val retryRes = client.newCall(
                ignoreDuplicates(requestBuilder("order_items?on_conflict=id", "POST", gson.toJson(fallbackDtos), false))
            ).execute()
            if (retryRes.isSuccessful) return true
            Log.e("OrdersApi", "Failed to insert order items after schema-lag retry: ${retryRes.code} - ${retryRes.body?.string()}")
            return false
        }
        Log.e("OrdersApi", "Failed to insert order items: ${itemsRes.code} - $itemsErrorBody")
        return false
    }

    /** Best-effort delete of a cloud order header when items failed after header upsert. CASCADE removes items. */
    private fun deleteOrphanOrderHeader(orderId: String) {
        try {
            val deleteReq = requestBuilder("orders?id=eq.$orderId", "DELETE", null, false)
            val deleteRes = client.newCall(deleteReq).execute()
            if (deleteRes.isSuccessful) {
                Log.d("OrdersApi", "Rolled back orphan order header $orderId after items failure")
            } else {
                Log.w(
                    "OrdersApi",
                    "Failed to roll back orphan order header $orderId: ${deleteRes.code} - ${deleteRes.body?.string()}"
                )
            }
        } catch (e: Exception) {
            Log.w("OrdersApi", "Exception rolling back orphan order header $orderId: ${e.message}")
        }
    }

    private fun isSchemaLagItemsError(errorBody: String): Boolean {
        val msg = errorBody.lowercase()
        return msg.contains("design_id") ||
            msg.contains("image_url") ||
            msg.contains("schema cache") ||
            msg.contains("could not find")
    }
}
