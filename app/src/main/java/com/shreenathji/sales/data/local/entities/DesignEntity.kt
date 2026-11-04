package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "designs")
data class DesignEntity(
    @PrimaryKey
    val id: String,
    val imageUrl: String,
    val thumbUrl: String? = null,
    val originalFilename: String? = null,
    val notes: String? = null,
    val uploadedByStaffId: String? = null,
    val uploadedByName: String? = null,
    val capturedOn: String,
    val isArchived: Boolean = false,
    /** null = Unfiled */
    val folderId: String? = null,
    val createdAt: String? = null
)
