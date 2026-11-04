package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationLogEntity): Long

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLocations(): Flow<List<LocationLogEntity>>

    @Query("SELECT * FROM location_logs WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsyncedLocations(): List<LocationLogEntity>

    @Query("UPDATE location_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markLocationsSynced(ids: List<Long>)

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocation(): LocationLogEntity?

    @Query("DELETE FROM location_logs WHERE timestamp < :cutoffMillis")
    suspend fun pruneOldLocations(cutoffMillis: Long): Int

    @Query("DELETE FROM location_logs")
    suspend fun clearAllLocations()
}
