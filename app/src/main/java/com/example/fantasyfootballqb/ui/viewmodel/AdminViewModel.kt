package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.repository.FireStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdminUser(
    val uid: String,
    val email: String,
    val username: String,
    val nomeTeam: String?,
    val isAdmin: Boolean = false
)

data class UserFormationRow(
    val uid: String,
    val username: String,
    val nomeTeam: String?,
    val qbIds: List<String>,
    val totalWeekScore: Double
)

class AdminViewModel : ViewModel() {

    private val repository = FireStoreRepository()

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _availableWeeks = MutableStateFlow<List<Int>>(emptyList())
    val availableWeeks: StateFlow<List<Int>> = _availableWeeks

    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _qbs = MutableStateFlow<List<QB>>(emptyList())
    val qbs: StateFlow<List<QB>> = _qbs

    private val _userFormations = MutableStateFlow<List<UserFormationRow>>(emptyList())
    val userFormations: StateFlow<List<UserFormationRow>> = _userFormations

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    val selectedWeek: StateFlow<Int?> = _selectedWeek

    private val _weekCalculated = MutableStateFlow(false)
    val weekCalculated: StateFlow<Boolean> = _weekCalculated

    init {
        observeUsers()
        observeGames()
        observeQBs()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            repository.observeAllUsers().collect { userList ->
                _users.value = userList.map { u ->
                    AdminUser(u.uid, u.email, u.username, u.nomeTeam, u.isAdmin)
                }
            }
        }
    }

    private fun observeGames() {
        viewModelScope.launch {
            repository.observeGames().collect { games ->
                val map = games.groupBy { it.weekNumber }.toSortedMap()
                _gamesByWeek.value = map
                _availableWeeks.value = map.keys.toList()
                if (_selectedWeek.value == null && _availableWeeks.value.isNotEmpty()) {
                    _selectedWeek.value = _availableWeeks.value.first()
                }
            }
        }
    }

    private fun observeQBs() {
        viewModelScope.launch {
            repository.observeQBs().collect { list ->
                _qbs.value = list
            }
        }
    }

    fun setSelectedWeek(week: Int?) {
        _selectedWeek.value = week
    }

    fun observeWeekCalculated(week: Int) {
        viewModelScope.launch {
            repository.observeWeekCalculated(week).collect { isCalc ->
                _weekCalculated.value = isCalc
            }
        }
    }

    // --- FUNZIONE DI AGGIORNAMENTO UTENTE  ---
    fun updateUserData(uid: String, username: String, nomeTeam: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val map = mapOf(
                    "username" to username,
                    "nomeTeam" to nomeTeam
                )
                repository.updateAdminUserData(uid, map)
                _success.value = "Dati utente aggiornati"
            } catch (e: Exception) {
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
                repository.updateGameResult(gameId, partitaGiocata, risultato)
                _success.value = "Partita aggiornata"
            } catch (e: Exception) {
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
                repository.setQBScore(gameId, qbId, punteggio)
                _success.value = "Punteggio QB aggiornato"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun validateWeek(week: Int): List<String> {
        val problems = mutableListOf<String>()
        try {
            val games = repository.getGamesForWeek(week)
            if (games.isEmpty()) return listOf("Nessuna partita per la week $week")

            games.filter { !it.partitaGiocata }.forEach { g ->
                problems.add("${g.squadraCasa} vs ${g.squadraOspite}: Non giocata")
            }

            for (g in games) {
                val statsDocs = repository.getWeekStatsForGame(g.id)
                if (statsDocs.isEmpty()) {
                    problems.add("${g.squadraCasa} vs ${g.squadraOspite}: Nessun punteggio inserito")
                    continue
                }
                val invalid = statsDocs.any { doc ->
                    val raw = doc.get("punteggioQB")
                    when (raw) {
                        is Number -> false
                        is String -> raw.toDoubleOrNull() == null
                        else -> true
                    }
                }
                if (invalid) problems.add("${g.squadraCasa} vs ${g.squadraOspite}: Punteggio non valido")
            }
        } catch (e: Exception) {
            problems.add("Errore validazione: ${e.message}")
        }
        return problems
    }

    fun calculateWeek(week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val errors = validateWeek(week)
                if (errors.isNotEmpty()) {
                    _error.value = "Impossibile calcolare:\n${errors.joinToString("\n")}"
                    return@launch
                }

                val games = repository.getGamesForWeek(week)
                val toUpdate = games.filter { !it.partitaCalcolata }.map { it.id }

                if (toUpdate.isNotEmpty()) {
                    repository.markWeekAsCalculated(week, toUpdate)
                    _success.value = "Week $week calcolata con successo"
                } else {
                    _success.value = "Week $week già calcolata"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadUserFormationsForWeek(week: Int?) {
        viewModelScope.launch {
            if (week == null) {
                _userFormations.value = emptyList(); return@launch
            }
            _loading.value = true
            try {
                val games = _gamesByWeek.value[week] ?: emptyList()
                val gameIds = games.map { it.id }
                val allStats = repository.getAllWeekStats()

                val statsMap = mutableMapOf<String, MutableList<Double>>()
                for (d in allStats) {
                    val gId = d.getString("game_id") ?: d.getString("gameId") ?: d.getString("game")
                    if (gId == null || !gameIds.contains(gId)) continue

                    val qbId = d.getString("qb_id") ?: d.getString("qbId") ?: continue
                    val raw = d.get("punteggioQB") ?: d.get("punteggio_qb") ?: d.get("score") ?: d.get("punteggio")
                    val value: Double? = when (raw) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    if (value != null) {
                        statsMap.getOrPut(qbId) { mutableListOf() }.add(value)
                    }
                }

                val rows = mutableListOf<UserFormationRow>()
                for (u in _users.value) {
                    if (u.isAdmin) continue
                    val qbIds = repository.getUserFormationIds(u.uid, week)
                    var total = 0.0
                    qbIds.forEach { qid ->
                        val scores = statsMap[qid]
                        total += (scores?.filter { it > 0.0 }?.sum() ?: 0.0)
                    }
                    rows.add(UserFormationRow(u.uid, u.username, u.nomeTeam, qbIds, total))
                }
                _userFormations.value = rows.sortedBy { it.username.lowercase() }

            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateUserFormation(uid: String, week: Int, newQbIds: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (newQbIds.size != 3 || newQbIds.toSet().size != 3) {
                    _error.value = "Servono 3 QB distinti."
                    return@launch
                }

                val data = mapOf(
                    "qbIds" to newQbIds,
                    "weekNumber" to week
                )
                repository.updateUserFormationData(uid, week, data)

                loadUserFormationsForWeek(week)
                _success.value = "Formazione aggiornata"
            } catch (e: Exception) {
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
}