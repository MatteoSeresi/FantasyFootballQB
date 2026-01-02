package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.User
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    // Usiamo il repository
    private val repository = FireStoreRepository()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    init {
        observeUser()
    }

    private fun observeUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Il repository gestisce il listener e il parsing
            repository.observeUser(uid).collect { u ->
                _user.value = u
            }
        }
    }

    fun createOrUpdateTeam(name: String) {
        val uid = auth.currentUser?.uid ?: run {
            _error.value = "Utente non autenticato"
            return
        }

        viewModelScope.launch {
            try {
                _loading.value = true
                // Chiamata pulita al repository
                repository.updateTeamName(uid, name)

                _loading.value = false
                _success.value = "Nome squadra aggiornato"
            } catch (e: Exception) {
                _loading.value = false
                _error.value = e.message ?: "Errore durante aggiornamento team"
            }
        }
    }

    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }
}