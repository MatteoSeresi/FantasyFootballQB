package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.ui.viewmodel.HomeViewModel

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onRegister: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schermata Login")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onLoginSuccess() }) {
                Text("Accedi (demo)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onRegister() }) {
                Text("Vai a Registrazione")
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schermata Registrazione")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onRegisterSuccess() }) {
                Text("Registrati (demo)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onBackToLogin() }) {
                Text("Torna al Login")
            }
        }
    }
}

@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel()) {
    val qbsState = homeViewModel.qbs.collectAsState()
    val gamesState = homeViewModel.games.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "QBs disponibili", style = MaterialTheme.typography.titleMedium)
        }
        items(qbsState.value) { qb ->
            QBItem(qb)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Calendario partite", style = MaterialTheme.typography.titleMedium)
        }

        items(gamesState.value) { game ->
            GameItem(game)
        }

        if (qbsState.value.isEmpty() && gamesState.value.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun dato trovato su Firestore (controlla la console Firebase / regole).")
                }
            }
        }
    }
}

@Composable
fun QBItem(qb: QB) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = qb.nome, style = MaterialTheme.typography.titleSmall)
            Text(text = "Squadra: ${qb.squadra}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Stato: ${qb.stato}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun GameItem(game: Game) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Week ${game.weekNumber} — ${game.squadraCasa} vs ${game.squadraOspite}", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(6.dp))
            if (game.partitaGiocata) {
                Text(text = "Risultato: ${game.risultato ?: "non disponibile"}", style = MaterialTheme.typography.bodyMedium)
                // Qui potrai estendere per visualizzare punteggi fantasy dei QB collegati alla partita
            } else {
                Text(text = "Partita ancora da disputare", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun TeamScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Schermata Gestione Squadra")
    }
}

@Composable
fun CalendarScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Schermata Calendario")
    }
}

@Composable
fun StatsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Schermata Statistiche")
    }
}

@Composable
fun RankingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Schermata Classifica")
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schermata Profilo")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onLogout() }) {
                Text("Logout (demo)")
            }
        }
    }
}
