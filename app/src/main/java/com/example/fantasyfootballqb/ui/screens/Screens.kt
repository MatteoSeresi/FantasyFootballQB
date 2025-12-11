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
fun HomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text("Schermata Home")
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
