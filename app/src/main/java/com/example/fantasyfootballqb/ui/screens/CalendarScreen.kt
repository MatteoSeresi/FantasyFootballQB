package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.models.Game
import com.example.fantasyfootballqb.ui.viewmodel.CalendarViewModel
import com.example.fantasyfootballqb.ui.viewmodel.QBStat
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(calendarViewModel: CalendarViewModel = viewModel()) {
    val gamesByWeek by calendarViewModel.gamesByWeek.collectAsState()
    val weeks by calendarViewModel.weeks.collectAsState()
    val loading by calendarViewModel.loading.collectAsState()
    val error by calendarViewModel.error.collectAsState()

    var selectedWeek by remember { mutableStateOf(weeks.firstOrNull() ?: 1) }

    // dialog state (rimane sulla stessa schermata)
    var showStatsDialog by remember { mutableStateOf(false) }
    var currentGameForDialog by remember { mutableStateOf<Game?>(null) }
    var currentStats by remember { mutableStateOf<List<QBStat>?>(null) }
    var dialogLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(weeks) {
        if (weeks.isNotEmpty() && selectedWeek !in weeks) {
            selectedWeek = weeks.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Weeks row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Weeks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val weekButtons = if (weeks.isNotEmpty()) weeks else (1..5).toList()
                        weekButtons.forEach { w ->
                            val selected = w == selectedWeek
                            Button(
                                onClick = { selectedWeek = w },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                            ) {
                                Text(text = w.toString(), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Games list for selectedWeek
            val gamesForWeek = gamesByWeek[selectedWeek] ?: emptyList()
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (gamesForWeek.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nessuna partita per la week $selectedWeek", textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), state = rememberLazyListState()) {
                        items(gamesForWeek) { game ->
                            GameRow(game = game, onStatsClick = {
                                // carica i dati e apri dialog
                                currentGameForDialog = game
                                showStatsDialog = true
                                dialogLoading = true
                                coroutineScope.launch {
                                    val stats = calendarViewModel.getStatsForGame(game.id, game.partitaGiocata)
                                    currentStats = stats
                                    dialogLoading = false
                                }
                            })
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (error != null) {
            LaunchedEffect(error) {
                calendarViewModel.clearError()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 6.dp, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (showStatsDialog && currentGameForDialog != null) {
            val g = currentGameForDialog!!
            AlertDialog(
                onDismissRequest = {
                    showStatsDialog = false
                    currentGameForDialog = null
                    currentStats = null
                },
                title = { Text("${g.squadraCasa} vs ${g.squadraOspite}") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!g.partitaGiocata) {
                            Text("Partita ancora da disputare")
                        } else {
                            if (dialogLoading) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                if (currentStats == null || currentStats!!.isEmpty()) {
                                    Text("Punteggi dei giocatori non ancora inseriti")
                                } else {
                                    val grouped = currentStats!!.groupBy { it.qb.squadra }
                                    grouped.forEach { (team, list) ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = "$team:", style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        list.forEach { stat ->
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "- ${stat.qb.nome}:", modifier = Modifier.weight(1f))
                                                val scoreText = stat.punteggio?.let { String.format("%.1f pts", it) } ?: "panchina"
                                                Text(text = scoreText)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showStatsDialog = false
                        currentGameForDialog = null
                        currentStats = null
                    }) {
                        Text("Chiudi")
                    }
                }
            )
        }
    }
}

@Composable
private fun GameRow(game: Game, onStatsClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = game.squadraCasa, style = MaterialTheme.typography.bodyLarge)
            }

            val scoreText = if (game.partitaGiocata) (game.risultato ?: "-") else "-"
            Text(text = scoreText, modifier = Modifier.padding(horizontal = 8.dp))

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text = game.squadraOspite, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onStatsClick) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Dettagli")
            }
        }
    }
}