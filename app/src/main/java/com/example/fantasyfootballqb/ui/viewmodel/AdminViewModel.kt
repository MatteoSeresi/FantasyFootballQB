package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AdminUser(
    val uid: String,
    val email: String,
    val username: String,
    val nomeTeam: String,
    val isAdmin: Boolean = false
)

class AdminViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _availableWeeks = MutableStateFlow<List<Int>>(emptyList())
    val availableWeeks: StateFlow<List<Int>> = _availableWeeks

    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _qbs = MutableStateFlow<List<QB>>(emptyList())
    val qbs: StateFlow<List<QB>> = _qbs

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    val selectedWeek: StateFlow<Int?> = _selectedWeek

    // stato week calcolata (true se tutte le games della week hanno partitaCalcolata == true)
    private val _weekCalculated = MutableStateFlow(false)
    val weekCalculated: StateFlow<Boolean> = _weekCalculated

    private var weekGamesListener: ListenerRegistration? = null

    init {
        observeUsers()
        observeGames()
        observeQBs()
    }

    private fun observeUsers() {
        db.collection("users")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _error.value = err.message
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { d ->
                    try {
                        AdminUser(
                            uid = d.id,
                            email = d.getString("email") ?: "",
                            username = d.getString("username") ?: "",
                            nomeTeam = d.getString("nomeTeam") ?: "",
                            isAdmin = d.getBoolean("isAdmin") == true
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                _users.value = list
            }
    }

    private fun observeGames() {
        db.collection("games")
            .orderBy("weekNumber")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _error.value = err.message
                    return@addSnapshotListener
                }
                val games = snaps?.documents?.mapNotNull { d ->
                    try {
                        val week = (d.getLong("weekNumber") ?: 0L).toInt()
                        Game(
                            id = d.id,
                            weekNumber = week,
                            squadraCasa = d.getString("squadraCasa") ?: "",
                            squadraOspite = d.getString("squadraOspite") ?: "",
                            partitaGiocata = d.getBoolean("partitaGiocata") ?: false,
                            risultato = d.getString("risultato"),
                            partitaCalcolata = d.getBoolean("partitaCalcolata") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                val map = games.groupBy { it.weekNumber }.toSortedMap()
                _gamesByWeek.value = map
                _availableWeeks.value = map.keys.toList()
                if (_selectedWeek.value == null && _availableWeeks.value.isNotEmpty()) {
                    _selectedWeek.value = _availableWeeks.value.first()
                }
            }
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
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
                } ?: emptyList()
                _qbs.value = list
            }
    }

    fun setSelectedWeek(week: Int?) {
        _selectedWeek.value = week
    }

    fun updateUserData(uid: String, email: String, username: String, nomeTeam: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val map = mapOf(
                    "email" to email,
                    "username" to username,
                    "nomeTeam" to nomeTeam
                )
                db.collection("users").document(uid).set(map, SetOptions.merge()).await()
                _success.value = "Dati utente aggiornati"
            } catch (e: Exception) {
                Log.e("AdminVM", "updateUserData: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateGame(gameId: String, partitaGiocata: Boolean, risultato: String?) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val map: MutableMap<String, Any?> = mutableMapOf("partitaGiocata" to partitaGiocata)
                map["risultato"] = risultato
                db.collection("games").document(gameId).set(map, SetOptions.merge()).await()
                _success.value = "Partita aggiornata"
            } catch (e: Exception) {
                Log.e("AdminVM", "updateGame: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun setQBScore(gameId: String, qbId: String, punteggio: Double) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val coll = db.collection("weekstats")
                val qsnap = coll
                    .whereEqualTo("game_id", gameId)
                    .whereEqualTo("qb_id", qbId)
                    .get()
                    .await()

                if (!qsnap.isEmpty) {
                    val doc = qsnap.documents.first()
                    coll.document(doc.id).set(mapOf("punteggioQB" to punteggio), SetOptions.merge()).await()
                } else {
                    val new = mapOf(
                        "game_id" to gameId,
                        "qb_id" to qbId,
                        "punteggioQB" to punteggio
                    )
                    coll.add(new).await()
                }
                _success.value = "Punteggio QB aggiornato"
            } catch (e: Exception) {
                Log.e("AdminVM", "setQBScore: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _success.value = null
    }

    /**
     * Observe weekCalculated (true se tutte le games della week hanno partitaCalcolata == true)
     */
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

    /**
     * Nuova funzione: valida la week (senza modificare DB).
     * Ritorna una lista di messaggi (vuota = tutto ok).
     */
    suspend fun validateWeek(week: Int): List<String> {
        val problems = mutableListOf<String>()
        try {
            val gamesSnap = db.collection("games").whereEqualTo("weekNumber", week).get().await()
            val gamesDocs = gamesSnap.documents
            if (gamesDocs.isEmpty()) {
                problems.add("Nessuna partita per la week $week")
                return problems
            }

            // verifica partite giocate
            val notPlayed = mutableListOf<String>()
            for (g in gamesDocs) {
                val played = g.getBoolean("partitaGiocata") == true
                if (!played) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    notPlayed.add(label)
                }
            }
            if (notPlayed.isNotEmpty()) {
                problems.add("Partite non giocate: ${notPlayed.joinToString(" ; ")}")
            }

            // verifica weekstats per ogni partita (esistenza e punteggi numerici)
            val missingStats = mutableListOf<String>()
            for (g in gamesDocs) {
                val gameId = g.id
                val wsSnap = db.collection("weekstats").whereEqualTo("game_id", gameId).get().await()
                if (wsSnap.isEmpty) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    missingStats.add("$label -> nessun weekstats")
                    continue
                }

                val invalid = wsSnap.documents.any { doc ->
                    val raw = doc.get("punteggioQB")
                    when (raw) {
                        is Number -> false
                        is String -> raw.toDoubleOrNull() == null
                        else -> true
                    }
                }
                if (invalid) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    missingStats.add("$label -> weekstats senza punteggio valido")
                }
            }
            if (missingStats.isNotEmpty()) {
                problems.addAll(missingStats)
            }

        } catch (e: Exception) {
            Log.e("AdminVM", "validateWeek: ${e.message}", e)
            problems.add("Errore durante la validazione: ${e.message}")
        }
        return problems
    }

    /**
     * calculateWeek: come prima — valida (anche se chiamato direttamente), poi setta partitaCalcolata=true via batch
     */
    fun calculateWeek(week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _success.value = null
            try {
                // 1) prendi tutte le games della week
                val gamesSnap = db.collection("games").whereEqualTo("weekNumber", week).get().await()
                val gamesDocs = gamesSnap.documents
                if (gamesDocs.isEmpty()) {
                    _error.value = "Nessuna partita trovata per la week $week"
                    return@launch
                }

                // 2) verifica che tutte le partite siano giocate
                val notPlayed = mutableListOf<String>()
                for (g in gamesDocs) {
                    val played = g.getBoolean("partitaGiocata") == true
                    if (!played) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        notPlayed.add(label)
                    }
                }
                if (notPlayed.isNotEmpty()) {
                    _error.value = "Non tutte le partite sono giocate:\n" + notPlayed.joinToString("\n")
                    return@launch
                }

                // 3) verifica weekstats per ogni partita: esistenza e punteggio numerico
                val missingStats = mutableListOf<String>()
                for (g in gamesDocs) {
                    val gameId = g.id
                    val wsSnap = db.collection("weekstats").whereEqualTo("game_id", gameId).get().await()
                    if (wsSnap.isEmpty) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        missingStats.add("$label -> nessun weekstats")
                        continue
                    }

                    val invalid = wsSnap.documents.any { doc ->
                        val raw = doc.get("punteggioQB")
                        when (raw) {
                            is Number -> false
                            is String -> raw.toDoubleOrNull() == null
                            else -> true
                        }
                    }
                    if (invalid) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        missingStats.add("$label -> weekstats senza punteggio valido")
                    }
                }
                if (missingStats.isNotEmpty()) {
                    _error.value = "Ci sono partite senza punteggi completi:\n" + missingStats.joinToString("\n")
                    return@launch
                }

                // 4) tutto ok -> batch update partitaCalcolata = true per tutte le games della week che non lo sono
                val batch = db.batch()
                var updates = 0
                for (g in gamesDocs) {
                    val already = g.getBoolean("partitaCalcolata") == true
                    if (!already) {
                        batch.update(g.reference, mapOf("partitaCalcolata" to true))
                        updates++
                    }
                }
                if (updates == 0) {
                    _error.value = "La week $week è già stata calcolata."
                } else {
                    batch.commit().await()
                    _success.value = "Week $week marcata come calcolata"
                }
            } catch (e: Exception) {
                Log.e("AdminVM", "calculateWeek: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        weekGamesListener?.remove()
    }
}