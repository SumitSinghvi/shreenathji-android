package com.shreenathji.sales.data.local.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shreenathji.sales.data.local.AppDatabase
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.service.LocationTrackingService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Re-validates the local license against the server without rebinding hardware.
 * On Reset device lock / revoke / HW mismatch: wipe local Room + auth and emit
 * [sessionInvalidated] so the UI returns to the license gate.
 *
 * Network failures do NOT wipe — offline field use must keep working.
 */
class LicenseSessionGuard(
    private val context: Context,
    private val authManager: AuthManager,
    private val supabaseClient: SupabaseClient,
    private val database: AppDatabase
) {
    private val mutex = Mutex()

    private val _sessionInvalidated = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionInvalidated: SharedFlow<String> = _sessionInvalidated.asSharedFlow()

    /**
     * @return true if the session is still usable (valid, no license, or offline skip).
     *         false if the session was invalidated and local data was wiped.
     */
    suspend fun validateOrWipe(): Boolean = mutex.withLock {
        val licenseKey = authManager.getActiveLicenseKey()
        if (licenseKey.isNullOrBlank()) {
            return true
        }

        val result = supabaseClient.checkLicenseSession(
            licenseKey = licenseKey,
            hardwareId = authManager.getDeviceId()
        )

        // Network / parse failure — keep local offline data
        if (result == null) {
            Log.w(TAG, "License session check skipped (network error)")
            return true
        }

        if (result.valid) {
            return true
        }

        val message = result.message
            ?: "License was unlocked by an administrator. Please activate again."
        Log.w(TAG, "License session invalid (${result.error_code}): $message")
        wipeLocalSession()
        _sessionInvalidated.tryEmit(message)
        return false
    }

    private suspend fun wipeLocalSession() {
        // Clear cached server data, but keep work not yet uploaded (orders, visits, new stores):
        // a license reset by the office must not destroy field orders. They upload after the
        // device is activated again (orders carry their own sales_rep_id).
        // Location logs are dropped: they are tagged with whoever is signed in at upload time.
        try {
            database.productDao().clearAllProducts()
            database.designDao().clearAllDesigns()
            database.designFolderDao().clearAllFolders()
            database.locationDao().clearAllLocations()
            database.orderDao().clearSyncedOrderItems()
            database.orderDao().clearSyncedOrders()
            database.dailyVisitPlanDao().clearSyncedPlans()
            database.partyDao().clearSyncedParties()
            // Delta cursors must reset with the tables, or the next sync pulls only changes
            // into empty tables.
            context.getSharedPreferences("sync_cursors", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Local wipe failed: ${e.message}", e)
        }

        authManager.clearLicense()
        supabaseClient.setAuthToken(null)
        stopTrackingService()
    }

    private fun stopTrackingService() {
        try {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tracking service: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "LicenseSessionGuard"
    }
}
