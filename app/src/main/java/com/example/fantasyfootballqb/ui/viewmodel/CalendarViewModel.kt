package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.repository.FireStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Classe di supporto richiesta dalla tua UI
data class QBStat(
    val qb: QB,
    val punteggio: Double?
)

class CalendarViewModel : ViewModel() {

    private val repository = FireStoreRepository()

    // La tua UI si aspetta una Mappa: Week -> Lista Partite
    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    // La tua UI si aspetta una lista di numeri interi per le settimane
    private val _weeks = MutableStateFlow<List<Int>>(emptyList())
    val weeks: StateFlow<List<Int>> = _weeks

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        observeGames()
    }

    private fun observeGames() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Usiamo il repository pulito
                repository.observeGames().collect { games ->
                    // Raggruppiamo le partite per week come vuole la tua UI
                    val map = games.groupBy { it.weekNumber }.toSortedMap()
                    _gamesByWeek.value = map
                    _weeks.value = map.keys.toList()
                    _loading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _loading.value = false
            }
        }
    }

    // Funzione chiamata al click sulla lente d'ingrandimento
    suspend fun getStatsForGame(gameId: String, played: Boolean): List<QBStat> {
        if (!played) return emptyList()

        return try {
            // 1. Scarichiamo le stats pulite (WeekStats) dal repository
            val weekStats = repository.getWeekStatsForGame(gameId)

            val result = mutableListOf<QBStat>()

            // 2. Per ogni stat, dobbiamo recuperare i dettagli del QB (nome, squadra)
            for (stat in weekStats) {
                val qb = repository.getQB(stat.qbId)
                if (qb != null) {
                    result.add(QBStat(qb, stat.punteggio))
                }
            }
            // Ordiniamo per squadra per la visualizzazione nel dialog
            result.sortedBy { it.qb.squadra }
        } catch (e: Exception) {
            _error.value = "Errore caricamento dettagli: ${e.message}"
            emptyList()
        }
    }

    fun clearError() {
        _error.value = null
    }
}