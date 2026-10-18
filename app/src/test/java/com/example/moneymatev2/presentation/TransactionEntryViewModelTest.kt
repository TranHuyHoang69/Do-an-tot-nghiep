package com.example.moneymatev2.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.moneymatev2.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.moneymatev2.presentation.transaction.TransactionEntryViewModel
import com.example.moneymatev2.presentation.transaction.TransactionEntryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEntryViewModelTest {
    private class EditableTransactions(private val row: MutableStateFlow<TransactionModel?>) : TransactionRepository {
        var failUpdate = true
        var failDelete = true
        override fun getAllTransactions(userId: String): Flow<List<TransactionModel>> = flowOf(emptyList())
        override fun getTransactionByPeriod(userId: String, start: Long, end: Long): Flow<List<TransactionModel>> = flowOf(emptyList())
        override fun getTransactionByCategory(userId: String, categoryId: String): Flow<List<TransactionModel>> = flowOf(emptyList())
        override fun getTransactionById(userId: String, localId: String): Flow<TransactionModel?> = row
        override suspend fun createTransaction(transaction: TransactionModel) = Unit
        override suspend fun updateTransaction(transaction: TransactionModel) {
            if (failUpdate) error("offline write failure")
            row.value = transaction
        }
        override suspend fun deleteTransaction(userId: String, id: String) {
            if (failDelete) error("offline write failure")
            row.value = null
        }
        override suspend fun syncPendingTransactions(userId: String) = Unit
        override suspend fun reassignCategory(userId: String, oldCategoryId: String, newCategoryId: String) = Unit
    }

    @Test fun saveAndDeleteNavigateOnlyAfterSuccessfulLocalWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mock(AuthRepository::class.java)
            `when`(auth.getCurrentUserId()).thenReturn("a")
            `when`(auth.observeAuthState()).thenReturn(MutableStateFlow(UserModel("a", null, null, null)))
            val food = CategoryModel("a", "food", null, "Food", TransactionType.EXPENSE,
                "ic_category", "#123456", false, false)
            val categories = mock(CategoryRepository::class.java)
            `when`(categories.getCategory("a", "food")).thenReturn(food)
            `when`(categories.getActiveCategoriesByType("a", TransactionType.EXPENSE)).thenReturn(flowOf(listOf(food)))
            val row = MutableStateFlow<TransactionModel?>(TransactionModel("tx", "a", TransactionType.EXPENSE,
                Money(100, "VND"), "food", null, 123456, 200000, false))
            val transactions = EditableTransactions(row)
            val vm = TransactionEntryViewModel(SavedStateHandle(mapOf("transactionId" to "tx")),
                auth, transactions, categories, UpdateTransactionUseCase(transactions, auth),
                DeleteTransactionUseCase(transactions, auth))
            advanceUntilIdle()
            vm.save()
            advanceUntilIdle()
            assertNotNull(vm.state.value.error)
            assertNotNull(row.value)
            transactions.failUpdate = false
            vm.save()
            advanceUntilIdle()
            assertEquals(TransactionEntryEvent.Saved, vm.events.first())
            vm.delete()
            advanceUntilIdle()
            assertNotNull(vm.state.value.error)
            assertNotNull(row.value)
            transactions.failDelete = false
            vm.delete()
            advanceUntilIdle()
            assertEquals(TransactionEntryEvent.Deleted, vm.events.first())
            assertNull(row.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun prefillOnceAndClearWhenAccountChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var uid: String? = "a"
            val auth = mock(AuthRepository::class.java)
            val users = MutableStateFlow<UserModel?>(UserModel("a", null, null, null))
            `when`(auth.getCurrentUserId()).thenAnswer { uid }
            `when`(auth.observeAuthState()).thenReturn(users)
            val transactions = mock(TransactionRepository::class.java)
            val original = TransactionModel("tx", "a", TransactionType.EXPENSE,
                Money(40000, "VND"), "food", "lunch", 123456, 200000, false)
            val rows = MutableStateFlow<TransactionModel?>(original)
            `when`(transactions.getTransactionById("a", "tx")).thenReturn(rows)
            val categories = mock(CategoryRepository::class.java)
            val food = CategoryModel("a", "food", null, "Food", TransactionType.EXPENSE,
                "ic_category", "#123456", false, false)
            `when`(categories.getCategory("a", "food")).thenReturn(food)
            `when`(categories.getActiveCategoriesByType("a", TransactionType.EXPENSE)).thenReturn(flowOf(listOf(food)))
            val vm = TransactionEntryViewModel(SavedStateHandle(mapOf("transactionId" to "tx")),
                auth, transactions, categories, mock(UpdateTransactionUseCase::class.java),
                mock(DeleteTransactionUseCase::class.java))
            advanceUntilIdle()
            assertEquals("40000", vm.state.value.amountText)
            assertEquals("food", vm.state.value.selectedCategoryId)
            assertEquals(123456L, vm.state.value.selectedDate)
            vm.amountChanged("50.000")
            rows.value = original.copy(money = Money(99999, "VND"), note = "remote")
            advanceUntilIdle()
            assertEquals("50000", vm.state.value.amountText)
            uid = "b"
            users.value = UserModel("b", null, null, null)
            advanceUntilIdle()
            assertNull(vm.state.value.transaction)
            assertNull(vm.state.value.ownerId)
            vm.save()
            assertNull(vm.state.value.transaction)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
