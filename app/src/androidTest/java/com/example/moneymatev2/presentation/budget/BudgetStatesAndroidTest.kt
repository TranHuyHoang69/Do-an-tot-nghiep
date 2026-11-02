package com.example.moneymatev2.presentation.budget

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.R
import com.example.moneymatev2.presentation.theme.MoneyMatev2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class BudgetStatesAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun emptyStateOffersAddActionOnlyWhenAvailable() {
        var taps = 0
        compose.setContent {
            MoneyMatev2Theme {
                EmptyBudgetState(canAdd = true, hasCategories = true, onAdd = { taps++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.no_budget_yet)).assertExists()
        compose.onNodeWithText(context.getString(R.string.budget_empty_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.add_budget)).performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun loadErrorHasRetryActionInDarkTheme() {
        var retries = 0
        compose.setContent {
            MoneyMatev2Theme(darkTheme = true) { BudgetLoadError(onRetry = { retries++ }) }
        }
        compose.onNodeWithText(context.getString(R.string.budget_load_failed)).assertExists()
        compose.onNodeWithText(context.getString(R.string.budget_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun monthSwitcherLabelsBothNavigationButtons() {
        val anchor = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 1) }.timeInMillis
        compose.setContent {
            MoneyMatev2Theme {
                MonthSwitcher(anchorDate = anchor, onPrevious = {}, onNext = {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.budget_previous_month)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.budget_next_month)).assertExists()
    }
}
