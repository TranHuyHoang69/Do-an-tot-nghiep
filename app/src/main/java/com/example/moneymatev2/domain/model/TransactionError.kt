package com.example.moneymatev2.domain.model

sealed class TransactionError {
    object InvalidAmount: TransactionError()
    object CategoryNotSelected: TransactionError()
    object NotAuthenticated: TransactionError()
    object InvalidCategoryName: TransactionError() // mới thêm
    data class UnknownError(val message: String): TransactionError()
}