package com.example.moneymatev2.presentation.budget

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.BudgetOverviewModel
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.GetMonthlyBudgetOverviewUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
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
class BudgetOperationTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock(AuthRepository::class.java)
    private val categories = mock(CategoryRepository::class.java)
    private val overview = mock(GetMonthlyBudgetOverviewUseCase::class.java)
    private val repository = FakeBudgets()
    private val authState = MutableStateFlow<UserModel?>(UserModel("A", null, null, null))
    private var uid: String? = "A"
    private lateinit var viewModel: BudgetViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        `when`(auth.observeAuthState()).thenReturn(authState)
        `when`(categories.getAllCategories("A")).thenReturn(flowOf(listOf(category("A", "food"), category("A", "travel"))))
        `when`(categories.getAllCategories("B")).thenReturn(flowOf(listOf(category("B", "food"))))
        val budget = BudgetModel("budget-A", "food", Money(2_000_000, "VND"), Money.zero("VND"), 10, 2026)
        `when`(overview.invoke(eq("A") ?: "A", anyInt(), anyInt(), anyLong(), anyLong()))
            .thenReturn(flowOf(BudgetOverviewModel(listOf(budget), Money(2_000_000, "VND"),
                Money.zero("VND"), Money(2_000_000, "VND"))))
        `when`(overview.invoke(eq("B") ?: "B", anyInt(), anyInt(), anyLong(), anyLong()))
            .thenReturn(flowOf(BudgetOverviewModel(emptyList(), Money.zero("VND"),
                Money.zero("VND"), Money.zero("VND"))))
        viewModel = BudgetViewModel(overview, repository, categories, auth)
    }

    @After fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun load() { viewModel.uiState.first { !it.isLoading && it.userId == "A" } }

    @Test fun failedOverviewShowsErrorAndRetryLoadsForTheSameOwner() = runTest(dispatcher) {
        var reads = 0
        val result = BudgetOverviewModel(emptyList(), Money.zero("VND"),
            Money.zero("VND"), Money.zero("VND"))
        `when`(overview.invoke(eq("A") ?: "A", anyInt(), anyInt(), anyLong(), anyLong()))
            .thenAnswer {
                reads++
                if (reads == 1) flow<BudgetOverviewModel> { throw IllegalStateException("Room read failed") }
                else flowOf(result)
            }
        viewModel.viewModelScope.cancel()
        viewModel = BudgetViewModel(overview, repository, categories, auth)

        val failed = viewModel.uiState.first { it.hasLoadError }
        assertEquals("A", failed.userId)
        assertFalse(failed.isLoading)
        assertTrue(failed.budgets.isEmpty())
        viewModel.retryLoad()
        val loaded = viewModel.uiState.first { !it.isLoading && !it.hasLoadError && it.userId == "A" }
        assertEquals(2, reads)
        assertTrue(loaded.budgets.isEmpty())
    }

    @Test fun changingMonthEmitsLoadingForTheNewPeriodBeforeItsOverview() = runTest(dispatcher) {
        load()
        val previousAnchor = viewModel.uiState.value.periodAnchor
        val gate = CompletableDeferred<Unit>()
        val next = BudgetOverviewModel(emptyList(), Money.zero("VND"),
            Money.zero("VND"), Money.zero("VND"))
        `when`(overview.invoke(eq("A") ?: "A", anyInt(), anyInt(), anyLong(), anyLong()))
            .thenReturn(flow { gate.await(); emit(next) })

        viewModel.moveNextMonth()
        runCurrent()
        assertNotEquals(previousAnchor, viewModel.anchorDate)
        assertEquals(viewModel.anchorDate, viewModel.uiState.value.periodAnchor)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.budgets.isEmpty())

        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(viewModel.anchorDate, viewModel.uiState.value.periodAnchor)
    }

    @Test fun selectedMonthSurvivesBudgetDestinationReentryWithoutLeakingToAnotherUid() = runTest(dispatcher) {
        val selection = BudgetMonthSelectionStore()
        viewModel.viewModelScope.cancel()
        viewModel = BudgetViewModel(overview, repository, categories, auth, selection)
        load()
        viewModel.movePreviousMonth()
        val selected = viewModel.anchorDate
        viewModel.viewModelScope.cancel()

        viewModel = BudgetViewModel(overview, repository, categories, auth, selection)
        assertEquals(selected, viewModel.anchorDate)
        load()
        assertEquals(selected, viewModel.uiState.value.periodAnchor)

        uid = "B"
        authState.value = UserModel("B", null, null, null)
        viewModel.uiState.first { !it.isLoading && it.userId == "B" }
        assertNotEquals(selected, viewModel.anchorDate)
        assertEquals("B", viewModel.uiState.value.userId)
    }

    @Test fun saveWaitsForRoomBlocksDoubleTapAndOldSuccessDoesNotCloseNewForm() = runTest(dispatcher) {
        load()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        viewModel.openEditor()
        val firstId = viewModel.editor.value!!.requestId
        viewModel.changeEditorAmount("2000000")
        viewModel.saveBudget()
        viewModel.saveBudget()
        assertEquals(BudgetOperationStatus.Working, viewModel.editor.value!!.status)
        runCurrent()
        assertEquals(1, repository.saveCalls)
        assertEquals("2000000", viewModel.editor.value!!.amountText)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(BudgetOperationStatus.Success, viewModel.editor.value!!.status)
        viewModel.dismissEditor()
        viewModel.openEditor()
        assertTrue(viewModel.editor.value!!.requestId > firstId)
        assertEquals(BudgetOperationStatus.Idle, viewModel.editor.value!!.status)
        assertEquals("", viewModel.editor.value!!.amountText)
        viewModel.dismissEditor(firstId)
        assertNotNull(viewModel.editor.value)
    }

    @Test fun invalidLongNeverCallsRepositoryAndFailureKeepsCategoryAndInputForRetry() = runTest(dispatcher) {
        load()
        viewModel.openEditor()
        viewModel.selectEditorCategory("travel")
        viewModel.changeEditorAmount("9223372036854775808")
        viewModel.saveBudget()
        assertEquals(BudgetOperationStatus.Error(BudgetOperationError.AMOUNT_OUT_OF_RANGE), viewModel.editor.value!!.status)
        assertEquals(0, repository.saveCalls)
        assertEquals("9223372036854775808", viewModel.editor.value!!.amountText)
        assertEquals("travel", viewModel.editor.value!!.categoryId)

        repository.saveFailure = IllegalStateException("Room failed")
        viewModel.changeEditorAmount("500")
        viewModel.saveBudget()
        runCurrent()
        assertEquals(BudgetOperationStatus.Error(BudgetOperationError.SAVE_FAILED), viewModel.editor.value!!.status)
        assertEquals("500", viewModel.editor.value!!.amountText)
        assertEquals("travel", viewModel.editor.value!!.categoryId)
        repository.saveFailure = null
        viewModel.saveBudget()
        advanceUntilIdle()
        assertEquals(BudgetOperationStatus.Success, viewModel.editor.value!!.status)
        assertEquals(2, repository.saveCalls)
    }

    @Test fun invalidLimitKeepsExactInputAndNeverStartsLocalWrite() = runTest(dispatcher) {
        load()
        viewModel.openEditor()
        viewModel.selectEditorCategory("travel")
        val invalid = listOf(
            "" to BudgetOperationError.INVALID_AMOUNT,
            "0" to BudgetOperationError.NON_POSITIVE_AMOUNT,
            "0000" to BudgetOperationError.NON_POSITIVE_AMOUNT,
            "-1000" to BudgetOperationError.INVALID_AMOUNT,
            "1.5" to BudgetOperationError.INVALID_AMOUNT,
            "abc123" to BudgetOperationError.INVALID_AMOUNT,
            "9".repeat(1000) to BudgetOperationError.AMOUNT_OUT_OF_RANGE
        )
        invalid.forEach { (input, reason) ->
            viewModel.changeEditorAmount(input)
            viewModel.saveBudget()
            assertEquals(input, viewModel.editor.value!!.amountText)
            assertEquals("travel", viewModel.editor.value!!.categoryId)
            assertEquals(BudgetOperationStatus.Error(reason), viewModel.editor.value!!.status)
        }
        assertEquals(0, repository.saveCalls)
        viewModel.changeEditorAmount("1.000")
        viewModel.saveBudget()
        advanceUntilIdle()
        assertEquals(BudgetOperationStatus.Success, viewModel.editor.value!!.status)
        assertEquals(1, repository.saveCalls)
    }

    @Test fun deleteWaitsForRoomBlocksDoubleTapAndFailureCanRetryOrCancel() = runTest(dispatcher) {
        load()
        val target = viewModel.uiState.value.budgets.single()
        viewModel.openDelete(target)
        repository.deleteFailure = IllegalStateException("Room failed")
        viewModel.deleteBudget()
        viewModel.deleteBudget()
        assertEquals(BudgetOperationStatus.Working, viewModel.deletion.value!!.status)
        viewModel.dismissDelete()
        assertNotNull(viewModel.deletion.value)
        runCurrent()
        assertEquals(1, repository.deleteCalls)
        assertEquals(BudgetOperationStatus.Error(BudgetOperationError.DELETE_FAILED), viewModel.deletion.value!!.status)

        val gate = CompletableDeferred<Unit>()
        repository.deleteFailure = null
        repository.deleteGate = gate
        viewModel.deleteBudget()
        runCurrent()
        assertEquals(2, repository.deleteCalls)
        assertEquals(BudgetOperationStatus.Working, viewModel.deletion.value!!.status)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(BudgetOperationStatus.Success, viewModel.deletion.value!!.status)
        viewModel.dismissDelete()
        assertNull(viewModel.deletion.value)
        viewModel.openDelete(target)
        val oldDeleteId = viewModel.deletion.value!!.requestId
        viewModel.dismissDelete()
        viewModel.openDelete(target)
        viewModel.dismissDelete(oldDeleteId)
        assertNotNull(viewModel.deletion.value)
        viewModel.dismissDelete()
        assertEquals(2, repository.deleteCalls)
    }

    @Test fun accountSwitchDiscardsInFlightSaveAndDeleteResults() = runTest(dispatcher) {
        load()
        val saveGate = CompletableDeferred<Unit>()
        repository.saveGate = saveGate
        viewModel.openEditor()
        viewModel.changeEditorAmount("100")
        viewModel.saveBudget()
        runCurrent()
        uid = "B"
        authState.value = UserModel("B", null, null, null)
        runCurrent()
        assertNull(viewModel.editor.value)
        viewModel.openEditor()
        val newOwnerForm = viewModel.editor.value!!
        assertEquals("B", newOwnerForm.userId)
        saveGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(newOwnerForm, viewModel.editor.value)
        viewModel.dismissEditor()

        uid = "A"
        authState.value = UserModel("A", null, null, null)
        load()
        val target = viewModel.uiState.value.budgets.single()
        val deleteGate = CompletableDeferred<Unit>()
        repository.deleteGate = deleteGate
        viewModel.openDelete(target)
        viewModel.deleteBudget()
        runCurrent()
        uid = "B"
        authState.value = UserModel("B", null, null, null)
        runCurrent()
        assertNull(viewModel.deletion.value)
        deleteGate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.deletion.value)
        viewModel.openEditor()
        assertEquals("B", viewModel.editor.value?.userId)
        assertEquals(BudgetOperationStatus.Idle, viewModel.editor.value?.status)
        uid = null
        authState.value = null
        runCurrent()
        assertNull(viewModel.editor.value)
    }

    @Test fun staleOwnerCannotOpenOrWriteBudget() = runTest(dispatcher) {
        load()
        val target = viewModel.uiState.value.budgets.single()
        uid = "B"
        viewModel.openEditor()
        viewModel.openDelete(target)
        viewModel.saveBudget()
        viewModel.deleteBudget()
        assertNull(viewModel.editor.value)
        assertNull(viewModel.deletion.value)
        assertEquals(0, repository.saveCalls + repository.deleteCalls)
    }

    private fun category(owner: String, id: String) = CategoryModel(owner, id, null, id,
        TransactionType.EXPENSE, "ic_food", "#123456", false, false)

    private class FakeBudgets : BudgetRepository {
        var saveGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        var saveFailure: Exception? = null
        var deleteFailure: Exception? = null
        var saveCalls = 0
        var deleteCalls = 0
        override fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>> = flowOf(emptyList())
        override suspend fun setBudget(userId: String, categoryId: String, amountMinor: Long, currency: String) {
            saveCalls++
            saveGate?.await()
            saveFailure?.let { throw it }
        }
        override suspend fun deleteBudget(userId: String, categoryId: String, expectedBudgetId: String?) {
            deleteCalls++
            deleteGate?.await()
            deleteFailure?.let { throw it }
        }
        override suspend fun syncPendingBudgets(userId: String) = Unit
    }
}
