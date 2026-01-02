package com.example.fantasyfootballqb.repository

import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository centrale per gestire tutte le interazioni con Firestore.
 * Usa i mapper definiti in FirestoreMappers.kt per convertire i dati.
 */
class FireStoreRepository {

    private val db = FirebaseFirestore.getInstance()

    // --- STREAM DI DATI (FLOW) - AGGIORNAMENTO TEMPO REALE ---

    // Osserva la lista di tutti i QB
    fun observeQBs(): Flow<List<QB>> = callbackFlow {
        val listener = db.collection("qbs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val qbs = snapshot?.documents?.mapNotNull { it.toQB() } ?: emptyList()
                trySend(qbs)
            }
        awaitClose { listener.remove() }
    }

    // Osserva la lista di tutte le Partite (ordinate per week)
    fun observeGames(): Flow<List<Game>> = callbackFlow {
        val listener = db.collection("games")
            .orderBy("weekNumber")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val games = snapshot?.documents?.mapNotNull { it.toGame() } ?: emptyList()
                trySend(games)
            }
        awaitClose { listener.remove() }
    }

    // Osserva i dati di un singolo utente
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error) // o gestisci l'errore diversamente
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toUser())
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    // Osserva se una week è stata calcolata (controlla se tutte le partite hanno partitaCalcolata=true)
    fun observeWeekCalculated(week: Int): Flow<Boolean> = callbackFlow {
        val listener = db.collection("games")
            .whereEqualTo("weekNumber", week)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                if (docs.isEmpty()) {
                    trySend(false)
                } else {
                    // Controlla se TUTTE le partite della week sono calcolate
                    val allCalculated = docs.all { it.getBoolean("partitaCalcolata") == true }
                    trySend(allCalculated)
                }
            }
        awaitClose { listener.remove() }
    }


    // --- OPERAZIONI ONE-SHOT (SUSPEND) - CARICAMENTO E SALVATAGGIO ---

    // Ottieni la lista delle partite per una week specifica
    suspend fun getGamesForWeek(week: Int): List<Game> {
        val snapshot = db.collection("games")
            .whereEqualTo("weekNumber", week)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toGame() }
    }

    // Ottieni un singolo QB per ID
    suspend fun getQB(qbId: String): QB? {
        val doc = db.collection("qbs").document(qbId).get().await()
        return doc.toQB() // Usa il mapper che abbiamo creato
    }

    // Ottieni tutte le partite (utile per calcoli interni senza listener)
    suspend fun getAllGames(): List<Game> {
        val snapshot = db.collection("games").get().await()
        return snapshot.documents.mapNotNull { it.toGame() }
    }

    // Ottieni gli ID dei QB schierati in una formazione utente
    suspend fun getUserFormationIds(uid: String, week: Int): List<String> {
        val doc = db.collection("users")
            .document(uid)
            .collection("formations")
            .document(week.toString())
            .get()
            .await()

        if (doc.exists()) {
            return (doc.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        }
        return emptyList()
    }

    // Controlla se una formazione è bloccata (locked)
    suspend fun isFormationLocked(uid: String, week: Int): Boolean {
        val doc = db.collection("users")
            .document(uid)
            .collection("formations")
            .document(week.toString())
            .get()
            .await()
        return doc.getBoolean("locked") ?: false
    }

    // Salva la formazione (User side)
    suspend fun submitFormation(uid: String, week: Int, qbIds: List<String>) {
        val data = mapOf(
            "weekNumber" to week,
            "qbIds" to qbIds,
            "locked" to true
        )
        db.collection("users")
            .document(uid)
            .collection("formations")
            .document(week.toString())
            .set(data, SetOptions.merge())
            .await()
    }

    // Aggiorna nome team (User Profile)
    suspend fun updateTeamName(uid: String, newName: String) {
        db.collection("users")
            .document(uid)
            .update("nomeTeam", newName)
            .await()
    }

    // --- FUNZIONI WEEKSTATS & SCORE (Raw Fetching) ---
    // Manteniamo la logica flessibile: ti restituisce i documenti raw,
    // così il ViewModel può applicare la logica specifica sui nomi dei campi.

    suspend fun getWeekStatsForQb(qbId: String): List<com.google.firebase.firestore.DocumentSnapshot> {
        return db.collection("weekstats")
            .whereEqualTo("qb_id", qbId)
            .get()
            .await()
            .documents
    }
}