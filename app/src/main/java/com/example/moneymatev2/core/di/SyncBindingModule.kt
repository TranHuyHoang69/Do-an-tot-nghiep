package com.example.moneymatev2.core.di

import com.example.moneymatev2.domain.usecase.SyncManager
import com.example.moneymatev2.domain.usecase.SyncOrchestrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingModule {
    @Binds
    @Singleton
    abstract fun bindSyncOrchestrator(syncManager: SyncManager): SyncOrchestrator
}
