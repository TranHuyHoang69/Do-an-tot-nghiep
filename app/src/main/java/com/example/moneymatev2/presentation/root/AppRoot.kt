package com.example.moneymatev2.presentation.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.moneymatev2.navigation.NavGraph
import com.example.moneymatev2.navigation.Screen
import com.example.moneymatev2.presentation.home.HomeViewModel
import com.example.moneymatev2.presentation.theme.MoneyMatev2Theme
import com.example.moneymatev2.ui.components.AppDrawer
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    rootViewModel: AppRootViewModel = hiltViewModel()
) {
    val authState by rootViewModel.authState.collectAsState()
    val themeMode by rootViewModel.themeMode.collectAsState()


    val darkTheme = when (themeMode) {
        "dark" -> true
        else -> false
    }

    MoneyMatev2Theme(
        darkTheme = darkTheme
    ) {
        if (authState is AuthStartupState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val currentRoute =
        navController.currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val totalBalance by homeViewModel.totalBalance.collectAsState()

    AppDrawer(
        drawerState = drawerState,
        scope = scope,
        totalBalance = totalBalance,
        themeColor = MaterialTheme.colorScheme.primary,
        currentRoute = currentRoute,
        onLogout = {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) {
                    inclusive = true
                }
            }
        },
        onNavigate = { route ->
            navController.navigate(route)
        }
    ) {
        NavGraph(
            navController = navController,
            startDestination = Screen.Home.route,
            onOpenDrawer = {
                scope.launch {
                    drawerState.open()
                }
            }
        )
    }
}