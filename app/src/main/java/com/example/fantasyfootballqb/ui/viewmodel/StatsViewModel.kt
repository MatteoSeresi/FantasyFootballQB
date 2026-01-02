package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.repository.FireStoreRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatRow(
    val qbId: String,
    val nome: String,
    val squadra: String,
    val gp: Int,
    val ptot: Double,
    val ppg: Double
)

class StatsViewModel : ViewModel() {

    private val repository = FireStoreRepository()

    private val _rows = MutableStateFlow<List<StatRow>>(emptyList())
    val rows: StateFlow<List<StatRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val qbsCache: MutableMap<String, QB> = mutableMapOf()
    private val gamesCache: MutableMap<String, Game> = mutableMapOf()
    private var lastWeekstatsDocs: List<DocumentSnapshot> = emptyList()

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    val selectedWeek: StateFlow<Int?> = _selectedWeek.asStateFlow()

    private val _selectedTeams = MutableStateFlow<Set<String>>(emptySet())
    val selectedTeams: StateFlow<Set<String>> = _selectedTeams.asStateFlow()

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _availableWeeks = MutableStateFlow<List<Int>>(emptyList())
    val availableWeeks: StateFlow<List<Int>> = _availableWeeks.asStateFlow()

    private val _availableTeams = MutableStateFlow<List<String>>(emptyList())
    val availableTeams: StateFlow<List<String>> = _availableTeams.asStateFlow()

    init {
        observeGames()
        observeQBs()
        observeWeekstats()
    }

    private fun observeGames() {
        viewModelScope.launch {
            repository.observeGames().collect { games ->
                gamesCache.clear()
                games.forEach { gamesCache[it.id] = it }
                _availableWeeks.value = games.map { it.weekNumber }.distinct().sorted()
                recomputeRows()
            }
        }
    }

    private fun observeQBs() {
        viewModelScope.launch {
            repository.observeQBs().collect { qbs ->
                qbsCache.clear()
                qbs.forEach { qbsCache[it.id] = it }
                _availableTeams.value = qbs.map { it.squadra }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                recomputeRows()
            }
        }
    }

    private fun observeWeekstats() {
        viewModelScope.launch {
            _loading.value = true
            repository.observeWeekStats().collect { docs ->
                lastWeekstatsDocs = docs
                recomputeRows()
                _loading.value = false
            }
        }
    }

    fun setWeekFilter(week: Int?) {
        _selectedWeek.value = week
        recomputeRows()
    }

    fun toggleTeamSelection(team: String) {
        val cur = _selectedTeams.value.toMutableSet()
        if (cur.contains(team)) cur.remove(team) else cur.add(team)
        _selectedTeams.value = cur
        recomputeRows()
    }

    fun resetTeamsSelection() {
        _selectedTeams.value = emptySet()
        recomputeRows()
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        recomputeRows()
    }

    private fun recomputeRows() {
        try {
            val selWeek = _selectedWeek.value
            val selTeams = _selectedTeams.value
            val q = _searchQuery.value.trim().lowercase()

            val filteredDocs = lastWeekstatsDocs.filter { doc ->
                if (selWeek != null) {
                    val gameId = doc.getString("game_id") ?: doc.getString("gameId") ?: doc.getString("game")
                    if (gameId == null) return@filter false
                    val game = gamesCache[gameId]
                    if (game == null || game.weekNumber != selWeek) return@filter false
                }
                true
            }

            val nonZeroScores = mutableMapOf<String, MutableList<Double>>()
            val countEntries = mutableMapOf<String, Int>()

            filteredDocs.forEach { doc ->
                val qbId = doc.getString("qb_id") ?: doc.getString("qbId") ?: doc.getString("qb") ?: return@forEach
                val raw = doc.get("punteggioQB") ?: doc.get("score") // ...altri campi se servono...
                val value = when(raw) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null }

                countEntries[qbId] = (countEntries[qbId] ?: 0) + 1
                if (value != null && value > 0.0) {
                    nonZeroScores.getOrPut(qbId) { mutableListOf() }.add(value)
                }
            }

            val candidateQbIds = (countEntries.keys + nonZeroScores.keys).toSet()

            val calculatedRows = candidateQbIds.mapNotNull { qbId ->
                val qb = qbsCache[qbId] ?: QB(qbId, qbId, "", "")

                if (selTeams.isNotEmpty() && !selTeams.contains(qb.squadra)) return@mapNotNull null
                if (q.isNotEmpty() && !qb.nome.lowercase().contains(q)) return@mapNotNull null

                val gp = nonZeroScores[qbId]?.size ?: 0
                val ptot = nonZeroScores[qbId]?.sum() ?: 0.0
                val ppg = if (gp > 0) ptot / gp else 0.0

                StatRow(qbId, qb.nome, qb.squadra, gp, ptot, ppg)
            }.sortedByDescending { it.ptot }

            _rows.value = calculatedRows
        } catch (e: Exception) {
            Log.e("StatsVM", "Error: ${e.message}")
        }
    }

    fun clearError() { _error.value = null }
}