package com.example.moneymatev2.domain.model

sealed class AppResult<out T>{
    data class Success<T>(val data: T): AppResult<T>()
    data class Failure(val error: Any): AppResult<Nothing>()
    object Loading: AppResult<Nothing>()
}