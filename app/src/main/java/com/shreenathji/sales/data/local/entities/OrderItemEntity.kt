package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val productName: String,
    val meter: Double,
    val price: Double,
    val totalPrice: Double,
    val notes: String? = null,
    val designId: String? = null,
    val imageUrl: String? = null,
    val isMatching: Boolean = false,
    val multiplier: Int = 1,
    val cutMeter: Double? = null,
    val component: String? = null,
    val setGroupId: String? = null
)
