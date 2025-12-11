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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.fantasyfootballqb.ui.NavGraph
import com.example.fantasyfootballqb.ui.components.BottomBar
import com.example.fantasyfootballqb.navigation.Routes
import com.example.fantasyfootballqb.ui.theme.FantasyFootballTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // schermate dove mostrare la bottom bar
    val bottomBarRoutes = listOf(
        Routes.Home.route,
        Routes.Team.route,
        Routes.Calendar.route,
        Routes.Stats.route,
        Routes.Ranking.route,
        Routes.Profile.route
    )
    val showBottomBar = currentRoute in bottomBarRoutes

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomBar(navController = navController)
                }
            }
        ) { innerPadding ->
            // Passiamo il padding del Scaffold al NavHost tramite modifier
            NavGraph(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewApp() {
    FantasyFootballTheme {
        FantasyApp()
    }
}
