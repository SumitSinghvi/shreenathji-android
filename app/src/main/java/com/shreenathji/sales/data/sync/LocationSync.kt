package com.shreenathji.sales.data.sync

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.LicenseSessionGuard
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.dao.DailyVisitPlanDao
import com.shreenathji.sales.data.local.dao.DesignDao
import com.shreenathji.sales.data.local.dao.DesignFolderDao
import com.shreenathji.sales.data.local.dao.LocationDao
import com.shreenathji.sales.data.local.dao.OrderDao
import com.shreenathji.sales.data.local.dao.PartyDao
import com.shreenathji.sales.data.local.dao.ProductDao
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.DesignEntity
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.local.entities.ProductEntity
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.remote.dto.DesignDto
import com.shreenathji.sales.data.remote.dto.DesignFolderDto
import com.shreenathji.sales.data.remote.dto.PartyDto
import com.shreenathji.sales.data.remote.dto.ProductDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Location log upload, split out of [SupabaseSyncManager]. */
internal class LocationSync(
    private val supabaseClient: SupabaseClient,
    private val locationDao: LocationDao,
    private val authManager: AuthManager,
    private val licenseSessionGuard: LicenseSessionGuard?,
    private val scope: CoroutineScope
) {
    private var consecutiveLocationFailures = 0

    private val locationMutex = Mutex()

    /**
     * Uploads every unsynced location, oldest first, up to 50 per request. Serialized with a lock:
     * the tracking service (after each fix) and the full sync both call this, and without the lock
     * they could send the same rows twice.
     */
    suspend fun syncPendingLocations() = locationMutex.withLock {
        try {
            // Prune old logs (>48h) to prevent unbounded local DB growth
            val cutoff = System.currentTimeMillis() - (48 * 3600 * 1000L)
            locationDao.pruneOldLocations(cutoff)

            val staff = authManager.getCurrentStaff()
            repeat(MAX_LOCATION_BATCHES_PER_RUN) {
                val unsynced = locationDao.getUnsyncedLocations()
                if (unsynced.isEmpty()) return@withLock

                val success = supabaseClient.pushLocationLogsBatch(
                    logs = unsynced,
                    salesRepId = staff?.id,
                    salesRepName = staff?.name
                )

                if (success) {
                    consecutiveLocationFailures = 0
                    locationDao.markLocationsSynced(unsynced.map { it.id })
                    Log.d("SupabaseSync", "Uploaded ${unsynced.size} location logs in 1 batch")
                } else {
                    consecutiveLocationFailures++
                    Log.w("SupabaseSync", "Location batch sync failed (attempt $consecutiveLocationFailures)")
                    if (consecutiveLocationFailures >= 5) {
                        licenseSessionGuard?.validateOrWipe()
                    }
                    return@withLock
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error syncing pending locations: ${e.message}")
        }
    }

    fun pushLocationAsync(log: LocationLogEntity) {
        scope.launch {
            pushLocationAndMark(log)
        }
    }

    suspend fun pushLocationAndMark(log: LocationLogEntity): Boolean {
        val staff = authManager.getCurrentStaff()
        val success = supabaseClient.pushLocationLog(
            log = log,
            salesRepId = staff?.id,
            salesRepName = staff?.name
        )
        if (success) {
            consecutiveLocationFailures = 0
            if (log.id > 0) {
                locationDao.markLocationsSynced(listOf(log.id))
            }
        } else {
            consecutiveLocationFailures++
            if (consecutiveLocationFailures >= 5) {
                licenseSessionGuard?.validateOrWipe()
            }
        }
        return success
    }

    private companion object {
        const val MAX_LOCATION_BATCHES_PER_RUN = 5
    }
}
