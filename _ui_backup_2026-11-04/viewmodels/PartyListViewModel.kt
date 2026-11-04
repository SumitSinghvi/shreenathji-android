package com.shreenathji.sales.ui.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.data.local.entities.PartyEntity
import com.shreenathji.sales.data.repository.SalesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val SEARCH_DEBOUNCE_MS = 250L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PartyListViewModel(
    private val repository: SalesRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val currentStaff: StaffMember? = authManager.getCurrentStaff()

    val isStaffAdmin: Boolean = currentStaff?.role?.contains("admin", ignoreCase = true) == true ||
            currentStaff?.id == "staff_admin"

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _storeFilterMode = MutableStateFlow(if (isStaffAdmin) "ALL" else "ALLOTTED")
    val storeFilterMode: StateFlow<String> = _storeFilterMode.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Today's route, 1: My parties
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private fun getTodayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val isViewingToday: StateFlow<Boolean> = _selectedDate
        .flatMapLatest { date ->
            kotlinx.coroutines.flow.flowOf(date == getTodayDateString())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    val currentBeatPlans: StateFlow<List<DailyVisitPlanEntity>> = _selectedDate
        .flatMapLatest { date ->
            repository.getVisitPlansForDate(date, currentStaff?.name)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val todayVisitPlans: StateFlow<List<DailyVisitPlanEntity>> = repository
        .getTodayVisitPlans(currentStaff?.name)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val parties: StateFlow<List<PartyEntity>> = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.searchParties(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshSync()
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setStoreFilterMode(mode: String) {
        _storeFilterMode.value = mode
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        refreshSync()
    }

    fun selectDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun selectToday() {
        _selectedDate.value = getTodayDateString()
    }

    fun selectPreviousDay() {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val current = sdf.parse(_selectedDate.value) ?: Date()
            val cal = java.util.Calendar.getInstance().apply {
                time = current
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            _selectedDate.value = sdf.format(cal.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectNextDay() {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val current = sdf.parse(_selectedDate.value) ?: Date()
            val cal = java.util.Calendar.getInstance().apply {
                time = current
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            _selectedDate.value = sdf.format(cal.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun completeVisit(
        context: Context,
        planId: String?,
        party: PartyEntity?,
        remarks: String?,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (!_selectedDate.value.equals(getTodayDateString())) {
            // Guard: Cannot modify past or future date visits
            return
        }

        viewModelScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                val loc = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                if (loc != null) {
                    repository.logLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        speed = loc.speed
                    )
                    if (party != null && (party.latitude == null || party.longitude == null)) {
                        val updatedParty = party.copy(latitude = loc.latitude, longitude = loc.longitude)
                        repository.addParty(updatedParty)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (planId != null) {
                repository.completeVisit(planId, remarks)
                onDone(true)
            } else if (party != null) {
                val plan = repository.recordStoreVisitWithRemarks(
                    party = party,
                    remarks = remarks,
                    salesRepId = currentStaff?.id,
                    salesRepName = currentStaff?.name
                )
                onDone(plan != null)
            } else {
                onDone(false)
            }
        }
    }

    fun markVisitUnvisited(context: Context, planId: String, onDone: () -> Unit = {}) {
        if (!_selectedDate.value.equals(getTodayDateString())) {
            // Guard: Cannot modify past or future date visits
            return
        }

        viewModelScope.launch {
            repository.markVisitUnvisited(planId)
            onDone()
        }
    }

    fun sendStoreChatMessage(planId: String, message: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.sendStoreChatMessage(planId, message)
            onDone()
        }
    }

    fun sendStoreVoiceMessage(
        planId: String,
        audioFile: java.io.File,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repository.sendStoreVoiceMessage(planId, audioFile)
            onResult(ok)
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleVisitStatus(context: Context, plan: DailyVisitPlanEntity) {
        if (plan.status == "VISITED") {
            markVisitUnvisited(context, plan.id)
        } else {
            completeVisit(context, plan.id, null, null)
        }
    }

    @SuppressLint("MissingPermission")
    fun markPartyVisited(context: Context, party: PartyEntity, onDone: (Boolean) -> Unit = {}) {
        completeVisit(context, null, party, null, onDone)
    }

    fun refreshSync() {
        repository.syncWithCloud()
    }

    private var isCreatingParty = false

    @SuppressLint("MissingPermission")
    fun createParty(
        context: Context,
        businessName: String,
        contactPerson: String?,
        phone: String?,
        addressLine1: String?,
        addressLine2: String?,
        defaultAgent: String?,
        onSuccess: () -> Unit
    ) {
        if (isCreatingParty) return
        isCreatingParty = true
        viewModelScope.launch {
            try {
                var lat: Double? = null
                var lng: Double? = null
                try {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val cts = CancellationTokenSource()
                    val loc = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                    lat = loc?.latitude
                    lng = loc?.longitude
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Sales reps who add a store are auto-assigned as its allotted rep
                val creatorRepId = currentStaff?.id?.takeIf { it.isNotBlank() && !isStaffAdmin }
                val assignedRepIdsJson = creatorRepId?.let {
                    Gson().toJson(listOf(it))
                }

                val newParty = PartyEntity(
                    id = UUID.randomUUID().toString(),
                    businessName = businessName.trim(),
                    contactPerson = contactPerson?.trim()?.ifBlank { null },
                    phone = phone?.trim()?.ifBlank { null },
                    addressLine1 = addressLine1?.trim()?.ifBlank { null },
                    addressLine2 = addressLine2?.trim()?.ifBlank { null },
                    defaultAgent = defaultAgent?.trim()?.ifBlank { null },
                    latitude = lat,
                    longitude = lng,
                    assignedRepIdsJson = assignedRepIdsJson,
                    isSynced = false // Locally created — will be marked synced after successful push
                )

                repository.addParty(newParty)
                onSuccess()

                // Brief delay to let the async push complete, then check sync status
                kotlinx.coroutines.delay(2000)
                val updatedParty = repository.getPartyById(newParty.id)
                if (updatedParty != null && !updatedParty.isSynced) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Store saved on this phone. It will upload when you are online again (open the app or pull to refresh).",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } finally {
                isCreatingParty = false
            }
        }
    }
}

class PartyListViewModelFactory(
    private val repository: SalesRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PartyListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PartyListViewModel(repository, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
