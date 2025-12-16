package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class PendingAction {
    NONE,
    UPDATE_EMAIL,
    DELETE_ACCOUNT
}

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

    // se serve riprovare un'azione dopo la reauth
    private var pendingAction: PendingAction = PendingAction.NONE
    private var pendingEmailTarget: String? = null

    init {
        loadUserData()
    }

    fun clearMessages() {
        _error.value = null
        _success.value = null
    }

    fun loadUserData() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val user = auth.currentUser
                if (user == null) {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }
                // preferiamo Firestore users doc per username e nomeTeam etc.
                _email.value = user.email
                val doc = db.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    _username.value = doc.getString("username") ?: ""
                } else {
                    _username.value = ""
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "loadUserData: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Aggiorna solo lo username nel documento users/{uid}
     */
    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val uid = auth.currentUser?.uid ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }
                val map = mapOf("username" to newUsername)
                db.collection("users").document(uid).set(map, SetOptions.merge()).await()
                _username.value = newUsername
                _success.value = "Username aggiornato"
            } catch (e: Exception) {
                Log.e("ProfileVM", "updateUsername: ${e.message}", e)
                _error.value = e.message ?: "Errore aggiornamento username"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Aggiorna l'email dell'utente. Se Firebase segnala che è necessaria una reauth,
     * segnaliamo la richiesta di password (askPassword = true) e salviamo l'azione pendente.
     */
    fun updateEmail(newEmail: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val user = auth.currentUser ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }

                try {
                    user.updateEmail(newEmail).await()
                    // aggiorna anche il campo email nel documento users (opzionale ma utile)
                    db.collection("users").document(user.uid).set(mapOf("email" to newEmail), SetOptions.merge()).await()
                    _email.value = newEmail
                    _success.value = "Email aggiornata"
                } catch (ex: Exception) {
                    // Gestione reauth richiesta
                    if (ex is FirebaseAuthRecentLoginRequiredException) {
                        pendingAction = PendingAction.UPDATE_EMAIL
                        pendingEmailTarget = newEmail
                        _askPassword.value = true
                    } else {
                        Log.e("ProfileVM", "updateEmail failed: ${ex.message}", ex)
                        _error.value = ex.message ?: "Errore aggiornamento email"
                    }
                }

            } catch (e: Exception) {
                Log.e("ProfileVM", "updateEmail outer: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Elimina i dati Firestore dell'utente (subcollection formations) e poi l'account Auth.
     * Se Firebase richiede reauth viene richiamata la pagina password.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val user = auth.currentUser ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }

                try {
                    // 1) elimina subcollection users/{uid}/formations
                    deleteUserFormations(user.uid)
                    // 2) elimina il doc users/{uid}
                    db.collection("users").document(user.uid).delete().await()
                    // 3) elimina account Authentication
                    user.delete().await()
                    _success.value = "Account eliminato"
                } catch (ex: Exception) {
                    if (ex is FirebaseAuthRecentLoginRequiredException) {
                        // serve reauth
                        pendingAction = PendingAction.DELETE_ACCOUNT
                        _askPassword.value = true
                    } else {
                        Log.e("ProfileVM", "deleteAccount failed: ${ex.message}", ex)
                        _error.value = ex.message ?: "Errore eliminazione account"
                    }
                }

            } catch (e: Exception) {
                Log.e("ProfileVM", "deleteAccount outer: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Reautentica con email + password (l'email la prendiamo dall'utente autenticato).
     * Se la reauth riesce, tenta di nuovo l'azione pendente (updateEmail o deleteAccount).
     */
    fun reauthenticateWithPassword(password: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val user = auth.currentUser ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }
                val emailAddr = user.email ?: run {
                    _error.value = "Email non disponibile per reauth"
                    _loading.value = false
                    return@launch
                }
                val cred = EmailAuthProvider.getCredential(emailAddr, password)
                user.reauthenticate(cred).await()
                _askPassword.value = false
                // retry pending action
                when (pendingAction) {
                    PendingAction.UPDATE_EMAIL -> {
                        val target = pendingEmailTarget
                        pendingEmailTarget = null
                        pendingAction = PendingAction.NONE
                        if (target != null) updateEmail(target)
                    }
                    PendingAction.DELETE_ACCOUNT -> {
                        pendingAction = PendingAction.NONE
                        deleteAccount()
                    }
                    else -> { /* niente */ }
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "reauth failed: ${e.message}", e)
                _error.value = e.message ?: "Re-authentication fallita"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun deleteUserFormations(uid: String) {
        try {
            val coll = db.collection("users").document(uid).collection("formations")
            val snap = coll.get().await()
            for (doc in snap.documents) {
                // cancella ogni doc nella subcollection
                coll.document(doc.id).delete().await()
            }
        } catch (e: Exception) {
            // log ma non bloccare l'eliminazione se non trova subcollection
            Log.w("ProfileVM", "deleteUserFormations: ${e.message}")
        }
    }

    /**
     * Logout locale (client) — non cancella dati.
     */
    fun logout() {
        auth.signOut()
    }
}
