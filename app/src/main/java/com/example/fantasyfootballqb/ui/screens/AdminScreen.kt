package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.AdminViewModel
import com.example.fantasyfootballqb.ui.viewmodel.AdminUser
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    vm: AdminViewModel = viewModel()
) {
    val users by vm.users.collectAsState()
    val weeks by vm.availableWeeks.collectAsState()
    val gamesByWeek by vm.gamesByWeek.collectAsState()
    val qbs by vm.qbs.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val success by vm.success.collectAsState()
    val selectedWeek by vm.selectedWeek.collectAsState()

    // flag che segnala se la week è già stata calcolata (tutte le games hanno partitaCalcolata == true)
    val weekCalculated by vm.weekCalculated.collectAsState()

    var showUsersDialog by remember { mutableStateOf(false) }
    var showModifyGamesDialog by remember { mutableStateOf(false) }
    var showModifyQBsDialog by remember { mutableStateOf(false) }

    // dialog di validazione / conferma calcolo
    var showConfirmCalculateDialog by remember { mutableStateOf(false) }
    var missingDataList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMissingDataDialog by remember { mutableStateOf(false) }

    val snackHost = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // mostra snack quando cambia error / success
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackHost.showSnackbar(error!!)
            vm.clearMessages()
        }
    }
    LaunchedEffect(success) {
        if (!success.isNullOrBlank()) {
            snackHost.showSnackbar(success!!)
            vm.clearMessages()
        }
    }

    // quando cambia la selectedWeek, avvia l'osservazione dello stato "partitaCalcolata"
    LaunchedEffect(selectedWeek) {
        selectedWeek?.let { vm.observeWeekCalculated(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fantasy Football",
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 16.dp).weight(1f),
                            fontSize = 26.sp
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFEF5350)), contentAlignment = Alignment.Center) {
                    Text("AMMINISTRATORE", color = Color.White, modifier = Modifier.padding(10.dp))
                }

                // Users card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Utenti:", style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { showUsersDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica dati utente")
                        }
                        Button(onClick = { /* stub */ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica formazione utente")
                        }
                    }
                }

                // Calcolo week card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Calcolo week:", style = MaterialTheme.typography.titleSmall)

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                                Text("Week:", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }

                            var expanded by remember { mutableStateOf(false) }
                            val label = selectedWeek?.toString() ?: "Seleziona week"
                            Button(onClick = { expanded = true }) {
                                Text(label)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Seleziona week") }, onClick = {
                                    vm.setSelectedWeek(null); expanded = false
                                })
                                weeks.forEach { w ->
                                    DropdownMenuItem(text = { Text("Week $w") }, onClick = {
                                        vm.setSelectedWeek(w); expanded = false
                                        // l'osservazione dello stato weekCalculated parte nel LaunchedEffect
                                    })
                                }
                            }
                        }

                        Button(onClick = { showModifyGamesDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica dati partite")
                        }
                        Button(onClick = { showModifyQBsDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica punteggi QBs")
                        }

                        // Bottone CALCOLA WEEK -> prima valida, poi chiede conferma, poi esegue calculateWeek
                        Button(
                            onClick = {
                                val w = selectedWeek
                                if (w == null) {
                                    coroutineScope.launch { snackHost.showSnackbar("Seleziona prima una week") }
                                    return@Button
                                }
                                coroutineScope.launch {
                                    // chiama la suspend validateWeek
                                    val problems = vm.validateWeek(w)
                                    if (problems.isNotEmpty()) {
                                        missingDataList = problems
                                        showMissingDataDialog = true
                                    } else {
                                        showConfirmCalculateDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = (selectedWeek != null) && !weekCalculated,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text(
                                if (weekCalculated) "Week già calcolata" else "CALCOLA WEEK: ${selectedWeek ?: "-"}",
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("LOGOUT", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // Dialogs
            if (showUsersDialog) {
                UsersDialog(users = users, onDismiss = { showUsersDialog = false }, onSave = { u, newEmail, newUsername, newNomeTeam ->
                    vm.updateUserData(u.uid, newEmail, newUsername, newNomeTeam)
                })
            }

            if (showModifyGamesDialog) {
                val gamesForWeek = selectedWeek?.let { gamesByWeek[it] } ?: emptyList()
                ModifyGamesDialog(
                    week = selectedWeek,
                    games = gamesForWeek,
                    onDismiss = { showModifyGamesDialog = false },
                    // IMPORTANT: if result is not blank -> set partitaGiocata = true
                    onSaveGame = { gameId, _partitaGiocataIgnored, risultato ->
                        val played = risultato?.isNotBlank() == true
                        vm.updateGame(gameId, played, risultato)
                    }
                )
            }

            if (showModifyQBsDialog) {
                val gamesForWeek = selectedWeek?.let { gamesByWeek[it] } ?: emptyList()
                ModifyQBsDialog(
                    week = selectedWeek,
                    games = gamesForWeek,
                    allQBs = qbs,
                    onDismiss = { showModifyQBsDialog = false },
                    onSaveScore = { gameId, qbId, score ->
                        vm.setQBScore(gameId, qbId, score)
                    },
                    onShowMessage = { msg ->
                        coroutineScope.launch { snackHost.showSnackbar(msg) }
                    }
                )
            }

            // Dialog: dati mancanti
            if (showMissingDataDialog) {
                AlertDialog(onDismissRequest = { showMissingDataDialog = false }, title = { Text("Dati mancanti") }, text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        Text("Non è possibile calcolare la week: mancano i seguenti dati:")
                        Spacer(modifier = Modifier.height(8.dp))
                        missingDataList.forEach { item ->
                            Text("- $item", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }, confirmButton = {
                    TextButton(onClick = { showMissingDataDialog = false }) { Text("Chiudi") }
                }, dismissButton = {})
            }

            // Dialog: conferma calcolo
            if (showConfirmCalculateDialog) {
                AlertDialog(onDismissRequest = { showConfirmCalculateDialog = false }, title = { Text("Conferma calcolo") }, text = {
                    Text("Sei sicuro di voler calcolare la week ${selectedWeek ?: "-"}? Questa operazione la renderà definitiva.")
                }, confirmButton = {
                    TextButton(onClick = {
                        showConfirmCalculateDialog = false
                        val w = selectedWeek
                        if (w != null) {
                            vm.calculateWeek(w)
                        }
                    }) { Text("Conferma") }
                }, dismissButton = {
                    TextButton(onClick = { showConfirmCalculateDialog = false }) { Text("Annulla") }
                })
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/* ----- Dialogs ----- */

@Composable
private fun UsersDialog(users: List<AdminUser>, onDismiss: () -> Unit, onSave: (AdminUser, String, String, String) -> Unit) {
    var editingUser by remember { mutableStateOf<AdminUser?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Utenti") }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            if (users.isEmpty()) {
                Text("Nessun utente")
            } else {
                LazyColumn {
                    items(users) { u ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.email, fontWeight = FontWeight.SemiBold)
                                Text(u.username, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { editingUser = u }) { Text("Modifica") }
                        }
                        Divider()
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Chiudi") }
    }, dismissButton = {})

    if (editingUser != null) {
        val u = editingUser!!
        var newEmail by remember { mutableStateOf(u.email) }
        var newUsername by remember { mutableStateOf(u.username) }
        var newNomeTeam by remember { mutableStateOf(u.nomeTeam) }

        AlertDialog(onDismissRequest = { editingUser = null }, title = { Text("Modifica utente") }, text = {
            Column {
                OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email (solo Firestore)") })
                OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") })
                OutlinedTextField(value = newNomeTeam, onValueChange = { newNomeTeam = it }, label = { Text("Nome squadra") })
                Text("Nota: la modifica dell'email qui aggiorna solo il documento Firestore. Per cambiare la email in Firebase Authentication è necessario usare Admin SDK.")
            }
        }, confirmButton = {
            TextButton(onClick = {
                onSave(u, newEmail.trim(), newUsername.trim(), newNomeTeam.trim())
                editingUser = null
            }) { Text("Salva") }
        }, dismissButton = {
            TextButton(onClick = { editingUser = null }) { Text("Annulla") }
        })
    }
}

/**
 * ModifyGamesDialog: ora mostra solo campo risultato.
 * Quando si salva, se risultato non vuoto => partitaGiocata = true.
 */
@Composable
private fun ModifyGamesDialog(week: Int?, games: List<Game>, onDismiss: () -> Unit, onSaveGame: (String, Boolean, String?) -> Unit) {
    var localGames by remember { mutableStateOf(games.map { it.copy() }) }
    LaunchedEffect(games) { localGames = games.map { it.copy() } }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica dati partite - Week ${week ?: "-"}") }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (localGames.isEmpty()) {
                Text("Nessuna partita per questa week")
            } else {
                LazyColumn {
                    items(localGames) { g ->
                        val gameIndex = localGames.indexOfFirst { it.id == g.id }
                        val gameState = localGames.getOrNull(gameIndex) ?: g
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${g.squadraCasa} - ${g.squadraOspite}", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var localResult by remember { mutableStateOf(gameState.risultato ?: "") }
                                OutlinedTextField(value = localResult, onValueChange = { newText ->
                                    localResult = newText
                                    localGames = localGames.map { if (it.id == g.id) it.copy(risultato = if (newText.isBlank()) null else newText) else it }
                                }, label = { Text("Risultato partita:") }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val gs = localGames.firstOrNull { it.id == g.id }
                                    if (gs != null) {
                                        val res = gs.risultato
                                        val played = res?.isNotBlank() == true
                                        onSaveGame(gs.id, played, res)
                                    }
                                }) {
                                    Text("Salva partita")
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Chiudi") }
    }, dismissButton = {})
}

/**
 * ModifyQBsDialog: carica i weekstats per la partita selezionata e mostra SOLO i QBs associati.
 */
@Composable
private fun ModifyQBsDialog(
    week: Int?,
    games: List<Game>,
    allQBs: List<QB>,
    onDismiss: () -> Unit,
    onSaveScore: (String, String, Double) -> Unit,
    onShowMessage: (String) -> Unit
) {
    // quale partita è espansa (mostra i QB per quella partita)
    var expandedGameId by remember { mutableStateOf<String?>(null) }

    // mappa gameId -> (qbId -> scoreString)
    val localScoresState = remember { mutableStateMapOf<String, MutableMap<String, String>>() }

    // funzione per caricare weekstats per una partita (popola localScoresState[gameId])
    suspend fun loadWeekstatsForGame(gameId: String, teamHome: String, teamAway: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            // fetch weekstats per gameId
            val snap = db.collection("weekstats")
                .whereEqualTo("game_id", gameId)
                .get()
                .await()

            // map qbId -> punteggio (string)
            val scoresFromDB = mutableMapOf<String, String>()
            for (doc in snap.documents) {
                val qbId = doc.getString("qb_id") ?: continue
                val raw = doc.get("punteggioQB")
                val scoreStr = when (raw) {
                    is Number -> raw.toString()
                    is String -> raw
                    else -> ""
                }
                scoresFromDB[qbId] = scoreStr
            }

            // get QBs for the two teams (home + away)
            val qbsForGame = allQBs.filter { it.squadra == teamHome || it.squadra == teamAway }

            // inizializza inner map con i qbs della partita (valori presi da DB se presenti, altrimenti "")
            val inner = mutableMapOf<String, String>()
            qbsForGame.forEach { qb ->
                inner[qb.id] = scoresFromDB[qb.id] ?: ""
            }

            localScoresState[gameId] = inner
        } catch (e: Exception) {
            onShowMessage("Errore caricamento weekstats: ${e.message}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica punteggi QBs - Week ${week ?: "-"}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                if (games.isEmpty()) {
                    Text("Nessuna partita per questa week")
                    return@Column
                }

                // Lista partite (card per partita). La lista è scrollabile (LazyColumn)
                LazyColumn {
                    items(games) { g ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${g.squadraCasa}  vs  ${g.squadraOspite}", fontWeight = FontWeight.SemiBold)
                                        val resText = if (g.partitaGiocata) (g.risultato ?: "-") else "(non giocata)"
                                        Text(resText, style = MaterialTheme.typography.bodySmall)
                                    }

                                    TextButton(onClick = {
                                        // espandi / richiudi
                                        expandedGameId = if (expandedGameId == g.id) null else g.id
                                    }) {
                                        Text(if (expandedGameId == g.id) "Chiudi" else "Apri")
                                    }
                                }

                                // se questa partita è espansa, mostriamo i QBs associati alle squadre
                                if (expandedGameId == g.id) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (!g.partitaGiocata) {
                                        // non permettere la modifica se partita non giocata
                                        Text("Impossibile modificare i punteggi: la partita non è ancora stata disputata.")
                                    } else {
                                        // Carichiamo weekstats per questa partita la prima volta:
                                        LaunchedEffect(g.id) {
                                            if (!localScoresState.containsKey(g.id)) {
                                                // scarica weekstats e popola mappe locali
                                                loadWeekstatsForGame(g.id, g.squadraCasa, g.squadraOspite)
                                            }
                                        }

                                        // Otteniamo la inner map (potrebbe essere null se ancora in caricamento)
                                        val inner = localScoresState[g.id]

                                        if (inner == null) {
                                            // caricamento in corso
                                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            // Lista QBs per questa partita: prendi i QB dalla inner map keys (ma vogliamo visualizzare i dettagli dal allQBs)
                                            val qbsForGame = allQBs.filter { it.squadra == g.squadraCasa || it.squadra == g.squadraOspite }
                                            if (qbsForGame.isEmpty()) {
                                                Text("Nessun QB trovato per le squadre di questa partita.")
                                            } else {
                                                qbsForGame.forEach { qb ->
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(qb.nome, fontWeight = FontWeight.SemiBold)
                                                            Text(qb.squadra, style = MaterialTheme.typography.bodySmall)
                                                        }

                                                        val currentText = inner[qb.id] ?: ""
                                                        var textState by remember { mutableStateOf(currentText) }

                                                        OutlinedTextField(
                                                            value = textState,
                                                            onValueChange = { v ->
                                                                textState = v
                                                                // aggiorna la inner map in localScoresState
                                                                val m = localScoresState[g.id] ?: mutableMapOf()
                                                                m[qb.id] = v
                                                                localScoresState[g.id] = m
                                                            },
                                                            label = { Text("Punteggio") },
                                                            singleLine = true,
                                                            modifier = Modifier.width(120.dp)
                                                        )

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        TextButton(onClick = {
                                                            val txt = localScoresState[g.id]?.get(qb.id) ?: textState
                                                            val valnum = txt.toDoubleOrNull()
                                                            if (valnum != null) {
                                                                onSaveScore(g.id, qb.id, valnum)
                                                                onShowMessage("Punteggio salvato per ${qb.nome}")
                                                            } else {
                                                                onShowMessage("Punteggio non valido per ${qb.nome}")
                                                            }
                                                        }) {
                                                            Text("Salva")
                                                        }
                                                    }
                                                    Divider()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
        dismissButton = {}
    )
}
