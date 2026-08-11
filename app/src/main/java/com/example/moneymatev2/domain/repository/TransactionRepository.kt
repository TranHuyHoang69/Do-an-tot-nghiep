package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.TransactionModel
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    fun getAllTransactions(userId: String): Flow<List<TransactionModel>>

    fun getTransactionByPeriod(userId: String, start: Long, end: Long): Flow<List<TransactionModel>>

    fun getTransactionByCategory(userId: String, categoryId: String): Flow<List<TransactionModel>>

    fun getTransactionById(localId: Long): Flow<TransactionModel?>

    suspend fun createTransaction(transaction: TransactionModel)

    suspend fun updateTransaction(transaction: TransactionModel)

    suspend fun deleteTransaction(id: String)

    suspend fun syncPendingTransactions(userId: String)


}