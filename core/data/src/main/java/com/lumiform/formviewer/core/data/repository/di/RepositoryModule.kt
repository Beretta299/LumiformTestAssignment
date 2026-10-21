package com.lumiform.formviewer.core.data.repository.di

import com.lumiform.formviewer.core.data.repository.OfflineFirstFormRepository
import com.lumiform.formviewer.core.domain.repository.FormRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun bindFormRepository(impl: OfflineFirstFormRepository): FormRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.System
    }
}
