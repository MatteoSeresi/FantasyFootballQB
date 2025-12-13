package com.example.fantasyfootballqb.models

data class Game(
    val id: String = "",
    val weekNumber: Int = 0,
    val squadraCasa: String = "",
    val squadraOspite: String = "",
    val partitaGiocata: Boolean = false,
    val risultato: String? = null
)
