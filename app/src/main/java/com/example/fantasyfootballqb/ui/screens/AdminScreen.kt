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
import com.example.fantasyfootballqb.ui.viewmodel.UserFormationRow
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.models.QB
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.fantasyfootballqb.components.AppTopBar

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
    val weekCalculated by vm.weekCalculated.collectAsState()
    val userFormations by vm.userFormations.collectAsState()

    var showUsersDialog by remember { mutableStateOf(false) }
    var showModifyGamesDialog by remember { mutableStateOf(false) }
    var showModifyQBsDialog by remember { mutableStateOf(false) }
    var showModifyFormationsDialog by remember { mutableStateOf(false) }
    var editingFormationRow by remember { mutableStateOf<UserFormationRow?>(null) }
    var modifyFormationsWeek by remember { mutableStateOf<Int?>(selectedWeek) }

    var showConfirmCalculateDialog by remember { mutableStateOf(false) }
    var missingDataList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMissingDataDialog by remember { mutableStateOf(false) }

    val snackHost = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(selectedWeek) {
        selectedWeek?.let { vm.observeWeekCalculated(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Fantasy Football",
                modifier = Modifier.fillMaxWidth(),
                logoSize = 75.dp,
                barHeight = 100.dp
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // importantissimo: rispettare innerPadding
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // badge "AMMINISTRATORE"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF5350)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AMMINISTRATORE", color = Color.White, modifier = Modifier.padding(10.dp))
                }

                // Users card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Utenti:", style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { showUsersDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica dati utente")
                        }

                        Button(onClick = {
                            modifyFormationsWeek = selectedWeek ?: weeks.firstOrNull()
                            vm.loadUserFormationsForWeek(modifyFormationsWeek)
                            showModifyFormationsDialog = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica formazione utente")
                        }
                    }
                }

                // Calcolo week card (il resto rimane invariato) ...
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Calcolo week:", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                                Text("Week:", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            var expanded by remember { mutableStateOf(false) }
                            val label = selectedWeek?.toString() ?: "Seleziona week"
                            Button(onClick = { expanded = true }) { Text(label) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Seleziona week") }, onClick = {
                                    vm.setSelectedWeek(null); expanded = false
                                })
                                weeks.forEach { w ->
                                    DropdownMenuItem(text = { Text("Week $w") }, onClick = {
                                        vm.setSelectedWeek(w); expanded = false
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

                        Button(
                            onClick = {
                                val w = selectedWeek
                                if (w == null) {
                                    coroutineScope.launch { snackHost.showSnackbar("Seleziona prima una week") }
                                    return@Button
                                }
                                coroutineScope.launch {
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

            // -- dialogs (users / modify games / qbs / formations ecc.) --
            if (showUsersDialog) {
                UsersDialog(users = users.filter { !it.isAdmin }, onDismiss = { showUsersDialog = false }, onSave = { u,  newUsername, newNomeTeam ->
                    vm.updateUserData(u.uid, newUsername, newNomeTeam)
                })
            }

            if (showModifyGamesDialog) {
                val gamesForWeek = selectedWeek?.let { gamesByWeek[it] } ?: emptyList()
                ModifyGamesDialog(week = selectedWeek, games = gamesForWeek, onDismiss = { showModifyGamesDialog = false }, onSaveGame = { gameId, _, risultato ->
                    val played = risultato?.isNotBlank() == true
                    vm.updateGame(gameId, played, risultato)
                })
            }

            if (showModifyQBsDialog) {
                val gamesForWeek = selectedWeek?.let { gamesByWeek[it] } ?: emptyList()
                ModifyQBsDialog(week = selectedWeek, games = gamesForWeek, allQBs = qbs, onDismiss = { showModifyQBsDialog = false }, onSaveScore = { gameId, qbId, score ->
                    vm.setQBScore(gameId, qbId, score)
                }, onShowMessage = { msg -> coroutineScope.launch { snackHost.showSnackbar(msg) } })
            }

            if (showModifyFormationsDialog) {
                ModifyFormationsDialog(
                    selectedWeek = modifyFormationsWeek,
                    availableWeeks = weeks,
                    userFormations = userFormations,
                    allUsers = users,
                    onDismiss = { showModifyFormationsDialog = false },
                    onWeekSelected = { newWeek ->
                        modifyFormationsWeek = newWeek
                        vm.loadUserFormationsForWeek(newWeek)
                    },
                    onEdit = { row -> editingFormationRow = row }
                )
            }

            if (editingFormationRow != null) {
                val targetWeek = modifyFormationsWeek
                if (targetWeek != null) {
                    EditFormationDialog(userFormation = editingFormationRow!!, week = targetWeek, availableWeeks = weeks, qbs = qbs, onDismiss = {
                        editingFormationRow = null
                        vm.loadUserFormationsForWeek(modifyFormationsWeek)
                    }, onSave = { uid, weekNum, qbIds ->
                        vm.updateUserFormation(uid, weekNum, qbIds)
                        editingFormationRow = null
                        vm.loadUserFormationsForWeek(modifyFormationsWeek)
                    })
                }
            }

            if (showMissingDataDialog) {
                AlertDialog(onDismissRequest = { showMissingDataDialog = false }, title = { Text("Dati mancanti") }, text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        Text("Non è possibile calcolare la week: mancano i seguenti dati:")
                        Spacer(modifier = Modifier.height(8.dp))
                        missingDataList.forEach { item -> Text("- $item", style = MaterialTheme.typography.bodySmall) }
                    }
                }, confirmButton = { TextButton(onClick = { showMissingDataDialog = false }) { Text("Chiudi") } }, dismissButton = {})
            }

            if (showConfirmCalculateDialog) {
                AlertDialog(onDismissRequest = { showConfirmCalculateDialog = false }, title = { Text("Conferma calcolo") }, text = { Text("Sei sicuro di voler calcolare la week ${selectedWeek ?: "-"}? Questa operazione la renderà definitiva.") }, confirmButton = {
                    TextButton(onClick = {
                        showConfirmCalculateDialog = false
                        val w = selectedWeek
                        if (w != null) vm.calculateWeek(w)
                    }) { Text("Conferma") }
                }, dismissButton = { TextButton(onClick = { showConfirmCalculateDialog = false }) { Text("Annulla") } })
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}


/**
 * UsersDialog: mostra lista utenti e permette modifica.
 */
@Composable
private fun UsersDialog(users: List<AdminUser>, onDismiss: () -> Unit, onSave: (AdminUser, String, String) -> Unit) {
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
        var newNomeTeam by remember { mutableStateOf(u.nomeTeam ?: "") }

        AlertDialog(onDismissRequest = { editingUser = null }, title = { Text("Modifica utente") }, text = {
            Column {
                OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") })
                OutlinedTextField(value = newNomeTeam, onValueChange = { newNomeTeam = it }, label = { Text("Nome squadra") })
            }
        }, confirmButton = {
            TextButton(onClick = {
                onSave(u, newUsername.trim(), newNomeTeam.trim())
                editingUser = null
            }) { Text("Salva") }
        }, dismissButton = {
            TextButton(onClick = { editingUser = null }) { Text("Annulla") }
        })
    }
}

/**
 * ModifyGamesDialog: modifica risultato -> imposta partitaGiocata true se risultato non vuoto
 */

@Composable
private fun ModifyGamesDialog(
    week: Int?,
    games: List<Game>,
    onDismiss: () -> Unit,
    onSaveGame: (String, Boolean, String?) -> Unit
) {
    var localGames by remember { mutableStateOf(games.map { it.copy() }) }
    LaunchedEffect(games) { localGames = games.map { it.copy() } }

    val resultRegex = remember { Regex("""^\s*\d{1,2}\s*-\s*\d{1,2}\s*$""") }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica dati partite - Week ${week ?: "-"}") }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (localGames.isEmpty()) {
                Text("Nessuna partita per questa week")
            } else {
                LazyColumn {
                    items(localGames) { g ->
                        // Per ogni riga manteniamo stato locale del risultato ed eventuale messaggio di errore
                        val gameState = localGames.firstOrNull { it.id == g.id } ?: g
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${g.squadraCasa} - ${g.squadraOspite}", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var localResult by remember { mutableStateOf(gameState.risultato ?: "") }
                                var localError by remember { mutableStateOf<String?>(null) }

                                OutlinedTextField(
                                    value = localResult,
                                    onValueChange = { newText ->
                                        localResult = newText
                                        // reset errore on change
                                        if (localError != null) localError = null
                                        localGames = localGames.map {
                                            if (it.id == g.id) it.copy(risultato = if (newText.isBlank()) null else newText) else it
                                        }
                                    },
                                    label = { Text("Risultato partita") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val gameIndex = localGames.indexOfFirst { it.id == g.id }
                            Text("Formato richiesto: \"x - x\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    // recuperiamo lo stato corrente della riga
                                    val gs = localGames.firstOrNull { it.id == g.id }
                                    if (gs != null) {
                                        val res = gs.risultato
                                        // se vuoto => non giocata
                                        if (res.isNullOrBlank()) {
                                            onSaveGame(gs.id, false, null)
                                        } else {
                                            val txt = res.trim()
                                            if (resultRegex.matches(txt)) {
                                                // formato OK: salva
                                                onSaveGame(gs.id, true, txt)
                                            } else {
                                            }
                                        }
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
 * ModifyQBsDialog: carica i weekstats per la partita selezionata e mostra i QBs associati.
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
    var expandedGameId by remember { mutableStateOf<String?>(null) }
    val localScoresState = remember { mutableStateMapOf<String, MutableMap<String, String>>() }

    suspend fun loadWeekstatsForGame(gameId: String, teamHome: String, teamAway: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val snap = db.collection("weekstats")
                .whereEqualTo("game_id", gameId)
                .get()
                .await()

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

            val qbsForGame = allQBs.filter { it.squadra == teamHome || it.squadra == teamAway }
            val inner = mutableMapOf<String, String>()
            qbsForGame.forEach { qb -> inner[qb.id] = scoresFromDB[qb.id] ?: "" }
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
                                        expandedGameId = if (expandedGameId == g.id) null else g.id
                                    }) {
                                        Text(if (expandedGameId == g.id) "Chiudi" else "Apri")
                                    }
                                }

                                if (expandedGameId == g.id) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (!g.partitaGiocata) {
                                        Text("Impossibile modificare i punteggi: la partita non è ancora stata disputata.")
                                    } else {
                                        LaunchedEffect(g.id) {
                                            if (!localScoresState.containsKey(g.id)) {
                                                loadWeekstatsForGame(g.id, g.squadraCasa, g.squadraOspite)
                                            }
                                        }

                                        val inner = localScoresState[g.id]
                                        if (inner == null) {
                                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
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

/* ---------------- ModifyFormationsDialog & EditFormationDialog ---------------- */

@Composable
private fun ModifyFormationsDialog(
    selectedWeek: Int?,
    availableWeeks: List<Int>,
    userFormations: List<UserFormationRow>,
    allUsers: List<AdminUser>,
    onDismiss: () -> Unit,
    onWeekSelected: (Int?) -> Unit,
    onEdit: (UserFormationRow) -> Unit
) {
    // show only non-admin users: map by uid
    val nonAdminUsersMap = allUsers.filter { !it.isAdmin }.associateBy { it.uid }

    // local week state inside this dialog (separata dal selectedWeek principale)
    var selectedWeekLocal by remember { mutableStateOf<Int?>(selectedWeek) }
    // when selectedWeekLocal changes, notify parent so it can load data
    LaunchedEffect(selectedWeekLocal) {
        onWeekSelected(selectedWeekLocal)
    }

    // dropdown expanded state
    var expandedWeek by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = {
        Column {
            Text("Modifica formazioni utenti")
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Week:", modifier = Modifier.padding(end = 8.dp))
                Box {
                    Button(onClick = { expandedWeek = true }) {
                        Text(selectedWeekLocal?.toString() ?: "Seleziona week")
                    }
                    DropdownMenu(expanded = expandedWeek, onDismissRequest = { expandedWeek = false }) {
                        DropdownMenuItem(text = { Text("Seleziona week") }, onClick = {
                            selectedWeekLocal = null
                            expandedWeek = false
                        })
                        availableWeeks.forEach { w ->
                            DropdownMenuItem(text = { Text("Week $w") }, onClick = {
                                selectedWeekLocal = w
                                expandedWeek = false
                            })
                        }
                    }
                }
            }
        }
    }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedWeekLocal == null) {
                Text("Seleziona una week per vedere e modificare le formations.")
            } else {
                if (userFormations.isEmpty()) {
                    Text("Nessuna formation trovata per questa week")
                } else {
                    LazyColumn {
                        items(userFormations) { uf ->
                            val user = nonAdminUsersMap[uf.uid] ?: return@items
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.username ?: user.email, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // show total score only (no QB list)
                                    Text("Punteggio totale: ${uf.totalWeekScore.toInt()}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { onEdit(uf) }) {
                                    Text("Modifica")
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

@Composable
private fun EditFormationDialog(
    userFormation: UserFormationRow,
    week: Int,
    availableWeeks: List<Int>,
    qbs: List<QB>,
    onDismiss: () -> Unit,
    onSave: (uid: String, week: Int, qbIds: List<String>) -> Unit
) {
    // slots for the 3 QB ids
    val slots = remember { mutableStateListOf<String>() }
    LaunchedEffect(userFormation) {
        slots.clear()
        val initial = userFormation.qbIds
        for (i in 0 until 3) slots.add(initial.getOrNull(i) ?: "")
    }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    // local week selection inside edit dialog (so you can change the week where to save this formation)
    var weekLocal by remember { mutableStateOf(week) }
    var expandedWeek by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica formazione: ${userFormation.username}") }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Week:", modifier = Modifier.padding(end = 8.dp))
                Box {
                    Button(onClick = { expandedWeek = true }) {
                        Text(weekLocal.toString())
                    }
                    DropdownMenu(expanded = expandedWeek, onDismissRequest = { expandedWeek = false }) {
                        availableWeeks.forEach { w ->
                            DropdownMenuItem(text = { Text("Week $w") }, onClick = {
                                weekLocal = w
                                expandedWeek = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (i in 0 until 3) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Slot ${i + 1}:", modifier = Modifier.width(80.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val currentId = slots.getOrNull(i) ?: ""
                    val currentLabel = qbs.firstOrNull { it.id == currentId }?.nome ?: "Seleziona QB"

                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(currentLabel)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            val candidates = qbs.filter { it.stato == "Titolare" || it.stato.isBlank() }
                            candidates.forEach { qb ->
                                DropdownMenuItem(text = { Text("${qb.nome} (${qb.squadra})") }, onClick = {
                                    slots[i] = qb.id
                                    expanded = false
                                })
                            }
                            // allow clearing selection per slot
                            DropdownMenuItem(text = { Text("Rimuovi selezione") }, onClick = {
                                slots[i] = ""
                                expanded = false
                            })
                        }
                    }
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val ids = slots.map { it.trim() }.filter { it.isNotBlank() }
            if (ids.size != 3) {
                errorMsg = "Devi selezionare esattamente 3 QB."
                return@TextButton
            }
            if (ids.toSet().size != 3) {
                errorMsg = "I 3 QB devono essere distinti."
                return@TextButton
            }
            onSave(userFormation.uid, weekLocal, ids)
        }) {
            Text("Salva")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text("Annulla") }
    })
}
