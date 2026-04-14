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
    // NavHost definisce il contenitore in cui verranno renderizzate le schermate
    // La destinazione di partenza (startDestination) è sempre la schermata di Login
    NavHost(
        navController = navController,
        startDestination = Routes.Login.route,
        modifier = modifier
    ) {
        //Gestisce il login
        composable(Routes.Login.route) {
            LoginScreen(
                onLoginNavigate = { isAdmin ->
                    if (isAdmin) {
                        // Se l'utente è un amministratore, vai alla dashboard Admin
                        navController.navigate(Routes.Admin.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        // Se l'utente è standard, vai alla Home
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRegister = {
                    // Naviga verso la registrazione senza rimuovere il login dallo stack, per permettere di tornare indietro con il tasto back.
                    navController.navigate(Routes.Register.route)
                }
            )
        }

        //Gestiscce la registrazione
        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    // Dopo la registrazione, l'utente è autenticato e va direttamente alla Home.
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

        //Schermate principali
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
        composable(Routes.Admin.route) {
            AdminScreen(onLogout = {
                navController.navigate(Routes.Login.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
    }
}

