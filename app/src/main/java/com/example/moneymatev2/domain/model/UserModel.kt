package com.example.moneymatev2.domain.model

data class UserModel(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)