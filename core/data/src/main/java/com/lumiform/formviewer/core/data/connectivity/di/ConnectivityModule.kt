package com.lumiform.formviewer.core.data.connectivity.di

import com.lumiform.formviewer.core.data.connectivity.AndroidConnectivityRepository
import com.lumiform.formviewer.core.domain.repository.ConnectivityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectivityModule {
    @Binds
    abstract fun bindConnectivityRepository(impl: AndroidConnectivityRepository): ConnectivityRepository
}
