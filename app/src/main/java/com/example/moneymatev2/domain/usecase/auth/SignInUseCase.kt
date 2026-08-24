package com.example.moneymatev2.domain.usecase.auth

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.AuthError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.UserRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(email: String, password: String): AppResult<UserModel> {
        if (email.isBlank() || password.isBlank()) {
            return AppResult.Failure(AuthError.EmptyCredentials)
        }

        return when (val result = authRepository.signIn(email, password)) {
            is AppResult.Success -> {
                userRepository.upsertUser(result.data)
                categoryRepository.seedDefaultCategoriesIfNeeded(result.data.userId)
                result
            }
            is AppResult.Failure -> result
            AppResult.Loading -> result
        }
    }
}