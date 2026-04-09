package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.R
import com.example.fantasyfootballqb.ui.viewmodel.AuthUiState
import com.example.fantasyfootballqb.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginNavigate: (isAdmin: Boolean) -> Unit,
    onRegister: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val loginState by authViewModel.loginState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Gestiamo i cambiamenti di stato (Errori e Navigazione di Successo)
    LaunchedEffect(loginState) {
        when (loginState) {
            is AuthUiState.Error -> {
                val errorMessage = (loginState as AuthUiState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            is AuthUiState.Success -> {
                val successState = loginState as AuthUiState.Success
                // Navighiamo passando il ruolo corretto
                onLoginNavigate(successState.isAdmin)
            }
            else -> { /* Idle o Loading */ }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App logo",
                    modifier = Modifier
                        .size(140.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(text = "Accedi", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = {
                            passwordVisible = !passwordVisible
                        }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Default.VisibilityOff
                                else
                                    Icons.Default.Visibility,
                                contentDescription = if (passwordVisible)
                                    "Nascondi password"
                                else
                                    "Mostra password"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            // Deleghiamo al ViewModel l'operazione
                            authViewModel.login(email, password)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Compila tutti i campi per accedere") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    // Disabilitiamo il pulsante se l'app sta già caricando
                    enabled = loginState !is AuthUiState.Loading
                ) {
                    Text("Accedi")
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRegister) {
                    Text("Non sei registrato? Registrati adesso")
                }
            }

            // Se lo stato è Loading, mostriamo l'indicatore di caricamento
            if (loginState is AuthUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}