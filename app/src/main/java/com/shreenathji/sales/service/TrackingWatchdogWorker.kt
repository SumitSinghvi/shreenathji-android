package com.shreenathji.sales.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shreenathji.sales.SalesApplication
import java.util.Calendar

/**
 * Runs every ~15 min. If tracking should be on but has gone quiet (service killed by the phone's
 * battery manager, permission revoked, GPS off), it tries to restart the service — silently, no
 * notification to the rep. If a restart isn't possible from the background (Android 12+ blocks
 * that), it just logs the status; the next app open restarts the service as before.
 */
class TrackingWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SalesApplication
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val licensed = app.authManager.isLicensed()
        val last = if (licensed) app.database.locationDao().getLatestLocation() else null

        val status = TrackingHealth.evaluate(
            licensed = licensed,
            hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            fineGranted = fine,
            backgroundGranted = background,
            gpsEnabled = gpsOn,
            msSinceLastFix = last?.let { System.currentTimeMillis() - it.timestamp }
        )
        Log.d(TAG, "Tracking health: $status")

        when (status) {
            TrackingHealth.Status.OK, TrackingHealth.Status.NOT_APPLICABLE -> { /* nothing to do */ }
            TrackingHealth.Status.NEEDS_PERMISSION, TrackingHealth.Status.GPS_OFF ->
                Log.w(TAG, "Tracking blocked: $status (no user-facing alert)")
            TrackingHealth.Status.STALE -> tryRestartService()
        }
        return Result.success()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED

    /** Android 12+ forbids starting a foreground service from the background; that throws. */
    private fun tryRestartService() {
        try {
            ContextCompat.startForegroundService(
                applicationContext, Intent(applicationContext, LocationTrackingService::class.java)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot restart tracking from background: ${e.message}")
        }
    }

    companion object {
        const val UNIQUE_NAME = "tracking_watchdog"
        private const val TAG = "TrackingWatchdog"
    }
}
