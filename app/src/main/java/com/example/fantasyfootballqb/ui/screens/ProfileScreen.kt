package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onLogout: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val email by vm.email.collectAsState()
    val username by vm.username.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val success by vm.success.collectAsState()

    val askPassword by vm.askPassword.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditUsername by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(error!!) }
            vm.clearMessages()
        }
    }
    LaunchedEffect(success) {
        if (!success.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(success!!) }
            if (success!!.contains("eliminato", ignoreCase = true)) {
                onLogout()
            }
            vm.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Profilo", style = MaterialTheme.typography.titleMedium)

                        // Email (Sola lettura per semplicità)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email", style = MaterialTheme.typography.bodySmall)
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 6.dp)) {
                                    Text(email ?: "", modifier = Modifier.padding(12.dp))
                                }
                            }
                        }

                        // Username
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Username", style = MaterialTheme.typography.bodySmall)
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 6.dp)) {
                                    Text(username ?: "", modifier = Modifier.padding(12.dp))
                                }
                            }
                            IconButton(onClick = { showEditUsername = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifica Username")
                            }
                        }

                        Button(
                            onClick = { vm.logout(); onLogout() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Logout")
                        }
                    }
                }

                // Bottone Elimina Account
                Button(
                    onClick = { showConfirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elimina account", color = MaterialTheme.colorScheme.onError)
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Dialog Modifica Username
            if (showEditUsername) {
                var newUsername by remember { mutableStateOf(username ?: "") }
                AlertDialog(
                    onDismissRequest = { showEditUsername = false },
                    title = { Text("Modifica Username") },
                    text = {
                        Column {
                            OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, singleLine = true, label = { Text("Username") })
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showEditUsername = false
                            vm.updateUsername(newUsername.trim())
                        }) { Text("Salva") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditUsername = false }) { Text("Annulla") }
                    }
                )
            }

            // Dialog Conferma Eliminazione
            if (showConfirmDelete) {
                AlertDialog(
                    onDismissRequest = { showConfirmDelete = false },
                    title = { Text("Conferma eliminazione") },
                    text = { Text("Sei sicuro? L'operazione è irreversibile.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmDelete = false
                            vm.deleteAccount()
                        }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDelete = false }) { Text("Annulla") }
                    }
                )
            }

            // --- DIALOG RICHIESTA PASSWORD (NECESSARIO PER ELIMINARE) ---
            if (askPassword) {
                var pwd by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { /* Obbligatorio interagire */ },
                    title = { Text("Conferma Password") },
                    text = {
                        Column {
                            Text("Per sicurezza, inserisci la tua password per confermare l'eliminazione.")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pwd,
                                onValueChange = { pwd = it },
                                singleLine = true,
                                label = { Text("Password") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.reauthenticateWithPassword(pwd)
                        }) { Text("Conferma") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            vm.clearMessages()
                            vm.loadUserData()
                        }) { Text("Annulla") }
                    }
                )
            }
        }
    }
}