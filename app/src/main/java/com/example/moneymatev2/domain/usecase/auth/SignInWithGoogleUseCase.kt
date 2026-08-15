package com.example.moneymatev2.domain.usecase.auth

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.UserRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository
){
    suspend operator fun invoke(idToken: String): AppResult<UserModel> {
        return when(val result = authRepository.signInWithGoogle(idToken)){
            is AppResult.Success -> {
                userRepository.upsertUser(result.data)
                categoryRepository.seedDefaultCategoriesIfNeeded(result.data.userId)
                result
            }
            is AppResult.Failure -> result
        }
    }
}