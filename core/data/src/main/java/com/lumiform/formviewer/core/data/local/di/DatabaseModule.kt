package com.lumiform.formviewer.core.data.local.di

import android.content.Context
import androidx.room.Room
import com.lumiform.formviewer.core.data.local.FormDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FormDatabase = Room.databaseBuilder(context, FormDatabase::class.java, FormDatabase.NAME).build()
}
