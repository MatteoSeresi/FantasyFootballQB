package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
