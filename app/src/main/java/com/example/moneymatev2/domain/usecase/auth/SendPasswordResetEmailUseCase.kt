package com.example.moneymatev2.domain.usecase.auth

import android.util.Patterns
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.PasswordResetError
import com.example.moneymatev2.domain.repository.AuthRepository
import javax.inject.Inject

class SendPasswordResetEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): AppResult<Unit> {
        val normalized = email.trim()
        if (normalized.isEmpty()) return AppResult.Failure(PasswordResetError.EmptyEmail)
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            return AppResult.Failure(PasswordResetError.InvalidEmail)
        }
        return authRepository.sendPasswordResetEmail(normalized)
    }
}
