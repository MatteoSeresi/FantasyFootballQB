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
                val users = repository.getAllUsers()
                val entries = mutableListOf<RankingEntry>()
                val allWeekStats = repository.getAllWeekStats()

                // Cache ID partite per week
                val gamesCache = mutableMapOf<Int, List<String>>()

                for (u in users) {
                    if (u.isAdmin) continue

                    // --- QUI USIAMO IL NUOVO MODEL ---
                    // Restituisce List<Formation> pulita
                    val formations = repository.getUserFormations(u.uid)
                    var totalForUser = 0.0

                    for (formation in formations) {
                        // 1. Se abbiamo già il totale salvato, usalo (il Mapper lo ha già trovato)
                        if (formation.totalScore != null) {
                            totalForUser += formation.totalScore
                        } else {
                            // 2. Altrimenti calcola al volo usando WeekStats
                            if (formation.qbIds.isNotEmpty()) {
                                val weekNum = formation.weekNumber
                                val gameIds = gamesCache.getOrPut(weekNum) {
                                    repository.getGamesForWeek(weekNum).map { it.id }
                                }

                                var sum = 0.0
                                for (qbId in formation.qbIds) {
                                    val stat = allWeekStats.find {
                                        it.qbId == qbId && gameIds.contains(it.gameId)
                                    }
                                    if (stat != null) sum += stat.punteggio
                                }
                                totalForUser += sum
                            }
                        }
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
                // Anche qui usiamo List<Formation>
                val formations = repository.getUserFormations(uid)
                val counts = mutableMapOf<String, Int>()

                for (f in formations) {
                    f.qbIds.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
                }

                if (counts.isEmpty()) {
                    _selectedUserDetail.value = UserDetail(uid, username, nomeTeam, null)
                    return@launch
                }

                val maxCount = counts.values.maxOrNull() ?: 0
                val topQbs = counts.filterValues { it == maxCount }.keys.toList()

                val favName = if (maxCount > 0 && topQbs.size == 1) {
                    val qbId = topQbs.first()
                    repository.getQB(qbId)?.nome ?: qbId
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