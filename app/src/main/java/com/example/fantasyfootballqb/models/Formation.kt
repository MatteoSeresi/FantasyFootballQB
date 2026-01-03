package com.example.fantasyfootballqb.models

data class Formation(
    val id: String,
    val weekNumber: Int,
    val qbIds: List<String>,
    val locked: Boolean = false,
    val totalScore: Double? = null
)