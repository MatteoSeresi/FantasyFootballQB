package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.fantasyfootballqb.ui.viewmodel.StatsViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.FilterList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val rows by vm.rows.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val availableWeeks by vm.availableWeeks.collectAsState()
    val availableTeams by vm.availableTeams.collectAsState()
    val selectedWeek by vm.selectedWeek.collectAsState()
    val selectedTeams by vm.selectedTeams.collectAsState()
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
                            if (selectedTeams.isNotEmpty()) append(selectedTeams.joinToString(", ") + "  ")
                            if (searchQuery.isNotBlank()) append("ricerca: \"${searchQuery}\"")
                        }
                        if (summary.isNotBlank()) {
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Apri filtri")
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
                            Text(
                                "Giocatore",
                                modifier = Modifier.weight(0.65f),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Team",
                                modifier = Modifier.weight(0.15f),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(modifier = Modifier.weight(0.20f), contentAlignment = Alignment.Center) {
                                Text(
                                    "Points",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Default: Giocatore | Team | GP | PTOT | PPG
                        // NOTE: Team column shrunk (0.12) and PPG enlarged (0.13) to avoid wrap on decimals.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Giocatore",
                                modifier = Modifier.weight(0.50f),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Team",
                                modifier = Modifier.weight(0.12f),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp

                            )
                            Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
                                Text(
                                    "PG",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                                Text(
                                    "PTOT",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            Box(modifier = Modifier.weight(0.13f), contentAlignment = Alignment.Center) {
                                Text(
                                    "PxP",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (rows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("Nessuna statistica disponibile")
                        }
                    } else {
                        LazyColumn {
                            itemsIndexed(rows) { index, r ->
                                val background = if (index % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.12f) else Color.Transparent

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
                                        Text(
                                            r.nome,
                                            modifier = Modifier.weight(0.65f),
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            r.squadra,
                                            modifier = Modifier.weight(0.15f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // Punteggio: se 0 mostra "-" altrimenti valore (intero) - centrato
                                        val puntText = if (r.ptot == 0.0) "-" else r.ptot.toInt().toString()
                                        Box(modifier = Modifier.weight(0.20f), contentAlignment = Alignment.Center) {
                                            Text(puntText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
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
                                        Text(
                                            r.nome,
                                            modifier = Modifier.weight(0.50f),
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            r.squadra,
                                            modifier = Modifier.weight(0.12f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // GP centered
                                        Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
                                            Text(r.gp.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        // PTOT centered
                                        val ptotText = if (r.ptot == 0.0) "-" else r.ptot.toInt().toString()
                                        Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                                            Text(ptotText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        // PPG centered
                                        val ppgText = if (r.ptot == 0.0) "-" else String.format("%.1f", r.ppg)
                                        Box(modifier = Modifier.weight(0.13f), contentAlignment = Alignment.Center) {
                                            Text(ppgText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
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

        // --- Bottom sheet for filters (with 4x8 team grid) ---
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Filtro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            vm.setWeekFilter(null)
                            vm.resetTeamsSelection()
                            vm.setSearchQuery("")
                        }) {
                            Text("Reset")
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

                    // Week selector
                    Column {
                        Text("Week", style = MaterialTheme.typography.bodyMedium)
                        WeekSelector(availableWeeks = availableWeeks, initial = selectedWeek) { week ->
                            vm.setWeekFilter(week)
                        }
                    }

                    // Team grid multi-select
                    Column {
                        Text("Squadre", style = MaterialTheme.typography.bodyMedium)
                        TeamsGridMulti(
                            allTeams = if (availableTeams.size >= 32) availableTeams else buildDefaultTeamList(availableTeams),
                            selected = selectedTeams,
                            onToggle = { t -> vm.toggleTeamSelection(t) }
                        )

                    }
                }
            }
        }
    }
}

/* ---------------- helpers (unchanged) ---------------- */

private fun buildDefaultTeamList(existing: List<String>): List<String> {
    val result = existing.toMutableList()
    var i = 1
    while (result.size < 32) {
        val code = "T${i}"
        if (!result.contains(code)) result.add(code)
        i++
    }
    return result.take(32)
}

@Composable
private fun TeamsGridMulti(
    allTeams: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val teams = if (allTeams.size >= 32) allTeams.take(32) else buildDefaultTeamList(allTeams)
    val rows = teams.chunked(8)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { code ->
                    val isSelected = selected.contains(code)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .defaultMinSize(minWidth = 36.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onToggle(code) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                            Text(
                                text = code,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(
    availableWeeks: List<Int>,
    initial: Int?,
    onWeekSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = initial?.let { "Week $it" } ?: "Tutte le weeks"

    Box {
        Button(onClick = { expanded = true }) {
            Text(text = label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Tutte le weeks") },
                onClick = {
                    onWeekSelected(null)
                    expanded = false
                }
            )

            availableWeeks.forEach { w ->
                DropdownMenuItem(
                    text = { Text("Week $w") },
                    onClick = {
                        onWeekSelected(w)
                        expanded = false
                    }
                )
            }
        }
    }
}
