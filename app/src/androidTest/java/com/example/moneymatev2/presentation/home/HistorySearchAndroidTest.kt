package com.example.moneymatev2.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.navigation.HomeNavKeys
import com.example.moneymatev2.presentation.theme.MoneyMatev2Theme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class HistorySearchAndroidTest {
    @get:Rule val rule = createComposeRule()

    @Test fun headerSearchFiltersAndRestoresCurrentHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val categories = listOf("groceries" to "Groceries", "taxi" to "Taxi").map { (id, name) ->
            CategoryModel("test-user", id, null, name, TransactionType.EXPENSE,
                "ic_food", "#123456", false, false)
        }
        val transactions = listOf("coffee" to "groceries", "ride" to "taxi").map { (note, id) ->
            TransactionModel(note, "test-user", TransactionType.EXPENSE, Money(50_000, "VND"),
                id, note, now, now, false)
        }
        val auth = fake<AuthRepository> { name ->
            if (name == "observeAuthState") flowOf(UserModel("test-user", null, null, null))
            else error("Unexpected AuthRepository call: $name")
        }
        val transactionRepo = fake<TransactionRepository> { name ->
            if (name == "getAllTransactions") flowOf(transactions)
            else error("Unexpected TransactionRepository call: $name")
        }
        val categoryRepo = fake<CategoryRepository> { name ->
            if (name == "getAllCategories") flowOf(categories)
            else error("Unexpected CategoryRepository call: $name")
        }
        val vm = HistoryViewModel(SavedStateHandle(mapOf(
            HomeNavKeys.SELECTED_PERIOD to HomePeriod.MONTH.name,
            HomeNavKeys.SELECTED_TYPE to TransactionType.EXPENSE.name,
            HomeNavKeys.ANCHOR_DATE to now
        )), GetTransactionWithCategoryUseCase(transactionRepo, categoryRepo, auth))

        rule.setContent { MoneyMatev2Theme { HistoryScreen(vm, onOpenDrawer = {}) } }
        rule.waitUntil(5_000) { !vm.isLoading.value }
        rule.onNodeWithText("Groceries").assertIsDisplayed()
        rule.onNodeWithText("Taxi").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.month)).assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription(context.getString(R.string.history_previous_period)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.history_next_period)).assertExists()

        rule.onNodeWithContentDescription(context.getString(R.string.history_search_open)).performClick()
        rule.onNodeWithText(context.getString(R.string.history_search_guide)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("coffee")
        rule.onNodeWithText(context.getString(R.string.history_search_guide)).assertDoesNotExist()
        rule.waitUntil(5_000) { !vm.isLoading.value && vm.groupedItems.value.size == 1 }
        rule.onNodeWithText("Groceries").assertIsDisplayed()
        rule.onNodeWithText("Taxi").assertDoesNotExist()

        rule.onNode(hasSetTextAction()).performTextReplacement("unmatched")
        rule.waitUntil(5_000) { !vm.isLoading.value && vm.groupedItems.value.isEmpty() }
        rule.onNodeWithText(context.getString(R.string.history_search_no_results)).assertIsDisplayed()

        rule.onNode(hasSetTextAction()).performTextReplacement("200000-50000")
        rule.waitUntil(5_000) {
            !vm.isLoading.value && vm.searchError.value == HistorySearchQuery.InvalidAmountRange
        }
        rule.onNodeWithText(context.getString(R.string.history_search_invalid_range)).assertIsDisplayed()

        rule.onNodeWithContentDescription(context.getString(R.string.history_search_clear)).performClick()
        rule.onNodeWithText(context.getString(R.string.history_search_guide)).assertIsDisplayed()
        rule.waitUntil(5_000) { !vm.isLoading.value && vm.groupedItems.value.size == 2 }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Taxi"))
        rule.onNodeWithText("Taxi").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.history_search_back)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.history_search_open)).assertIsDisplayed()
        rule.runOnIdle {
            assertFalse(vm.searchOpen.value)
            assertEquals(HomePeriod.MONTH, vm.selectedPeriod)
            assertEquals(TransactionType.EXPENSE, vm.selectedType)
            assertEquals(now, vm.anchorDate)
        }
    }

    private inline fun <reified T : Any> fake(crossinline response: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            response(method.name)
        } as T
}
