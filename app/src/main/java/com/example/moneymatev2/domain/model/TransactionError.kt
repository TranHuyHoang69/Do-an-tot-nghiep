package com.example.moneymatev2.domain.model

sealed class TransactionError {
    object InvalidAmount: TransactionError()
    object CategoryNotSelected: TransactionError()
    object NotAuthenticated: TransactionError()
    data class UnknownError(val message: String): TransactionError()
}