package com.shreenathji.sales.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.remote.dto.DispatchOrderDto
import com.shreenathji.sales.data.remote.dto.DispatchRecordDto
import com.shreenathji.sales.ui.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DispatchItemInput(
    val orderItemId: String,
    val actualMeterText: String,
    val notes: String = ""
)

data class DispatchFormInput(
    val orderId: String,
    val items: List<DispatchItemInput>,
    val lrNumber: String = "",
    val transporter: String = "",
    val vehicleNo: String = "",
    val notes: String = ""
)

/** Pure form rules, kept free of Android types so they are unit-testable. */
object DispatchForm {
    fun parseMeter(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    /** Returns an error message, or null when the form can be submitted. */
    fun validate(input: DispatchFormInput): String? {
        if (input.items.isEmpty()) return "This order has no items to dispatch"
        if (input.items.any { parseMeter(it.actualMeterText) == null }) {
            return "Enter actual meters (0 or more) for every item"
        }
        return null
    }

    fun payload(
        input: DispatchFormInput,
        staffId: String,
        staffName: String,
        photoUrls: List<String>
    ): Map<String, Any?> = mapOf(
        "p_order_id" to input.orderId,
        "p_staff_id" to staffId,
        "p_staff_name" to staffName,
        "p_lr_number" to input.lrNumber.trim().ifBlank { null },
        "p_transporter" to input.transporter.trim().ifBlank { null },
        "p_vehicle_no" to input.vehicleNo.trim().ifBlank { null },
        "p_notes" to input.notes.trim().ifBlank { null },
        "p_photo_urls" to photoUrls,
        "p_items" to input.items.map {
            mapOf(
                "order_item_id" to it.orderItemId,
                "actual_meter" to parseMeter(it.actualMeterText),
                "notes" to it.notes.trim().ifBlank { null }
            )
        }
    )
}

enum class DispatchDateFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("7 Days")
}

object DispatchDateUtils {
    val KOLKATA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun kolkataDayKey(isoTimestamp: String?): String? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            val normalized = isoTimestamp.trim().replace(" ", "T")
            val clean = normalized.substringBefore(".").substringBefore("+").substringBefore("Z")
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = if (normalized.endsWith("Z") || normalized.contains("+")) TimeZone.getTimeZone("UTC") else KOLKATA_TZ
            }
            val date = parser.parse(clean) ?: return isoTimestamp.take(10)
            val out = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = KOLKATA_TZ }
            out.format(date)
        } catch (_: Exception) {
            isoTimestamp.take(10)
        }
    }

    fun currentCalendarKeys(): Triple<String, String, String> {
        val cal = Calendar.getInstance(KOLKATA_TZ)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = KOLKATA_TZ }
        val today = sdf.format(cal.time)
        cal.add(Calendar.DATE, -1)
        val yesterday = sdf.format(cal.time)
        cal.add(Calendar.DATE, -5) // "7 Days" = today + previous 6 days
        val weekAgo = sdf.format(cal.time)
        return Triple(today, yesterday, weekAgo)
    }

    fun formatOrderTimestamp(isoTimestamp: String?): String? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            val normalized = isoTimestamp.trim().replace(" ", "T")
            val clean = normalized.substringBefore(".").substringBefore("+").substringBefore("Z")
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = if (normalized.endsWith("Z") || normalized.contains("+")) TimeZone.getTimeZone("UTC") else KOLKATA_TZ
            }
            val date = parser.parse(clean) ?: return isoTimestamp.take(16).replace('T', ' ')
            val out = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).apply { timeZone = KOLKATA_TZ }
            out.format(date).lowercase()
        } catch (_: Exception) {
            isoTimestamp.take(16).replace('T', ' ')
        }
    }
}

object DispatchFilters {
    fun filterOrders(
        orders: List<DispatchOrderDto>,
        query: String,
        dateFilter: DispatchDateFilter,
        partyFilter: String?,
        todayKey: String,
        yesterdayKey: String,
        weekAgoKey: String
    ): List<DispatchOrderDto> {
        val q = query.trim().lowercase()
        return orders.filter { order ->
            val matchesQ = q.isBlank() ||
                order.order_number.lowercase().contains(q) ||
                order.party_name.lowercase().contains(q) ||
                (order.party_address?.lowercase()?.contains(q) ?: false)
            if (!matchesQ) return@filter false

            if (!partyFilter.isNullOrBlank() && !order.party_name.equals(partyFilter, ignoreCase = true)) {
                return@filter false
            }

            val dayKey = DispatchDateUtils.kolkataDayKey(order.order_timestamp)
            when (dateFilter) {
                DispatchDateFilter.ALL -> true
                DispatchDateFilter.TODAY -> dayKey == todayKey
                DispatchDateFilter.YESTERDAY -> dayKey == yesterdayKey
                DispatchDateFilter.THIS_WEEK -> dayKey != null && dayKey >= weekAgoKey
            }
        }
    }

    fun filterDispatches(
        dispatches: List<DispatchRecordDto>,
        query: String,
        dateFilter: DispatchDateFilter,
        partyFilter: String?,
        todayKey: String,
        yesterdayKey: String,
        weekAgoKey: String
    ): List<DispatchRecordDto> {
        val q = query.trim().lowercase()
        return dispatches.filter { record ->
            val matchesQ = q.isBlank() ||
                (record.orders?.order_number?.lowercase()?.contains(q) ?: false) ||
                (record.orders?.party_name?.lowercase()?.contains(q) ?: false) ||
                (record.lr_number?.lowercase()?.contains(q) ?: false) ||
                (record.transporter?.lowercase()?.contains(q) ?: false)
            if (!matchesQ) return@filter false

            if (!partyFilter.isNullOrBlank() && !record.orders?.party_name.equals(partyFilter, ignoreCase = true)) {
                return@filter false
            }

            val dayKey = DispatchDateUtils.kolkataDayKey(record.dispatched_at)
            when (dateFilter) {
                DispatchDateFilter.ALL -> true
                DispatchDateFilter.TODAY -> dayKey == todayKey
                DispatchDateFilter.YESTERDAY -> dayKey == yesterdayKey
                DispatchDateFilter.THIS_WEEK -> dayKey != null && dayKey >= weekAgoKey
            }
        }
    }
}

data class DispatchUiState(
    val toDispatch: List<DispatchOrderDto> = emptyList(),
    val dispatched: List<DispatchRecordDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val loadError: String? = null
)

class DispatchViewModel(
    private val supabaseClient: SupabaseClient,
    private val authManager: AuthManager,
    private val appContext: Context
) : ViewModel() {

    val staff = authManager.getCurrentStaff()

    private val _state = MutableStateFlow(DispatchUiState())
    val state: StateFlow<DispatchUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val staffId = staff?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val pending = supabaseClient.getToDispatch()
            val mine = supabaseClient.getMyDispatches(staffId)
            _state.update {
                it.copy(
                    isLoading = false,
                    toDispatch = pending ?: it.toDispatch,
                    dispatched = mine ?: it.dispatched,
                    loadError = if (pending == null || mine == null) "Could not load. Pull to retry." else null
                )
            }
        }
    }

    fun findOrder(orderId: String): DispatchOrderDto? =
        _state.value.toDispatch.firstOrNull { it.id == orderId }

    /** Uploads optional photos, then saves. [onResult] gets null on success or an error message. */
    fun submit(order: DispatchOrderDto, input: DispatchFormInput, photos: List<Uri>, onResult: (String?) -> Unit) {
        val me = staff
        if (me == null) {
            onResult("Not signed in")
            return
        }
        DispatchForm.validate(input)?.let { onResult(it); return }
        if (_state.value.isSubmitting) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val urls = mutableListOf<String>()
            for (uri in photos) {
                val bytes = withContext(Dispatchers.IO) {
                    val compressed = ImageUtils.compressImage(appContext, uri)
                    appContext.contentResolver.openInputStream(compressed)?.use { it.readBytes() }
                }
                val url = bytes?.let { supabaseClient.uploadOrderImage(it, order.order_number) }
                if (url == null) {
                    _state.update { it.copy(isSubmitting = false) }
                    onResult("Photo upload failed. Remove the photo or try again.")
                    return@launch
                }
                urls += url
            }
            val error = supabaseClient.saveDispatch(DispatchForm.payload(input, me.id, me.name, urls))
            _state.update { it.copy(isSubmitting = false) }
            if (error == null) refresh()
            onResult(error)
        }
    }
}

class DispatchViewModelFactory(
    private val supabaseClient: SupabaseClient,
    private val authManager: AuthManager,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DispatchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DispatchViewModel(supabaseClient, authManager, appContext.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
