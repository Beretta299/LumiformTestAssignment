package com.lumiform.formviewer.core.data.remote.di

import android.content.Context
import com.lumiform.formviewer.core.data.remote.FormApi
import com.lumiform.formviewer.core.data.remote.FormRemoteDataSource
import com.lumiform.formviewer.core.data.remote.RetrofitFormRemoteDataSource
import com.lumiform.formviewer.core.data.remote.RetryPolicy
import com.lumiform.formviewer.core.data.remote.createFormApi
import com.lumiform.formviewer.core.data.remote.security.ConnectionSecurityInterceptor
import com.lumiform.formviewer.core.domain.repository.ConnectionSecurityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    private const val HTTP_CACHE_DIR = "http_cache"
    private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

    /** Shared app-wide, so images reuse its connection pool and security interceptor. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        securityInterceptor: ConnectionSecurityInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            // Bounds a refresh at about 3 attempts × 15 s + backoff ≈ 47 s; cached data stays visible meanwhile.
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE_BYTES))
            .addInterceptor(securityInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideFormApi(client: OkHttpClient): FormApi = createFormApi(FormApi.BASE_URL, client)

    @Provides
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemoteBindingsModule {
    @Binds
    abstract fun bindFormRemoteDataSource(impl: RetrofitFormRemoteDataSource): FormRemoteDataSource

    @Binds
    abstract fun bindConnectionSecurityRepository(impl: ConnectionSecurityInterceptor): ConnectionSecurityRepository
}
