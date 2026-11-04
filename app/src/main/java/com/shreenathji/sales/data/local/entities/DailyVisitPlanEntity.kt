package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_visit_plans")
data class DailyVisitPlanEntity(
    @PrimaryKey
    val id: String,
    val planDate: String, // "YYYY-MM-DD"
    val salesRepId: String,
    val salesRepName: String,
    val partyId: String,
    val partyName: String,
    val partyAddress: String? = null,
    val partyPhone: String? = null,
    val priorityOrder: Int = 1,
    val instructions: String? = null,
    val visitPurpose: String = "ORDER", // "ORDER", "PAYMENT", "SAMPLE", "GENERAL"
    val status: String = "PENDING",     // "PENDING", "IN_PROGRESS", "VISITED", "SKIPPED"
    val visitedAt: Long? = null,
    val orderId: String? = null,
    val remarks: String? = null,
    val isSynced: Boolean = true
)
