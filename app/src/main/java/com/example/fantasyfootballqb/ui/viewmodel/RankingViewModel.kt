package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.repository.FireStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RankingEntry(
    val uid: String,
    val username: String,
    val nomeTeam: String?,
    val total: Double
)

data class UserDetail(
    val uid: String,
    val username: String,
    val nomeTeam: String?,
    val favoriteQBName: String?
)

class RankingViewModel : ViewModel() {

    private val repository = FireStoreRepository()

    private val _ranking = MutableStateFlow<List<RankingEntry>>(emptyList())
    val ranking: StateFlow<List<RankingEntry>> = _ranking

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _selectedUserDetail = MutableStateFlow<UserDetail?>(null)
    val selectedUserDetail: StateFlow<UserDetail?> = _selectedUserDetail

    private val _selectedUserLoading = MutableStateFlow(false)
    val selectedUserLoading: StateFlow<Boolean> = _selectedUserLoading

    private val _selectedUserError = MutableStateFlow<String?>(null)
    val selectedUserError: StateFlow<String?> = _selectedUserError

    init {
        loadRanking()
    }

    fun reload() { loadRanking() }

    private fun loadRanking() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // 1. Prendi tutti gli utenti
                val users = repository.getAllUsers()
                val entries = mutableListOf<RankingEntry>()

                // 2. Cache dei Game ID per ogni Week (Week -> Lista ID Partite)
                val gamesCache = mutableMapOf<Int, List<String>>()

                // 3. Scarichiamo TUTTE le weekstats una volta sola (Ottimizzazione)
                // Ora otteniamo oggetti WeekStats puliti, non documenti grezzi!
                val allWeekStats = repository.getAllWeekStats()

                for (u in users) {
                    if (u.isAdmin) continue

                    // Prendi formazioni raw (qui manteniamo raw perché non abbiamo creato un Model Formation)
                    val formations = repository.getUserFormations(u.uid)
                    var totalForUser = 0.0

                    for (f in formations) {
                        // A) Tentativo lettura diretta totale salvato nella formazione
                        val possibleKeys = listOf("punteggioQbs", "punteggioQb", "totalWeekScore", "totalScore", "punteggio")
                        var valueFound: Double? = null

                        for (k in possibleKeys) {
                            val raw = f.get(k)
                            val num = when (raw) {
                                is Number -> raw.toDouble()
                                is String -> raw.toDoubleOrNull()
                                else -> null
                            }
                            if (num != null) { valueFound = num; break }
                        }

                        // B) Se non c'è il totale salvato, lo ricalcoliamo usando WeekStats
                        if (valueFound == null) {
                            val weekNum = (f.getLong("weekNumber") ?: f.getLong("week") ?: 0L).toInt()
                            val qbIds = (f.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                            if (qbIds.isNotEmpty()) {
                                // Recupera ID partite per quella week
                                val gameIds = gamesCache.getOrPut(weekNum) {
                                    repository.getGamesForWeek(weekNum).map { it.id }
                                }

                                var sum = 0.0
                                for (qbId in qbIds) {
                                    // FILTRAGGIO PULITO: Usiamo gli oggetti, non le stringhe map
                                    // Cerchiamo nelle statistiche scaricate quella che:
                                    // 1. Appartiene a questo QB
                                    // 2. Appartiene a una delle partite di questa settimana
                                    val stat = allWeekStats.find {
                                        it.qbId == qbId && gameIds.contains(it.gameId)
                                    }

                                    if (stat != null) {
                                        sum += stat.punteggio
                                    }
                                }
                                valueFound = sum
                            }
                        }
                        totalForUser += (valueFound ?: 0.0)
                    }
                    entries.add(RankingEntry(u.uid, u.username, u.nomeTeam, totalForUser))
                }
                _ranking.value = entries.sortedByDescending { it.total }

            } catch (e: Exception) {
                Log.e("RankingVM", "Error: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadUserDetail(uid: String, username: String, nomeTeam: String?) {
        viewModelScope.launch {
            _selectedUserLoading.value = true
            _selectedUserDetail.value = null
            try {
                val formations = repository.getUserFormations(uid)
                val counts = mutableMapOf<String, Int>()

                for (f in formations) {
                    val qbIds = (f.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    qbIds.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
                }

                if (counts.isEmpty()) {
                    _selectedUserDetail.value = UserDetail(uid, username, nomeTeam, null)
                    return@launch
                }

                val maxCount = counts.values.maxOrNull() ?: 0
                val topQbs = counts.filterValues { it == maxCount }.keys.toList()

                val favName = if (maxCount > 0 && topQbs.size == 1) {
                    val qbId = topQbs.first()
                    val qb = repository.getQB(qbId)
                    qb?.nome ?: qbId
                } else null

                _selectedUserDetail.value = UserDetail(uid, username, nomeTeam, favName)

            } catch (e: Exception) {
                _selectedUserError.value = e.message
            } finally {
                _selectedUserLoading.value = false
            }
        }
    }

    fun clearSelectedUserDetail() { _selectedUserDetail.value = null }
    fun clearError() { _error.value = null }
}