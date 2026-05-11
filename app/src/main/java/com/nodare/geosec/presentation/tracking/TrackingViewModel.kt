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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents an employee's real-time tracking data for the admin map view.
 * Includes bearing for directional arrow rotation (Waze/Grab-style).
 */
data class EmployeeTrackingInfo(
    val userId: String,
    val userName: String,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
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

    // Track per-session GPS observation jobs so we can cancel them when sessions change
    private val sessionGpsJobs = mutableMapOf<String, Job>()

    // Current active sessions and their latest GPS data, merged into the LiveData
    private val activeSessions = mutableMapOf<String, DispatchSession>()
    private val latestGpsPerSession = mutableMapOf<String, GpsLog>()

    /**
     * Observe all active dispatch sessions in real-time.
     * For each session, starts a real-time GPS listener so markers move live on the map.
     */
    fun observeActiveEmployees() {
        if (isObserving) return
        isObserving = true

        viewModelScope.launch {
            dispatchRepository.observeActiveSessions()
                .collectLatest { sessions ->
                    handleSessionsUpdate(sessions)
                }
        }
    }

    /**
     * When the list of active sessions changes:
     * - Start GPS listeners for new sessions
     * - Cancel GPS listeners for sessions that ended
     * - Rebuild the employee locations list
     */
    private fun handleSessionsUpdate(sessions: List<DispatchSession>) {
        _isLoading.value = true

        val currentSessionIds = sessions.map { it.id }.toSet()
        val previousSessionIds = activeSessions.keys.toSet()

        // Cancel GPS listeners for sessions that are no longer active
        val removedSessions = previousSessionIds - currentSessionIds
        for (sessionId in removedSessions) {
            sessionGpsJobs[sessionId]?.cancel()
            sessionGpsJobs.remove(sessionId)
            activeSessions.remove(sessionId)
            latestGpsPerSession.remove(sessionId)
        }

        // Update session data and start GPS listeners for new sessions
        for (session in sessions) {
            activeSessions[session.id] = session

            if (session.id !in sessionGpsJobs) {
                // Start real-time GPS observation for this session
                val job = viewModelScope.launch {
                    gpsLogRepository.observeLatestLogForSession(session.id)
                        .collectLatest { gpsLog ->
                            if (gpsLog != null) {
                                latestGpsPerSession[session.id] = gpsLog
                            }
                            rebuildEmployeeLocations()
                        }
                }
                sessionGpsJobs[session.id] = job
            }
        }

        // Rebuild immediately for sessions that may not have GPS data yet
        rebuildEmployeeLocations()
    }

    /**
     * Merge active sessions with their latest GPS data into the UI model.
     * Called whenever sessions change OR when any session's GPS data updates.
     */
    private fun rebuildEmployeeLocations() {
        val trackingInfoList = mutableListOf<EmployeeTrackingInfo>()

        for ((sessionId, session) in activeSessions) {
            val gpsLog = latestGpsPerSession[sessionId]

            when {
                gpsLog != null -> {
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = sessionId,
                            latitude = gpsLog.latitude,
                            longitude = gpsLog.longitude,
                            accuracy = gpsLog.accuracy,
                            speed = gpsLog.speed,
                            bearing = gpsLog.bearing,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = gpsLog.timestamp?.toDate()?.time ?: 0L
                        )
                    )
                }
                session.startLocation != null -> {
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = sessionId,
                            latitude = session.startLocation.latitude,
                            longitude = session.startLocation.longitude,
                            accuracy = 0f,
                            speed = 0f,
                            bearing = 0f,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = session.startTime?.toDate()?.time ?: 0L
                        )
                    )
                }
                else -> {
                    trackingInfoList.add(
                        EmployeeTrackingInfo(
                            userId = session.userId,
                            userName = session.userName,
                            sessionId = sessionId,
                            latitude = 0.0,
                            longitude = 0.0,
                            accuracy = 0f,
                            speed = 0f,
                            bearing = 0f,
                            isSuspicious = session.isSuspicious,
                            lastUpdateTime = session.startTime?.toDate()?.time ?: 0L
                        )
                    )
                }
            }
        }

        _employeeLocations.postValue(trackingInfoList)
        _isLoading.postValue(false)
    }

    /**
     * Force refresh — cancels all GPS listeners and re-observes from scratch.
     */
    fun refresh() {
        // Cancel all existing GPS observation jobs
        sessionGpsJobs.values.forEach { it.cancel() }
        sessionGpsJobs.clear()
        activeSessions.clear()
        latestGpsPerSession.clear()
        isObserving = false
        observeActiveEmployees()
    }

    override fun onCleared() {
        super.onCleared()
        sessionGpsJobs.values.forEach { it.cancel() }
        sessionGpsJobs.clear()
    }
}
