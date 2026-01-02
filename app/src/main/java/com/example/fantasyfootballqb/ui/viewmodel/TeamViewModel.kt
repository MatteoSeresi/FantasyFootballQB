package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QBWithScore(
    val qb: QB,
    val score: Double?,
    val opponentTeam: String?
)

class TeamViewModel : ViewModel() {

    // Inizializziamo il Repository (in un'app più grande useremmo Hilt/Koin per iniettarlo)
    private val repository = FireStoreRepository()
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

    private val _gamesForWeek = MutableStateFlow<List<Game>>(emptyList())
    val gamesForWeek: StateFlow<List<Game>> = _gamesForWeek

    private val _availableWeeks = MutableStateFlow<List<Int>>(emptyList())
    val availableWeeks: StateFlow<List<Int>> = _availableWeeks

    private val _firstUncalculatedWeek = MutableStateFlow<Int?>(null)
    val firstUncalculatedWeek: StateFlow<Int?> = _firstUncalculatedWeek

    private val _weekCalculated = MutableStateFlow(false)
    val weekCalculated: StateFlow<Boolean> = _weekCalculated

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        observeQBs()
        loadUserProfile()
        loadAvailableWeeks()
        loadFirstUncalculatedWeek()
    }

    private fun observeQBs() {
        viewModelScope.launch {
            // repository.observeQBs() ci restituisce già oggetti QB mappati
            repository.observeQBs().collect { qbs ->
                _availableQBs.value = qbs.filter { it.stato.equals("Titolare", ignoreCase = true) }
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            repository.observeUser(uid).collect { user ->
                _userTeamName.value = user?.nomeTeam ?: ""
            }
        }
    }

    fun loadAvailableWeeks() {
        viewModelScope.launch {
            try {
                // Scarichiamo tutte le partite per estrarre le week
                val games = repository.getAllGames()
                val weeks = games.map { it.weekNumber }.distinct().sorted()
                _availableWeeks.value = weeks

                // Aggiorniamo anche la firstUncalculatedWeek per sicurezza
                calculateFirstUncalculatedInternal(games)
            } catch (e: Exception) {
                Log.e("TeamVM", "loadAvailableWeeks: ${e.message}", e)
                _error.value = e.message
            }
        }
    }

    fun loadFirstUncalculatedWeek() {
        viewModelScope.launch {
            try {
                val games = repository.getAllGames()
                calculateFirstUncalculatedInternal(games)
            } catch (e: Exception) {
                Log.e("TeamVM", "loadFirstUncalculatedWeek: ${e.message}", e)
                _firstUncalculatedWeek.value = null
            }
        }
    }

    // Logica helper per trovare la prima week non calcolata (in memoria)
    private fun calculateFirstUncalculatedInternal(games: List<Game>) {
        val firstUncalc = games
            .filter { !it.partitaCalcolata }
            .minByOrNull { it.weekNumber }
        _firstUncalculatedWeek.value = firstUncalc?.weekNumber
    }

    fun observeWeekCalculated(week: Int) {
        viewModelScope.launch {
            repository.observeWeekCalculated(week).collect { isCalculated ->
                _weekCalculated.value = isCalculated
            }
        }
    }

    fun loadGamesForWeek(week: Int) {
        viewModelScope.launch {
            try {
                _gamesForWeek.value = repository.getGamesForWeek(week)
            } catch (e: Exception) {
                Log.e("TeamVM", "loadGamesForWeek: ${e.message}", e)
                _error.value = e.message
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

                // Carica giochi e osserva stato week
                loadGamesForWeek(week)
                observeWeekCalculated(week)

                // 1. Controlla se è locked
                val isLocked = repository.isFormationLocked(uid, week)
                _formationLocked.value = isLocked

                // 2. Recupera gli ID dei QB
                val qbIds = repository.getUserFormationIds(uid, week)

                if (qbIds.isNotEmpty()) {
                    // 3. Recupera gli oggetti QB completi usando il nuovo metodo del Repo
                    val qbList = qbIds.mapNotNull { id -> repository.getQB(id) }
                    _formationQBs.value = qbList

                    // 4. Carica i punteggi
                    loadScoresForFormation(qbList, week)
                } else {
                    _formationQBs.value = emptyList()
                    _formationScores.value = emptyList()
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

                repository.submitFormation(uid, week, qbIds)

                // Ricarica la formazione
                loadUserFormationForWeek(week)
                // Ricalcola le week disponibili/calcolate
                loadFirstUncalculatedWeek()

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
            try {
                // Recupera le partite se non ci sono
                val games = if (_gamesForWeek.value.isNotEmpty() && _gamesForWeek.value.first().weekNumber == week) {
                    _gamesForWeek.value
                } else {
                    repository.getGamesForWeek(week)
                }

                // Mappa team -> opponent
                val teamToOpponent = mutableMapOf<String, String>()
                for (g in games) {
                    teamToOpponent[g.squadraCasa] = g.squadraOspite
                    teamToOpponent[g.squadraOspite] = g.squadraCasa
                }

                val results = mutableListOf<QBWithScore>()
                for (qb in qbs) {
                    // Ottieni i documenti raw dei punteggi per questo QB dal Repo
                    val wsDocs = repository.getWeekStatsForQb(qb.id)

                    var foundScore: Double? = null

                    // Cerca il documento che corrisponde a una delle partite di questa week
                    for (doc in wsDocs) {
                        val gameId = doc.getString("game_id") ?: doc.getString("gameId")
                        if (gameId != null && games.any { it.id == gameId }) {
                            // Trovato! Applichiamo la tua logica di parsing specifica
                            val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("punteggio")
                            foundScore = when (raw) {
                                is Number -> raw.toDouble()
                                is String -> raw.toDoubleOrNull()
                                else -> null
                            }
                            break
                        }
                    }
                    val opponent = teamToOpponent[qb.squadra]
                    results.add(QBWithScore(qb = qb, score = foundScore, opponentTeam = opponent))
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
}