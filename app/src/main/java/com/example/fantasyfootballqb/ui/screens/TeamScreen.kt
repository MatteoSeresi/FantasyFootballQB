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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch

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

    var selectedWeek by remember { mutableStateOf(weekDefault) }

    // selected slots: 3 nullable
    var selectedSlots by remember { mutableStateOf<List<QB?>>(listOf(null, null, null)) }
    var slotDialogOpen by remember { mutableStateOf(false) }
    var slotIndexForDialog by remember { mutableStateOf<Int?>(null) }
    var showConfirmInsertDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // load formation + observe weekCalculated on week change
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
                            (1..18).forEach { w ->
                                DropdownMenuItem(text = { Text(w.toString()) }, onClick = {
                                    selectedWeek = w
                                    expanded = false
                                })
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (formationQBs.isEmpty()) {
                            // Selection phase
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
                                                        // formato NO - ARI
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

                        } else {
                            // Formation exists: show formation + matchup and total
                            Text("Formazione:", fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val scoresMap = formationScores.associateBy({ it.qb.id }, { it })
                                formationQBs.forEach { qb ->
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("- ${qb.nome}", fontWeight = FontWeight.SemiBold)
                                                // format TEAM - OPP
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
