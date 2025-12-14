package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // <-- assicurati che esista questa proprietà
    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        observeUser()
    }

    private fun observeUser() {
        val uid = auth.currentUser?.uid ?: return
        listenerRegistration?.remove()
        listenerRegistration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _error.value = error.message
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val u = User(
                        uid = snapshot.id,
                        email = snapshot.getString("email") ?: auth.currentUser?.email ?: "",
                        username = snapshot.getString("username") ?: "",
                        nomeTeam = snapshot.getString("nomeTeam") ?: "",
                        isAdmin = snapshot.getBoolean("isAdmin") ?: false
                    )
                    _user.value = u
                } else {
                    _user.value = null
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
                val map = mapOf(
                    "nomeTeam" to name,
                    "email" to (auth.currentUser?.email ?: ""),
                    "username" to (auth.currentUser?.displayName ?: "")
                )
                db.collection("users").document(uid)
                    .set(map, SetOptions.merge())
                    .await()

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

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
