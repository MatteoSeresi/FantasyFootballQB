package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StatRow(
    val qbId: String,
    val nome: String,
    val squadra: String,
    val gp: Int,
    val ptot: Double,
    val ppg: Double
)

class StatsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // rows già filtrate (quelle mostrate in UI)
    private val _rows = MutableStateFlow<List<StatRow>>(emptyList())
    val rows: StateFlow<List<StatRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // cache
    private val qbsCache: MutableMap<String, QB> = mutableMapOf()
    private val gamesCache: MutableMap<String, Game> = mutableMapOf()

    // ultimi documenti weekstats letti (usati per ricomputare con filtri)
    private var lastWeekstatsDocs: List<DocumentSnapshot> = emptyList()

    // filtri
    private val _selectedWeek = MutableStateFlow<Int?>(null) // null => tutte le weeks
    val selectedWeek: StateFlow<Int?> = _selectedWeek.asStateFlow()

    // ora multi-team: set di sigle selezionate (vuoto => tutte)
    private val _selectedTeams = MutableStateFlow<Set<String>>(emptySet())
    val selectedTeams: StateFlow<Set<String>> = _selectedTeams.asStateFlow()

    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // liste utili per la UI (weeks e teams disponibili)
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
        db.collection("games")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    Log.w("StatsVM", "observeGames error: ${error.message}")
                    _error.value = error.message
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { d ->
                    try {
                        Game(
                            id = d.id,
                            weekNumber = (d.getLong("weekNumber") ?: 0L).toInt(),
                            squadraCasa = d.getString("squadraCasa") ?: "",
                            squadraOspite = d.getString("squadraOspite") ?: "",
                            partitaGiocata = d.getBoolean("partitaGiocata") ?: false,
                            risultato = d.getString("risultato")
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                gamesCache.clear()
                list.forEach { gamesCache[it.id] = it }

                // aggiorna availableWeeks
                val weeks = list.map { it.weekNumber }.distinct().sorted()
                _availableWeeks.value = weeks

                // ricomputa con i dati correnti
                recomputeRows()
            }
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    Log.w("StatsVM", "observeQBs error: ${error.message}")
                    _error.value = error.message
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { d ->
                    try {
                        QB(
                            id = d.id,
                            nome = d.getString("nome") ?: "",
                            squadra = d.getString("squadra") ?: "",
                            stato = d.getString("stato") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                qbsCache.clear()
                list.forEach { qbsCache[it.id] = it }

                // aggiorna lista squadre disponibile (ordina alfabeticamente, uniche)
                val teams = list.map { it.squadra }.filter { it.isNotBlank() }.distinct().sorted()
                _availableTeams.value = teams

                recomputeRows()
            }
    }

    private fun observeWeekstats() {
        db.collection("weekstats")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    Log.w("StatsVM", "observeWeekstats error: ${error.message}")
                    _error.value = error.message
                    return@addSnapshotListener
                }
                _loading.value = true
                val docs = snaps?.documents ?: emptyList()
                lastWeekstatsDocs = docs
                recomputeRows()
                _loading.value = false
            }
    }

    // setter filtri (UI li chiama)
    fun setWeekFilter(week: Int?) {
        _selectedWeek.value = week
        recomputeRows()
    }

    // toggle su singola squadra (multi-select)
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

    // funzione che applica i filtri e calcola GP/PTOT/PPG
    private fun recomputeRows() {
        try {
            val selWeek = _selectedWeek.value
            val selTeams = _selectedTeams.value // set (vuoto => tutti)
            val q = _searchQuery.value.trim().lowercase()

            // 1) filtra i weekstats docs in base a week (se selezionata)
            val filteredDocs = lastWeekstatsDocs.filter { doc ->
                // filtro per weekNumber -> serve game_id -> lookup in gamesCache
                if (selWeek != null) {
                    val gameId = doc.getString("game_id") ?: doc.getString("gameId") ?: doc.getString("game")
                    if (gameId == null) return@filter false
                    val game = gamesCache[gameId]
                    if (game == null) return@filter false
                    if (game.weekNumber != selWeek) return@filter false
                }
                true
            }

            // 2) aggrega per qbId: vogliamo contare GP come numero di doc (presenze),
            // e PTOT come somma dei punteggi > 0 (gli 0 NON si sommano ma contano in GP).
            val countEntries = mutableMapOf<String, Int>()
            val nonZeroScores = mutableMapOf<String, MutableList<Double>>()

            filteredDocs.forEach { doc ->
                val qbId = doc.getString("qb_id") ?: doc.getString("qbId") ?: doc.getString("qb") ?: return@forEach

                val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("score") ?: doc.get("punteggio") ?: doc.get("points")
                val value: Double? = when (raw) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    else -> null
                }

                // GP incrementa se c'è il documento
                countEntries[qbId] = (countEntries[qbId] ?: 0) + 1

                // PTOT considera solo valori > 0
                if (value != null && value > 0.0) {
                    val list = nonZeroScores.getOrPut(qbId) { mutableListOf() }
                    list.add(value)
                }
            }

            // 3) adesso applica il filtro per squadra (multi) e per ricerca nome.
            // Consideriamo soltanto i qb presenti in countEntries (GP>0)
            val candidateQbIds = countEntries.keys

            val rows = candidateQbIds.mapNotNull { qbId ->
                val qb = qbsCache[qbId] ?: QB(id = qbId, nome = qbId, squadra = "", stato = "")

                // filtro per squadra (multi): se selTeams vuoto => ok, altrimenti qb.squadra deve essere in set
                if (selTeams.isNotEmpty()) {
                    if (qb.squadra.isBlank() || !selTeams.contains(qb.squadra)) return@mapNotNull null
                }

                // filtro ricerca nome
                if (q.isNotEmpty()) {
                    val nomeLower = qb.nome.lowercase()
                    if (!nomeLower.contains(q)) return@mapNotNull null
                }

                val gp = countEntries[qbId] ?: 0
                val ptot = (nonZeroScores[qbId]?.sum() ?: 0.0)
                val ppg = if (gp > 0) ptot / gp else 0.0

                StatRow(qbId = qbId, nome = qb.nome, squadra = qb.squadra, gp = gp, ptot = ptot, ppg = ppg)
            }.sortedByDescending { it.ptot }

            _rows.value = rows
        } catch (e: Exception) {
            Log.e("StatsVM", "recomputeRows error: ${e.message}")
        }
    }

    fun clearError() { _error.value = null }
}
