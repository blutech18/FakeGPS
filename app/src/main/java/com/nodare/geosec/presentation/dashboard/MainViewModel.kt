package com.nodare.geosec.presentation.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodare.geosec.data.model.User
import com.nodare.geosec.data.repository.AuthRepository
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUser = MutableLiveData<Resource<User>>()
    val currentUser: LiveData<Resource<User>> = _currentUser

    private var cachedUser: User? = null

    val isAdmin: Boolean
        get() {
            val role = cachedUser?.role
            return role == Constants.ROLE_CEO || role == Constants.ROLE_ADMIN
        }

    val isCeo: Boolean
        get() = cachedUser?.role == Constants.ROLE_CEO

    val userRole: String
        get() = cachedUser?.role ?: ""

    val userId: String
        get() = authRepository.currentUserId ?: ""

    val userName: String
        get() = cachedUser?.displayName ?: ""

    val isUserLoaded: Boolean
        get() = cachedUser != null

    fun loadCurrentUser() {
        _currentUser.value = Resource.Loading
        viewModelScope.launch {
            val result = authRepository.getCurrentUser()
            if (result is Resource.Success) {
                cachedUser = result.data
            }
            _currentUser.value = result
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
