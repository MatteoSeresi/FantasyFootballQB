package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalendarScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schermata Calendario")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Mostra calendario week-by-week con punteggi.")
        }
    }
}
