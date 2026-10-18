package com.example.moneymatev2.domain.usecase

interface SyncOrchestrator {
    suspend fun syncAll(userId: String)
}
