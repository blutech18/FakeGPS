package com.nodare.geosec.presentation.checkin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.CheckInLog
import com.nodare.geosec.data.repository.CheckInRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val checkInRepository: CheckInRepository
) : ViewModel() {

    private val _checkInState = MutableLiveData<Resource<String>>()
    val checkInState: LiveData<Resource<String>> = _checkInState

    private val _checkOutState = MutableLiveData<Resource<Unit>>()
    val checkOutState: LiveData<Resource<Unit>> = _checkOutState

    private val _isCheckedIn = MutableLiveData<Boolean>()
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _allLogs = MutableLiveData<List<CheckInLog>>()
    private val _checkInLogs = MutableLiveData<List<CheckInLog>>()
    val checkInLogs: LiveData<List<CheckInLog>> = _checkInLogs

    private var currentRoleFilter: String? = null

    fun checkIn(
        userId: String,
        userName: String,
        role: String,
        deviceId: String,
        latitude: Double,
        longitude: Double
    ) {
        _checkInState.value = Resource.Loading
        viewModelScope.launch {
            _checkInState.value = checkInRepository.checkIn(
                userId, userName, role, deviceId, latitude, longitude
            )
        }
    }

    fun checkOut(userId: String) {
        _checkOutState.value = Resource.Loading
        viewModelScope.launch {
            _checkOutState.value = checkInRepository.checkOut(userId)
        }
    }

    fun checkActiveSession(userId: String) {
        viewModelScope.launch {
            _isCheckedIn.value = checkInRepository.hasActiveCheckIn(userId)
        }
    }

    fun observeAllLogs() {
        viewModelScope.launch {
            checkInRepository.observeCheckInLogs()
                .catch { _allLogs.value = emptyList() }
                .collect { logs ->
                    _allLogs.value = logs
                    applyRoleFilter()
                }
        }
    }

    fun filterByRole(role: String?) {
        currentRoleFilter = role
        applyRoleFilter()
    }

    private fun applyRoleFilter() {
        val all = _allLogs.value ?: emptyList()
        _checkInLogs.value = if (currentRoleFilter == null) {
            all
        } else {
            all.filter { it.role == currentRoleFilter }
        }
    }
}
