package com.shreenathji.sales.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "design_folders")
data class DesignFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val coverDesignId: String? = null,
    val createdAt: String? = null
)
