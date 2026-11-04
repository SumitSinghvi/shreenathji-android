package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.DesignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignDao {
    @Query("SELECT * FROM designs ORDER BY capturedOn DESC, createdAt DESC")
    fun getAllDesigns(): Flow<List<DesignEntity>>

    @Query("SELECT * FROM designs WHERE isArchived = 0 ORDER BY capturedOn DESC, createdAt DESC")
    fun getActiveDesigns(): Flow<List<DesignEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesigns(designs: List<DesignEntity>)

    @Query("DELETE FROM designs")
    suspend fun clearAllDesigns()

    @Query("DELETE FROM designs WHERE id NOT IN (:ids)")
    suspend fun deleteDesignsNotIn(ids: List<String>)

    @Transaction
    suspend fun syncReplaceDesigns(designs: List<DesignEntity>) {
        if (designs.isNotEmpty()) {
            insertDesigns(designs)
            deleteDesignsNotIn(designs.map { it.id })
        } else {
            clearAllDesigns()
        }
    }
}
