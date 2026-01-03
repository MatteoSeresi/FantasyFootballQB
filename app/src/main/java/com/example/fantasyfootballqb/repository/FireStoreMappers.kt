package com.example.fantasyfootballqb.repository

import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.models.User
import com.example.fantasyfootballqb.models.WeekStats
import com.example.fantasyfootballqb.models.Formation
import com.google.firebase.firestore.DocumentSnapshot


fun DocumentSnapshot.toGame(): Game? {
    return try {
        Game(
            id = this.id,
            weekNumber = (this.getLong("weekNumber") ?: 0L).toInt(),
            squadraCasa = this.getString("squadraCasa") ?: "",
            squadraOspite = this.getString("squadraOspite") ?: "",
            partitaGiocata = this.getBoolean("partitaGiocata") ?: false,
            risultato = this.getString("risultato"),
            partitaCalcolata = this.getBoolean("partitaCalcolata") ?: false
        )
    } catch (e: Exception) {
        null
    }
}

fun DocumentSnapshot.toQB(): QB? {
    return try {
        QB(
            id = this.id,
            nome = this.getString("nome") ?: "",
            squadra = this.getString("squadra") ?: "",
            stato = this.getString("stato") ?: ""
        )
    } catch (e: Exception) {
        null
    }
}

fun DocumentSnapshot.toUser(): User? {
    return try {
        User(
            uid = this.id,
            email = this.getString("email") ?: "",
            username = this.getString("username") ?: "",
            nomeTeam = this.getString("nomeTeam") ?: "",
            isAdmin = this.getBoolean("isAdmin") ?: false
        )
    } catch (e: Exception) {
        null
    }
}

fun DocumentSnapshot.toWeekStats(): WeekStats? {
    return try {

        val rawScore = this.get("punteggioQB")
            ?: this.get("punteggio_qb")
            ?: this.get("score")
            ?: this.get("punteggio")

        val scoreValue = when (rawScore) {
            is Number -> rawScore.toDouble()
            is String -> rawScore.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        val gId = this.getString("game_id")
            ?: this.getString("gameId")
            ?: this.getString("game")
            ?: return null

        val qId = this.getString("qb_id")
            ?: this.getString("qbId")
            ?: return null

        WeekStats(
            id = this.id,
            gameId = gId,
            qbId = qId,
            punteggio = scoreValue
        )
    } catch (e: Exception) {
        null
    }
}

fun DocumentSnapshot.toFormation(): Formation? {
    return try {
        // 1. Recupera weekNumber (prova field o usa id documento)
        val wNum = this.getLong("weekNumber")?.toInt()
            ?: this.getLong("week")?.toInt()
            ?: this.id.toIntOrNull()
            ?: 0

        // 2. Recupera la lista di QB
        val ids = (this.get("qbIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        // 3. Recupera stato locked
        val isLocked = this.getBoolean("locked") ?: false

        // 4. Recupera eventuale totale salvato (gestisce nomi sporchi)
        val rawScore = this.get("totalWeekScore")
            ?: this.get("totalScore")
            ?: this.get("punteggio")
            ?: this.get("punteggioQbs")

        val scoreVal = when (rawScore) {
            is Number -> rawScore.toDouble()
            is String -> rawScore.toDoubleOrNull()
            else -> null
        }

        Formation(
            id = this.id,
            weekNumber = wNum,
            qbIds = ids,
            locked = isLocked,
            totalScore = scoreVal
        )
    } catch (e: Exception) {
        null
    }
}