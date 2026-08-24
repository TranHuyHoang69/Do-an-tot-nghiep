package com.example.moneymatev2.data.repository

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.AuthError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
): AuthRepository {
    override fun observeAuthState(): Flow<UserModel?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toUserModel())
        }
        firebaseAuth.addAuthStateListener(listener)

        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): AppResult<UserModel> {
        return try{
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user?.toUserModel()
                ?: return AppResult.Failure(AuthError.Unknown("FireBase trả về user null"))
            AppResult.Success(user)
        }catch (e: Exception) {
            AppResult.Failure(e.toAuthError())
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): AppResult<UserModel> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return AppResult.Failure(AuthError.Unknown("Firebase trả về user null sau khi đăng ký"))

            if (displayName.isNotBlank()) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }

            AppResult.Success(firebaseUser.toUserModel().copy(displayName = displayName.ifBlank { null }))
        } catch (e: Exception) {
            AppResult.Failure(e.toAuthError())
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<UserModel> {
        return try{
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user?.toUserModel()
                ?: return AppResult.Failure(AuthError.Unknown("FireBase trả về user null"))
            AppResult.Success(user)
        }catch (e: Exception) {
            AppResult.Failure(e.toAuthError())
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toUserModel(): UserModel {
        return UserModel(
            userId = uid,
            email = email ,
            displayName = displayName,
            photoUrl = photoUrl?.toString()
        )
    }

    private fun Exception.toAuthError(): AuthError = when(this){
        is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials
        is FirebaseAuthInvalidUserException -> AuthError.InvalidCredentials
        is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
        is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
        is FirebaseNetworkException -> AuthError.NetworkError
        else -> AuthError.Unknown(message ?: "An unknown error occurred")
    }
}