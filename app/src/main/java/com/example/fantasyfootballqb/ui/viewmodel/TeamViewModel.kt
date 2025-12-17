package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.models.Game
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class QBWithScore(
    val qb: QB,
    val score: Double?
)

class TeamViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _availableQBs = MutableStateFlow<List<QB>>(emptyList())
    val availableQBs: StateFlow<List<QB>> = _availableQBs

    private val _formationQBs = MutableStateFlow<List<QB>>(emptyList())
    val formationQBs: StateFlow<List<QB>> = _formationQBs

    private val _formationLocked = MutableStateFlow<Boolean?>(null)
    val formationLocked: StateFlow<Boolean?> = _formationLocked

    private val _formationScores = MutableStateFlow<List<QBWithScore>>(emptyList())
    val formationScores: StateFlow<List<QBWithScore>> = _formationScores

    private val _userTeamName = MutableStateFlow<String?>(null)
    val userTeamName: StateFlow<String?> = _userTeamName

    // weekCalculated (derived from games.partitaCalcolata)
    private val _weekCalculated = MutableStateFlow(false)
    val weekCalculated: StateFlow<Boolean> = _weekCalculated
    private var weekGamesListener: ListenerRegistration? = null

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        observeQBs()
        loadUserProfile()
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _error.value = err.message
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { d ->
                    try {
                        QB(
                            id = d.id,
                            nome = d.getString("nome") ?: "",
                            squadra = d.getString("squadra") ?: "",
                            stato = d.getString("stato") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.filter { it.stato.equals("Titolare", ignoreCase = true) } ?: emptyList()
                _availableQBs.value = list
            }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val doc = db.collection("users").document(uid).get().await()
                _userTeamName.value = if (doc.exists()) doc.getString("nomeTeam") ?: "" else ""
            } catch (e: Exception) {
                Log.e("TeamVM", "loadUserProfile: ${e.message}", e)
                _error.value = e.message
            }
        }
    }

    fun observeWeekCalculated(week: Int) {
        weekGamesListener?.remove()
        weekGamesListener = db.collection("games")
            .whereEqualTo("weekNumber", week)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _weekCalculated.value = false
                    return@addSnapshotListener
                }
                val docs = snaps?.documents ?: emptyList()
                if (docs.isEmpty()) {
                    _weekCalculated.value = false
                } else {
                    val allCalculated = docs.all { it.getBoolean("partitaCalcolata") == true }
                    _weekCalculated.value = allCalculated
                }
            }
    }

    fun loadUserFormationForWeek(week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val uid = auth.currentUser?.uid ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }
                // osserva lo stato della week
                observeWeekCalculated(week)

                val docRef = db.collection("users").document(uid).collection("formations").document(week.toString())
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    _formationQBs.value = emptyList()
                    _formationLocked.value = null
                    _formationScores.value = emptyList()
                } else {
                    val qbIds = snap.get("qbIds") as? List<*>
                    val locked = snap.getBoolean("locked") ?: false
                    _formationLocked.value = locked
                    if (qbIds != null) {
                        val qblist = qbIds.mapNotNull { id ->
                            if (id is String) {
                                val qbDoc = db.collection("qbs").document(id).get().await()
                                if (qbDoc.exists()) {
                                    QB(
                                        id = qbDoc.id,
                                        nome = qbDoc.getString("nome") ?: "",
                                        squadra = qbDoc.getString("squadra") ?: "",
                                        stato = qbDoc.getString("stato") ?: ""
                                    )
                                } else null
                            } else null
                        }
                        _formationQBs.value = qblist
                        loadScoresForFormation(qblist, week)
                    } else {
                        _formationQBs.value = emptyList()
                        _formationScores.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e("TeamVM", "loadUserFormationForWeek: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun submitFormation(week: Int, qbIds: List<String>) {
        if (qbIds.size != 3) {
            _error.value = "Devi selezionare 3 QB distinti"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val uid = auth.currentUser?.uid ?: run {
                    _error.value = "Utente non autenticato"
                    _loading.value = false
                    return@launch
                }
                val docRef = db.collection("users").document(uid).collection("formations").document(week.toString())
                val data: MutableMap<String, Any?> = mutableMapOf(
                    "weekNumber" to week,
                    "qbIds" to qbIds,
                    "locked" to false
                )
                docRef.set(data, SetOptions.merge()).await()
                docRef.update(mapOf("locked" to true)).await()
                loadUserFormationForWeek(week)
            } catch (e: Exception) {
                Log.e("TeamVM", "submitFormation: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadScoresForFormation(qbs: List<QB>, week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val gamesSnap = db.collection("games").whereEqualTo("weekNumber", week).get().await()
                val gameIds = gamesSnap.documents.map { it.id }.toSet()

                val results = mutableListOf<QBWithScore>()
                for (qb in qbs) {
                    val wsSnap = db.collection("weekstats").whereEqualTo("qb_id", qb.id).get().await()
                    var foundScore: Double? = null
                    for (doc in wsSnap.documents) {
                        val gameId = doc.getString("game_id") ?: doc.getString("gameId")
                        if (gameId != null && gameIds.contains(gameId)) {
                            val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("punteggio")
                            val valnum: Double? = when (raw) {
                                is Number -> raw.toDouble()
                                is String -> raw.toDoubleOrNull()
                                else -> null
                            }
                            foundScore = valnum
                            break
                        }
                    }
                    results.add(QBWithScore(qb = qb, score = foundScore))
                }
                _formationScores.value = results
            } catch (e: Exception) {
                Log.e("TeamVM", "loadScoresForFormation: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        weekGamesListener?.remove()
    }
}
