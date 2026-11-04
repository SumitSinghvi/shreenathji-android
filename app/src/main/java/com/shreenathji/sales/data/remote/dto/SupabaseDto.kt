package com.shreenathji.sales.data.remote.dto

data class StaffDto(
    val id: String,
    val name: String,
    val role: String,
    val pin_code: String? = null,
    val phone: String? = null,
    val order_prefix: String? = null,
    val is_active: Boolean = true
)

data class PartyDto(
    val id: String,
    val business_name: String,
    val contact_person: String? = null,
    val phone: String? = null,
    val address_line1: String? = null,
    val address_line2: String? = null,
    val default_agent: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val assigned_rep_ids: List<String>? = null,
    val updated_at: String? = null
)

data class ProductDto(
    val id: String,
    val name: String,
    val category: String? = null,
    val default_rate: Double,
    val unit: String = "Mtr",
    val is_active: Boolean = true,
    val is_matching: Boolean = false,
    val matching_multiplier: Int? = 1,
    val is_top_bottom: Boolean? = false,
    val default_rate_bottom: Double? = null,
    val updated_at: String? = null
)

/** Row of public.deleted_records (server-side tombstone). */
data class TombstoneDto(
    val table_name: String,
    val record_id: String,
    val deleted_at: String
)

data class OrderDto(
    val id: String,
    val order_number: String,
    val party_id: String? = null,
    val party_name: String,
    val party_address: String? = null,
    val agent: String? = null,
    val po_number: String? = null,
    val delivery_address: String? = null,
    val total_amount: Double,
    val order_status: String = "PENDING",
    val notes: String? = null,
    val rejection_reason: String? = null,
    val sales_rep_id: String? = null,
    val sales_rep_name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val image_urls: List<String>? = null,
    val design_ids: List<String>? = null,
    val created_at: String? = null,
    val order_timestamp: String? = null,
    val order_items: List<OrderItemDto>? = null
)

data class OrderItemDto(
    val id: String,
    val order_id: String,
    val product_name: String,
    val meter: Double,
    val price: Double,
    val total_price: Double,
    val notes: String? = null,
    val design_id: String? = null,
    val image_url: String? = null,
    val is_matching: Boolean = false,
    val multiplier: Int = 1,
    val cut_meter: Double? = null,
    val component: String? = null,
    val set_group_id: String? = null
)

data class DailyVisitPlanDto(
    val id: String,
    val plan_date: String,
    val sales_rep_id: String,
    val sales_rep_name: String,
    val party_id: String,
    val party_name: String,
    val party_address: String? = null,
    val priority_order: Int = 1,
    val instructions: String? = null,
    val visit_purpose: String = "ORDER",
    val status: String = "PENDING",
    val visited_at: String? = null,
    val order_id: String? = null,
    val remarks: String? = null
)

data class DesignDto(
    val id: String,
    val image_url: String,
    val thumb_url: String? = null,
    val original_filename: String? = null,
    val notes: String? = null,
    val uploaded_by_staff_id: String? = null,
    val uploaded_by_name: String? = null,
    val captured_on: String,
    val is_archived: Boolean = false,
    val folder_id: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class DesignFolderDto(
    val id: String,
    val name: String,
    val parent_id: String? = null,
    val sort_order: Int = 0,
    val cover_design_id: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class LocationLogDto(
    val sales_rep_id: String? = null,
    val sales_rep_name: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val battery_level: Int? = null,
    /** Capture time on device (ISO-8601 UTC); preserves trail order after offline backfill. */
    val recorded_at: String? = null
)

data class AuthTokenResponseDto(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long? = null,
    val user: AuthUserDto? = null
)

data class AuthUserDto(
    val id: String? = null,
    /** Parsed from Auth JSON only; the app does not use email/password login. */
    val email: String? = null
)

data class ActiveLicenseDto(
    val id: String? = null,
    val license_key: String? = null,
    val role: String? = null,
    val bound_hardware_id: String? = null,
    val device_name: String? = null,
    val device_model: String? = null,
    val activated_at: String? = null
)

data class LicenseVerificationResultDto(
    val success: Boolean,
    val error_code: String? = null,
    val message: String? = null,
    val bound_device: String? = null,
    val license: ActiveLicenseDto? = null,
    val staff: StaffDto? = null
)

/** Result of check_license_session — check-only, never binds hardware. */
data class LicenseSessionCheckDto(
    val valid: Boolean = false,
    val error_code: String? = null,
    val message: String? = null
)

