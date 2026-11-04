package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [Index(value = ["orderTimestamp"]), Index(value = ["isSynced"])]
)
data class OrderEntity(
    @PrimaryKey
    val id: String,
    val orderNumber: String,
    val partyId: String,
    val partyName: String,
    val partyAddress: String? = null,
    val agent: String? = null,              // Optional
    val poNumber: String? = null,           // Optional
    val deliveryAddress: String? = null,    // Optional
    val imageUrisJson: String? = null,      // Optional JSON list of local image paths/URIs
    val designIdsJson: String? = null,      // Optional JSON list of design library ids
    val totalAmount: Double,
    val orderStatus: String = "PENDING",
    val rejectionReason: String? = null,    // Set by office if order is rejected
    val notes: String? = null,
    val salesRepId: String? = null,
    val salesRepName: String? = null,
    val orderTimestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,           // Recorded silently in background
    val longitude: Double? = null,          // Recorded silently in background
    val isSynced: Boolean = false
)
