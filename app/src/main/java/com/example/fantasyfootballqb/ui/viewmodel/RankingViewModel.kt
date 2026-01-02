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
                // 1. Prendi tutti gli utenti dal repo
                val users = repository.getAllUsers()
                val entries = mutableListOf<RankingEntry>()

                // Cache games (week -> list gameIds) per ottimizzare fallback
                val gamesCache = mutableMapOf<Int, List<String>>()

                for (u in users) {
                    if (u.isAdmin) continue

                    // 2. Prendi formazioni raw per l'utente (per parsing custom)
                    val formations = repository.getUserFormations(u.uid)
                    var totalForUser = 0.0

                    for (f in formations) {
                        // A) Tentativo lettura diretta campo totale
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

                        // B) Tentativo somma mappa 'punteggi'
                        if (valueFound == null) {
                            val mapObj = f.get("punteggi")
                            if (mapObj is Map<*, *>) {
                                var sum = 0.0
                                mapObj.forEach { (_, v) ->
                                    val n = when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null }
                                    if (n != null) sum += n
                                }
                                valueFound = sum
                            }
                        }

                        // C) Fallback calcolo da weekstats (Lento ma robusto)
                        if (valueFound == null) {
                            val weekNum = (f.getLong("weekNumber") ?: f.getLong("week") ?: 0L).toInt()
                            val qbIds = (f.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                            if (qbIds.isNotEmpty()) {
                                // Cache gameIDs della week se serve
                                val gameIds = gamesCache.getOrPut(weekNum) {
                                    repository.getGamesForWeek(weekNum).map { it.id }
                                }

                                var sum = 0.0
                                for (qbId in qbIds) {
                                    // Fetch stats raw per QB
                                    val qbStats = repository.getWeekStatsForQb(qbId)
                                    for (doc in qbStats) {
                                        val gid = doc.getString("game_id") ?: doc.getString("gameId")
                                        if (gid != null && gameIds.contains(gid)) {
                                            val raw = doc.get("punteggioQB") ?: doc.get("score") // ecc...
                                            val vn = when(raw) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null }
                                            if (vn != null) { sum += vn; break }
                                        }
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