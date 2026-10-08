package com.example.moneymatev2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.navigation.Screen
import com.example.moneymatev2.presentation.theme.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    scope: CoroutineScope,
    totalBalance: Long, // lấy từ HomeViewModel/caller, Drawer KHÔNG tự tính lại để tránh trùng lặp logic (đã có HomeViewModel.totalBalance)
    themeColor: Color,
    currentRoute: String?,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DrawerViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val user by viewModel.currentUser.collectAsState()
    val isLoggedIn = user != null

    val home = StringResource(StringRes.drawer_home)
    val history = StringResource(StringRes.drawer_history)
    val categoryManagement = StringResource(StringRes.drawer_manage_categories)
    val settings = StringResource(StringRes.drawer_settings)
    val logout = StringResource(StringRes.drawer_logout)
    val budget = StringResource(StringRes.drawer_budget)
    val reminders = StringResource(StringRes.drawer_reminders)
    val statistics = StringResource(StringRes.drawer_statistics)
    val profile = StringResource(StringRes.drawer_profile)
    val security = StringResource(StringRes.drawer_security)
    val recurringTransactions = StringResource(StringRes.drawer_recurring_transactions)
    val customization = StringResource(StringRes.drawer_customization)
    val login = StringResource(StringRes.login)
    val guestMode = StringResource(StringRes.drawer_guest_mode)
    val notLogin = StringResource(StringRes.drawer_not_login)

    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = themeColor.copy(alpha = 0.15f),
        selectedIconColor = themeColor,
        selectedTextColor = themeColor,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurface
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Thông tin tài khoản ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                onNavigate(if (isLoggedIn) Screen.Profile.route else Screen.Login.route)
                            }
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(themeColor.copy(0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {

                            val initial = if (isLoggedIn) user?.displayName?.take(1) ?: "U" else "?"
                            Text(initial, fontWeight = FontWeight.Bold, color = themeColor, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isLoggedIn) user?.displayName ?: "..." else guestMode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLoggedIn) {
                            Text(
                                text = "${CurrencyFormatter.formatFull(totalBalance)} đ",
                                color = if (totalBalance >= 0) themeColor else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = notLogin,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))


                    DrawerItem(
                        label = home, icon = Icons.Default.Home,
                        selected = currentRoute == Screen.Home.route, colors = drawerItemColors,
                        enabled = true
                    ) { scope.launch { drawerState.close() }; onNavigate(Screen.Home.route) }

                    DrawerItem(
                        label = history, icon = Icons.Default.History,
                        selected = currentRoute?.startsWith("history") == true, colors = drawerItemColors,
                        enabled = true
                    ) { scope.launch { drawerState.close() }; onNavigate(Screen.History.createFreshRoute()) }

                    DrawerItem(
                        label = categoryManagement, icon = Icons.AutoMirrored.Filled.List,
                        selected = currentRoute?.startsWith("category_management") == true, colors = drawerItemColors,
                        enabled = true
                    ) { scope.launch { drawerState.close() }; onNavigate(Screen.CategoryManagement.manageRoute()) }

                    // --- Mục CHƯA build — hiện placeholder, khoá, không điều hướng ---
                    DrawerItem(
                        label = budget, icon = Icons.Default.AccountBalanceWallet,
                        selected = currentRoute == Screen.Budget.route, colors = drawerItemColors,
                        enabled = true
                    ) { scope.launch { drawerState.close() }; onNavigate(Screen.Budget.route) }

                    DrawerItem(
                        label = reminders, icon = Icons.Default.Update,
                        selected = false, colors = drawerItemColors, enabled = false
                    ) {}

                    DrawerItem(
                        label = recurringTransactions, icon = Icons.Default.Repeat,
                        selected = false, colors = drawerItemColors, enabled = false
                    ) {}

                    DrawerItem(
                        label = profile, icon = Icons.Default.Person,
                        selected = false, colors = drawerItemColors, enabled = false
                    ) {}

                    DrawerItem(
                        label = statistics, icon = Icons.Default.BarChart,
                        selected = false, colors = drawerItemColors, enabled = false
                    ) {}

                    DrawerItem(
                        label = security, icon = Icons.Default.Lock,
                        selected = false, colors = drawerItemColors, enabled = false
                    ) {}

                    DrawerItem(
                        label = customization, icon = Icons.Default.Settings,
                        selected = currentRoute == Screen.Customization.route, colors = drawerItemColors,
                        enabled = true
                    ) { scope.launch { drawerState.close() }; onNavigate(Screen.Customization.route) }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isLoggedIn) {
                        NavigationDrawerItem(
                            label = { Text(text = logout) },
                            selected = false,
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            onClick = {
                                scope.launch { drawerState.close() }
                                viewModel.logout()
                                onLogout()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.error,
                                unselectedTextColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(16.dp)) {
                            NavigationDrawerItem(
                                label = { Text(text = login) },
                                selected = false,
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    onNavigate(Screen.Login.route)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        content = content
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colors: NavigationDrawerItemColors,
    enabled: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = if (enabled) label else "$label ",
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        selected = selected,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        colors = colors,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}