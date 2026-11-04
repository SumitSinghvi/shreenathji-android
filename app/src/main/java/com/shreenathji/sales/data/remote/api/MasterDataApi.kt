package com.shreenathji.sales.data.remote.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.remote.SupabaseConfig
import com.shreenathji.sales.data.remote.dto.DailyVisitPlanDto
import com.shreenathji.sales.data.remote.dto.DesignDto
import com.shreenathji.sales.data.remote.dto.DesignFolderDto
import com.shreenathji.sales.data.remote.dto.PartyDto
import com.shreenathji.sales.data.remote.dto.ProductDto
import com.shreenathji.sales.data.remote.dto.StaffDto
import com.shreenathji.sales.data.remote.dto.TombstoneDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MasterDataApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val requestBuilder: (String, String, String?, Boolean) -> Request
) {
    suspend fun getStaff(): List<StaffDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("staff?is_active=eq.true", "GET", null, false)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<StaffDto>>() {}.type
                return@withContext gson.fromJson<List<StaffDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getStaff HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch staff: ${e.message}")
        }
        null
    }

    suspend fun getParties(): List<PartyDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("parties?order=business_name.asc", "GET", null, false)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<PartyDto>>() {}.type
                return@withContext gson.fromJson<List<PartyDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getParties HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch parties: ${e.message}")
        }
        null
    }

    suspend fun getProducts(): List<ProductDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("products?is_active=eq.true&order=name.asc", "GET", null, false)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<ProductDto>>() {}.type
                return@withContext gson.fromJson<List<ProductDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getProducts HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch products: ${e.message}")
        }
        null
    }

    suspend fun getDesigns(): List<DesignDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder(
                "designs?order=captured_on.desc&order=created_at.desc",
                "GET",
                null,
                false
            )
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<DesignDto>>() {}.type
                return@withContext gson.fromJson<List<DesignDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getDesigns HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch designs: ${e.message}")
        }
        null
    }

    suspend fun getDesignFolders(): List<DesignFolderDto>? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder(
                "design_folders?order=sort_order.asc&order=name.asc",
                "GET",
                null,
                false
            )
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<DesignFolderDto>>() {}.type
                return@withContext gson.fromJson<List<DesignFolderDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getDesignFolders HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch design folders: ${e.message}")
        }
        null
    }

    // ---- Delta-sync reads (paged: PostgREST caps a response at 1000 rows) ----

    private suspend fun <T> getAllPages(path: String, elementType: Class<T>): List<T>? =
        withContext(Dispatchers.IO) {
            try {
                val type = TypeToken.getParameterized(List::class.java, elementType).type
                val all = mutableListOf<T>()
                val sep = if (path.contains('?')) "&" else "?"
                var offset = 0
                var lastPageSize = PAGE_SIZE
                var failed = false
                while (lastPageSize == PAGE_SIZE && !failed) {
                    val request = requestBuilder("$path${sep}limit=$PAGE_SIZE&offset=$offset", "GET", null, false)
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val page = gson.fromJson<List<T>>(response.body?.string() ?: "[]", type)
                            all += page
                            lastPageSize = page.size
                        } else {
                            Log.w("MasterDataApi", "GET $path HTTP ${response.code}")
                            failed = true
                        }
                    }
                    offset += PAGE_SIZE
                }
                if (failed) null else all
            } catch (e: Exception) {
                Log.e("MasterDataApi", "Failed GET $path: ${e.message}")
                null
            }
        }

    private fun since(cursor: String): String =
        "updated_at=gte." + java.net.URLEncoder.encode(cursor, "UTF-8") + "&order=updated_at.asc,id.asc"

    suspend fun getPartiesFull() = getAllPages("parties?order=id.asc", PartyDto::class.java)
    suspend fun getPartiesSince(cursor: String) = getAllPages("parties?${since(cursor)}", PartyDto::class.java)

    suspend fun getProductsFull() = getAllPages("products?is_active=eq.true&order=id.asc", ProductDto::class.java)
    // No is_active filter: a product turned inactive must reach the phone so it can be removed.
    suspend fun getProductsSince(cursor: String) = getAllPages("products?${since(cursor)}", ProductDto::class.java)

    suspend fun getDesignsFull() = getAllPages("designs?order=id.asc", DesignDto::class.java)
    suspend fun getDesignsSince(cursor: String) = getAllPages("designs?${since(cursor)}", DesignDto::class.java)

    suspend fun getDesignFoldersFull() = getAllPages("design_folders?order=id.asc", DesignFolderDto::class.java)
    suspend fun getDesignFoldersSince(cursor: String) =
        getAllPages("design_folders?${since(cursor)}", DesignFolderDto::class.java)

    /** Null if the server has no deleted_records table yet (migration not applied). */
    suspend fun getTombstones(table: String, sinceDeletedAt: String?): List<TombstoneDto>? {
        val filter = if (sinceDeletedAt == null) "" else
            "&deleted_at=gt." + java.net.URLEncoder.encode(sinceDeletedAt, "UTF-8")
        return getAllPages("deleted_records?table_name=eq.$table$filter&order=deleted_at.asc,id.asc", TombstoneDto::class.java)
    }

    private companion object {
        const val PAGE_SIZE = 1000
    }

    suspend fun pushParty(party: PartyEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val dto = PartyDto(
                id = party.id,
                business_name = party.businessName,
                contact_person = party.contactPerson,
                phone = party.phone,
                address_line1 = party.addressLine1,
                address_line2 = party.addressLine2,
                default_agent = party.defaultAgent,
                latitude = party.latitude,
                longitude = party.longitude,
                assigned_rep_ids = party.getAssignedRepIds().takeIf { it.isNotEmpty() }
            )
            val json = gson.toJson(dto)
            val request = requestBuilder("parties", "POST", json, true)
            val response = client.newCall(request).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to push party to Supabase: ${e.message}")
            return@withContext false
        }
    }

    suspend fun getVisitPlans(date: String? = null): List<DailyVisitPlanDto>? = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (date != null) "daily_visit_plans?plan_date=eq.$date&order=priority_order.asc"
                           else "daily_visit_plans?order=priority_order.asc"
            val request = requestBuilder(endpoint, "GET", null, false)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<DailyVisitPlanDto>>() {}.type
                return@withContext gson.fromJson<List<DailyVisitPlanDto>>(body, type)
            } else {
                Log.e("MasterDataApi", "getVisitPlans HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to fetch daily visit plans: ${e.message}")
        }
        null
    }

    suspend fun insertVisitPlan(dto: DailyVisitPlanDto): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(dto)
            val request = requestBuilder("daily_visit_plans", "POST", json, true)
            val response = client.newCall(request).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to insert visit plan to Supabase: ${e.message}")
            return@withContext false
        }
    }

    suspend fun updateVisitPlanStatus(
        planId: String,
        status: String,
        visitedAt: String?,
        orderId: String?,
        remarks: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val map = mutableMapOf<String, Any?>("status" to status)
            map["visited_at"] = visitedAt
            if (orderId != null) map["order_id"] = orderId
            map["remarks"] = remarks

            val json = gson.toJson(map)
            val request = requestBuilder("daily_visit_plans?id=eq.$planId", "PATCH", json, false)
            val res = client.newCall(request).execute()
            if (!res.isSuccessful) {
                Log.e("MasterDataApi", "updateVisitPlanStatus HTTP ${res.code}: ${res.body?.string()}")
            }
            return@withContext res.isSuccessful
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to update visit plan status: ${e.message}")
            return@withContext false
        }
    }

    suspend fun deleteVisitPlan(planId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("daily_visit_plans?id=eq.$planId", "DELETE", null, false)
            val res = client.newCall(request).execute()
            if (!res.isSuccessful) {
                Log.e("MasterDataApi", "deleteVisitPlan HTTP ${res.code}: ${res.body?.string()}")
            }
            return@withContext res.isSuccessful
        } catch (e: Exception) {
            Log.e("MasterDataApi", "Failed to delete visit plan from Supabase: ${e.message}")
            return@withContext false
        }
    }
}
