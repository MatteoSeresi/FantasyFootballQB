package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Enum per ricordare cosa voleva fare l'utente prima che chiedessimo la password
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

    // Stato per mostrare il dialog della password
    private val _askPassword = MutableStateFlow(false)
    val askPassword: StateFlow<Boolean> = _askPassword

    private var pendingAction: PendingAction = PendingAction.NONE
    private var userObserverJob: Job? = null

    init {
        loadUserData()
    }

    fun clearMessages() { _error.value = null; _success.value = null }

    fun loadUserData() {
        userObserverJob?.cancel()
        userObserverJob = viewModelScope.launch {
            // Inizio caricamento
            _loading.value = true

            val uid = auth.currentUser?.uid
            if (uid == null) {
                _loading.value = false
                return@launch
            }
            _email.value = auth.currentUser?.email

            // FIX CARICAMENTO INFINITO:
            // Non usiamo try/finally qui perché .collect sospende all'infinito.
            // Spegniamo il loading direttamente dentro il collect appena arrivano i dati.
            repository.observeUser(uid)
                .catch { e ->
                    Log.w("ProfileVM", "Error observing: ${e.message}")
                    _loading.value = false // Spegni loading se c'è errore
                }
                .collect { u ->
                    _username.value = u?.username ?: ""
                    _loading.value = false // Spegni loading appena arriva il dato!
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
                // 1. Fermiamo l'ascolto per evitare crash (Importante!)
                userObserverJob?.cancel()
                delay(100)

                // 2. Cancelliamo i dati dal DB
                repository.deleteUserData(user.uid)

                // 3. Cancelliamo l'utente Auth
                user.delete().await()

                _success.value = "Account eliminato"
            } catch (ex: Exception) {
                // Se Firebase dice "Serve login recente", chiediamo la password
                if (ex is FirebaseAuthRecentLoginRequiredException) {
                    // Riattiviamo l'ascolto perché l'utente è ancora qui
                    loadUserData()
                    pendingAction = PendingAction.DELETE_ACCOUNT
                    _askPassword.value = true
                } else {
                    _error.value = ex.message
                    // Riattiviamo l'ascolto in caso di errore generico
                    loadUserData()
                }
            } finally {
                _loading.value = false
            }
        }
    }

    // Funzione chiamata dal Dialog quando l'utente inserisce la password
    fun reauthenticateWithPassword(password: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val user = auth.currentUser ?: return@launch
                val emailAddr = user.email ?: return@launch

                // Creiamo le credenziali
                val credential = EmailAuthProvider.getCredential(emailAddr, password)

                // Rieseguiamo il login (Re-auth)
                user.reauthenticate(credential).await()

                // Se va a buon fine, nascondiamo il dialog
                _askPassword.value = false

                // E riproviamo l'azione che era fallita (Eliminazione)
                if (pendingAction == PendingAction.DELETE_ACCOUNT) {
                    pendingAction = PendingAction.NONE
                    deleteAccount() // Riprova a cancellare
                }
            } catch (e: Exception) {
                _error.value = "Password errata o errore: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userObserverJob?.cancel()
            delay(200)
            auth.signOut()
            _username.value = null
            _email.value = null
        }
    }
}