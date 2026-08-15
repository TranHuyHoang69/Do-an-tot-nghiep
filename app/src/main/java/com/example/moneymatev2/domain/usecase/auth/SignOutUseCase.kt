package com.example.moneymatev2.domain.usecase.auth

import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.UserRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() {
        authRepository.signOut()
        userRepository.signOut()
    }
}