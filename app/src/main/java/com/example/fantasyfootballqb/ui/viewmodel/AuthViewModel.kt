package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.fantasyfootballqb.repository.AuthRepository

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val uid: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {

    private val _registerState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val registerState: StateFlow<AuthUiState> = _registerState

    private val _loginState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState> = _loginState

    fun register(email: String, password: String, username: String) {
        viewModelScope.launch {
            _registerState.value = AuthUiState.Loading
            val res = repo.register(email.trim(), password, username.trim())
            if (res.isSuccess) {
                _registerState.value = AuthUiState.Success(res.getOrThrow())
            } else {
                _registerState.value = AuthUiState.Error(res.exceptionOrNull()?.message ?: "Errore sconosciuto")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val res = repo.login(email.trim(), password)
            if (res.isSuccess) {
                _loginState.value = AuthUiState.Success(res.getOrThrow())
            } else {
                _loginState.value = AuthUiState.Error(res.exceptionOrNull()?.message ?: "Errore sconosciuto")
            }
        }
    }

    fun logout() {
        repo.logout()
    }

    fun currentUid(): String? = repo.getCurrentUid()
}
