package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")

data class UserEntity(
    @PrimaryKey
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastSyncError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdatedAt: Long? = null
)