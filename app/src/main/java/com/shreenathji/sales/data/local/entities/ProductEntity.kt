package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** Process type: "Dying" or "Print". */
    val category: String,
    val defaultRate: Double,
    val unit: String = "Mtr", // "Mtr", "Piece", "Than", "Kg"
    val isMatching: Boolean = false,
    val matchingMultiplier: Int = 1,
    val isTopBottom: Boolean = false,
    val defaultRateBottom: Double? = null
)
