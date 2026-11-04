package com.shreenathji.sales.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class DeviceHardwareInfo(
    val brand: String,
    val model: String,
    val fullName: String,
    val osVersion: String,
    val simPhoneNumber: String?,
    val simOperator: String?
)

object DeviceHardwareHelper {

    @SuppressLint("HardwareIds")
    fun getPersistentHardwareId(context: Context): String {
        var androidId = ""
        try {
            androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val cleanModel = Build.MODEL.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return if (androidId.isNotBlank() && androidId != "9774d56d682e549c") {
            "dev_${cleanModel.take(10)}_$androidId"
        } else {
            val fallback = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.BOARD}".lowercase().replace(Regex("[^a-z0-9]"), "_")
            "dev_$fallback"
        }
    }

    @SuppressLint("HardwareIds")
    fun getHardwareInfo(context: Context): DeviceHardwareInfo {
        val manufacturer = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val brand = Build.BRAND.trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.trim()

        val brandName = if (brand.isNotEmpty() && !brand.equals("unknown", ignoreCase = true)) brand else manufacturer
        val fullName = if (model.startsWith(brandName, ignoreCase = true)) {
            model
        } else {
            "$brandName $model"
        }

        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        var phoneNumber: String? = null
        var simOperator: String? = null

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                simOperator = telephonyManager.simOperatorName.ifBlank {
                    telephonyManager.networkOperatorName
                }

                val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                val hasPhoneNumbersPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_PHONE_NUMBERS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasPhoneStatePermission && hasPhoneNumbersPermission) {
                    // Try SubscriptionManager first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                        val activeList = subManager?.activeSubscriptionInfoList
                        if (!activeList.isNullOrEmpty()) {
                            val primarySub = activeList[0]
                            val subNumber = primarySub.number
                            if (!subNumber.isNullOrBlank()) {
                                phoneNumber = subNumber
                            }
                            val carrier = primarySub.carrierName?.toString()
                            if (!carrier.isNullOrBlank()) {
                                simOperator = carrier
                            }
                        }
                    }

                    if (phoneNumber.isNullOrBlank()) {
                        @Suppress("DEPRECATION")
                        val line1 = telephonyManager.line1Number
                        if (!line1.isNullOrBlank()) {
                            phoneNumber = line1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return DeviceHardwareInfo(
            brand = brandName,
            model = model,
            fullName = fullName,
            osVersion = osVersion,
            simPhoneNumber = phoneNumber?.trim()?.ifBlank { null },
            simOperator = simOperator?.trim()?.ifBlank { null }
        )
    }
}
