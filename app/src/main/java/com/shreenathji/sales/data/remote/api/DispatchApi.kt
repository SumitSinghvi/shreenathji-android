package com.shreenathji.sales.data.remote.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.remote.dto.DispatchOrderDto
import com.shreenathji.sales.data.remote.dto.DispatchRecordDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class DispatchApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val requestBuilder: (String, String, String?, Boolean) -> Request
) {
    /** Approved orders waiting for dispatch. Selects meters only, never price/total. */
    suspend fun getToDispatch(): List<DispatchOrderDto>? = withContext(Dispatchers.IO) {
        val endpoint = "orders?order_status=eq.CONFIRMED" +
            "&select=id,order_number,party_name,party_address,notes,order_timestamp,image_urls," +
            "order_items(id,product_name,meter,component,notes,image_url)" +
            "&order=order_timestamp.asc&limit=300" // ponytail: oldest 300 first; add offset paging + server search if backlog nears this
        fetchList(endpoint, object : TypeToken<List<DispatchOrderDto>>() {})
    }

    /** Dispatches submitted by this staff member only. */
    suspend fun getMyDispatches(staffId: String): List<DispatchRecordDto>? = withContext(Dispatchers.IO) {
        val who = URLEncoder.encode(staffId, "UTF-8")
        val endpoint = "dispatches?dispatched_by=eq.$who" +
            "&select=id,order_id,dispatched_at,lr_number,transporter,vehicle_no,notes,photo_urls," +
            "dispatch_items(id,order_item_id,product_name,component,ordered_meter,actual_meter,notes)," +
            "orders!inner(order_number,party_name,order_status,image_urls)" +
            "&orders.order_status=in.(DISPATCHED,DELIVERED)" + // reverted orders keep their dispatches row; hide them
            "&order=dispatched_at.desc&limit=200"
        fetchList(endpoint, object : TypeToken<List<DispatchRecordDto>>() {})
    }

    /** Calls save_dispatch. Returns null on success, otherwise a readable error. */
    suspend fun saveDispatch(payload: Map<String, Any?>): String? = withContext(Dispatchers.IO) {
        try {
            // Gson drops nulls by default, which PostgREST sees as missing function arguments.
            val body = gson.newBuilder().serializeNulls().create().toJson(payload)
            val req = requestBuilder("rpc/save_dispatch", "POST", body, false)
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) return@withContext null
                val body = res.body?.string().orEmpty()
                Log.e("DispatchApi", "save_dispatch HTTP ${res.code}: $body")
                errorMessage(body) ?: "Could not save dispatch (HTTP ${res.code})"
            }
        } catch (e: Exception) {
            Log.e("DispatchApi", "save_dispatch failed: ${e.message}")
            "Network error. Check your connection and try again."
        }
    }

    private fun <T> fetchList(endpoint: String, type: TypeToken<List<T>>): List<T>? {
        return try {
            client.newCall(requestBuilder(endpoint, "GET", null, false)).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.e("DispatchApi", "GET $endpoint HTTP ${res.code}: ${res.body?.string()}")
                    return null
                }
                gson.fromJson<List<T>>(res.body?.string() ?: "[]", type.type)
            }
        } catch (e: Exception) {
            Log.e("DispatchApi", "GET failed: ${e.message}")
            null
        }
    }

    private fun errorMessage(body: String): String? = try {
        JsonParser.parseString(body).asJsonObject.get("message")?.asString
    } catch (_: Exception) {
        null
    }
}
