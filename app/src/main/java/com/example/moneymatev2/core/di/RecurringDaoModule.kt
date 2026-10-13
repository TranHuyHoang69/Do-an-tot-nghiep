package com.example.moneymatev2.core.di

import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.dao.RecurringTransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RecurringDaoModule {
    @Provides
    fun provideRecurringTransactionDao(db: AppDatabase): RecurringTransactionDao = db.recurringTransactionDao()
}
