package com.lumiform.formviewer.core.data.local.di

import android.content.Context
import androidx.room.Room
import com.lumiform.formviewer.core.data.local.FormDatabase
import com.lumiform.formviewer.core.data.local.dao.AnswerDao
import com.lumiform.formviewer.core.data.local.dao.FormDao
import com.lumiform.formviewer.core.data.local.dao.SyncMetadataDao
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

    @Provides
    fun provideFormDao(database: FormDatabase): FormDao = database.formDao()

    @Provides
    fun provideAnswerDao(database: FormDatabase): AnswerDao = database.answerDao()

    @Provides
    fun provideSyncMetadataDao(database: FormDatabase): SyncMetadataDao = database.syncMetadataDao()
}
