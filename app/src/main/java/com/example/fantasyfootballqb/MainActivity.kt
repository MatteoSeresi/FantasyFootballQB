package com.example.fantasyfootballqb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.fantasyfootballqb.ui.NavGraph
import com.example.fantasyfootballqb.components.BottomBar
import com.example.fantasyfootballqb.navigation.Routes
import com.example.fantasyfootballqb.ui.theme.FantasyFootballTheme
import com.google.firebase.FirebaseApp
import com.example.fantasyfootballqb.components.AppTopBar


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            FantasyFootballTheme {
                FantasyApp()
            }
        }
    }
}

@Composable
fun FantasyApp() {
    val navController = rememberNavController()

    // route corrente
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // schermate dove mostrare la bottom bar / top bar
    val visibleRoutes = listOf(
        Routes.Home,
        Routes.Team,
        Routes.Calendar,
        Routes.Stats,
        Routes.Ranking,
        Routes.Profile
    )
    val showBars = visibleRoutes.any { it.route == currentRoute }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                if (showBars) {
                    AppTopBar()
                }
            },
            bottomBar = {
                if (showBars) {
                    BottomBar(navController = navController)
                }
            }
        ) { innerPadding ->
            NavGraph(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
