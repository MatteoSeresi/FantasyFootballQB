package com.example.fantasyfootballqb.models

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val nomeTeam: String = "",
    val isAdmin: Boolean = false
)
