package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AdminUser(
    val uid: String,
    val email: String,
    val username: String,
    val nomeTeam: String?,
    val isAdmin: Boolean = false
)

/**
 * Rappresenta la formation visualizzata nella lista admin per una week:
 * - qbIds: lista (potrebbe essere vuota)
 * - totalWeekScore: punteggio calcolato per quella formation in quella week (somma punteggi > 0)
 */
data class UserFormationRow(
    val uid: String,
    val username: String,
    val nomeTeam: String?,
    val qbIds: List<String>,
    val totalWeekScore: Double
)

class AdminViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _users = MutableStateFlow<List<AdminUser>>(emptyList())
    val users: StateFlow<List<AdminUser>> = _users

    private val _availableWeeks = MutableStateFlow<List<Int>>(emptyList())
    val availableWeeks: StateFlow<List<Int>> = _availableWeeks

    private val _gamesByWeek = MutableStateFlow<Map<Int, List<Game>>>(emptyMap())
    val gamesByWeek: StateFlow<Map<Int, List<Game>>> = _gamesByWeek

    private val _qbs = MutableStateFlow<List<QB>>(emptyList())
    val qbs: StateFlow<List<QB>> = _qbs

    // per la UI di modifica formation
    private val _userFormations = MutableStateFlow<List<UserFormationRow>>(emptyList())
    val userFormations: StateFlow<List<UserFormationRow>> = _userFormations

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    private val _selectedWeek = MutableStateFlow<Int?>(null)
    val selectedWeek: StateFlow<Int?> = _selectedWeek

    // stato week calcolata (true se tutte le games della week hanno partitaCalcolata == true)
    private val _weekCalculated = MutableStateFlow(false)
    val weekCalculated: StateFlow<Boolean> = _weekCalculated

    private var weekGamesListener: ListenerRegistration? = null

    init {
        observeUsers()
        observeGames()
        observeQBs()
    }

    private fun observeUsers() {
        db.collection("users")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _error.value = err.message
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { d ->
                    try {
                        AdminUser(
                            uid = d.id,
                            email = d.getString("email") ?: "",
                            username = d.getString("username") ?: d.getString("email") ?: d.id,
                            nomeTeam = d.getString("nomeTeam"),
                            isAdmin = d.getBoolean("isAdmin") == true
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                _users.value = list
            }
    }

    private fun observeGames() {
        db.collection("games")
            .orderBy("weekNumber")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _error.value = err.message
                    return@addSnapshotListener
                }
                val games = snaps?.documents?.mapNotNull { d ->
                    try {
                        val week = (d.getLong("weekNumber") ?: 0L).toInt()
                        Game(
                            id = d.id,
                            weekNumber = week,
                            squadraCasa = d.getString("squadraCasa") ?: "",
                            squadraOspite = d.getString("squadraOspite") ?: "",
                            partitaGiocata = d.getBoolean("partitaGiocata") ?: false,
                            risultato = d.getString("risultato"),
                            partitaCalcolata = d.getBoolean("partitaCalcolata") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                val map = games.groupBy { it.weekNumber }.toSortedMap()
                _gamesByWeek.value = map
                _availableWeeks.value = map.keys.toList()
                if (_selectedWeek.value == null && _availableWeeks.value.isNotEmpty()) {
                    _selectedWeek.value = _availableWeeks.value.first()
                }
            }
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
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
                _qbs.value = list
            }
    }

    fun setSelectedWeek(week: Int?) {
        _selectedWeek.value = week
    }

    fun updateUserData(uid: String, email: String, username: String, nomeTeam: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val map = mapOf(
                    "email" to email,
                    "username" to username,
                    "nomeTeam" to nomeTeam
                )
                db.collection("users").document(uid).set(map, SetOptions.merge()).await()
                _success.value = "Dati utente aggiornati"
            } catch (e: Exception) {
                Log.e("AdminVM", "updateUserData: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * updateGame: aggiorna risultato e partitaGiocata.
     * Usato dal dialog "Modifica dati partite".
     */
    fun updateGame(gameId: String, partitaGiocata: Boolean, risultato: String?) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val map: MutableMap<String, Any?> = mutableMapOf("partitaGiocata" to partitaGiocata)
                map["risultato"] = risultato
                db.collection("games").document(gameId).set(map, SetOptions.merge()).await()
                _success.value = "Partita aggiornata"
            } catch (e: Exception) {
                Log.e("AdminVM", "updateGame: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * setQBScore: scrive/aggiorna il punteggio in weekstats per (gameId, qbId).
     * Usato dal dialog "Modifica punteggi QBs".
     */
    fun setQBScore(gameId: String, qbId: String, punteggio: Double) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val coll = db.collection("weekstats")
                val qsnap = coll
                    .whereEqualTo("game_id", gameId)
                    .whereEqualTo("qb_id", qbId)
                    .get()
                    .await()

                if (!qsnap.isEmpty) {
                    val doc = qsnap.documents.first()
                    coll.document(doc.id).set(mapOf("punteggioQB" to punteggio), SetOptions.merge()).await()
                } else {
                    val new = mapOf(
                        "game_id" to gameId,
                        "qb_id" to qbId,
                        "punteggioQB" to punteggio
                    )
                    coll.add(new).await()
                }
                _success.value = "Punteggio QB aggiornato"
            } catch (e: Exception) {
                Log.e("AdminVM", "setQBScore: ${e.message}", e)
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

    /**
     * Observe weekCalculated (true se tutte le games della week hanno partitaCalcolata == true)
     */
    fun observeWeekCalculated(week: Int) {
        weekGamesListener?.remove()
        weekGamesListener = db.collection("games")
            .whereEqualTo("weekNumber", week)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    _weekCalculated.value = false
                    return@addSnapshotListener
                }
                val docs = snaps?.documents ?: emptyList()
                if (docs.isEmpty()) {
                    _weekCalculated.value = false
                } else {
                    val allCalculated = docs.all { it.getBoolean("partitaCalcolata") == true }
                    _weekCalculated.value = allCalculated
                }
            }
    }

    /**
     * Nuova funzione: valida la week (senza modificare DB).
     * Ritorna una lista di messaggi (vuota = tutto ok).
     */
    suspend fun validateWeek(week: Int): List<String> {
        val problems = mutableListOf<String>()
        try {
            val gamesSnap = db.collection("games").whereEqualTo("weekNumber", week).get().await()
            val gamesDocs = gamesSnap.documents
            if (gamesDocs.isEmpty()) {
                problems.add("Nessuna partita per la week $week")
                return problems
            }

            // verifica partite giocate
            val notPlayed = mutableListOf<String>()
            for (g in gamesDocs) {
                val played = g.getBoolean("partitaGiocata") == true
                if (!played) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    notPlayed.add(label)
                }
            }
            if (notPlayed.isNotEmpty()) {
                problems.add("Partite non giocate: ${notPlayed.joinToString(" ; ")}")
            }

            // verifica weekstats per ogni partita (esistenza e punteggi numerici)
            val missingStats = mutableListOf<String>()
            for (g in gamesDocs) {
                val gameId = g.id
                val wsSnap = db.collection("weekstats").whereEqualTo("game_id", gameId).get().await()
                if (wsSnap.isEmpty) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    missingStats.add("$label -> Punteggi giocatori mancatnti")
                    continue
                }

                val invalid = wsSnap.documents.any { doc ->
                    val raw = doc.get("punteggioQB")
                    when (raw) {
                        is Number -> false
                        is String -> raw.toDoubleOrNull() == null
                        else -> true
                    }
                }
                if (invalid) {
                    val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                    missingStats.add("$label -> weekstats senza punteggio valido")
                }
            }
            if (missingStats.isNotEmpty()) {
                problems.addAll(missingStats)
            }

        } catch (e: Exception) {
            Log.e("AdminVM", "validateWeek: ${e.message}", e)
            problems.add("Errore durante la validazione: ${e.message}")
        }
        return problems
    }

    /**
     * calculateWeek: valida e poi setta partitaCalcolata = true via batch
     */
    fun calculateWeek(week: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _success.value = null
            try {
                // 1) prendi tutte le games della week
                val gamesSnap = db.collection("games").whereEqualTo("weekNumber", week).get().await()
                val gamesDocs = gamesSnap.documents
                if (gamesDocs.isEmpty()) {
                    _error.value = "Nessuna partita trovata per la week $week"
                    return@launch
                }

                // 2) verifica che tutte le partite siano giocate
                val notPlayed = mutableListOf<String>()
                for (g in gamesDocs) {
                    val played = g.getBoolean("partitaGiocata") == true
                    if (!played) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        notPlayed.add(label)
                    }
                }
                if (notPlayed.isNotEmpty()) {
                    _error.value = "Non tutte le partite sono giocate:\n" + notPlayed.joinToString("\n")
                    return@launch
                }

                // 3) verifica weekstats per ogni partita: esistenza e punteggio numerico
                val missingStats = mutableListOf<String>()
                for (g in gamesDocs) {
                    val gameId = g.id
                    val wsSnap = db.collection("weekstats").whereEqualTo("game_id", gameId).get().await()
                    if (wsSnap.isEmpty) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        missingStats.add("$label -> nessun weekstats")
                        continue
                    }

                    val invalid = wsSnap.documents.any { doc ->
                        val raw = doc.get("punteggioQB")
                        when (raw) {
                            is Number -> false
                            is String -> raw.toDoubleOrNull() == null
                            else -> true
                        }
                    }
                    if (invalid) {
                        val label = "${g.getString("squadraCasa") ?: "?"} vs ${g.getString("squadraOspite") ?: "?"}"
                        missingStats.add("$label -> weekstats senza punteggio valido")
                    }
                }
                if (missingStats.isNotEmpty()) {
                    _error.value = "Ci sono partite senza punteggi completi:\n" + missingStats.joinToString("\n")
                    return@launch
                }

                // 4) tutto ok -> batch update partitaCalcolata = true per tutte le games della week che non lo sono
                val batch = db.batch()
                var updates = 0
                for (g in gamesDocs) {
                    val already = g.getBoolean("partitaCalcolata") == true
                    if (!already) {
                        batch.update(g.reference, mapOf("partitaCalcolata" to true))
                        updates++
                    }
                }
                if (updates == 0) {
                    _error.value = "La week $week è già stata calcolata."
                } else {
                    batch.commit().await()
                    _success.value = "Week $week marcata come calcolata"
                }
            } catch (e: Exception) {
                Log.e("AdminVM", "calculateWeek: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Carica per la UI (admin) la lista di utenti (escludendo admin) con la formation per la week data
     * e calcola il punteggio totale di quella formation usando i weekstats esistenti.
     */
    fun loadUserFormationsForWeek(week: Int?) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (week == null) {
                    _userFormations.value = emptyList()
                    return@launch
                }

                // get games for week -> serve per filtrare weekstats
                val gamesForWeek = _gamesByWeek.value[week] ?: emptyList()
                val gameIds = gamesForWeek.map { it.id }

                // fetch all weekstats (poi filter client-side)
                val allWeekstatsSnap = db.collection("weekstats").get().await()
                val allWeekstatsDocs = allWeekstatsSnap.documents

                // index weekstats by qbId for quick lookup for this week
                val wsByQb = mutableMapOf<String, MutableList<Double>>()
                for (d in allWeekstatsDocs) {
                    val gId = d.getString("game_id") ?: d.getString("gameId") ?: d.getString("game")
                    if (gId == null || !gameIds.contains(gId)) continue
                    val qbId = d.getString("qb_id") ?: d.getString("qbId") ?: continue
                    val raw = d.get("punteggioQB") ?: d.get("punteggio_qb") ?: d.get("score") ?: d.get("punteggio")
                    val value: Double? = when (raw) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull()
                        else -> null
                    }
                    if (value != null) {
                        val list = wsByQb.getOrPut(qbId) { mutableListOf() }
                        list.add(value)
                    }
                }

                // load users (exclude admin) and compute formation totals
                val usersSnap = db.collection("users").get().await()
                val rows = mutableListOf<UserFormationRow>()
                for (udoc in usersSnap.documents) {
                    val isAdmin = udoc.getBoolean("isAdmin") == true
                    if (isAdmin) continue // skip admins

                    val uid = udoc.id
                    val username = udoc.getString("username") ?: udoc.getString("email") ?: uid
                    val nomeTeam = udoc.getString("nomeTeam")

                    // formation doc for the week
                    val fdoc = db.collection("users").document(uid).collection("formations").document(week.toString()).get().await()
                    val qbIds = (fdoc.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    // totalWeekScore: somma dei punteggi > 0 per i qb schierati (se non ci sono weekstats -> 0)
                    var total = 0.0
                    qbIds.forEach { qid ->
                        val scores = wsByQb[qid]
                        val s = (scores?.filter { it > 0.0 }?.sum() ?: 0.0)
                        total += s
                    }

                    rows.add(UserFormationRow(uid = uid, username = username, nomeTeam = nomeTeam, qbIds = qbIds, totalWeekScore = total))
                }

                _userFormations.value = rows.sortedBy { it.username.lowercase() }
            } catch (e: Exception) {
                Log.e("AdminVM", "loadUserFormationsForWeek: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Aggiorna la formation di un utente per una week.
     * - Scrive qbIds nella formation
     * - Ricava i punteggi attuali dai weekstats (se presenti) e scrive 'punteggi' e 'totalWeekScore' nella formation
     * - Ricalcola users/{uid}.totalScore (somma di tutti totalWeekScore nelle formations dell'utente)
     *
     * Nota: viene permessa la modifica anche se la week è stata calcolata; NON tocchiamo partitaCalcolata.
     */
    fun updateUserFormation(uid: String, week: Int, newQbIds: List<String>) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (newQbIds.size != 3) {
                    _error.value = "Devono essere esatti 3 QB."
                    return@launch
                }
                if (newQbIds.toSet().size != 3) {
                    _error.value = "I 3 QB devono essere distinti."
                    return@launch
                }

                // get games for week
                val gamesForWeek = _gamesByWeek.value[week] ?: emptyList()
                val gameIds = gamesForWeek.map { it.id }

                // fetch all weekstats and filter by gameIds (client-side)
                val allWeekstatsSnap = db.collection("weekstats").get().await()
                val allWeekstatsDocs = allWeekstatsSnap.documents

                // map qbId -> sum of scores > 0 for that week
                val qbScoreMap = mutableMapOf<String, Double?>()

                for (qbId in newQbIds) {
                    // find documents for qbId within gameIds
                    val matched = allWeekstatsDocs.filter { d ->
                        val gId = d.getString("game_id") ?: d.getString("gameId") ?: d.getString("game")
                        if (gId == null) return@filter false
                        if (!gameIds.contains(gId)) return@filter false
                        val q = d.getString("qb_id") ?: d.getString("qbId") ?: d.getString("qb")
                        q != null && q == qbId
                    }

                    if (matched.isEmpty()) {
                        qbScoreMap[qbId] = null
                    } else {
                        // sum only values > 0
                        val values = matched.mapNotNull { doc ->
                            val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("score") ?: doc.get("punteggio")
                            when (raw) {
                                is Number -> raw.toDouble()
                                is String -> raw.toDoubleOrNull()
                                else -> null
                            }
                        }.filter { it > 0.0 }
                        qbScoreMap[qbId] = if (values.isEmpty()) 0.0 else values.sum()
                    }
                }

                // compute totalWeekScore = sum of qbScoreMap values > 0
                val totalWeekScore = qbScoreMap.values.mapNotNull { it?.takeIf { v -> v > 0.0 } }.sum()

                // write formation data (merge)
                val formationData: MutableMap<String, Any?> = mutableMapOf(
                    "qbIds" to newQbIds,
                    "weekNumber" to week,
                    "punteggi" to qbScoreMap,       // map qbId -> Double? (null if not present)
                    "totalWeekScore" to totalWeekScore
                )

                val formRef = db.collection("users").document(uid).collection("formations").document(week.toString())
                formRef.set(formationData, SetOptions.merge()).await()

                // recompute aggregated totalScore for user (sum of all totalWeekScore in their formations)
                val formsSnap = db.collection("users").document(uid).collection("formations").get().await()
                var userTotal = 0.0
                for (f in formsSnap.documents) {
                    val raw = f.get("totalWeekScore")
                    val v = when (raw) {
                        is Number -> raw.toDouble()
                        is String -> raw.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    userTotal += v
                }

                // update users/{uid}.totalScore
                db.collection("users").document(uid).set(mapOf("totalScore" to userTotal), SetOptions.merge()).await()

                // refresh UI list for the same week
                loadUserFormationsForWeek(week)

                _success.value = "Formazione aggiornata per ${week} (user total aggiornato)"
            } catch (e: Exception) {
                Log.e("AdminVM", "updateUserFormation: ${e.message}", e)
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        weekGamesListener?.remove()
    }
}
