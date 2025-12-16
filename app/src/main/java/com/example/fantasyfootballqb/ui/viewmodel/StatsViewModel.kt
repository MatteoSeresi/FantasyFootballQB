package com.example.fantasyfootballqb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
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

    private val _rows = MutableStateFlow<List<StatRow>>(emptyList())
    val rows: StateFlow<List<StatRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // cache qbs
    private val qbsCache: MutableMap<String, QB> = mutableMapOf()

    init {
        observeQBs()
        observeWeekstatsAndCompute()
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("StatsVM", "observeQBs error: ${error.message}")
                    _error.value = error.message
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
                        null
                    }
                } ?: emptyList()
                qbsCache.clear()
                list.forEach { qbsCache[it.id] = it }
                // recompute will happen when weekstats snapshot listener fires
            }
    }

    private fun observeWeekstatsAndCompute() {
        db.collection("weekstats")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("StatsVM", "observeWeekstats error: ${error.message}")
                    _error.value = error.message
                    return@addSnapshotListener
                }
                _loading.value = true
                val docs = snapshots?.documents ?: emptyList()
                computeStatsFromWeekStats(docs)
                _loading.value = false
            }
    }

    private fun computeStatsFromWeekStats(docs: List<DocumentSnapshot>) {
        // map per qbId:
        // - countEntries: count totale di documenti (GP)
        // - nonZeroScores: lista dei punteggi > 0 da sommare in PTOT
        val countEntries = mutableMapOf<String, Int>()
        val nonZeroScores = mutableMapOf<String, MutableList<Double>>()

        docs.forEach { doc ->
            val qbId = doc.getString("qb_id") ?: doc.getString("qbId") ?: doc.getString("qb")
            if (qbId == null) return@forEach

            // estrai punteggio (Number o String)
            val raw = doc.get("punteggioQB") ?: doc.get("punteggio_qb") ?: doc.get("score") ?: doc.get("punteggio") ?: doc.get("points")
            val value: Double? = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }

            // incrementa il contatore delle presenze (GP) anche se value == 0.0
            countEntries[qbId] = (countEntries[qbId] ?: 0) + 1

            // considera solo valori > 0 per PTOT
            if (value != null && value > 0.0) {
                val list = nonZeroScores.getOrPut(qbId) { mutableListOf() }
                list.add(value)
            }
            // se value == 0 => il giocatore è stato convocato ma non ha segnato punti:
            // conta in GP ma NON aggiunge a PTOT (richiesta tua)
            // se value == null => non c'è punteggio (non dovrebbe accadere se esiste doc), trattato come presenza?
        }

        // costruisci set di qbId da considerare
        // includiamo solo i QB con GP > 0 (ovvero presenti in countEntries)
        val allQbIds = countEntries.keys.toSet()

        val rows = allQbIds.map { qbId ->
            val qb = qbsCache[qbId] ?: QB(id = qbId, nome = qbId, squadra = "", stato = "")
            val gp = countEntries[qbId] ?: 0
            val ptot = (nonZeroScores[qbId]?.sum() ?: 0.0)
            val ppg = if (gp > 0) ptot / gp else 0.0
            StatRow(
                qbId = qbId,
                nome = qb.nome,
                squadra = qb.squadra,
                gp = gp,
                ptot = ptot,
                ppg = ppg
            )
        }.sortedByDescending { it.ptot } // ordina per PTOT discendente

        _rows.value = rows
    }

    fun clearError() { _error.value = null }
}
