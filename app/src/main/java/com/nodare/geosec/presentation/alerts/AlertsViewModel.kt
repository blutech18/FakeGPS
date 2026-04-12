package com.nodare.geosec.presentation.alerts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.SecurityAlert
import com.nodare.geosec.data.repository.SecurityAlertRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertFilter {
    ALL, UNRESOLVED, RESOLVED
}

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: SecurityAlertRepository
) : ViewModel() {

    private val _alerts = MutableLiveData<List<SecurityAlert>>()
    val alerts: LiveData<List<SecurityAlert>> = _alerts

    private val _resolveState = MutableLiveData<Resource<Unit>>()
    val resolveState: LiveData<Resource<Unit>> = _resolveState

    private var allAlerts: List<SecurityAlert> = emptyList()
    private var currentFilter: AlertFilter = AlertFilter.ALL

    init {
        observeAlerts()
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            alertRepository.observeAlerts()
                .catch { _alerts.value = emptyList() }
                .collect { alerts ->
                    // Sort: unresolved first (by timestamp desc), then resolved (by timestamp desc)
                    allAlerts = alerts.sortedWith(
                        compareBy<SecurityAlert> { it.isResolved }
                            .thenByDescending { it.timestamp }
                    )
                    applyFilter()
                }
        }
    }

    fun setFilter(filter: AlertFilter) {
        currentFilter = filter
        applyFilter()
    }

    private fun applyFilter() {
        _alerts.value = when (currentFilter) {
            AlertFilter.ALL -> allAlerts
            AlertFilter.UNRESOLVED -> allAlerts.filter { !it.isResolved }
            AlertFilter.RESOLVED -> allAlerts.filter { it.isResolved }
        }
    }

    fun resolveAlert(alertId: String) {
        _resolveState.value = Resource.Loading
        viewModelScope.launch {
            _resolveState.value = alertRepository.resolveAlert(alertId)
        }
    }
}
