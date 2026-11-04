package com.shreenathji.sales.data.remote.api

import android.util.Log
import com.google.gson.Gson
import com.shreenathji.sales.data.local.entities.LocationLogEntity
import com.shreenathji.sales.data.remote.dto.LocationLogDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TrackingApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val requestBuilder: (String, String, String?, Boolean) -> Request
) {
    suspend fun pushLocationLog(log: LocationLogEntity, salesRepId: String?, salesRepName: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val dto = LocationLogDto(
                sales_rep_id = salesRepId,
                sales_rep_name = salesRepName,
                latitude = log.latitude,
                longitude = log.longitude,
                accuracy = log.accuracy,
                speed = log.speed,
                battery_level = log.batteryLevel,
                recorded_at = formatRecordedAtUtc(log.timestamp)
            )
            val json = gson.toJson(dto)
            val request = requestBuilder("location_logs", "POST", json, false)
            val response = client.newCall(request).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e("TrackingApi", "Failed to push location log to Supabase: ${e.message}")
            return@withContext false
        }
    }

    suspend fun pushLocationLogsBatch(logs: List<LocationLogEntity>, salesRepId: String?, salesRepName: String?): Boolean = withContext(Dispatchers.IO) {
        if (logs.isEmpty()) return@withContext true
        try {
            val dtos = logs.map { log ->
                LocationLogDto(
                    sales_rep_id = salesRepId,
                    sales_rep_name = salesRepName,
                    latitude = log.latitude,
                    longitude = log.longitude,
                    accuracy = log.accuracy,
                    speed = log.speed,
                    battery_level = log.batteryLevel,
                    recorded_at = formatRecordedAtUtc(log.timestamp)
                )
            }
            val json = gson.toJson(dtos)
            val request = requestBuilder("location_logs", "POST", json, false)
            return@withContext client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("TrackingApi", "Failed to push location batch to Supabase: ${e.message}")
            return@withContext false
        }
    }

    private fun formatRecordedAtUtc(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }
}
