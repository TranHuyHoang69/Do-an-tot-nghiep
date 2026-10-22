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
import com.example.moneymatev2.presentation.recurring.RecurringTransactionScreen
import com.example.moneymatev2.presentation.reminder.ReminderScreen
import com.example.moneymatev2.presentation.settings.SettingScreen
import com.example.moneymatev2.presentation.profile.ProfileScreen
import com.example.moneymatev2.presentation.transaction.AddTransactionScreen
import com.example.moneymatev2.presentation.transaction.AddTransactionViewmodel
import com.example.moneymatev2.presentation.transaction.TransactionDetailScreen
import com.example.moneymatev2.presentation.transaction.EditTransactionScreen
import com.example.moneymatev2.presentation.transaction.TransactionEntryViewModel
import com.example.moneymatev2.presentation.statistics.StatisticsScreen
import com.example.moneymatev2.presentation.statistics.CategoryBreakdownScreen
import com.example.moneymatev2.presentation.statistics.MonthlyTrendsScreen
import com.example.moneymatev2.presentation.statistics.CategoryTransactionsScreen

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
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
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
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
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

            val selectedCategory by backStackEntry.savedStateHandle
                .getStateFlow<ArrayList<String>?>("selected_category_owner_and_id", null)
                .collectAsState()

            LaunchedEffect(selectedCategory) {
                selectedCategory?.takeIf { it.size == 2 }?.let { selection ->
                    viewModel.onCategoryResult(selection[0], selection[1])
                    backStackEntry.savedStateHandle["selected_category_owner_and_id"] = null
                }
            }

            AddTransactionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onManageCategoryClick = { navController.navigate(Screen.CategoryManagement.pickRoute()) }
            )
        }

        composable(Screen.TransactionDetail.route) { entry ->
            val id = checkNotNull(entry.arguments?.getString("transactionId"))
            TransactionDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditTransaction.createRoute(id)) { launchSingleTop = true } },
                onDeleted = { navController.popBackStack() },
                viewModel = hiltViewModel(entry)
            )
        }

        composable(Screen.EditTransaction.route) { entry ->
            val viewModel: TransactionEntryViewModel = hiltViewModel(entry)
            val selectedCategory by entry.savedStateHandle
                .getStateFlow<ArrayList<String>?>("selected_category_owner_and_id", null)
                .collectAsState()
            LaunchedEffect(selectedCategory) {
                selectedCategory?.takeIf { it.size == 2 }?.let {
                    viewModel.categoryResult(it[0], it[1])
                    entry.savedStateHandle["selected_category_owner_and_id"] = null
                }
            }
            EditTransactionScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onManageCategoryClick = { navController.navigate(Screen.CategoryManagement.pickRoute()) },
                viewModel = viewModel
            )
        }

        composable(
            route = Screen.CategoryManagement.route,
            arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val isPickMode = backStackEntry.arguments?.getBoolean("pick") ?: false

            ManagementCategoryScreen(
                onOpenDrawer = onOpenDrawer,
                onAddCategoryClick = { navController.navigate(Screen.AddCategory.route) },
                onCategoryClick = if (isPickMode) { category ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        "selected_category_owner_and_id", arrayListOf(category.userId, category.id))
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
            HistoryScreen(onOpenDrawer = onOpenDrawer,
                onTransactionClick = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) { launchSingleTop = true } })
        }

        composable(Screen.Budget.route) {
            BudgetScreen(onOpenDrawer = onOpenDrawer)
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onOpenDrawer = onOpenDrawer,
                onBreakdown = { start, end -> navController.navigate(Screen.StatisticsBreakdown.createRoute(start, end)) },
                onTrends = { navController.navigate(Screen.StatisticsTrends.route) },
                onCategory = { start, end, id -> navController.navigate(Screen.StatisticsCategory.createRoute(start, end, id)) }
            )
        }
        composable(Screen.StatisticsMonth.route, arguments = listOf(
            navArgument("start") { type = NavType.LongType }, navArgument("end") { type = NavType.LongType }
        )) {
            StatisticsScreen(
                onOpenDrawer = onOpenDrawer,
                onBreakdown = { start, end -> navController.navigate(Screen.StatisticsBreakdown.createRoute(start, end)) },
                onTrends = { navController.navigate(Screen.StatisticsTrends.route) },
                onCategory = { start, end, id -> navController.navigate(Screen.StatisticsCategory.createRoute(start, end, id)) }
            )
        }
        composable(Screen.StatisticsBreakdown.route, arguments = listOf(
            navArgument("start") { type = NavType.LongType }, navArgument("end") { type = NavType.LongType }
        )) {
            CategoryBreakdownScreen(
                onBack = { navController.popBackStack() },
                onCategory = { start, end, id -> navController.navigate(Screen.StatisticsCategory.createRoute(start, end, id)) }
            )
        }
        composable(Screen.StatisticsTrends.route) {
            MonthlyTrendsScreen(onBack = { navController.popBackStack() }, onMonth = { start, end ->
                navController.navigate(Screen.StatisticsMonth.createRoute(start, end))
            })
        }
        composable(Screen.StatisticsCategory.route, arguments = listOf(
            navArgument("start") { type = NavType.LongType }, navArgument("end") { type = NavType.LongType },
            navArgument("categoryId") { type = NavType.StringType }
        )) { entry ->
            CategoryTransactionsScreen(
                categoryId = checkNotNull(entry.arguments?.getString("categoryId")),
                onBack = { navController.popBackStack() },
                onTransaction = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) { launchSingleTop = true } }
            )
        }

        composable(Screen.Customization.route) {
            SettingScreen(
                navController = navController,
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onOpenDrawer = onOpenDrawer,
                onRequireLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Profile.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Reminder.route) {
            ReminderScreen(
                onOpenDrawer = onOpenDrawer
            )
        }
        composable(
            Screen.RecurringTransactions.route
        ) {
            RecurringTransactionScreen(
                onOpenDrawer = onOpenDrawer
            )
        }
    }
}
