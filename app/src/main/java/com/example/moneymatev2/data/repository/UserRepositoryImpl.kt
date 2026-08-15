package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.UserDao
import com.example.moneymatev2.data.local.entity.UserEntity
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
    private val syncTrigger: SyncTrigger
): UserRepository {
    override fun getCurrentUser(userId: String): Flow<UserModel?> =
        dao.getUser(userId).map { it?.toModel() }

    override suspend fun upsertUser(user: UserModel) {
        val now = System.currentTimeMillis()
        dao.upsertUser(
            UserEntity(
                userId = user.userId,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl,
                createdAt = now,
                updatedAt = now
            )
        )
        syncTrigger.requestImmediateSync()
    }

    override suspend fun signOut() {
        TODO("Not yet implemented")
    }

    private fun UserEntity.toModel() = UserModel(
        userId = userId,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl
    )
}