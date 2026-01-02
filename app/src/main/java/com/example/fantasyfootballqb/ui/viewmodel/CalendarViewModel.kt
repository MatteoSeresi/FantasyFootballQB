package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.repository.FireStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QBStat(
    val qb: QB,
    val punteggio: Double? // null -> not played / not available
)

class CalendarViewModel : ViewModel() {

    private val repository = FireStoreRepository()

    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _weeks = MutableStateFlow<List<Int>>(emptyList())
    val weeks: StateFlow<List<Int>> = _weeks

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Cache locale per mapping veloce ID -> QB
    private val qbsCache: MutableMap<String, QB> = mutableMapOf()

    init {
        observeGames()
        observeQBs()
    }

    private fun observeGames() {
        viewModelScope.launch {
            repository.observeGames().collect { games ->
                val map = games.groupBy { it.weekNumber }.toSortedMap()
                _gamesByWeek.value = map
                _weeks.value = map.keys.toList()
            }
        }
    }

    private fun observeQBs() {
        viewModelScope.launch {
            repository.observeQBs().collect { qbs ->
                qbsCache.clear()
                qbs.forEach { qbsCache[it.id] = it }
            }
        }
    }

    suspend fun getStatsForGame(gameId: String, partitaGiocata: Boolean): List<QBStat>? {
        if (!partitaGiocata) return null

        _loading.value = true
        _error.value = null

        try {
            // Usa il repository per scaricare i dati raw.
            // Nota: getWeekStatsForGame nel repo filtra già per "game_id"
            var docs = repository.getWeekStatsForGame(gameId)

            // Fallback: se vuoto, potrebbe essere salvato con field diversi o ref.
            // Scarichiamo tutto solo se strettamente necessario (come da tua logica originale)
            if (docs.isEmpty()) {
                val allDocs = repository.getAllWeekStats()
                docs = allDocs.filter { d ->
                    val v1 = d.getString("game_id")
                    val v2 = d.getString("gameId")
                    val v3 = d.getString("game")
                    // Controllo reference semplificato (string check)
                    val refPath = d.getDocumentReference("game_id")?.path

                    listOf(v1, v2, v3).any { it == gameId } || (refPath != null && refPath.endsWith(gameId))
                }
            }

            if (docs.isEmpty()) {
                _loading.value = false
                return emptyList()
            }

            val stats = docs.mapNotNull { doc ->
                mapDocToQBStatOrNull(doc.data ?: return@mapNotNull null, doc.id)
            }

            _loading.value = false
            return stats

        } catch (e: Exception) {
            _loading.value = false
            _error.value = e.message
            Log.e("CalendarVM", "getStatsForGame error: ${e.message}", e)
            return emptyList()
        }
    }

    private suspend fun mapDocToQBStatOrNull(data: Map<String, Any?>, docId: String): QBStat? {
        try {
            val qbId = (data["qb_id"] as? String)
                ?: (data["qbId"] as? String)
                ?: (data["qb"] as? String)
                ?: return null

            // Logica parsing punteggi (preservata come richiesto)
            val raw = data["punteggioQB"] ?: data["punteggio_qb"] ?: data["score"] ?: data["punteggio"] ?: data["points"]
            val punteggio: Double? = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }

            val finalScore = if (punteggio != null && punteggio == 0.0) null else punteggio

            // Recupera QB da cache o Repo se manca
            val qb = qbsCache[qbId] ?: run {
                val fetched = repository.getQB(qbId)
                if (fetched != null) {
                    qbsCache[qbId] = fetched
                    fetched
                } else {
                    QB(id = qbId, nome = qbId, squadra = "", stato = "unknown").also { qbsCache[qbId] = it }
                }
            }

            return QBStat(qb = qb, punteggio = finalScore)
        } catch (e: Exception) {
            Log.w("CalendarVM", "Mapping error $docId: ${e.message}")
            return null
        }
    }

    fun clearError() { _error.value = null }
}