package com.shreenathji.sales.data.local.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class StaffMember(
    val id: String,
    val name: String,
    val role: String = "Field Sales Rep",
    val pinCode: String = "0000",
    val phone: String? = null,
    val orderPrefix: String? = null,
    /** Orders page shows every rep's orders (dashboard "All Orders (App)"). */
    val canViewAllOrders: Boolean = false
) {
    val accessCode: String get() = pinCode

    /** Dispatch logins only ever see the dispatch screens. */
    val isDispatch: Boolean get() = role.trim().equals("dispatch", ignoreCase = true)
    val prefix: String get() {
        if (!orderPrefix.isNullOrBlank()) return orderPrefix.trim().uppercase()
        val words = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (words.size >= 2) {
            "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        } else {
            (name.take(3).ifBlank { "ST" }).uppercase()
        }
    }
}

class AuthManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shreenathji_auth_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        val DEFAULT_STAFF: List<StaffMember> = emptyList()

        val STAFF_MEMBERS: List<StaffMember> = emptyList()

        private const val KEY_LOGGED_IN_ID = "logged_in_staff_id"
        private const val KEY_CACHED_STAFF_JSON = "cached_staff_json"
        private const val KEY_ACTIVE_LICENSE_KEY = "active_license_key"
        private const val KEY_ACTIVE_STAFF_JSON = "active_staff_json"
        private const val KEY_AUTH_TOKEN = "cached_auth_jwt_token"
        private const val KEY_AUTH_TOKEN_EXPIRY = "cached_auth_jwt_expiry"
    }

    fun getAllStaff(): List<StaffMember> {
        val json = prefs.getString(KEY_CACHED_STAFF_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<StaffMember>>() {}.type
                val cached: List<StaffMember>? = gson.fromJson(json, type)
                if (cached != null) return cached
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    fun updateStaffList(newList: List<StaffMember>) {
        val json = gson.toJson(newList)
        prefs.edit().putString(KEY_CACHED_STAFF_JSON, json).apply()
        // Do not clear the active license if a roster id is missing — license auth is
        // independent of the cached staff list, and wiping mid-sync leaves an empty DB.
    }

    private val defaultStaff = StaffMember(
        id = "sales_rep_1",
        name = "Field Sales Rep",
        role = "Field Sales Rep",
        pinCode = "0000",
        phone = "9999999999",
        orderPrefix = "SR"
    )

    fun getCurrentStaff(): StaffMember? {
        val json = prefs.getString(KEY_ACTIVE_STAFF_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val staff = gson.fromJson(json, StaffMember::class.java)
                if (staff != null) return staff
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val id = prefs.getString(KEY_LOGGED_IN_ID, null)
        if (id != null) {
            val found = getAllStaff().find { it.id == id }
            if (found != null) return found
        }
        return getAllStaff().firstOrNull() ?: defaultStaff
    }

    fun saveActiveLicense(licenseKey: String, staff: StaffMember) {
        prefs.edit()
            .putString(KEY_ACTIVE_LICENSE_KEY, licenseKey.trim().uppercase())
            .putString(KEY_LOGGED_IN_ID, staff.id)
            .putString(KEY_ACTIVE_STAFF_JSON, gson.toJson(staff))
            .apply()

        val current = getAllStaff().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == staff.id }
        if (existingIndex >= 0) {
            current[existingIndex] = staff
        } else {
            current.add(staff)
        }
        updateStaffList(current)
    }

    fun getActiveLicenseKey(): String? = prefs.getString(KEY_ACTIVE_LICENSE_KEY, null)

    fun isLicensed(): Boolean = !getActiveLicenseKey().isNullOrBlank()

    fun isLoggedIn(): Boolean = isLicensed()

    fun logout() {
        clearLicense()
    }

    fun clearLicense() {
        prefs.edit()
            .remove(KEY_ACTIVE_LICENSE_KEY)
            .remove(KEY_LOGGED_IN_ID)
            .remove(KEY_ACTIVE_STAFF_JSON)
            .remove(KEY_CACHED_STAFF_JSON)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_AUTH_TOKEN_EXPIRY)
            .apply()
    }

    fun saveAuthToken(token: String, expiresInSeconds: Long) {
        val expiryMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putLong(KEY_AUTH_TOKEN_EXPIRY, expiryMillis)
            .apply()
    }

    fun getAuthToken(): String? {
        val token = prefs.getString(KEY_AUTH_TOKEN, null) ?: return null
        val expiry = prefs.getLong(KEY_AUTH_TOKEN_EXPIRY, 0L)
        // Ensure at least 60 seconds of validity remaining
        return if (System.currentTimeMillis() < (expiry - 60_000L)) token else null
    }

    fun clearAuthToken() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_AUTH_TOKEN_EXPIRY)
            .apply()
    }

    fun getDeviceId(): String {
        return com.shreenathji.sales.util.DeviceHardwareHelper.getPersistentHardwareId(appContext)
    }

    fun getDeviceName(): String {
        return com.shreenathji.sales.util.DeviceHardwareHelper.getHardwareInfo(appContext).fullName
    }

    fun getDeviceModel(): String {
        return com.shreenathji.sales.util.DeviceHardwareHelper.getHardwareInfo(appContext).model
    }
}
