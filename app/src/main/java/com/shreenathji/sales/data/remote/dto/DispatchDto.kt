package com.shreenathji.sales.data.remote.dto

// Dispatch screens never request rates or amounts: these DTOs have no price fields.

data class DispatchOrderItemDto(
    val id: String,
    val product_name: String,
    val meter: Double,
    val component: String? = null,
    val notes: String? = null,
    val image_url: String? = null
)

/** An approved (CONFIRMED) order waiting to be dispatched. */
data class DispatchOrderDto(
    val id: String,
    val order_number: String,
    val party_name: String,
    val party_address: String? = null,
    val notes: String? = null,
    val order_timestamp: String? = null,
    val image_urls: List<String>? = null,
    val order_items: List<DispatchOrderItemDto>? = null
)

data class DispatchItemDto(
    val id: String,
    val order_item_id: String,
    val product_name: String,
    val component: String? = null,
    val ordered_meter: Double,
    val actual_meter: Double,
    val notes: String? = null,
    val image_url: String? = null
)

data class DispatchOrderRefDto(
    val order_number: String,
    val party_name: String,
    val order_status: String? = null,
    val image_urls: List<String>? = null
)

/** A dispatch this staff member already submitted. */
data class DispatchRecordDto(
    val id: String,
    val order_id: String,
    val dispatched_at: String? = null,
    val lr_number: String? = null,
    val transporter: String? = null,
    val vehicle_no: String? = null,
    val notes: String? = null,
    val photo_urls: List<String>? = null,
    val dispatch_items: List<DispatchItemDto>? = null,
    val orders: DispatchOrderRefDto? = null
)
