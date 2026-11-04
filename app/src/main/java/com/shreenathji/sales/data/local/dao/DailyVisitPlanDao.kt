package com.shreenathji.sales.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyVisitPlanDao {

    @Query("SELECT * FROM daily_visit_plans WHERE planDate = :date AND (salesRepName = :repName OR salesRepId = :repName) ORDER BY priorityOrder ASC")
    fun getPlansForRepAndDate(date: String, repName: String): Flow<List<DailyVisitPlanEntity>>

    @Query("SELECT * FROM daily_visit_plans WHERE (salesRepName = :repName OR salesRepId = :repName) ORDER BY priorityOrder ASC")
    fun getPlansForRep(repName: String): Flow<List<DailyVisitPlanEntity>>

    @Query("SELECT * FROM daily_visit_plans WHERE planDate = :date ORDER BY priorityOrder ASC")
    fun getAllPlansForDate(date: String): Flow<List<DailyVisitPlanEntity>>

    @Query("SELECT * FROM daily_visit_plans ORDER BY priorityOrder ASC")
    fun getAllPlans(): Flow<List<DailyVisitPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<DailyVisitPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: DailyVisitPlanEntity)

    @Query("SELECT * FROM daily_visit_plans WHERE partyId = :partyId AND planDate = :date LIMIT 1")
    suspend fun getPlanByPartyAndDate(partyId: String, date: String): DailyVisitPlanEntity?

    @Query("DELETE FROM daily_visit_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Query("UPDATE daily_visit_plans SET isSynced = 1 WHERE id = :id")
    suspend fun markPlanAsSynced(id: String)

    @Query("DELETE FROM daily_visit_plans WHERE isSynced = 1")
    suspend fun clearSyncedPlans()

    @Query("DELETE FROM daily_visit_plans")
    suspend fun clearAllPlans()

    @Transaction
    suspend fun syncReplacePlans(plans: List<DailyVisitPlanEntity>) {
        clearSyncedPlans()
        insertPlans(plans)
    }

    @Query("SELECT * FROM daily_visit_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: String): DailyVisitPlanEntity?

    @Query("UPDATE daily_visit_plans SET status = :status, visitedAt = :visitedAt, remarks = :remarks, orderId = :orderId, isSynced = :isSynced WHERE id = :id")
    suspend fun updatePlanStatus(
        id: String,
        status: String,
        visitedAt: Long?,
        remarks: String?,
        orderId: String?,
        isSynced: Boolean
    )

    @Query("UPDATE daily_visit_plans SET remarks = :remarks, isSynced = 0 WHERE id = :id")
    suspend fun updateRemarks(id: String, remarks: String)

    @Query("UPDATE daily_visit_plans SET status = 'PENDING', visitedAt = NULL, remarks = NULL, isSynced = 0 WHERE id = :id")
    suspend fun markPlanUnvisited(id: String)

    @Query("SELECT * FROM daily_visit_plans WHERE isSynced = 0")
    suspend fun getUnsyncedPlans(): List<DailyVisitPlanEntity>
}
