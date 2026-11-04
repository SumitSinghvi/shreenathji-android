package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY businessName ASC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun getPartyById(id: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE businessName LIKE '%' || :query || '%' OR addressLine1 LIKE '%' || :query || '%' OR addressLine2 LIKE '%' || :query || '%' OR contactPerson LIKE '%' || :query || '%' ORDER BY businessName ASC")
    fun searchParties(query: String): Flow<List<PartyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParties(parties: List<PartyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity)

    @Query("DELETE FROM parties")
    suspend fun clearAllParties()

    @Query("UPDATE parties SET isSynced = 1 WHERE id = :partyId")
    suspend fun markPartySynced(partyId: String)

    @Query("SELECT * FROM parties WHERE isSynced = 0 ORDER BY businessName ASC")
    suspend fun getUnsyncedParties(): List<PartyEntity>

    @Query("DELETE FROM parties WHERE id NOT IN (:ids) AND isSynced = 1")
    suspend fun deleteSyncedPartiesNotIn(ids: List<String>)

    @Query("DELETE FROM parties WHERE id IN (:ids) AND isSynced = 1")
    suspend fun deleteSyncedPartiesByIds(ids: List<String>)

    @Transaction
    suspend fun syncReplaceParties(parties: List<PartyEntity>) {
        // Upsert all remote parties (INSERT OR REPLACE on primary key)
        if (parties.isNotEmpty()) {
            insertParties(parties)
        }
        // Remove server-deleted parties (only those that were synced, preserving local-only)
        if (parties.isNotEmpty()) {
            val remoteIds = parties.map { it.id }
            deleteSyncedPartiesNotIn(remoteIds)
        }
    }

    @Query("SELECT COUNT(*) FROM parties")
    suspend fun getCount(): Int
}
