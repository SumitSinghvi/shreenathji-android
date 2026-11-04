package com.shreenathji.sales.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.pm.PackageInfoCompat
import com.shreenathji.sales.data.remote.SupabaseConfig
import com.shreenathji.sales.data.remote.dto.AppUpdateDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val percentage: Int) : DownloadState()
    data class Success(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class AppUpdateManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val tag = "AppUpdateManager"

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(packageInfo)
        } catch (e: Exception) {
            1L
        }
    }

    /**
     * Checks Supabase `app_versions` table for any newer version release.
     * Returns AppUpdateDto if an update is available, null otherwise.
     */
    suspend fun checkForUpdate(
        customEndpointUrl: String? = null
    ): AppUpdateDto? = withContext(Dispatchers.IO) {
        try {
            val url = customEndpointUrl ?: "${SupabaseConfig.REST_URL}/app_versions?select=*&order=version_code.desc&limit=1"
            
            val requestBuilder = Request.Builder().url(url)
            // Add Supabase headers if hitting Supabase endpoint
            if (url.contains(SupabaseConfig.BASE_URL)) {
                requestBuilder.addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                requestBuilder.addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Log.w(tag, "Version check HTTP error code: ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            
            val updateDto = if (bodyString.trim().startsWith("[")) {
                val listType = object : TypeToken<List<AppUpdateDto>>() {}.type
                val list: List<AppUpdateDto> = gson.fromJson(bodyString, listType)
                list.firstOrNull()
            } else {
                gson.fromJson(bodyString, AppUpdateDto::class.java)
            }

            if (updateDto != null) {
                val currentVersionCode = getCurrentVersionCode()
                Log.d(tag, "Current version code: $currentVersionCode, Remote version code: ${updateDto.version_code}")
                
                if (updateDto.version_code.toLong() > currentVersionCode) {
                    val minSupported = updateDto.min_supported_version_code?.toLong() ?: 0L
                    val isMandatory = updateDto.is_mandatory || (minSupported > 0L && currentVersionCode < minSupported)
                    return@withContext updateDto.copy(is_mandatory = isMandatory)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(tag, "Failed to check for app update", e)
            null
        }
    }

    /**
     * Downloads the APK file to the app's cache directory and streams download progress.
     */
    fun downloadApk(downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0))
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                emit(DownloadState.Error("Server returned code ${response.code} when downloading update."))
                return@flow
            }

            val body = response.body!!
            val contentLength = body.contentLength()
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destinationFile = File(updateDir, "update.apk")

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastPercentage = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            val percentage = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                            if (percentage > lastPercentage) {
                                lastPercentage = percentage
                                emit(DownloadState.Progress(percentage))
                            }
                        }
                    }
                    output.flush()
                }
            }

            emit(DownloadState.Success(destinationFile))
        } catch (e: Exception) {
            Log.e(tag, "Error during APK download", e)
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown error occurred while downloading update"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Triggers the Android Package Installer for the downloaded APK file.
     */
    fun installApk(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch package installer", e)
        }
    }
}
