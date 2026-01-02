package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class PendingAction { NONE, DELETE_ACCOUNT }

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repository = FireStoreRepository()

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    private val _askPassword = MutableStateFlow(false)
    val askPassword: StateFlow<Boolean> = _askPassword

    private var pendingAction: PendingAction = PendingAction.NONE

    init {
        loadUserData()
    }

    fun clearMessages() { _error.value = null; _success.value = null }

    fun loadUserData() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _error.value = "Non autenticato"; _loading.value = false; return@launch
                }
                _email.value = auth.currentUser?.email

                // Repo call (observe o get one-shot)
                val flow = repository.observeUser(uid)
                // Qui facciamo una collect parziale solo per prendere il primo valore,
                // oppure usiamo collect normale se vogliamo aggiornamenti live.
                // Per semplicità di migrazione usiamo collect in coroutine separata
                // o passiamo a un metodo get one-shot nel repo.
                // Dato che observeUser è Flow, lanciamo una coroutine che ascolta
                launch {
                    flow.collect { u -> _username.value = u?.username ?: "" }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val uid = auth.currentUser?.uid ?: return@launch
                repository.updateUsername(uid, newUsername)
                _username.value = newUsername
                _success.value = "Username aggiornato"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _loading.value = true
            val user = auth.currentUser ?: return@launch
            try {
                // 1. Cancella dati Firestore tramite Repo
                repository.deleteUserData(user.uid)
                // 2. Cancella Auth
                user.delete().await()
                _success.value = "Account eliminato"
            } catch (ex: Exception) {
                if (ex is FirebaseAuthRecentLoginRequiredException) {
                    pendingAction = PendingAction.DELETE_ACCOUNT
                    _askPassword.value = true
                } else {
                    _error.value = ex.message
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() { auth.signOut() }
}