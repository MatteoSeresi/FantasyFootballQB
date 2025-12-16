package com.example.fantasyfootballqb.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fantasyfootballqb.R

/**
 * Schermata Admin (UI only). Tutti i bottoni sono stub tranne il logout.
 *
 * onLogout: callback eseguito quando si preme LOGOUT (chiude sessione / ritorna al login)
 */
@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Cerca la risorsa drawable "logo" dinamicamente; se non esiste resId == 0
    val logoResId = remember { context.resources.getIdentifier("logo", "drawable", context.packageName) }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header grande blu con titolo + logo (come mock)
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
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f),
                            fontSize = 26.sp
                        )

                        // se esiste la risorsa drawable, la mostriamo
                        if (logoResId != 0) {
                            Image(
                                painter = painterResource(id = logoResId),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Admin badge rosso
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF5350)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AMMINISTRATORE",
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // Card Utenti
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "Utenti:", style = MaterialTheme.typography.titleSmall)
                        FilledTonalButton(
                            onClick = { /* TODO: Modifica dati utente */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Modifica dati utente")
                        }
                        FilledTonalButton(
                            onClick = { /* TODO: Modifica formazione utente */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Modifica formazione utente")
                        }
                    }
                }

                // Card Calcolo week
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "Calcolo week:", style = MaterialTheme.typography.titleSmall)

                        // Row with "Week" label and dropdown placeholder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text("Week:", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("numweek")
                                    // sostituito icona placeholder con spazio (se vuoi icona dropdown cambia qui)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }

                        FilledTonalButton(onClick = { /* TODO: Modifica dati partite */ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica dati partite")
                        }
                        FilledTonalButton(onClick = { /* TODO: Modifica punteggi QBs */ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modifica punteggi QBs")
                        }
                        FilledTonalButton(onClick = { /* TODO: Visualizza dati Week */ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Visualizza dati Week: numweek")
                        }

                        // Calc week black button
                        Button(
                            onClick = { /* TODO: Calcola week */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Text("CALCOLA WEEK", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Logout button in fondo (blue)
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("LOGOUT", color = MaterialTheme.colorScheme.onPrimary)
                }
            } // end Column
        } // end Box
    } // end Scaffold
}
