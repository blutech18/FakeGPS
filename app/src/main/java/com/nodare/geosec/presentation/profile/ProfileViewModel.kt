package com.nodare.geosec.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.repository.AuthRepository
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _updateState = MutableLiveData<Resource<Unit>>()
    val updateState: LiveData<Resource<Unit>> = _updateState

    fun updateProfile(userId: String, displayName: String, email: String) {
        _updateState.value = Resource.Loading
        viewModelScope.launch {
            _updateState.value = authRepository.updateUserProfile(userId, displayName, email)
        }
    }
}
