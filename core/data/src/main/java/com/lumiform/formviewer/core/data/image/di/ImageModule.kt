package com.lumiform.formviewer.core.data.image.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lumiform.formviewer.core.data.image.CoilImageRepository
import com.lumiform.formviewer.core.domain.repository.ImageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ImageModule {
    @Binds
    abstract fun bindImageRepository(impl: CoilImageRepository): ImageRepository

    companion object {
        private const val IMAGE_CACHE_DIR = "image_cache"
        private const val IMAGE_CACHE_SIZE_BYTES = 50L * 1024 * 1024

        /**
         * Reuses the app's OkHttp client so pinning and the security interceptor cover images too; its HTTP cache is
         * dropped because Coil keeps its own. The disk cache lives in `filesDir`, which the system never clears.
         */
        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            okHttpClient: OkHttpClient,
        ): ImageLoader =
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.newBuilder().cache(null).build() }),
                    )
                }.diskCache {
                    DiskCache
                        .Builder()
                        .directory(context.filesDir.resolve(IMAGE_CACHE_DIR))
                        .maxSizeBytes(IMAGE_CACHE_SIZE_BYTES)
                        .build()
                }.build()
    }
}
