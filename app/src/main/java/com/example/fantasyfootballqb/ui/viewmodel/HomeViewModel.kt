package com.example.fantasyfootballqb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _qbs = MutableStateFlow<List<QB>>(emptyList())
    val qbs: StateFlow<List<QB>> = _qbs

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    init {
        observeQBs()
        observeGames()
    }

    private fun observeQBs() {
        db.collection("qbs")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // puoi loggare l'errore in Logcat
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
                viewModelScope.launch {
                    _qbs.value = list
                }
            }
    }

    private fun observeGames() {
        db.collection("games")
            .orderBy("weekNumber")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val list = snapshots?.documents?.mapNotNull { doc ->
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
                        null
                    }
                } ?: emptyList()
                viewModelScope.launch {
                    _games.value = list
                }
            }
    }
}
