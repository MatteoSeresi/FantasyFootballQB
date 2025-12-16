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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.StatRow
import com.example.fantasyfootballqb.ui.viewmodel.StatsViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val rowsAll by vm.rows.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    // Filtra fuori i giocatori con GP == 0 (non convocati / senza weekstats)
    val rows = remember(rowsAll) { rowsAll.filter { it.gp > 0 } }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Statistiche", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // filtro non implementato per ora (placeholder)
                    IconButton(onClick = { /* placeholder per filtro */ }) {
                        Icon(Icons.Default.List, contentDescription = "Filtro")
                    }
                }
            }

            // table
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // header row (blue)
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

                    if (rows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("Nessuna statistica disponibile")
                        }
                    } else {
                        LazyColumn {
                            items(rows) { r ->
                                val background = if (rows.indexOf(r) % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.12f) else Color.Transparent
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

                                    // PTOT: se = 0 mostra "-" (richiesta)
                                    val ptotText = if (r.ptot == 0.0) "-" else r.ptot.toInt().toString()
                                    Text(ptotText, modifier = Modifier.weight(0.15f))

                                    // PPG: se PTOT == 0 mostra "-" altrimenti mostra con 1 decimale
                                    val ppgText = if (r.ptot == 0.0) "-" else String.format("%.1f", r.ppg)
                                    Text(ppgText, modifier = Modifier.weight(0.1f))
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
    }
}
