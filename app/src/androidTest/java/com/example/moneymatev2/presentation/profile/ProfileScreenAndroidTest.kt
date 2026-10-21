package com.example.moneymatev2.presentation.profile

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.UserRepository
import com.example.moneymatev2.domain.usecase.auth.SignOutUseCase
import com.example.moneymatev2.navigation.Screen
import com.example.moneymatev2.presentation.theme.MoneyMatev2Theme
import com.example.moneymatev2.ui.components.AppDrawer
import com.example.moneymatev2.ui.components.DrawerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenAndroidTest {
    @get:Rule val rule = createComposeRule()

    @Test fun menuAndDeletionDialogsDoNotDeleteAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FakeAuth()
        val vm = ProfileViewModel(auth, FakeUsers())
        var menuOpens = 0
        rule.setContent {
            MoneyMatev2Theme {
                ProfileScreen(onOpenDrawer = { menuOpens++ }, onRequireLogin = {}, viewModel = vm)
            }
        }

        rule.onNodeWithText("Alice").assertIsDisplayed()
        rule.onNodeWithText("alice@example.com").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.profile_menu)).performClick()
        rule.runOnIdle { assertEquals(1, menuOpens) }

        rule.onNodeWithText(context.getString(R.string.profile_delete)).performClick()
        rule.onNodeWithText(context.getString(R.string.profile_delete_confirmation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        rule.onNodeWithText(context.getString(R.string.profile_delete_confirmation)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, auth.signOutCalls) }

        rule.onNodeWithText(context.getString(R.string.profile_delete)).performClick()
        rule.onNodeWithText(context.getString(R.string.profile_delete_confirmation)).assertIsDisplayed()
        rule.onNodeWithTag("profile_delete_confirm").performClick()
        rule.onNodeWithText(context.getString(R.string.profile_deletion_unavailable)).assertIsDisplayed()
        rule.onNodeWithTag("profile_delete_final").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, auth.signOutCalls) }
    }

    @Test fun drawerProfileItemNavigatesToProfileRoute() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FakeAuth()
        val users = FakeUsers()
        val drawerVm = DrawerViewModel(auth, SignOutUseCase(auth, users))
        val routes = mutableListOf<String>()
        rule.setContent {
            MoneyMatev2Theme {
                AppDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    scope = rememberCoroutineScope(),
                    totalBalance = 0L,
                    themeColor = Color.Blue,
                    currentRoute = Screen.Home.route,
                    onLogout = {},
                    onNavigate = { routes += it },
                    viewModel = drawerVm
                ) {}
            }
        }
        rule.onNodeWithText(context.getString(R.string.drawer_profile)).performClick()
        rule.runOnIdle { assertEquals(Screen.Profile.route, routes.last()) }
    }

    @Test fun drawerAccountHeaderNavigatesToProfileRoute() {
        val auth = FakeAuth()
        val users = FakeUsers()
        val drawerVm = DrawerViewModel(auth, SignOutUseCase(auth, users))
        val routes = mutableListOf<String>()
        rule.setContent {
            MoneyMatev2Theme {
                AppDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    scope = rememberCoroutineScope(),
                    totalBalance = 0L,
                    themeColor = Color.Blue,
                    currentRoute = Screen.Home.route,
                    onLogout = {},
                    onNavigate = { routes += it },
                    viewModel = drawerVm
                ) {}
            }
        }
        rule.onNodeWithText("Alice").performClick()
        rule.runOnIdle { assertEquals(Screen.Profile.route, routes.last()) }
    }

    @Test fun logoutRemovesPreviousAccountFromVisibleProfile() {
        val auth = FakeAuth()
        var loginRequests = 0
        val vm = ProfileViewModel(auth, FakeUsers())
        rule.setContent {
            MoneyMatev2Theme {
                ProfileScreen(onOpenDrawer = {}, onRequireLogin = { loginRequests++ }, viewModel = vm)
            }
        }
        rule.onNodeWithText("Alice").assertIsDisplayed()
        rule.runOnIdle { auth.account.value = null }
        rule.waitForIdle()
        rule.onNodeWithText("Alice").assertDoesNotExist()
        rule.runOnIdle { assertTrue(loginRequests > 0) }
    }

    private class FakeAuth : AuthRepository {
        val account = MutableStateFlow<UserModel?>(UserModel("a", "alice@example.com", "Alice", null))
        var signOutCalls = 0
        override fun observeAuthState(): Flow<UserModel?> = account
        override fun getCurrentUserId(): String? = account.value?.userId
        override fun getCurrentProviderIds(): Set<String> = setOf("password")
        override suspend fun signIn(email: String, password: String): AppResult<UserModel> = error("unused")
        override suspend fun signUp(email: String, password: String, displayName: String): AppResult<UserModel> = error("unused")
        override suspend fun signInWithGoogle(idToken: String): AppResult<UserModel> = error("unused")
        override suspend fun signOut() { signOutCalls++; account.value = null }
    }

    private class FakeUsers : UserRepository {
        override fun getCurrentUser(userId: String): Flow<UserModel?> = MutableStateFlow(null)
        override suspend fun upsertUser(user: UserModel) = Unit
        override suspend fun signOut() = Unit
    }
}
