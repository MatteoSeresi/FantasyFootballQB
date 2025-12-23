package com.example.fantasyfootballqb.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.fantasyfootballqb.navigation.Routes

@Composable
fun BottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val items = listOf(
        Routes.Home,
        Routes.Team,
        Routes.Calendar,
        Routes.Stats,
        Routes.Ranking,
        Routes.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(modifier = modifier) {
        items.forEach { Routes ->
            NavigationBarItem(
                icon = {
                    Routes.icon?.let { Icon(it, contentDescription = Routes.title) }
                },
                label = { Text(Routes.title) },
                selected = currentRoute == Routes.route,
                onClick = {
                    navController.navigate(Routes.route) {
                        // evita duplicati nello stack
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            )
        }
    }
}
