package com.nodare.geosec.presentation.tracking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.DispatchSession
import com.nodare.geosec.data.model.GpsLog
import com.nodare.geosec.data.repository.DispatchRepository
import com.nodare.geosec.data.repository.GpsLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents an employee's real-time tracking data for the admin map view.
 */
data class EmployeeTrackingInfo(
    val userId: String,
    val userName: String,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val isSuspicious: Boolean,
    val lastUpdateTime: Long
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val dispatchRepository: DispatchRepository,
    private val gpsLogRepository: GpsLogRepository
) : ViewModel() {

    private val _employeeLocations = MutableLiveData<List<EmployeeTrackingInfo>>()
    val employeeLocations: LiveData<List<EmployeeTrackingInfo>> = _employeeLocations

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private var isObserving = false

    /**
     * Observe all active dispatch sessions, then fetch latest GPS log for each.
     */
    fun observeActiveEmployees() {
        if (isObserving) return
        isObserving = true

        viewModelScope.launch {
            dispatchRepository.observeActiveSessions()
                .collect { sessions ->
                    loadLatestLocations(sessions)
                }
        }
    }

    /**
     * For each active session, get the latest GPS log to determine employee's current location.
     * Always includes the employee even if no location data is available yet.
     */
    private suspend fun loadLatestLocations(sessions: List<DispatchSession>) {
        _isLoading.value = true
        val trackingInfoList = mutableListOf<EmployeeTrackingInfo>()

        for (session in sessions) {
            val latestLog = try {
                gpsLogRepository.getLatestLogForSession(session.id)
            } catch (e: Exception) {
                null
            }

            when {
                latestLog != null -> {
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = session.id,
                            latitude = latestLog.latitude,
                            longitude = latestLog.longitude,
                            accuracy = latestLog.accuracy,
                            speed = latestLog.speed,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = latestLog.timestamp?.toDate()?.time ?: 0L
                        )
                    )
                }
                session.startLocation != null -> {
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = session.id,
                            latitude = session.startLocation.latitude,
                            longitude = session.startLocation.longitude,
                            accuracy = 0f,
                            speed = 0f,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = session.startTime?.toDate()?.time ?: 0L
                        )
                    )
                }
                else -> {
                    // No location data yet — still include employee with 0,0 so they appear in the list
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = session.id,
                            latitude = 0.0,
                            longitude = 0.0,
                            accuracy = 0f,
                            speed = 0f,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = session.startTime?.toDate()?.time ?: 0L
                        )
                    )
                }
            }
        }

        _employeeLocations.value = trackingInfoList
        _isLoading.value = false
    }

    /**
     * Force refresh all employee locations.
     */
    fun refresh() {
        isObserving = false
        observeActiveEmployees()
    }
}
