package com.example.moneymatev2.data.remote.dto

import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.UserEntity
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserDto(
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = 0,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    @ServerTimestamp
    var updatedAt: Date? = null
)

fun UserEntity.toDto() = UserDto(
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt
)

fun UserDto.toEntity(userId: String, syncStatus: SyncStatus) = UserEntity(
    userId = userId,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt?.time ?: System.currentTimeMillis(),
    remoteUpdatedAt = updatedAt?.time
)