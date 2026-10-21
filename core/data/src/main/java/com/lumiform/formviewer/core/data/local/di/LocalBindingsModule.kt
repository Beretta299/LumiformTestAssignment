package com.lumiform.formviewer.core.data.local.di

import com.lumiform.formviewer.core.data.local.FormLocalDataSource
import com.lumiform.formviewer.core.data.local.RoomFormLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocalBindingsModule {
    @Binds
    abstract fun bindFormLocalDataSource(impl: RoomFormLocalDataSource): FormLocalDataSource
}
