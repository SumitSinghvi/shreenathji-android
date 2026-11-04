package com.shreenathji.sales.service

/** Pure decision logic for the tracking watchdog, kept free of Android types so it can be unit-tested. */
object TrackingHealth {
    enum class Status { OK, NOT_APPLICABLE, NEEDS_PERMISSION, GPS_OFF, STALE }

    /** No fix for this long while tracking should be on = the service was likely killed (interval is 2 min). */
    const val STALE_AFTER_MS = 10 * 60 * 1000L
    const val WORK_START_HOUR = 8
    const val WORK_END_HOUR = 21

    fun evaluate(
        licensed: Boolean,
        hourOfDay: Int,
        fineGranted: Boolean,
        backgroundGranted: Boolean,
        gpsEnabled: Boolean,
        msSinceLastFix: Long?
    ): Status = when {
        !licensed || hourOfDay < WORK_START_HOUR || hourOfDay >= WORK_END_HOUR -> Status.NOT_APPLICABLE
        !fineGranted || !backgroundGranted -> Status.NEEDS_PERMISSION
        !gpsEnabled -> Status.GPS_OFF
        msSinceLastFix == null || msSinceLastFix > STALE_AFTER_MS -> Status.STALE
        else -> Status.OK
    }
}
