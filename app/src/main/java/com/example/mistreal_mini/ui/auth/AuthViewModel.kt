package com.example.mistreal_mini.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _authSuccess = MutableSharedFlow<Unit>()
    val authSuccess = _authSuccess.asSharedFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _errorEvent.emit("Please fill all fields") }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = authRepository.signInWithEmail(email, password)) {
                is Resource.Success -> _authSuccess.emit(Unit)
                is Resource.Error -> _errorEvent.emit(result.message ?: "Login failed")
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _errorEvent.emit("Please fill all fields") }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = authRepository.signUpWithEmail(email, password)) {
                is Resource.Success -> _authSuccess.emit(Unit)
                is Resource.Error -> _errorEvent.emit(result.message ?: "Sign up failed")
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun isUserLoggedIn() = authRepository.currentUser != null
}
