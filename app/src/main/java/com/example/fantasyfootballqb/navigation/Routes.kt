package com.example.fantasyfootballqb.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Routes(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Routes("login", "Login")
    object Register : Routes("register", "Registrazione")
    object Home : Routes("home", "Home", Icons.Default.Home)
    object Team : Routes("team", "Gestione Squadra", Icons.Default.SportsFootball)
    object Calendar : Routes("calendar", "Calendario", Icons.Default.CalendarToday)
    object Stats : Routes("stats", "Statistiche", Icons.Default.BarChart)
    object Ranking : Routes("ranking", "Classifica", Icons.Default.EmojiEvents)
    object Profile : Routes("profile", "Profilo", Icons.Default.Person)
}
