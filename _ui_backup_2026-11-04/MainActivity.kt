package com.shreenathji.sales.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shreenathji.sales.SalesApplication
import com.shreenathji.sales.data.remote.dto.AppUpdateDto
import com.shreenathji.sales.service.LocationTrackingService
import com.shreenathji.sales.ui.components.AppUpdateDialog
import com.shreenathji.sales.ui.navigation.AppNavigation
import com.shreenathji.sales.ui.theme.ShreenathjiSalesTheme
import com.shreenathji.sales.util.AppUpdateManager
import com.shreenathji.sales.util.DownloadState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startTrackingService()
            requestBackgroundLocationIfNeeded()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Background location result — service already running as foreground service
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        requestIgnoreBatteryOptimizations()

        val app = application as SalesApplication
        val repository = app.repository
        val authManager = app.authManager

        // Push immediate online heartbeat on app launch if staff is already logged in
        if (authManager.isLoggedIn()) {
            val staff = authManager.getCurrentStaff()
            if (staff != null) {
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this@MainActivity)
                            fusedClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        repository.logLocation(
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            accuracy = loc.accuracy,
                                            speed = loc.speed
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        setContent {
            ShreenathjiSalesTheme {
                val updateManager = remember { AppUpdateManager(this@MainActivity) }
                var updateDto by remember { mutableStateOf<AppUpdateDto?>(null) }
                var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
                val coroutineScope = rememberCoroutineScope()

                // Check for updates on startup
                LaunchedEffect(Unit) {
                    val update = updateManager.checkForUpdate()
                    if (update != null) {
                        updateDto = update
                    }
                }

                AppNavigation(
                    repository = repository,
                    authManager = authManager,
                    supabaseClient = app.supabaseClient,
                    licenseSessionGuard = app.licenseSessionGuard
                )

                // Render In-App Update Dialog when a newer release is detected
                updateDto?.let { info ->
                    AppUpdateDialog(
                        updateDto = info,
                        downloadState = downloadState,
                        onStartDownload = {
                            coroutineScope.launch {
                                updateManager.downloadApk(info.apk_url).collect { state ->
                                    downloadState = state
                                    if (state is DownloadState.Success) {
                                        updateManager.installApk(state.apkFile)
                                    }
                                }
                            }
                        },
                        onInstall = {
                            (downloadState as? DownloadState.Success)?.let { successState ->
                                updateManager.installApk(successState.apkFile)
                            }
                        },
                        onDismiss = {
                            updateDto = null
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as SalesApplication
        if (app.authManager.isLicensed()) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                app.licenseSessionGuard.validateOrWipe()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Foreground permissions already granted — start service and request background
            startTrackingService()
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startTrackingService() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            val app = application as? SalesApplication
            if (app != null && !app.authManager.isLicensed()) {
                return
            }
            val serviceIntent = Intent(this, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
