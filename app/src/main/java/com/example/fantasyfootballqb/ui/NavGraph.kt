package com.example.fantasyfootballqb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fantasyfootballqb.navigation.Routes
import com.example.fantasyfootballqb.ui.screens.*

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Login.route,
        modifier = modifier
    ) {
        composable(Routes.Login.route) {
            LoginScreen(
                onLoginNavigate = { isAdmin ->
                    if (isAdmin) {
                        navController.navigate(Routes.Admin.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRegister = {
                    navController.navigate(Routes.Register.route)
                }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Register.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.Home.route) { HomeScreen() }
        composable(Routes.Team.route) { TeamScreen() }
        composable(Routes.Calendar.route) { CalendarScreen() }
        composable(Routes.Stats.route) { StatsScreen() }
        composable(Routes.Ranking.route) { RankingScreen() }
        composable(Routes.Profile.route) {
            ProfileScreen(onLogout = {
                navController.navigate(Routes.Login.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        // composable Admin screen
        composable(Routes.Admin.route) {
            AdminScreen(onLogout = {
                // assicuriamoci di pulire sessione e tornare a Login
                // puoi anche chiamare FirebaseAuth.getInstance().signOut() prima di navigare,
                // ma se il logout è gestito all'interno di Admin (o Profile) puoi omettere.
                navController.navigate(Routes.Login.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
    }
}

