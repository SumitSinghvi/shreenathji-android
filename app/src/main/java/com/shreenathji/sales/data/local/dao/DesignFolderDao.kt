package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignFolderDao {
    @Query("SELECT * FROM design_folders ORDER BY sortOrder ASC, name ASC")
    fun getAllFolders(): Flow<List<DesignFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<DesignFolderEntity>)

    @Query("DELETE FROM design_folders")
    suspend fun clearAllFolders()

    @Query("DELETE FROM design_folders WHERE id NOT IN (:ids)")
    suspend fun deleteFoldersNotIn(ids: List<String>)

    @Transaction
    suspend fun syncReplaceFolders(folders: List<DesignFolderEntity>) {
        if (folders.isNotEmpty()) {
            insertFolders(folders)
            deleteFoldersNotIn(folders.map { it.id })
        } else {
            clearAllFolders()
        }
    }
}
