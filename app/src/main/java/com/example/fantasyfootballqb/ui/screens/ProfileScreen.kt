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

    // UI states for edit dialogs
    var showEditEmail by remember { mutableStateOf(false) }
    var showEditUsername by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    // password for reauth dialog
    var passwordForReauth by remember { mutableStateOf("") }

    // show messages
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar(error!!)
            }
            vm.clearMessages()
        }
    }
    LaunchedEffect(success) {
        if (!success.isNullOrBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar(success!!)
            }
            if (success!!.contains("eliminato", ignoreCase = true)) {
                onLogout()
            }
            vm.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // usiamo paddingValues fornito dallo Scaffold
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

                        // Email row
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email", style = MaterialTheme.typography.bodySmall)
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 6.dp)) {
                                    Text(email ?: "", modifier = Modifier.padding(12.dp))
                                }
                            }
                            IconButton(onClick = { showEditEmail = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifica Email")
                            }
                        }

                        // Username row
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

                        // Logout button
                        Button(
                            onClick = {
                                vm.logout()
                                onLogout()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Logout")
                        }
                    }
                }

                // Delete account big button (danger)
                Button(
                    onClick = { showConfirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Elimina account", color = MaterialTheme.colorScheme.onError)
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Loading overlay
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Edit Email dialog
            if (showEditEmail) {
                var newEmail by remember { mutableStateOf(email ?: "") }
                AlertDialog(
                    onDismissRequest = { showEditEmail = false },
                    title = { Text("Modifica Email") },
                    text = {
                        Column {
                            OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, singleLine = true, label = { Text("Email") })
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Attenzione: per aggiornare l'email potrebbe essere richiesta la password recente.")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showEditEmail = false
                            vm.updateEmail(newEmail.trim())
                        }) { Text("Salva") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditEmail = false }) { Text("Annulla") }
                    }
                )
            }

            // Edit Username dialog
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

            // Confirm delete dialog
            if (showConfirmDelete) {
                AlertDialog(
                    onDismissRequest = { showConfirmDelete = false },
                    title = { Text("Conferma eliminazione") },
                    text = { Text("Sei sicuro di voler eliminare il tuo account?") },
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

            // Reauth password dialog
            if (askPassword) {
                var pwd by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { /* non dismiss automatico */ },
                    title = { Text("Re-authentication") },
                    text = {
                        Column {
                            Text("Per completare l'operazione inserisci la password del tuo account.")
                            OutlinedTextField(value = pwd, onValueChange = { pwd = it }, singleLine = true, label = { Text("Password") })
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
                        }) { Text("Annulla") }
                    }
                )
            }
        }
    }
}