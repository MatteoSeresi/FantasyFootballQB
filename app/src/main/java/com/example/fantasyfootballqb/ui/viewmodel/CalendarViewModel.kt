package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class QBStat(
    val qb: QB,
    val punteggio: Double? // null -> not played / not available
)

class CalendarViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _weeks = MutableStateFlow<List<Int>>(emptyList())
    val weeks: StateFlow<List<Int>> = _weeks

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // cache dei QBs (id -> QB) per non rifare fetch ogni volta
    private val qbsCache: MutableMap<String, QB> = mutableMapOf()

    init {
        observeGames()
        observeQBs()
    }

    private fun observeGames() {
        db.collection("games")
            .orderBy("weekNumber")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _error.value = error.message
                    return@addSnapshotListener
                }
                val games = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        val weekNum = (doc.getLong("weekNumber") ?: 0L).toInt()
                        Game(
                            id = doc.id,
                            weekNumber = weekNum,
                            squadraCasa = doc.getString("squadraCasa") ?: "",
                            squadraOspite = doc.getString("squadraOspite") ?: "",
                            partitaGiocata = doc.getBoolean("partitaGiocata") ?: false,
                            risultato = doc.getString("risultato")
                        )
                    } catch (e: Exception) {
                        Log.w("CalendarVM", "parse game doc ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()

                val map = games.groupBy { it.weekNumber }.toSortedMap()
                _gamesByWeek.value = map
                _weeks.value = map.keys.toList()
            }
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("CalendarVM", "observeQBs error: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        QB(
                            id = doc.id,
                            nome = doc.getString("nome") ?: "",
                            squadra = doc.getString("squadra") ?: "",
                            stato = doc.getString("stato") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.w("CalendarVM", "parse qb ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                qbsCache.clear()
                list.forEach { qbsCache[it.id] = it }
            }
    }

    /**
     * Recupera le statistics per una partita (gameId) dalla collection root /weekstats.
     * Ritorna una lista di QBStat oppure null se partita non giocata.
     *
     * - supporta campi stringa game_id / gameId / game
     * - fallback client-side include controllo DocumentReference
     * - punteggio == 0 -> considered "not played" (ritorna null)
     */
    suspend fun getStatsForGame(gameId: String, partitaGiocata: Boolean): List<QBStat>? {
        if (!partitaGiocata) return null

        _loading.value = true
        _error.value = null

        try {
            var snapshot = db.collection("weekstats")
                .whereEqualTo("game_id", gameId)
                .get()
                .await()

            if (snapshot.isEmpty) {
                snapshot = db.collection("weekstats")
                    .whereEqualTo("gameId", gameId)
                    .get()
                    .await()
            }

            if (snapshot.isEmpty) {
                snapshot = db.collection("weekstats")
                    .whereEqualTo("game", gameId)
                    .get()
                    .await()
            }

            if (snapshot.isEmpty) {
                // fallback: scarica tutti e filtra lato client (controlla anche DocumentReference)
                val all = db.collection("weekstats").get().await()
                val filtered = all.documents.filter { d ->
                    val v1 = d.getString("game_id")
                    val v2 = d.getString("gameId")
                    val v3 = d.getString("game")
                    val ref = d.get("game_id") as? DocumentReference
                    val matchesRef = ref?.id == gameId || ref?.path?.endsWith("/$gameId") == true
                    listOf(v1, v2, v3).any { it != null && it == gameId } || matchesRef
                }

                if (filtered.isEmpty()) {
                    _loading.value = false
                    return emptyList()
                }

                val stats = filtered.mapNotNull { doc ->
                    mapDocToQBStatOrNull(doc.data ?: return@mapNotNull null, doc.id)
                }
                _loading.value = false
                return stats
            }

            // mappa i documenti ottenuti dalla query
            val stats = snapshot.documents.mapNotNull { doc ->
                mapDocToQBStatOrNull(doc.data ?: return@mapNotNull null, doc.id)
            }

            _loading.value = false
            return stats
        } catch (e: Exception) {
            _loading.value = false
            _error.value = e.message
            Log.e("CalendarVM", "getStatsForGame failed for gameId=$gameId: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Mappa i campi del documento weekstats in QBStat.
     * Se il QB non esiste, crea un placeholder (non scarta il record).
     */
    private suspend fun mapDocToQBStatOrNull(data: Map<String, Any?>, docId: String): QBStat? {
        try {
            val qbId = (data["qb_id"] as? String)
                ?: (data["qbId"] as? String)
                ?: (data["qb"] as? String)
                ?: return null

            val raw = data["punteggioQB"] ?: data["punteggio_qb"] ?: data["score"] ?: data["punteggio"] ?: data["points"]
            val punteggio: Double? = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }

            // 0.0 => considerato "not played"
            val finalScore = if (punteggio != null && punteggio == 0.0) null else punteggio

            val qb = qbsCache[qbId] ?: run {
                val qbDoc = db.collection("qbs").document(qbId).get().await()
                if (qbDoc.exists()) {
                    QB(
                        id = qbDoc.id,
                        nome = qbDoc.getString("nome") ?: qbId,
                        squadra = qbDoc.getString("squadra") ?: "",
                        stato = qbDoc.getString("stato") ?: ""
                    ).also { qbsCache[qbId] = it }
                } else {
                    // placeholder
                    QB(id = qbId, nome = qbId, squadra = "", stato = "unknown").also { qbsCache[qbId] = it }
                }
            }

            return QBStat(qb = qb, punteggio = finalScore)
        } catch (e: Exception) {
            Log.w("CalendarVM", "Mapping error doc $docId: ${e.message}", e)
            return null
        }
    }

    fun clearError() {
        _error.value = null
    }
}
