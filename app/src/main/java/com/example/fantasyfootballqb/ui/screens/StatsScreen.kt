package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.StatRow
import com.example.fantasyfootballqb.ui.viewmodel.StatsViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val rows by vm.rows.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val availableWeeks by vm.availableWeeks.collectAsState()
    val availableTeams by vm.availableTeams.collectAsState()
    val selectedWeek by vm.selectedWeek.collectAsState()
    val selectedTeam by vm.selectedTeam.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    // bottom sheet visibility
    var showFilterSheet by remember { mutableStateOf(false) }

    // sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Top card: titolo + open filter button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Statistiche", style = MaterialTheme.typography.titleMedium)
                        val summary = buildString {
                            if (selectedWeek != null) append("Week ${selectedWeek}  ")
                            if (!selectedTeam.isNullOrBlank()) append("${selectedTeam}  ")
                            if (searchQuery.isNotBlank()) append("ricerca: \"${searchQuery}\"")
                        }
                        if (summary.isNotBlank()) {
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.List, contentDescription = "Apri filtri")
                    }
                }
            }

            // Table card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // header row -> cambia se siamo in modalità week-filter
                    if (selectedWeek != null) {
                        // Week filter active: show Giocatore | Team | Punteggio
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Giocatore", modifier = Modifier.weight(0.6f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Team", modifier = Modifier.weight(0.25f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Punteggio", modifier = Modifier.weight(0.15f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Default: Giocatore | Team | GP | PTOT | PPG
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Giocatore", modifier = Modifier.weight(0.45f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Team", modifier = Modifier.weight(0.2f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("GP", modifier = Modifier.weight(0.1f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("PTOT", modifier = Modifier.weight(0.15f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            Text("PPG", modifier = Modifier.weight(0.1f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (rows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("Nessuna statistica disponibile")
                        }
                    } else {
                        LazyColumn {
                            items(rows) { r ->
                                val idx = rows.indexOf(r)
                                val background = if (idx % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.12f) else Color.Transparent

                                if (selectedWeek != null) {
                                    // week-filtered row: Giocatore | Team | Punteggio
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(background)
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(r.nome, modifier = Modifier.weight(0.6f), fontSize = 15.sp)
                                        Text(r.squadra, modifier = Modifier.weight(0.25f))
                                        // Punteggio: se 0 mostra "-" altrimenti valore (intero)
                                        val puntText = if (r.ptot == 0.0) "-" else r.ptot.toInt().toString()
                                        Text(puntText, modifier = Modifier.weight(0.15f))
                                    }
                                } else {
                                    // default row: Giocatore | Team | GP | PTOT | PPG
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(background)
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(r.nome, modifier = Modifier.weight(0.45f), fontSize = 15.sp)
                                        Text(r.squadra, modifier = Modifier.weight(0.2f))
                                        Text(r.gp.toString(), modifier = Modifier.weight(0.1f))

                                        val ptotText = if (r.ptot == 0.0) "-" else r.ptot.toInt().toString()
                                        Text(ptotText, modifier = Modifier.weight(0.15f))

                                        val ppgText = if (r.ptot == 0.0) "-" else String.format("%.1f", r.ppg)
                                        Text(ppgText, modifier = Modifier.weight(0.1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (error != null) {
            LaunchedEffect(error) { vm.clearError() }
        }

        // --- Bottom sheet for filters ---
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
                modifier = Modifier.fillMaxWidth()
            ) {
                // content inside bottom sheet: scrollable column for mobile
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Filtri", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            // reset all filters, update VM and keep sheet open
                            vm.setWeekFilter(null)
                            vm.setTeamFilter(null)
                            vm.setSearchQuery("")
                        }) {
                            Text("Reset")
                        }
                    }

                    // Week selector
                    Column {
                        Text("Week", style = MaterialTheme.typography.bodyMedium)
                        WeekSelector(availableWeeks = availableWeeks, initial = selectedWeek) { week ->
                            vm.setWeekFilter(week)
                        }
                    }

                    // Team selector
                    Column {
                        Text("Squadra", style = MaterialTheme.typography.bodyMedium)
                        TeamSelector(availableTeams = availableTeams, initial = selectedTeam) { team ->
                            vm.setTeamFilter(team)
                        }
                    }

                    // Search field
                    Column {
                        Text("Cerca Giocatore", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cerca per nome...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(availableWeeks: List<Int>, initial: Int?, onWeekSelected: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = initial?.toString() ?: "Tutte le weeks"
    Box {
        Button(onClick = { expanded = true }) {
            Text(text = label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Tutte le weeks") }, onClick = {
                onWeekSelected(null); expanded = false
            })
            availableWeeks.forEach { w ->
                DropdownMenuItem(text = { Text("Week $w") }, onClick = {
                    onWeekSelected(w); expanded = false
                })
            }
        }
    }
}

@Composable
private fun TeamSelector(availableTeams: List<String>, initial: String?, onTeamSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = initial ?: "Tutte le squadre"
    Box {
        Button(onClick = { expanded = true }) {
            Text(text = label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Tutte le squadre") }, onClick = {
                onTeamSelected(null); expanded = false
            })
            availableTeams.forEach { t ->
                DropdownMenuItem(text = { Text(t) }, onClick = {
                    onTeamSelected(t); expanded = false
                })
            }
        }
    }
}
