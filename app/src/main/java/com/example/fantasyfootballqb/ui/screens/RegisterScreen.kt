package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.AuthViewModel
import com.example.fantasyfootballqb.ui.viewmodel.AuthUiState
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val registerState by authViewModel.registerState.collectAsState()

    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Registrazione", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Conferma Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (registerState) {
                is AuthUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is AuthUiState.Error -> {
                    Text(
                        text = (registerState as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is AuthUiState.Success -> {
                    LaunchedEffect(Unit) {
                        onRegisterSuccess()
                    }
                }
                else -> { /* Idle */ }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (password != confirmPassword) {
                        // segnala errore locale
                        // possiamo provocare lo stato di errore direttamente (semplice)
                        // ma preferisco usare un Toast / snackbar: qui imposto lo stato errore nel ViewModel
                        // per semplicità: mostriamo un Alert dialog inline:
                        // (qui, però, invoco register solo se ok)
                        return@Button
                    }
                    authViewModel.register(email, password, username)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Registrati")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { onBackToLogin() }) {
                Text("Torna al Login")
            }
        }
    }
}
