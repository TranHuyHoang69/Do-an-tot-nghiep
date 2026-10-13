package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.RecurringTransactionModel
import kotlinx.coroutines.flow.Flow

interface RecurringTransactionRepository {
    fun observe(userId: String): Flow<List<RecurringTransactionModel>>
    suspend fun getById(userId: String, id: String): RecurringTransactionModel?
    suspend fun save(userId: String, model: RecurringTransactionModel)
    suspend fun setActive(userId: String, id: String, active: Boolean)
    suspend fun delete(userId: String, id: String)
    suspend fun postDue(userId: String, maxOccurrences: Int = 100): Int
    suspend fun syncPending(userId: String)
}
