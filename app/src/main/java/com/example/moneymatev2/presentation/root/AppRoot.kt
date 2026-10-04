package com.example.moneymatev2.presentation.root

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
import com.example.moneymatev2.ui.components.AppDrawer
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    rootViewModel: AppRootViewModel = hiltViewModel()
) {
    val authState by rootViewModel.authState.collectAsState()

    // Chỉ dùng authState để chờ Firebase khôi phục session lúc khởi động (tránh Drawer
    // chớp nhoáng hiện "Khách" rồi lại đổi thành tên user ngay sau đó). Sau khi qua Loading,
    // Home LUÔN là startDestination -- không rẽ nhánh theo đăng nhập hay chưa, để tránh
    // việc Compose coi 2 trạng thái là "nhánh khác nhau" và build lại toàn bộ AppContent
    // (gây nhảy về Login mỗi khi đăng xuất).
    if (authState is AuthStartupState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val totalBalance by homeViewModel.totalBalance.collectAsState()

    AppDrawer(
        drawerState = drawerState,
        scope = scope,
        totalBalance = totalBalance,
        themeColor = MaterialTheme.colorScheme.primary,
        currentRoute = currentRoute,
        onLogout = {
            // Đăng xuất -> về lại Home (ở trạng thái khách), KHÔNG về Login.
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        },
        onNavigate = { route -> navController.navigate(route) }
    ) {
        NavGraph(
            navController = navController,
            startDestination = Screen.Home.route, // luôn luôn là Home
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}