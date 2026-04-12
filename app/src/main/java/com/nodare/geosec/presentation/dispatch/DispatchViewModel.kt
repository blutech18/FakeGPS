package com.nodare.geosec.presentation.dispatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.DispatchSession
import com.nodare.geosec.data.repository.CheckInRepository
import com.nodare.geosec.data.repository.DispatchRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the result of starting a dispatch — both the session ID and the dispatch key.
 */
data class DispatchStartResult(
    val sessionId: String,
    val dispatchKey: String
)

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val dispatchRepository: DispatchRepository,
    private val checkInRepository: CheckInRepository
) : ViewModel() {

    private val _startState = MutableLiveData<Resource<DispatchStartResult>>()
    val startState: LiveData<Resource<DispatchStartResult>> = _startState

    private val _endState = MutableLiveData<Resource<Unit>>()
    val endState: LiveData<Resource<Unit>> = _endState

    private val _activeSession = MutableLiveData<DispatchSession?>()
    val activeSession: LiveData<DispatchSession?> = _activeSession

    private val _allSessions = MutableLiveData<List<DispatchSession>>()
    val allSessions: LiveData<List<DispatchSession>> = _allSessions

    private val _userSessions = MutableLiveData<List<DispatchSession>>()
    val userSessions: LiveData<List<DispatchSession>> = _userSessions

    private val _isCheckedIn = MutableLiveData<Boolean>()
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private var isObservingUserHistory = false
    private var isObservingAllSessions = false

    fun startDispatch(userId: String, userName: String, role: String, lat: Double, lng: Double) {
        _startState.value = Resource.Loading
        viewModelScope.launch {
            val hasCheckIn = checkInRepository.hasActiveCheckIn(userId)
            if (!hasCheckIn) {
                _startState.value = Resource.Error("You must check in before starting a dispatch")
                return@launch
            }
            val result = dispatchRepository.startSession(userId, userName, role, lat, lng)
            // Parse the result: "sessionId|dispatchKey"
            _startState.value = when (result) {
                is Resource.Success -> {
                    val parts = result.data.split("|")
                    if (parts.size == 2) {
                        Resource.Success(DispatchStartResult(parts[0], parts[1]))
                    } else {
                        Resource.Success(DispatchStartResult(result.data, ""))
                    }
                }
                is Resource.Error -> Resource.Error(result.message, result.exception)
                is Resource.Loading -> Resource.Loading
            }
        }
    }

    /**
     * End dispatch with validation of the dispatch key.
     * The user must provide the correct confirmation code to end the dispatch.
     */
    fun endDispatch(sessionId: String, lat: Double, lng: Double, enteredKey: String) {
        _endState.value = Resource.Loading
        viewModelScope.launch {
            _endState.value = dispatchRepository.endSession(sessionId, lat, lng, enteredKey)
        }
    }

    fun loadActiveSession(userId: String) {
        viewModelScope.launch {
            _activeSession.value = dispatchRepository.getActiveSession(userId)
            _isCheckedIn.value = checkInRepository.hasActiveCheckIn(userId)
        }
    }

    fun observeUserHistory(userId: String) {
        if (isObservingUserHistory) return
        isObservingUserHistory = true
        viewModelScope.launch {
            dispatchRepository.observeUserSessions(userId)
                .collect { _userSessions.value = it }
        }
    }

    fun observeAllSessions() {
        if (isObservingAllSessions) return
        isObservingAllSessions = true
        viewModelScope.launch {
            dispatchRepository.observeAllSessions()
                .collect { _allSessions.value = it }
        }
    }

    fun fetchUserHistory(userId: String) {
        viewModelScope.launch {
            val sessions = dispatchRepository.fetchUserSessions(userId)
            if (sessions.isNotEmpty()) {
                _userSessions.value = sessions
            }
        }
    }

    fun resetStartState() {
        _startState.value = null
    }

    fun resetEndState() {
        _endState.value = null
    }
}
