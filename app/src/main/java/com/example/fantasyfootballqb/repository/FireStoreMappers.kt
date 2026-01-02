package com.example.fantasyfootballqb.repository

import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.models.User
import com.google.firebase.firestore.DocumentSnapshot

// --- Extension Functions per il Mapping ---

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