package com.shreenathji.sales.data.remote

import com.google.gson.Gson
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.remote.api.MasterDataApi
import com.shreenathji.sales.data.remote.api.OrdersApi
import com.shreenathji.sales.data.remote.api.TrackingApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Re-export DTO types for backward compatibility
typealias StaffDto = com.shreenathji.sales.data.remote.dto.StaffDto
typealias PartyDto = com.shreenathji.sales.data.remote.dto.PartyDto
typealias ProductDto = com.shreenathji.sales.data.remote.dto.ProductDto
typealias OrderDto = com.shreenathji.sales.data.remote.dto.OrderDto
typealias OrderItemDto = com.shreenathji.sales.data.remote.dto.OrderItemDto
typealias DailyVisitPlanDto = com.shreenathji.sales.data.remote.dto.DailyVisitPlanDto
typealias LocationLogDto = com.shreenathji.sales.data.remote.dto.LocationLogDto
typealias AuthTokenResponseDto = com.shreenathji.sales.data.remote.dto.AuthTokenResponseDto
typealias AuthUserDto = com.shreenathji.sales.data.remote.dto.AuthUserDto
typealias LicenseVerificationResultDto = com.shreenathji.sales.data.remote.dto.LicenseVerificationResultDto
typealias ActiveLicenseDto = com.shreenathji.sales.data.remote.dto.ActiveLicenseDto
typealias LicenseSessionCheckDto = com.shreenathji.sales.data.remote.dto.LicenseSessionCheckDto

class SupabaseClient(
    private val tokenProvider: (() -> String?)? = null,
    private val authManager: com.shreenathji.sales.data.local.auth.AuthManager? = null
) {

    private val rawClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            if (response.code == 401 && !request.url.encodedPath.contains("auth/v1/signup")) {
                response.close()
                val freshToken = refreshAnonymousSessionSync()
                if (!freshToken.isNullOrBlank()) {
                    val newReq = request.newBuilder()
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                    response = chain.proceed(newReq)
                }
            }
            response
        }
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var activeJwtToken: String? = null

    fun setAuthToken(token: String?) {
        activeJwtToken = token
    }

    private fun getEffectiveToken(): String? {
        return activeJwtToken
            ?: authManager?.getAuthToken()
            ?: tokenProvider?.invoke()
    }

    private fun applyLicenseHeaders(builder: Request.Builder) {
        val manager = authManager ?: return
        val licenseKey = manager.getActiveLicenseKey()
        if (!licenseKey.isNullOrBlank()) {
            builder.addHeader("x-license-key", licenseKey.trim().uppercase())
            builder.addHeader("x-hardware-id", manager.getDeviceId())
        }
    }

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        jsonBody: String? = null,
        isUpsert: Boolean = false
    ): Request {
        val url = "${SupabaseConfig.REST_URL}/$endpoint"
        val bearerToken = getEffectiveToken() ?: SupabaseConfig.PUBLISHABLE_KEY
        val preferHeader = if (isUpsert) "return=representation,resolution=merge-duplicates" else "return=representation"
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $bearerToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", preferHeader)

        applyLicenseHeaders(builder)

        if (method == "POST" && jsonBody != null) {
            builder.post(jsonBody.toRequestBody(jsonMediaType))
        } else if (method == "PATCH" && jsonBody != null) {
            builder.patch(jsonBody.toRequestBody(jsonMediaType))
        } else if (method == "DELETE") {
            builder.delete()
        }

        return builder.build()
    }

    @Synchronized
    private fun refreshAnonymousSessionSync(): String? {
        return try {
            val req = Request.Builder()
                .url("${SupabaseConfig.AUTH_URL}/signup")
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            val res = rawClient.newCall(req).execute()
            val body = res.body?.string() ?: "{}"
            if (!res.isSuccessful) {
                android.util.Log.e("SupabaseClient", "Anonymous sign-in failed HTTP ${res.code}: $body")
                return null
            }
            val parsed = gson.fromJson(body, AuthTokenResponseDto::class.java)
            val token = parsed.access_token
            if (!token.isNullOrBlank()) {
                val expiresIn = parsed.expires_in ?: 3600L
                activeJwtToken = token
                authManager?.saveAuthToken(token, expiresIn)
                android.util.Log.d("SupabaseClient", "Anonymous session refreshed (valid ${expiresIn}s)")
                token
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Exception during sync anonymous sign-in: ${e.message}", e)
            null
        }
    }

    /**
     * Elevates anon → authenticated so RLS policies apply after license bind.
     * Not email/password — product auth remains license key for all roles.
     * Requires Anonymous Sign-Ins enabled in the Supabase project.
     */
    suspend fun ensureAuthenticatedSession(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val effective = getEffectiveToken()
        if (!effective.isNullOrBlank() && effective != SupabaseConfig.PUBLISHABLE_KEY) {
            return@withContext true
        }
        val token = refreshAnonymousSessionSync()
        token != null
    }

    suspend fun verifyAndBindLicense(
        licenseKey: String,
        hardwareId: String,
        deviceModel: String,
        deviceName: String
    ): LicenseVerificationResultDto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val rpcUrl = "${SupabaseConfig.REST_URL}/rpc/verify_and_bind_license"
            val payload = mapOf(
                "p_license_key" to licenseKey.trim().uppercase(),
                "p_hardware_id" to hardwareId,
                "p_device_model" to deviceModel,
                "p_device_name" to deviceName
            )
            val req = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: "{}"
            val result = gson.fromJson(body, LicenseVerificationResultDto::class.java)
            if (result.success) {
                // Headers need the saved license; caller saves it, then we elevate session.
                // Temporary headers for immediate post-verify authenticated session:
                // ensureAuthenticatedSession is called by LicenseGateScreen after save.
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Exception verifying license: ${e.message}", e)
            LicenseVerificationResultDto(
                success = false,
                error_code = "NETWORK_ERROR",
                message = e.message ?: "Failed to connect to license server"
            )
        }
    }

    /**
     * Check-only: does NOT bind hardware.
     * Returns null on network/parse failure so callers can keep offline data.
     */
    suspend fun checkLicenseSession(
        licenseKey: String,
        hardwareId: String
    ): LicenseSessionCheckDto? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val rpcUrl = "${SupabaseConfig.REST_URL}/rpc/check_license_session"
            val payload = mapOf(
                "p_license_key" to licenseKey.trim().uppercase(),
                "p_hardware_id" to hardwareId
            )
            val req = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: "{}"
            if (!res.isSuccessful) {
                android.util.Log.e("SupabaseClient", "check_license_session HTTP ${res.code}: $body")
                return@withContext null
            }
            gson.fromJson(body, LicenseSessionCheckDto::class.java)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Exception checking license session: ${e.message}", e)
            null
        }
    }

    private val ordersApi = OrdersApi(client, gson, ::buildRequest, ::getEffectiveToken)
    private val masterDataApi = MasterDataApi(client, gson, ::buildRequest)
    private val trackingApi = TrackingApi(client, gson, ::buildRequest)

    // Master Data Operations
    suspend fun getStaff(): List<StaffDto>? = masterDataApi.getStaff()

    suspend fun getParties(): List<PartyDto>? = masterDataApi.getParties()

    suspend fun getProducts(): List<ProductDto>? = masterDataApi.getProducts()

    suspend fun getDesigns(): List<com.shreenathji.sales.data.remote.dto.DesignDto>? = masterDataApi.getDesigns()

    suspend fun getDesignFolders(): List<com.shreenathji.sales.data.remote.dto.DesignFolderDto>? =
        masterDataApi.getDesignFolders()

    suspend fun pushParty(party: PartyEntity): Boolean = masterDataApi.pushParty(party)

    suspend fun getVisitPlans(date: String? = null): List<DailyVisitPlanDto>? = masterDataApi.getVisitPlans(date)

    suspend fun pushVisitPlan(plan: DailyVisitPlanEntity): Boolean {
        val isoDate = plan.visitedAt?.let {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.format(java.util.Date(it))
        }
        val dto = com.shreenathji.sales.data.remote.dto.DailyVisitPlanDto(
            id = plan.id,
            plan_date = plan.planDate,
            sales_rep_id = plan.salesRepId,
            sales_rep_name = plan.salesRepName,
            party_id = plan.partyId,
            party_name = plan.partyName,
            party_address = plan.partyAddress,
            priority_order = plan.priorityOrder,
            instructions = plan.instructions,
            visit_purpose = plan.visitPurpose,
            status = plan.status,
            visited_at = isoDate,
            order_id = plan.orderId,
            remarks = plan.remarks
        )
        return masterDataApi.insertVisitPlan(dto)
    }

    suspend fun updateVisitPlanStatus(
        planId: String,
        status: String,
        visitedAt: String?,
        orderId: String?,
        remarks: String? = null
    ): Boolean = masterDataApi.updateVisitPlanStatus(planId, status, visitedAt, orderId, remarks)

    suspend fun deleteVisitPlan(planId: String): Boolean = masterDataApi.deleteVisitPlan(planId)

    // Orders Operations
    suspend fun getOrdersWithItems(): List<OrderDto>? {
        ensureAuthenticatedSession()
        return ordersApi.getOrdersWithItems()
    }

    suspend fun uploadOrderImage(imageBytes: ByteArray, orderNumber: String): String? {
        ensureAuthenticatedSession()
        return ordersApi.uploadOrderImage(imageBytes, orderNumber)
    }

    suspend fun uploadVisitVoiceNote(audioBytes: ByteArray, planId: String): String? {
        // Storage INSERT policies require the authenticated role (not anon/publishable key).
        ensureAuthenticatedSession()
        return ordersApi.uploadVisitVoiceNote(audioBytes, planId)
    }

    suspend fun pushOrder(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        uploadedImageUrls: List<String>? = null
    ): Boolean {
        ensureAuthenticatedSession()
        return ordersApi.pushOrder(order, items, uploadedImageUrls)
    }

    // Tracking Operations
    suspend fun pushLocationLog(
        log: LocationLogEntity,
        salesRepId: String?,
        salesRepName: String?
    ): Boolean {
        ensureAuthenticatedSession()
        return trackingApi.pushLocationLog(log, salesRepId, salesRepName)
    }

    suspend fun pushLocationLogsBatch(
        logs: List<LocationLogEntity>,
        salesRepId: String?,
        salesRepName: String?
    ): Boolean {
        if (logs.isEmpty()) return true
        ensureAuthenticatedSession()
        return trackingApi.pushLocationLogsBatch(logs, salesRepId, salesRepName)
    }
}
