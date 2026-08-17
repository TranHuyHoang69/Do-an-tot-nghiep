package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.UserModel
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<UserModel?>

    fun getCurrentUserId(): String?

    suspend fun signIn(email: String, password: String): AppResult<UserModel>

    suspend fun signUp(email: String, password: String, displayName: String): AppResult<UserModel>
    suspend fun signInWithGoogle(idToken: String): AppResult<UserModel>

    suspend fun signOut()
}