package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.UserModel
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    fun getCurrentUser(userId: String): Flow<UserModel?>

    suspend fun upsertUser(user: UserModel)

    suspend fun signOut()
}