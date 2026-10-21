package com.example.moneymatev2.presentation

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.*
import com.example.moneymatev2.domain.repository.*
import com.example.moneymatev2.domain.usecase.GetMonthlyBudgetOverviewUseCase
import com.example.moneymatev2.domain.usecase.category.DeleteCategoryUseCase
import com.example.moneymatev2.domain.usecase.category.CreateCategoryUseCase
import com.example.moneymatev2.domain.usecase.transaction.CreateTransactionUseCase
import com.example.moneymatev2.presentation.budget.BudgetViewModel
import com.example.moneymatev2.presentation.category.CategoryManagementViewModel
import com.example.moneymatev2.presentation.category.CategoryDeletePreview
import com.example.moneymatev2.presentation.transaction.AddTransactionViewmodel
import com.example.moneymatev2.presentation.recurring.RecurringTransactionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock(AuthRepository::class.java)
    private val categories = mock(CategoryRepository::class.java)
    private val transactions = mock(TransactionRepository::class.java)
    private val authState = MutableStateFlow<UserModel?>(UserModel("a", null, null, null))
    private var uid = "a"

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        `when`(auth.observeAuthState()).thenReturn(authState)
    }

    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun budget_keepsArchivedAndOrphanRows_andRejectsStaleOwnerEvents() = runTest(dispatcher) {
        val budgetRepository = mock(BudgetRepository::class.java)
        val overview = mock(GetMonthlyBudgetOverviewUseCase::class.java)
        val archived = category("a").copy(isArchived = true)
        val active = category("a").copy(id = "active")
        `when`(categories.getAllCategories("a")).thenReturn(flowOf(listOf(archived, active)))
        val rows = listOf(budget("legacy"), budget("missing"))
        `when`(overview.invoke(eq("a") ?: "a", anyInt(), anyInt(), anyLong(), anyLong()))
            .thenReturn(flowOf(BudgetOverviewModel(rows, Money(200, "VND"), Money.zero("VND"), Money(200, "VND"))))
        val viewModel = BudgetViewModel(overview, budgetRepository, categories, auth)
        try {
            val state = viewModel.uiState.first { !it.isLoading }
            assertEquals(2, state.budgets.size)
            assertEquals("Owner a", state.budgets.first().categoryName)
            assertTrue(state.budgets.last().categoryMissing)
            assertEquals(listOf(active), state.categories)
            uid = "b"
            viewModel.deleteBudget(state.budgets.first())
            viewModel.saveBudget(active, "100")
            advanceUntilIdle()
            verifyNoInteractions(budgetRepository)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun budgetCategoryArchivedDuringSave_reportsSelectionFailure() = runTest(dispatcher) {
        val budgetRepository = mock(BudgetRepository::class.java)
        val overview = mock(GetMonthlyBudgetOverviewUseCase::class.java)
        `when`(budgetRepository.setBudget("a", "legacy", 100L, "VND"))
            .thenThrow(IllegalArgumentException("Invalid budget category"))
        val viewModel = BudgetViewModel(overview, budgetRepository, categories, auth)
        try {
            viewModel.saveBudget(category("a"), "100")
            assertEquals("a", viewModel.categoryUnavailable.first())
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun staleCategoryDelete_doesNotApplyNewUidToLegacyId() = runTest(dispatcher) {
        val delete = mock(DeleteCategoryUseCase::class.java)
        val viewModel = CategoryManagementViewModel(categories, auth, delete)
        try {
            uid = "b"
            viewModel.deleteCategory(category("a"), category("a").copy(id = "replacement"))
            advanceUntilIdle()
            verifyNoInteractions(delete)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun cancelCategoryDeletePreviewDoesNotArchiveAnything() = runTest(dispatcher) {
        val delete = mock(DeleteCategoryUseCase::class.java)
        val viewModel = CategoryManagementViewModel(categories, auth, delete)
        try {
            viewModel.inspectCategoryDeletion(category("a"))
            viewModel.cancelCategoryDeletion()
            advanceUntilIdle()
            assertEquals(CategoryDeletePreview.Idle, viewModel.deletePreview.value)
            verifyNoInteractions(delete)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun failedUsageQueryNeverAppearsAsEmptyCategory() = runTest(dispatcher) {
        val delete = mock(DeleteCategoryUseCase::class.java)
        `when`(categories.getDeletionUsage("a", "legacy")).thenThrow(IllegalStateException("query failed"))
        val viewModel = CategoryManagementViewModel(categories, auth, delete)
        try {
            viewModel.inspectCategoryDeletion(category("a"))
            advanceUntilIdle()
            assertTrue(viewModel.deletePreview.value is CategoryDeletePreview.Failed)
            verifyNoInteractions(delete)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun categoryDeleteReportsFailureAndAllowsRetryWithoutDoubleSubmit() = runTest(dispatcher) {
        val delete = mock(DeleteCategoryUseCase::class.java)
        val source = category("a")
        val replacement = source.copy(id = "replacement")
        `when`(delete.invoke("a", source.id, replacement.id))
            .thenReturn(AppResult.Failure(TransactionError.CategoryInUse))
            .thenReturn(AppResult.Success(Unit))
        val viewModel = CategoryManagementViewModel(categories, auth, delete)
        try {
            viewModel.deleteCategory(source, replacement)
            viewModel.deleteCategory(source, replacement)
            advanceUntilIdle()
            assertEquals(CategoryManagementViewModel.DeleteCategoryEvent.Failed(TransactionError.CategoryInUse),
                viewModel.deleteEvents.first())
            verify(delete, times(1)).invoke("a", source.id, replacement.id)
            assertFalse(viewModel.isDeleting.value)

            viewModel.deleteCategory(source, replacement)
            advanceUntilIdle()
            assertEquals(CategoryManagementViewModel.DeleteCategoryEvent.Success, viewModel.deleteEvents.first())
            verify(delete, times(2)).invoke("a", source.id, replacement.id)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun transactionSelector_discardsOldOwnerEvenWhenLegacyIdsMatch() = runTest(dispatcher) {
        val categoryA = category("a")
        val categoryB = category("b")
        `when`(categories.getActiveCategoriesByType("a", TransactionType.EXPENSE)).thenReturn(flowOf(listOf(categoryA)))
        `when`(categories.getActiveCategoriesByType("b", TransactionType.EXPENSE)).thenReturn(flowOf(listOf(categoryB)))
        val viewModel = AddTransactionViewmodel(CreateTransactionUseCase(transactions, auth), categories, auth)
        try {
            advanceUntilIdle()
            viewModel.onAmountChange("100", "a")
            uid = "b"
            viewModel.save("a")
            viewModel.onCategoryResult("a", "legacy")
            authState.value = UserModel("b", null, null, null)
            advanceUntilIdle()
            viewModel.onCategorySelected(categoryA.copy(id = "stale"))
            viewModel.onAmountChange("999", "a")
            viewModel.onNoteChange("stale note", "a")
            viewModel.onTypeChange(TransactionType.INCOME, "a")
            assertEquals("b", viewModel.formState.value.userId)
            assertEquals(listOf(categoryB), viewModel.formState.value.categories)
            assertEquals("legacy", viewModel.formState.value.selectedCategoryId)
            assertEquals("", viewModel.formState.value.amountText)
            assertEquals("", viewModel.formState.value.note)
            assertEquals(TransactionType.EXPENSE, viewModel.formState.value.type)
            verifyNoInteractions(transactions)
            verify(categories, never()).getCategory("a", "legacy")
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun createTransaction_rejectsCategoryFromOtherOwner() = runTest(dispatcher) {
        uid = "b"
        val result = CreateTransactionUseCase(transactions, auth)(TransactionType.EXPENSE, 100, category("a"), null, 100)
        assertTrue(result is AppResult.Failure)
        verifyNoInteractions(transactions)
    }

    @Test fun categoryCreation_rejectsFormFromPreviousOwner() = runTest(dispatcher) {
        uid = "b"
        val result = CreateCategoryUseCase(categories, auth)("a", "test", TransactionType.EXPENSE, "ic_food", "#123456")
        assertTrue(result is AppResult.Failure)
        verifyNoInteractions(categories)
    }

    @Test fun recurringCreation_rejectsOldEditorWithSharedLegacyCategoryId() = runTest(dispatcher) {
        val recurring = mock(RecurringTransactionRepository::class.java)
        val viewModel = RecurringTransactionViewModel(auth, recurring, categories)
        try {
            advanceUntilIdle()
            clearInvocations(recurring)
            uid = "b"
            viewModel.save("a", null, TransactionType.EXPENSE, "100", "legacy", "",
                RecurringFrequency.MONTHLY, 100, "UTC", false)
            advanceUntilIdle()
            verifyNoInteractions(recurring)
        } finally { viewModel.viewModelScope.cancel() }
    }

    private fun category(owner: String) = CategoryModel(owner, "legacy", "spend_food", "Owner $owner",
        TransactionType.EXPENSE, "ic_food", "#123456", true, false)

    private fun budget(categoryId: String) = BudgetModel("budget-$categoryId", categoryId,
        Money(100, "VND"), Money.zero("VND"), 10, 2026)
}
