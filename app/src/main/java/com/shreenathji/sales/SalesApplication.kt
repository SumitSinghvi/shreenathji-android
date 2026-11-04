package com.shreenathji.sales

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.shreenathji.sales.data.local.AppDatabase
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.LicenseSessionGuard
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.repository.SalesRepository
import com.shreenathji.sales.data.sync.SupabaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SalesApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val authManager by lazy { AuthManager(this) }
    val supabaseClient by lazy { SupabaseClient(tokenProvider = null, authManager = authManager) }

    val licenseSessionGuard by lazy {
        LicenseSessionGuard(
            context = this,
            authManager = authManager,
            supabaseClient = supabaseClient,
            database = database
        )
    }

    val syncManager by lazy {
        SupabaseSyncManager(
            context = this,
            supabaseClient = supabaseClient,
            partyDao = database.partyDao(),
            productDao = database.productDao(),
            orderDao = database.orderDao(),
            locationDao = database.locationDao(),
            dailyVisitPlanDao = database.dailyVisitPlanDao(),
            designDao = database.designDao(),
            designFolderDao = database.designFolderDao(),
            authManager = authManager,
            licenseSessionGuard = licenseSessionGuard,
            scope = applicationScope
        )
    }

    val repository by lazy {
        SalesRepository(
            database.partyDao(),
            database.productDao(),
            database.orderDao(),
            database.locationDao(),
            database.dailyVisitPlanDao(),
            database.designDao(),
            database.designFolderDao(),
            syncManager = syncManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Validate license session, then sync — wipe local data if admin reset HW lock
        applicationScope.launch {
            if (authManager.isLicensed()) {
                val stillValid = licenseSessionGuard.validateOrWipe()
                if (stillValid) {
                    supabaseClient.ensureAuthenticatedSession()
                    syncManager.syncAll()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shreenathji Field Sales",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Field sales synchronization service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "shreenathji_tracking_channel"
    }
}
