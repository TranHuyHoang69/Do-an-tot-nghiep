package com.example.moneymatev2.domain.usecase.auth

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.AuthError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.UserRepository
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(email: String, password: String, displayName: String): AppResult<UserModel> {
        validate(email, password)?.let { return AppResult.Failure(it) }

        return when (val result = authRepository.signUp(email, password, displayName)) {
            is AppResult.Success -> {
                userRepository.upsertUser(result.data)
                categoryRepository.seedDefaultCategoriesIfNeeded(result.data.userId)
                result
            }
            is AppResult.Failure -> result
            AppResult.Loading -> result
        }
    }

    private fun validate(email: String, password: String): AuthError? = when {
        email.isBlank() || password.isBlank() -> AuthError.EmptyCredentials
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> AuthError.InvalidEmail
        password.length < 6 -> AuthError.WeakPassword
        else -> null
    }
}