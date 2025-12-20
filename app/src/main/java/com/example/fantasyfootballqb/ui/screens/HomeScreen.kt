package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.HomeViewModel
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel()) {
    val user by homeViewModel.user.collectAsState()
    val loading by homeViewModel.loading.collectAsState()
    val error by homeViewModel.error.collectAsState()
    val success by homeViewModel.success.collectAsState()

    // fallback dalle credenziali FirebaseAuth se il doc users/{uid} non esiste
    val firebaseAuth = FirebaseAuth.getInstance()
    val fallbackEmail = firebaseAuth.currentUser?.email ?: ""

    var teamInput by remember { mutableStateOf("") }

    // dialog di modifica
    var showEditDialog by remember { mutableStateOf(false) }

    // se l'user ha nomeTeam preimpostalo nell'input (utile se vuole modificarlo)
    LaunchedEffect(user?.nomeTeam) {
        teamInput = user?.nomeTeam ?: ""
    }

    // Snackbar host
    val snackbarHostState = remember { SnackbarHostState() }

    // show snackbar ad eventi di successo/errore
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            homeViewModel.clearError()
        }
    }
    LaunchedEffect(success) {
        success?.let {
            snackbarHostState.showSnackbar(it)
            homeViewModel.clearSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Account Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Account", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Email (read-only) -> usa fallback se user == null
                    OutlinedTextField(
                        value = user?.email ?: fallbackEmail,
                        onValueChange = { /* read-only */ },
                        label = { Text("email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Username (read-only). Se manca, mostra stringa vuota
                    OutlinedTextField(
                        value = user?.username ?: "",
                        onValueChange = { /* read-only */ },
                        label = { Text("username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false
                    )
                }
            }

            // Team Card: se nomeTeam vuoto mostra creazione, altrimenti mostra il nome team con bottone Edit
            if (user?.nomeTeam.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Crea la tua squadra",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = teamInput,
                            onValueChange = { teamInput = it },
                            label = { Text("Nome squadra") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !loading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (teamInput.isNotBlank()) {
                                    homeViewModel.createOrUpdateTeam(teamInput.trim())
                                } else {
                                    // mostra snackbar di errore locale
                                    // usiamo il snackbarHostState direttamente:
                                    // (non modifichiamo il ViewModel per questo semplice messaggio)
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            enabled = !loading
                        ) {
                            Text("Crea")
                        }
                    }
                }
            } else {
                // visualizza nome team esistente con bottone per modificare (apre dialog)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Team", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = user?.nomeTeam ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    // apre dialog con testo corrente
                                    teamInput = user?.nomeTeam ?: ""
                                    showEditDialog = true
                                },
                                enabled = !loading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifica"
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Regolamento:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // area testuale scrollabile con altezza massima
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 220.dp)    // regola questo valore a piacere
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = "Le partite sono organizzate in week, che corrispondono alle singole settimane di gioco. " +
                                    "Prima dell'inizio di ogni week, ogni utente deve schierare la propria formazione selezionando 3 Quarterback. " +
                                    "Una volta confermata la formazione, non sarà più possibile modificarla fino al termine della week in corso. Al termine della week verranno calcolati i punteggi ottenuti dai giocatori in base alle loro prestazioni e di conseguenza, sarà aggiornata la classifica generale dagli utenti partecipanti. " +
                                    "Questo processo si ripete in modo identico per ogni week successiva.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Loading indicator (centrato)
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // SnackbarHost (sopra la bottom bar)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }

        // Dialog di modifica nome team
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Modifica nome squadra") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = teamInput,
                            onValueChange = { teamInput = it },
                            label = { Text("Nome squadra") },
                            singleLine = true,
                            enabled = !loading
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (teamInput.isNotBlank()) {
                                homeViewModel.createOrUpdateTeam(teamInput.trim())
                                showEditDialog = false
                            } else {
                                // mostra snackbar locale con messaggio di errore
                                // qui possiamo sfruttare il snackbarHostState
                                // ma per semplicità: chiudiamo il dialog e lasciare che homeViewModel segnali errore altrove
                                showEditDialog = false
                            }
                        },
                        enabled = !loading
                    ) {
                        Text("Salva")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }, enabled = !loading) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}
