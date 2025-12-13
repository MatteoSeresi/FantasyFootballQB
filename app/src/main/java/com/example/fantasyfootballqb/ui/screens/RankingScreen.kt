package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RankingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schermata Classifica")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Visualizza classifica e punteggio totale degli utenti.")
        }
    }
}
