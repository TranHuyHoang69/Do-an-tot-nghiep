package com.example.moneymatev2.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.moneymatev2.presentation.auth.LoginScreen
import com.example.moneymatev2.presentation.auth.RegisterScreen
import com.example.moneymatev2.presentation.budget.BudgetScreen
import com.example.moneymatev2.presentation.category.AddCategoryScreen
import com.example.moneymatev2.presentation.category.ManagementCategoryScreen
import com.example.moneymatev2.presentation.home.HistoryScreen
import com.example.moneymatev2.presentation.home.HomeScreen
import com.example.moneymatev2.presentation.transaction.AddTransactionScreen
import com.example.moneymatev2.presentation.transaction.AddTransactionViewmodel

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    onOpenDrawer: () -> Unit = {} // mặc định rỗng -- cho tới khi MainActivity cung cấp DrawerState thật
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
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
                onAddTransaction = { navController.navigate(Screen.AddTransaction.route) },
                onSeeMoreDetail = { period, anchorDate, type, customEnd ->
                    navController.navigate(Screen.History.createRoute(period, anchorDate, type.name, customEnd))
                },
                onOpenHistoryIcon = { navController.navigate(Screen.History.createFreshRoute()) },
                onMenuClick = onOpenDrawer // nối thật vào Drawer, thay cho {} rỗng trước đây
            )
        }

        composable(Screen.AddTransaction.route) { backStackEntry ->
            val viewModel: AddTransactionViewmodel = hiltViewModel(backStackEntry)

            val selectedCategoryId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("selected_category_id", null)
                .collectAsState()

            LaunchedEffect(selectedCategoryId) {
                selectedCategoryId?.let { id ->
                    viewModel.onCategorySelected(id)
                    backStackEntry.savedStateHandle["selected_category_id"] = null
                }
            }

            AddTransactionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onManageCategoryClick = { navController.navigate(Screen.CategoryManagement.pickRoute()) }
            )
        }

        composable(
            route = Screen.CategoryManagement.route,
            arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val isPickMode = backStackEntry.arguments?.getBoolean("pick") ?: false

            ManagementCategoryScreen(
                onBack = { navController.popBackStack() },
                onAddCategoryClick = { navController.navigate(Screen.AddCategory.route) },
                onCategoryClick = if (isPickMode) { category ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_category_id", category.id)
                    navController.popBackStack()
                } else null // chế độ quản lý -> tap không làm gì, chỉ long-press mới có tác dụng
            )
        }

        composable(Screen.AddCategory.route) {
            AddCategoryScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument(HomeNavKeys.SELECTED_PERIOD) { type = NavType.StringType; defaultValue = "DAY" },
                navArgument(HomeNavKeys.ANCHOR_DATE) { type = NavType.LongType; defaultValue = System.currentTimeMillis() },
                navArgument(HomeNavKeys.SELECTED_TYPE) { type = NavType.StringType; defaultValue = "EXPENSE" },
                navArgument(HomeNavKeys.CUSTOM_END) { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        // Mới thêm: route Budget
        composable(Screen.Budget.route) {
            BudgetScreen(onOpenDrawer = onOpenDrawer)
        }
    }
}