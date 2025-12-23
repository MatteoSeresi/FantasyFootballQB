package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.models.QB
import com.example.fantasyfootballqb.ui.viewmodel.TeamViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun TeamScreen(
    weekDefault: Int = 1,
    vm: TeamViewModel = viewModel()
) {
    val availableQBs by vm.availableQBs.collectAsState()
    val formationQBs by vm.formationQBs.collectAsState()
    val formationLocked by vm.formationLocked.collectAsState()
    val formationScores by vm.formationScores.collectAsState()
    val userTeamName by vm.userTeamName.collectAsState()
    val weekCalculated by vm.weekCalculated.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val gamesForWeek by vm.gamesForWeek.collectAsState()
    val availableWeeks by vm.availableWeeks.collectAsState()

    // selectedWeek: inizialmente weekDefault, poi sincronizziamo con availableWeeks se presenti
    var selectedWeek by remember { mutableStateOf(weekDefault) }

    // firstUncalculatedWeek (prima week presente nelle games che NON è ancora partitaCalcolata)
    var firstUncalculatedWeek by remember { mutableStateOf<Int?>(null) }
    var computingFirstUncalculated by remember { mutableStateOf(false) }

    // selected slots: 3 nullable
    var selectedSlots by remember { mutableStateOf<List<QB?>>(listOf(null, null, null)) }
    var slotDialogOpen by remember { mutableStateOf(false) }
    var slotIndexForDialog by remember { mutableStateOf<Int?>(null) }
    var showConfirmInsertDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val db = remember { FirebaseFirestore.getInstance() }

    // se cambia availableWeeks e non è vuota, assicurati che selectedWeek sia valido;
    // se non è valido, imposta la week di default (qui la massima disponibile)
    LaunchedEffect(availableWeeks) {
        if (availableWeeks.isNotEmpty()) {
            val defaultWeek = availableWeeks.maxOrNull() ?: availableWeeks.first()
            if (!availableWeeks.contains(selectedWeek)) {
                selectedWeek = defaultWeek
            }
        }
        // ricalcola firstUncalculated quando availableWeeks cambia
        computeFirstUncalculatedWeek(db, availableWeeks) { found, computing ->
            firstUncalculatedWeek = found
            computingFirstUncalculated = computing
        }
    }

    // quando l'utente cambia la selectedWeek ricarichiamo formation e giochi e osserviamo il flag weekCalculated
    LaunchedEffect(selectedWeek) {
        vm.loadUserFormationForWeek(selectedWeek)
        vm.loadGamesForWeek(selectedWeek)
        vm.observeWeekCalculated(selectedWeek)
    }

    // when formation changes populate slots for display (not for deciding final)
    LaunchedEffect(formationQBs) {
        if (formationQBs.isNotEmpty()) {
            selectedSlots = formationQBs.toMutableList().let { list ->
                while (list.size < 3) list.add(QB(id = "", nome = "", squadra = "", stato = ""))
                list.take(3)
            }
        } else {
            selectedSlots = listOf(null, null, null)
        }
    }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            coroutineScope.launch { snackbarHostState.showSnackbar(error!!) }
            vm.clearError()
        }
    }

    // calcola totale punteggi (sommo solo punteggi > 0)
    val totalScore: Double? = remember(formationScores) {
        val s = formationScores.mapNotNull { it.score?.takeIf { v -> v > 0.0 } }.sum()
        if (s > 0.0) s else null
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("Gestione Squadra", fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Team: ${userTeamName ?: "—"}", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Week:", modifier = Modifier.padding(end = 8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Button(onClick = { expanded = true }) { Text(selectedWeek.toString()) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (availableWeeks.isEmpty()) {
                                // fallback: mostra 1..18 se non ci sono games nel db
                                (1..18).forEach { w ->
                                    DropdownMenuItem(text = { Text(w.toString()) }, onClick = {
                                        selectedWeek = w
                                        expanded = false
                                    })
                                }
                            } else {
                                // mostra solo le week effettive trovate in Firestore
                                availableWeeks.forEach { w ->
                                    DropdownMenuItem(text = { Text(w.toString()) }, onClick = {
                                        selectedWeek = w
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Calcolo regole di inserimento:
                        // - se firstUncalculatedWeek != null: solo quella week può ricevere inserimenti
                        // - se firstUncalculatedWeek == null: fallback -> usiamo !weekCalculated per la selectedWeek
                        val canInsertForThisWeek = if (firstUncalculatedWeek != null) {
                            selectedWeek == firstUncalculatedWeek
                        } else {
                            !weekCalculated
                        }

                        // Caso: utente non ha formazione
                        if (formationQBs.isEmpty()) {
                            when {
                                // 1) week non presente nelle availableWeeks -> messaggio esplicito
                                availableWeeks.isNotEmpty() && !availableWeeks.contains(selectedWeek) -> {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Week non disponibile", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "La week $selectedWeek non è presente nel calendario (le partite sono disponibili fino alla week ${availableWeeks.maxOrNull()}).",
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // 2) settimana già calcolata -> non si può inserire retroattivamente
                                weekCalculated -> {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("WEEK DISPUTATA", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (userTeamName.isNullOrBlank()) {
                                            Text(
                                                "Sei un nuovo utente e ti sei registrato dopo che questa week è stata disputata. " +
                                                        "Non è possibile schierare la formazione retroattivamente per questa week.",
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            Text(
                                                "La possibilità di schierare la formazione per questa week è terminata. " +
                                                        "Non è possibile schierare la formazione retroattivamente per questa week.",
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // 3) week non calcolata, ma non è la firstUncalculated -> mostra messaggio quale week è in corso
                                !canInsertForThisWeek -> {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Inserimento non consentito", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (computingFirstUncalculated) {
                                            Text("Verifica in corso sulle week disponibili...", textAlign = TextAlign.Center)
                                        } else {
                                            if (firstUncalculatedWeek != null) {
                                                Text(
                                                    "Non è possibile schierare la formazione per la week $selectedWeek perché è ancora in corso la week ${firstUncalculatedWeek}. " +
                                                            "Per poter inserire la formazione devi selezionare la week in corso (${firstUncalculatedWeek}).",
                                                    textAlign = TextAlign.Center
                                                )
                                            } else {
                                                // fallback generico
                                                Text(
                                                    "Non è possibile schierare la formazione per la week $selectedWeek in questo momento.",
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4) week non calcolata e consentita -> fase di selezione
                                else -> {
                                    // Selection phase (utente può scegliere)
                                    Text("Seleziona i 3 giocatori da schierare:", fontWeight = FontWeight.SemiBold)

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        selectedSlots.forEachIndexed { idx, qb ->
                                            Card(modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    slotIndexForDialog = idx
                                                    slotDialogOpen = true
                                                }, shape = RoundedCornerShape(8.dp)) {
                                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    if (qb != null && qb.id.isNotEmpty()) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("- ${qb.nome}", fontWeight = FontWeight.SemiBold)
                                                            // mostra anche contro quale squadra giocherà (se disponibile)
                                                            val game = gamesForWeek.firstOrNull { it.squadraCasa == qb.squadra || it.squadraOspite == qb.squadra }
                                                            val oppText = game?.let {
                                                                "${it.squadraCasa} - ${it.squadraOspite}"
                                                            } ?: "Avversario non disponibile"
                                                            Text("${oppText}", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Text("Cambia")
                                                    } else {
                                                        Box(modifier = Modifier.size(44.dp).background(Color.LightGray, shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                                            Text("+")
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Aggiungi QB")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Tocca uno slot (+) per scegliere un QB tra i titolari.", style = MaterialTheme.typography.bodySmall)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = {
                                        val filled = selectedSlots.filterNotNull().filter { it.id.isNotEmpty() }
                                        if (filled.size != 3) {
                                            coroutineScope.launch { snackbarHostState.showSnackbar("Devi selezionare 3 QB") }
                                            return@Button
                                        }
                                        val ids = filled.map { it.id }
                                        if (ids.toSet().size != 3) {
                                            coroutineScope.launch { snackbarHostState.showSnackbar("I 3 QB devono essere diversi") }
                                            return@Button
                                        }
                                        showConfirmInsertDialog = true
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("INSERISCI LA FORMAZIONE")
                                    }
                                }
                            }
                        } else {
                            // Caso: l'utente ha già una formazione (mostriamo la formazione e lo stato basato su weekCalculated)
                            Text("Formazione:", fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val scoresMap = formationScores.associateBy({ it.qb.id }, { it })
                                formationQBs.forEach { qb ->
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("- ${qb.nome}", fontWeight = FontWeight.SemiBold)
                                                val info = scoresMap[qb.id]
                                                val opp = info?.opponentTeam
                                                val matchup = if (!opp.isNullOrBlank()) "${qb.squadra} - $opp" else "${qb.squadra} - ?"
                                                Text(matchup, style = MaterialTheme.typography.bodySmall)
                                            }
                                            val score = scoresMap[qb.id]?.score
                                            val display = if (score == null || score == 0.0) "-" else String.format("%.1f", score)
                                            Text("pt: $display")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // show total
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Totale: ", fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = totalScore?.let { String.format("%.1f", it) } ?: "-",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (!weekCalculated) {
                                    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                        Text("ATTENDI IL CALCOLO DELLA WEEK", modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                                    }
                                } else {
                                    Button(onClick = { /* nulla per ora */ }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) {
                                        Text("WEEK DISPUTATA", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QB selection dialog
    if (slotDialogOpen && slotIndexForDialog != null) {
        val idx = slotIndexForDialog!!
        AlertDialog(onDismissRequest = { slotDialogOpen = false; slotIndexForDialog = null }, title = { Text("Scegli QB per slot ${idx + 1}") }, text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text("Titolari disponibili:", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(availableQBs) { qb ->
                        // risolvi avversaria usando gamesForWeek
                        val game = gamesForWeek.firstOrNull { it.squadraCasa == qb.squadra || it.squadraOspite == qb.squadra }
                        val oppText = game?.let { other ->
                            if (other.squadraCasa == qb.squadra) other.squadraOspite else other.squadraCasa
                        } ?: "N/D"

                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val new = selectedSlots.toMutableList()
                                new[idx] = qb
                                selectedSlots = new.toList()
                                slotDialogOpen = false
                                slotIndexForDialog = null
                            }
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(qb.nome, fontWeight = FontWeight.SemiBold)
                                Text("${qb.squadra} vs $oppText", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Seleziona")
                        }
                        Divider()
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            val new = selectedSlots.toMutableList()
                            new[idx] = null
                            selectedSlots = new.toList()
                            slotDialogOpen = false
                            slotIndexForDialog = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Rimuovi QB da questo slot", color = Color.Red)
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { slotDialogOpen = false; slotIndexForDialog = null }) { Text("Chiudi") }
        }, dismissButton = {})
    }

    // confirm insert dialog
    if (showConfirmInsertDialog) {
        AlertDialog(onDismissRequest = { showConfirmInsertDialog = false }, title = { Text("Conferma inserimento") }, text = {
            Text("Sei sicuro di inserire la formazione? Una volta inserita non potrai più modificarla.")
        }, confirmButton = {
            TextButton(onClick = {
                showConfirmInsertDialog = false
                val filled = selectedSlots.filterNotNull().filter { it.id.isNotEmpty() }
                val ids = filled.map { it.id }
                vm.submitFormation(selectedWeek, ids)
            }) { Text("Conferma") }
        }, dismissButton = {
            TextButton(onClick = { showConfirmInsertDialog = false }) { Text("Annulla") }
        })
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Coroutine helper: calcola la prima week (nell'ordine di availableWeeks)
 * per cui esiste almeno una partita con partitaCalcolata != true.
 *
 * - se availableWeeks è vuoto -> ritorna null
 * - imposta il callback con (foundWeek?, computingFlag)
 */
private fun computeFirstUncalculatedWeek(
    db: FirebaseFirestore,
    availableWeeks: List<Int>,
    onResult: (Int?, Boolean) -> Unit
) {
    // launch a coroutine via rememberCoroutineScope from caller (we cannot access it here),
    // so instead caller uses LaunchedEffect and invokes this suspend logic.
    // To make it simple, we start a coroutine on the common pool:
    kotlinx.coroutines.GlobalScope.launch {
        try {
            onResult(null, true)
            if (availableWeeks.isEmpty()) {
                onResult(null, false)
                return@launch
            }
            // iterate in ascending order
            val sorted = availableWeeks.sorted()
            var found: Int? = null
            for (w in sorted) {
                val snaps = db.collection("games")
                    .whereEqualTo("weekNumber", w)
                    .get()
                    .await()
                val docs = snaps.documents
                if (docs.isEmpty()) {
                    // if no games for this week, skip
                    continue
                }
                // if any doc has partitaCalcolata != true => this week is not completely calculated
                val anyNotCalculated = docs.any { it.getBoolean("partitaCalcolata") != true }
                if (anyNotCalculated) {
                    found = w
                    break
                }
            }
            onResult(found, false)
        } catch (e: Exception) {
            // on error, return null and stop computing
            onResult(null, false)
        }
    }
}
