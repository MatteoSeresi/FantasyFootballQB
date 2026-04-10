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

    // Mappa per raggruppare automaticamente le partite in base alla Week
    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _qbs = MutableStateFlow<List<QB>>(emptyList())
    val qbs: StateFlow<List<QB>> = _qbs

    private val _userFormations = MutableStateFlow<List<UserFormationRow>>(emptyList())
    val userFormations: StateFlow<List<UserFormationRow>> = _userFormations

    // Gestione stati interfaccia (caricamento ed esiti operazioni)
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

    //Ascolta in tempo reale la collezione degli utenti
    private fun observeUsers() {
        viewModelScope.launch {
            repository.observeAllUsers().collect { userList ->
                _users.value = userList.map { u ->
                    AdminUser(u.uid, u.email, u.username, u.nomeTeam, u.isAdmin)
                }
            }
        }
    }


    //Ascolta in tempo reale le partite.
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

    //Scarica e mantiene aggiornato l'elenco dei Quarterback.
    private fun observeQBs() {
        viewModelScope.launch {
            repository.observeQBs().collect { list ->
                _qbs.value = list
            }
        }
    }

    // Funzione per selezionare la week dal menu a tendina
    fun setSelectedWeek(week: Int?) {
        _selectedWeek.value = week
    }

    // Ascolta se una week è già stata calcolata
    fun observeWeekCalculated(week: Int) {
        viewModelScope.launch {
            repository.observeWeekCalculated(week).collect { isCalc ->
                _weekCalculated.value = isCalc
            }
        }
    }

    /**
     * Permette all'admin di cambiare nome utente o squadra
     */
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

    //Elimina completamente un utente dal sistema e delega al repository l'eliminazione a cascata (documento utente + tutte le sue formazioni).
    fun deleteUser(uid: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                repository.deleteUserData(uid)
                _success.value = "Utente e relativi dati eliminati con successo"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    //Aggiorna i dati della singola partita: spunta la partita come "Giocata" e salva il risultato testuale.
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

    //Salva o modifica il punteggio di un singolo Quarterback in una specifica partita (Game).
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

    /**
     * Prima di calcolare la settimana, controlla che
     * l'amministratore non abbia dimenticato nulla. Genera una lista dei dati mancanti.
     */
    suspend fun validateWeek(week: Int): List<String> {
        val problems = mutableListOf<String>()
        try {
            val games = repository.getGamesForWeek(week)
            if (games.isEmpty()) return listOf("Nessuna partita per la week $week")

            // 1. Verifica che tutte le caselle "Partita Giocata" siano state spuntate
            games.filter { !it.partitaGiocata }.forEach { g ->
                problems.add("${g.squadraCasa} vs ${g.squadraOspite}: Non giocata")
            }

            // 2. Verifica che siano stati inseriti i punteggi dei QB per quelle partite
            for (g in games) {
                val stats = repository.getWeekStatsForGame(g.id)

                if (stats.isEmpty()) {
                    problems.add("${g.squadraCasa} vs ${g.squadraOspite}: Nessun punteggio inserito")
                    continue
                }
            }
        } catch (e: Exception) {
            problems.add("Errore validazione: ${e.message}")
        }
        return problems
    }

    // Calcolo della week
    fun calculateWeek(week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // Esegue il controllo di sicurezza
                val errors = validateWeek(week)
                if (errors.isNotEmpty()) {
                    _error.value = "Impossibile calcolare:\n${errors.joinToString("\n")}"
                    return@launch
                }

                // Chiude le partite non ancora calcolate
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


    /**
     * Carica le formazioni degli utenti.
     * Incrocia tutti gli utenti con le loro formazioni settimanali e somma
     * in tempo reale i punteggi dei singoli QB scelti per mostrare il punteggio totale.
     */
    fun loadUserFormationsForWeek(week: Int?) {
        viewModelScope.launch {
            if (week == null) {
                _userFormations.value = emptyList(); return@launch
            }
            _loading.value = true
            try {
                // Mappatura delle partite e delle statistiche
                val games = _gamesByWeek.value[week] ?: emptyList()
                val gameIds = games.map { it.id }
                val allStats = repository.getAllWeekStats()

                // Associa ogni QB ai suoi punteggi per velocizzare il calcolo
                val statsMap = mutableMapOf<String, MutableList<Double>>()
                for (stat in allStats) {
                    if (gameIds.contains(stat.gameId)) {
                        statsMap.getOrPut(stat.qbId) { mutableListOf() }.add(stat.punteggio)
                    }
                }

                val rows = mutableListOf<UserFormationRow>()

                // Cicla tutti gli utenti (esclusi gli admin)
                for (u in _users.value) {
                    if (u.isAdmin) continue

                    // Recupera la formazione salvata dall'utente
                    val formation = repository.getFormation(u.uid, week)
                    val qbIds = formation?.qbIds ?: emptyList()

                    // Somma i punti dei 3 QB scelti
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

    // Permette all'admin di modificare i 3 Quarterback schierati da un utente
    fun updateUserFormation(uid: String, week: Int, newQbIds: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Validazione sicurezza Admin
                if (newQbIds.size != 3 || newQbIds.toSet().size != 3) {
                    _error.value = "Servono 3 QB distinti."
                    return@launch
                }

                val data = mapOf(
                    "qbIds" to newQbIds,
                    "weekNumber" to week
                )
                repository.updateUserFormationData(uid, week, data)

                // Ricarica la tabella dopo la modifica
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