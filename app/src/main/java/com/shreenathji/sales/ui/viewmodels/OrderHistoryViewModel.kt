package com.shreenathji.sales.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.data.repository.SalesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class OrderHistoryViewModel(
    private val repository: SalesRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val currentStaff = authManager.getCurrentStaff()

    // Login snapshot can be stale; synced staff list carries the latest dashboard toggle.
    private val canViewAllOrders: Boolean =
        authManager.getAllStaff().find { it.id == currentStaff?.id }?.canViewAllOrders
            ?: (currentStaff?.canViewAllOrders == true)

    val orders: StateFlow<List<OrderWithItems>> = repository
        .getOrdersByRep(currentStaff?.id, canViewAllOrders)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshOrders()
    }

    fun refreshOrders() {
        repository.syncWithCloud()
    }

    fun logout() {
        authManager.logout()
    }
}

class OrderHistoryViewModelFactory(
    private val repository: SalesRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrderHistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrderHistoryViewModel(repository, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
