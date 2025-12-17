package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fantasyfootballqb.ui.viewmodel.RankingViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@Composable
fun RankingScreen(vm: RankingViewModel = viewModel()) {
    val ranking by vm.ranking.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(error!!)
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // header card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Classifica", fontWeight = FontWeight.Bold)
                    }
                }

                // table header
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pos", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.SemiBold)
                        Text("Username", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
                        Text("Pts", modifier = Modifier.weight(0.2f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                    }
                }

                // ranking list
                Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = true), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (ranking.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nessun dato di classifica")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ranking) { index, entry ->
                                // card per riga
                                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))) {
                                    Row(modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "${index + 1}", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.SemiBold)
                                        Text(text = entry.username, modifier = Modifier.weight(0.6f))
                                        // mostra punteggio come intero se è avvalorabile come intero, altrimenti 1 decimale
                                        val totalText = if (entry.total % 1.0 == 0.0) {
                                            entry.total.toLong().toString()
                                        } else {
                                            String.format("%.1f", entry.total)
                                        }
                                        Text(text = totalText, modifier = Modifier.weight(0.2f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { coroutineScope.launch { vm.reload() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Aggiorna classifica")
                }
            }
        }
    }
}
