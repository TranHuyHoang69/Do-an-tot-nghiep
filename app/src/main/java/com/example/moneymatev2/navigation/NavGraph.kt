package com.example.moneymatev2.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.moneymatev2.presentation.auth.LoginScreen
import com.example.moneymatev2.presentation.auth.RegisterScreen
import com.example.moneymatev2.presentation.transaction.AddTransactionScreen
import com.example.moneymatev2.presentation.home.HomeScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ){
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route){
            RegisterScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onSeeMoreDetail = {period, anchorDate, type, customEnd ->
                    navController.navigate(Screen.History.createRoute(period, anchorDate, type, customEnd))
                },
                onMenuClick = {}
            )
        }


        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument(HomeNavKeys.SELECTED_PERIOD) { type = NavType.StringType },
                navArgument(HomeNavKeys.ANCHOR_DATE) { type = NavType.LongType },
                navArgument(HomeNavKeys.SELECTED_TYPE) { type = NavType.StringType },
                navArgument(HomeNavKeys.CUSTOM_END) { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
//            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}