package com.nodare.geosec.presentation.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.CheckInLog
import com.nodare.geosec.data.model.SecurityAlert
import com.nodare.geosec.data.repository.CheckInRepository
import com.nodare.geosec.data.repository.DispatchRepository
import com.nodare.geosec.data.repository.EmployeeStatus
import com.nodare.geosec.data.repository.SecurityAlertRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentActivity(
    val title: String,
    val time: String,
    val type: ActivityType
)

enum class ActivityType {
    ALERT, CHECK_IN, DISPATCH
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val checkInRepository: CheckInRepository,
    private val dispatchRepository: DispatchRepository,
    private val alertRepository: SecurityAlertRepository
) : ViewModel() {

    private val _isCheckedIn = MutableLiveData<Boolean>()
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _hasActiveDispatch = MutableLiveData<Boolean>()
    val hasActiveDispatch: LiveData<Boolean> = _hasActiveDispatch

    private val _dispatchSessionInfo = MutableLiveData<String>()
    val dispatchSessionInfo: LiveData<String> = _dispatchSessionInfo

    private val _checkInState = MutableLiveData<Resource<String>>()
    val checkInState: LiveData<Resource<String>> = _checkInState

    private val _checkOutState = MutableLiveData<Resource<Unit>>()
    val checkOutState: LiveData<Resource<Unit>> = _checkOutState

    // Admin stats - initialized with 0 so LiveData always has a value to emit
    private val _activeDispatchCount = MutableLiveData(0)
    val activeDispatchCount: LiveData<Int> = _activeDispatchCount

    private val _unresolvedAlertCount = MutableLiveData(0)
    val unresolvedAlertCount: LiveData<Int> = _unresolvedAlertCount

    private val _checkedInCount = MutableLiveData(0)
    val checkedInCount: LiveData<Int> = _checkedInCount

    private val _recentActivities = MutableLiveData<List<RecentActivity>>(emptyList())
    val recentActivities: LiveData<List<RecentActivity>> = _recentActivities

    // Employee statuses for Admin/CEO - shows online/dispatching status
    private val _employeeStatuses = MutableLiveData<List<EmployeeStatus>>(emptyList())
    val employeeStatuses: LiveData<List<EmployeeStatus>> = _employeeStatuses

    private var isObservingAdminStats = false
    private var isObservingEmployeeStatuses = false

    fun loadAdminStats() {
        // Only start observing once to prevent multiple Flow collectors
        if (isObservingAdminStats) return
        isObservingAdminStats = true

        viewModelScope.launch {
            dispatchRepository.observeActiveSessions()
                .catch { _activeDispatchCount.value = 0 }
                .collect { _activeDispatchCount.value = it.size }
        }
        viewModelScope.launch {
            alertRepository.observeUnresolvedAlerts()
                .catch { _unresolvedAlertCount.value = 0 }
                .collect { _unresolvedAlertCount.value = it.size }
        }
        viewModelScope.launch {
            checkInRepository.observeActiveCheckIns()
                .catch { _checkedInCount.value = 0 }
                .collect { _checkedInCount.value = it.size }
        }
        
        // Load recent activities
        loadRecentActivities()
    }

    /**
     * Load real-time employee statuses for Admin/CEO to see who is
     * online (checked in) or currently dispatching.
     */
    fun loadEmployeeStatuses() {
        if (isObservingEmployeeStatuses) return
        isObservingEmployeeStatuses = true

        viewModelScope.launch {
            dispatchRepository.observeEmployeeStatuses()
                .catch { _employeeStatuses.value = emptyList() }
                .collect { _employeeStatuses.value = it }
        }
    }
    
    private fun loadRecentActivities() {
        viewModelScope.launch {
            try {
                val activities = mutableListOf<RecentActivity>()
                
                // Get recent alerts
                alertRepository.observeAlerts()
                    .catch { /* ignore */ }
                    .collect { alerts ->
                        val recentAlerts = alerts.take(2).map { alert ->
                            RecentActivity(
                                title = if (alert.isResolved) "Alert resolved: ${alert.alertType.replace("_", " ")}" 
                                       else "New alert: ${alert.alertType.replace("_", " ")}",
                                time = formatTimeAgo(alert.timestamp?.toDate()?.time ?: 0),
                                type = ActivityType.ALERT
                            )
                        }
                        
                        // Combine with check-in activities
                        checkInRepository.observeActiveCheckIns()
                            .catch { /* ignore */ }
                            .collect { checkIns ->
                                val recentCheckIns = checkIns.take(1).map { checkIn ->
                                    RecentActivity(
                                        title = "${checkIn.userName} checked in",
                                        time = formatTimeAgo(checkIn.checkInTime?.toDate()?.time ?: 0),
                                        type = ActivityType.CHECK_IN
                                    )
                                }
                                
                                // Combine and sort by recency (we'll just interleave for now)
                                val combined = mutableListOf<RecentActivity>()
                                if (recentAlerts.isNotEmpty()) combined.add(recentAlerts[0])
                                if (recentCheckIns.isNotEmpty()) combined.add(recentCheckIns[0])
                                if (recentAlerts.size > 1) combined.add(recentAlerts[1])
                                
                                _recentActivities.value = combined.take(3)
                            }
                    }
            } catch (e: Exception) {
                _recentActivities.value = emptyList()
            }
        }
    }
    
    private fun formatTimeAgo(timestamp: Long): String {
        if (timestamp == 0L) return "Just now"
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            else -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
        }
    }

    fun loadDriverStatus(userId: String) {
        viewModelScope.launch {
            _isCheckedIn.value = checkInRepository.hasActiveCheckIn(userId)

            val session = dispatchRepository.getActiveSession(userId)
            _hasActiveDispatch.value = session != null
            _dispatchSessionInfo.value = if (session != null) {
                val displayRef = if (session.dispatchKey.isNotBlank()) {
                    session.dispatchKey.take(8).uppercase()
                } else {
                    session.id.take(6).uppercase()
                }
                val timeStr = session.startTime?.toDate()?.let { date ->
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(date)
                } ?: ""
                val statusLabel = when (session.status) {
                    "active" -> "In Progress"
                    "completed" -> "Completed"
                    "suspicious" -> "Flagged"
                    else -> session.status.replaceFirstChar { it.uppercase() }
                }
                "Dispatch #$displayRef · $statusLabel${if (timeStr.isNotEmpty()) "\nStarted at $timeStr" else ""}"
            } else {
                "No active dispatch"
            }
        }
    }

    fun checkIn(userId: String, userName: String, role: String, deviceId: String, latitude: Double, longitude: Double) {
        _checkInState.value = Resource.Loading
        viewModelScope.launch {
            val result = checkInRepository.checkIn(userId, userName, role, deviceId, latitude, longitude)
            _checkInState.value = result
            if (result is Resource.Success) {
                _isCheckedIn.value = true
            }
        }
    }

    fun checkOut(userId: String) {
        _checkOutState.value = Resource.Loading
        viewModelScope.launch {
            val result = checkInRepository.checkOut(userId)
            _checkOutState.value = result
            if (result is Resource.Success) {
                _isCheckedIn.value = false
            }
        }
    }
}
