package com.example.moneymatev2.presentation.statistics

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class StatisticsSourceFlowTest {
    @Test fun roomFlowsRecomputeForEditsDeletesSyncAndAccountSwitchWithoutWrites() = runBlocking {
        val auth = mock(AuthRepository::class.java)
        val transactions = mock(TransactionRepository::class.java)
        val categories = mock(CategoryRepository::class.java)
        val user = MutableStateFlow<UserModel?>(UserModel("A", null, null, null))
        val a = MutableStateFlow(listOf(tx("A", 100)))
        val b = MutableStateFlow(listOf(tx("B", 200)))
        val aCategory = MutableStateFlow(listOf(category("A", archived = true)))
        val bCategory = MutableStateFlow(listOf(category("B")))
        `when`(auth.observeAuthState()).thenReturn(user)
        `when`(transactions.getAllTransactions("A")).thenReturn(a)
        `when`(transactions.getAllTransactions("B")).thenReturn(b)
        `when`(categories.getAllCategories("A")).thenReturn(aCategory)
        `when`(categories.getAllCategories("B")).thenReturn(bCategory)
        val useCase = GetTransactionWithCategoryUseCase(transactions, categories, auth)
        var latest: List<TransactionWithCategory> = emptyList()
        val job = launch { useCase().collect { latest = it } }
        try {
            suspend fun awaitAmount(expected: Long?) = withTimeout(3_000) {
                while (latest.singleOrNull()?.transaction?.money?.amountMinor != expected ||
                    (expected == null && latest.isNotEmpty())) delay(10)
            }
            awaitAmount(100)
            assertTrue(latest.single().category!!.isArchived)
            a.value = listOf(tx("A", 150)) // edit or incoming Room sync
            awaitAmount(150)
            a.value = emptyList() // soft delete is removed by Room's query
            awaitAmount(null)
            user.value = UserModel("B", null, null, null)
            awaitAmount(200)
            assertEquals("B", latest.single().transaction.userId)
            user.value = null
            awaitAmount(null)
            verify(transactions, never()).syncPendingTransactions(anyString())
            verify(categories, never()).syncPendingCategories(anyString())
        } finally { job.cancel() }
    }

    private fun tx(owner: String, amount: Long) = TransactionModel(
        "same-local-id", owner, TransactionType.EXPENSE, Money(amount, "VND"), "same-category-id",
        null, 1L, 1L, true
    )
    private fun category(owner: String, archived: Boolean = false) = CategoryModel(
        owner, "same-category-id", null, "Food", TransactionType.EXPENSE, "ic_food", "#FF0000", false, archived
    )
}
