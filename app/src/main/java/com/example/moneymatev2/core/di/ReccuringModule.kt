package com.example.moneymatev2.core.di

import com.example.moneymatev2.data.repository.RecurringTransactionRepositoryImpl
import com.example.moneymatev2.domain.repository.RecurringTransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecurringModule {
    @Binds @Singleton
    abstract fun bindRecurringRepository(impl: RecurringTransactionRepositoryImpl): RecurringTransactionRepository
}
