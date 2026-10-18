package com.example.moneymatev2.core.di

import com.example.moneymatev2.data.remote.sync.CloudPullService
import com.example.moneymatev2.domain.repository.CloudPullRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudPullModule {
    @Binds
    abstract fun bindCloudPullRepository(implementation: CloudPullService): CloudPullRepository
}
