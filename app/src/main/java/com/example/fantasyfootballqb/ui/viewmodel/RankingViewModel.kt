package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RankingEntry(
    val uid: String,
    val username: String,
    val nomeTeam: String?,
    val total: Double
)

class RankingViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _ranking = MutableStateFlow<List<RankingEntry>>(emptyList())
    val ranking: StateFlow<List<RankingEntry>> = _ranking

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadRanking()
    }

    fun reload() {
        loadRanking()
    }

    private fun loadRanking() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // prendi tutti gli utenti
                val usersSnap = db.collection("users").get().await()
                val usersDocs = usersSnap.documents

                val entries = mutableListOf<RankingEntry>()

                // cache per games-by-week (week -> list of gameIds)
                val gamesCache = mutableMapOf<Int, List<String>>()

                for (u in usersDocs) {
                    // Escludi amministratori
                    val isAdmin = u.getBoolean("isAdmin") == true
                    if (isAdmin) continue

                    val uid = u.id
                    val username = u.getString("username") ?: u.getString("email") ?: uid
                    val nomeTeam = u.getString("nomeTeam")

                    // prendi tutte le formation di questo utente
                    val formsSnap = db.collection("users").document(uid).collection("formations").get().await()
                    var totalForUser = 0.0

                    for (f in formsSnap.documents) {
                        // 1) prova a leggere un campo totale pre-calcolato nella formation
                        val possibleKeys = listOf(
                            "punteggioQbs",
                            "punteggioQb",
                            "totalWeekScore",
                            "totalScore",
                            "punteggioQbsTotal",
                            "punteggio" // fallback
                        )

                        var valueFound: Double? = null
                        for (k in possibleKeys) {
                            val raw = f.get(k)
                            if (raw != null) {
                                val num = when (raw) {
                                    is Number -> raw.toDouble()
                                    is String -> raw.toDoubleOrNull()
                                    else -> null
                                }
                                if (num != null) {
                                    valueFound = num
                                    break
                                }
                            }
                        }

                        // 2) se non trovato, prova a sommare il campo 'punteggi' (map qbId -> score)
                        if (valueFound == null) {
                            val mapObj = f.get("punteggi")
                            if (mapObj is Map<*, *>) {
                                var sum = 0.0
                                mapObj.forEach { (_, v) ->
                                    val n = when (v) {
                                        is Number -> v.toDouble()
                                        is String -> v.toDoubleOrNull()
                                        else -> null
                                    }
                                    if (n != null) {
                                        // includiamo anche 0 (vale 0), non filtriamo: il totale viene sommato
                                        sum += n
                                    }
                                }
                                valueFound = sum
                            }
                        }

                        // 3) fallback robusto: se ancora nulla, calcola dal weekstats
                        if (valueFound == null) {
                            // serve la weekNumber e i qbIds
                            val weekNum = (f.getLong("weekNumber") ?: f.getLong("week") ?: 0L).toInt()
                            val qbIdsRaw = f.get("qbIds") as? List<*>
                            val qbIds = qbIdsRaw?.mapNotNull { it as? String } ?: emptyList()

                            if (qbIds.isEmpty()) {
                                // niente da sommare
                                valueFound = 0.0
                            } else {
                                // prendi i games della week (cache)
                                val gameIds = gamesCache.getOrPut(weekNum) {
                                    try {
                                        val snaps = db.collection("games").whereEqualTo("weekNumber", weekNum).get().await()
                                        snaps.documents.map { it.id }
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }

                                var sum = 0.0
                                // per ogni qb cerco weekstats e filtro per gameIds della week
                                for (qbId in qbIds) {
                                    try {
                                        val snap = db.collection("weekstats")
                                            .whereEqualTo("qb_id", qbId)
                                            .get()
                                            .await()

                                        // cerca doc che ha game_id che appartiene a gameIds
                                        for (doc in snap.documents) {
                                            val gid = doc.getString("game_id") ?: doc.getString("gameId") ?: (doc.get("game") as? String)
                                            if (gid != null && gameIds.contains(gid)) {
                                                val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("score") ?: doc.get("punteggio")
                                                val valnum: Double? = when (raw) {
                                                    is Number -> raw.toDouble()
                                                    is String -> raw.toDoubleOrNull()
                                                    else -> null
                                                }
                                                if (valnum != null) {
                                                    sum += valnum
                                                    break // se trovato un weekstat valido per questo qb nella week, passo al prossimo qb
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("RankingVM", "Errore fetch weekstats qb=$qbId: ${e.message}")
                                    }
                                }
                                valueFound = sum
                            }
                        }

                        // somma al totale utente (valueFound non sarà null a questo punto)
                        totalForUser += (valueFound ?: 0.0)
                    } // end formations loop

                    entries.add(RankingEntry(uid = uid, username = username, nomeTeam = nomeTeam, total = totalForUser))
                } // end users loop

                // ordina desc per total
                val sorted = entries.sortedByDescending { it.total }
                _ranking.value = sorted
            } catch (e: Exception) {
                Log.e("RankingVM", "loadRanking: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
