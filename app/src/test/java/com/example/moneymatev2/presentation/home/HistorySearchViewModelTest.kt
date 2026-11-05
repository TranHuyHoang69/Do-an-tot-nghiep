package com.example.moneymatev2.presentation.home

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.example.moneymatev2.navigation.HomeNavKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HistorySearchViewModelTest {
    @Test fun sourceErrorEndsLoadingWithoutShowingEmptySearchResult() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val useCase = mock(GetTransactionWithCategoryUseCase::class.java)
        `when`(useCase.invoke()).thenReturn(flow<List<TransactionWithCategory>> {
            throw IllegalStateException("Test source error")
        })
        val vm = HistoryViewModel(SavedStateHandle(), useCase)
        try {
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.loadError.value)
            assertFalse(vm.isLoading.value)
            assertTrue(vm.groupedItems.value.isEmpty())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun searchDoesNotShowEmptyStateBeforeHistorySourceLoads() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val useCase = mock(GetTransactionWithCategoryUseCase::class.java)
        `when`(useCase.invoke()).thenReturn(MutableSharedFlow<List<TransactionWithCategory>>())
        val vm = HistoryViewModel(SavedStateHandle(), useCase)
        try {
            vm.onSearchCategoryLabelsChange(mapOf(1 to "Food"))
            vm.onSearchQueryChange("food")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.isLoading.value)
            assertTrue(vm.groupedItems.value.isEmpty())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun openingClearingAndClosingSearchKeepsPeriodTypeAndAnchor() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val useCase = mock(GetTransactionWithCategoryUseCase::class.java)
        `when`(useCase.invoke()).thenReturn(flowOf(emptyList()))
        val anchor = 1_762_000_000_000L
        val vm = HistoryViewModel(SavedStateHandle(mapOf(
            HomeNavKeys.SELECTED_PERIOD to HomePeriod.MONTH.name,
            HomeNavKeys.SELECTED_TYPE to TransactionType.INCOME.name,
            HomeNavKeys.ANCHOR_DATE to anchor)), useCase)
        try {
            vm.openSearch()
            vm.onSearchQueryChange("salary")
            assertTrue(vm.searchOpen.value)
            assertEquals("salary", vm.searchQuery.value)
            assertEquals(HomePeriod.MONTH, vm.selectedPeriod)
            assertEquals(TransactionType.INCOME, vm.selectedType)
            assertEquals(anchor, vm.anchorDate)

            vm.onPeriodChange(HomePeriod.YEAR)
            vm.onTypeChange(TransactionType.EXPENSE)
            vm.onSearchQueryChange("")
            assertEquals("", vm.searchQuery.value)
            assertTrue(vm.searchOpen.value)
            vm.closeSearch()
            assertFalse(vm.searchOpen.value)
            assertEquals("", vm.searchQuery.value)
            assertEquals(HomePeriod.YEAR, vm.selectedPeriod)
            assertEquals(TransactionType.EXPENSE, vm.selectedType)
            assertEquals(anchor, vm.anchorDate)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
