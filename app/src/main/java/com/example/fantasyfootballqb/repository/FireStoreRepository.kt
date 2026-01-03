package com.example.fantasyfootballqb.repository

import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.models.User
import com.example.fantasyfootballqb.models.Formation
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
                    // SE L'ERRORE È "PERMISSION_DENIED" (succede al logout),
                    // chiudiamo il flusso gentilmente senza lanciare l'eccezione che fa crashare l'app.
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close()
                    } else {
                        close(error)
                    }
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

    suspend fun getFormation(uid: String, week: Int): Formation? {
        val doc = db.collection("users")
            .document(uid)
            .collection("formations")
            .document(week.toString())
            .get()
            .await()
        return doc.toFormation()
    }

    // Ottieni gli ID dei QB schierati in una formazione utente
    suspend fun getUserFormations(uid: String): List<Formation> {
        val snapshot = db.collection("users")
            .document(uid)
            .collection("formations")
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toFormation() }
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

    // --- FUNZIONI WEEKSTATS & SCORE
    suspend fun getQBWeekStats(qbId: String): List<com.example.fantasyfootballqb.models.WeekStats> {
        val snapshot = db.collection("weekstats")
            .whereEqualTo("qb_id", qbId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toWeekStats() }
    }

    // --- ADMIN FUNCTIONS ---

    // Osserva TUTTI gli utenti (per la lista Admin)
    fun observeAllUsers(): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull { it.toUser() } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    // Aggiorna dati utente (Admin override)
    suspend fun updateAdminUserData(uid: String, data: Map<String, Any?>) {
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    // Aggiorna risultato partita
    suspend fun updateGameResult(gameId: String, played: Boolean, result: String?) {
        val map = mutableMapOf<String, Any?>("partitaGiocata" to played)
        map["risultato"] = result
        db.collection("games").document(gameId).set(map, SetOptions.merge()).await()
    }

    // Imposta punteggio QB (Weekstats)
    suspend fun setQBScore(gameId: String, qbId: String, score: Double) {
        val coll = db.collection("weekstats")
        val qsnap = coll
            .whereEqualTo("game_id", gameId)
            .whereEqualTo("qb_id", qbId)
            .get()
            .await()

        if (!qsnap.isEmpty) {
            val doc = qsnap.documents.first()
            coll.document(doc.id).set(mapOf("punteggioQB" to score), SetOptions.merge()).await()
        } else {
            val newDoc = mapOf(
                "game_id" to gameId,
                "qb_id" to qbId,
                "punteggioQB" to score
            )
            coll.add(newDoc).await()
        }
    }

    // Ottieni tutte le weekstats (utile per calcoli massivi o validazione)
    suspend fun getAllWeekStats(): List<com.example.fantasyfootballqb.models.WeekStats> {
        val snapshot = db.collection("weekstats").get().await()
        return snapshot.documents.mapNotNull { it.toWeekStats() }
    }

    // Ottieni weekstats per una specifica partita (per validazione puntuale)
    suspend fun getWeekStatsForGame(gameId: String): List<com.example.fantasyfootballqb.models.WeekStats> {
        val snapshot = db.collection("weekstats")
            .whereEqualTo("game_id", gameId) // Nota: qui assume che nel DB il campo principale sia game_id
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toWeekStats() }
    }

    // Calcola Week (segna tutte le partite della week come calcolate)
    suspend fun markWeekAsCalculated(week: Int, gameIds: List<String>) {
        if (gameIds.isEmpty()) return
        val batch = db.batch()
        gameIds.forEach { gid ->
            val ref = db.collection("games").document(gid)
            batch.update(ref, "partitaCalcolata", true)
        }
        batch.commit().await()
    }

    // Aggiorna formazione utente (Admin override)
    suspend fun updateUserFormationData(uid: String, week: Int, data: Map<String, Any?>) {
        db.collection("users")
            .document(uid)
            .collection("formations")
            .document(week.toString())
            .set(data, SetOptions.merge())
            .await()
    }

    // --- FUNZIONI PER CALENDAR, STATS, RANKING & PROFILE ---

    // Ottieni tutti gli utenti una volta sola (per Ranking)
    suspend fun getAllUsers(): List<User> {
        val snapshot = db.collection("users").get().await()
        return snapshot.documents.mapNotNull { it.toUser() }
    }

    // Osserva tutte le Weekstats in tempo reale (per StatsViewModel)
    fun observeWeekStats(): Flow<List<com.google.firebase.firestore.DocumentSnapshot>> = callbackFlow {
        val listener = db.collection("weekstats")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snaps?.documents ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // Aggiorna solo lo username (Profile)
    suspend fun updateUsername(uid: String, newUsername: String) {
        db.collection("users").document(uid).update("username", newUsername).await()
    }

    // Elimina completamente i dati utente (Formazioni + Documento User)
    suspend fun deleteUserData(uid: String) {
        // 1. Prendi tutte le formazioni
        val formationsRef = db.collection("users").document(uid).collection("formations")
        val snapshots = formationsRef.get().await()

        // 2. Cancellazione in Batch per efficienza e atomicità
        val batch = db.batch()
        for (doc in snapshots) {
            batch.delete(doc.reference)
        }
        // 3. Cancella utente
        batch.delete(db.collection("users").document(uid))

        // Esegui tutto
        batch.commit().await()
    }
}