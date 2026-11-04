package com.shreenathji.sales.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shreenathji.sales.SalesApplication
import com.shreenathji.sales.ui.MainActivity
import java.util.Calendar

/**
 * Runs every ~15 min. If tracking should be on but has gone quiet (service killed by the phone's
 * battery manager, permission revoked, GPS off), it tries to restart the service and, when that is
 * not possible from the background, tells the rep with a notification (tapping it reopens the app,
 * which restarts the service).
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
            TrackingHealth.Status.OK, TrackingHealth.Status.NOT_APPLICABLE -> clearAlert()
            TrackingHealth.Status.NEEDS_PERMISSION ->
                alert("Location access needed", "Set location to \"Allow all the time\" so your visits are recorded.")
            TrackingHealth.Status.GPS_OFF ->
                alert("Location is turned off", "Turn on location so your visits are recorded.")
            TrackingHealth.Status.STALE -> {
                if (!tryRestartService()) {
                    alert("Tracking paused", "Tap to resume location tracking.")
                }
            }
        }
        return Result.success()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED

    /** Android 12+ forbids starting a foreground service from the background; that throws. */
    private fun tryRestartService(): Boolean = try {
        ContextCompat.startForegroundService(
            applicationContext, Intent(applicationContext, LocationTrackingService::class.java)
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot restart tracking from background: ${e.message}")
        false
    }

    private fun alert(title: String, text: String) {
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, SalesApplication.ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(ALERT_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notifications not permitted: ${e.message}")
        }
    }

    private fun clearAlert() = NotificationManagerCompat.from(applicationContext).cancel(ALERT_ID)

    companion object {
        const val UNIQUE_NAME = "tracking_watchdog"
        private const val ALERT_ID = 1002
        private const val TAG = "TrackingWatchdog"
    }
}
