package com.lumiform.formviewer.core.data.image

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.lumiform.formviewer.core.domain.repository.ImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class CoilImageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) : ImageRepository {
        // The disk cache keeps the downloaded bytes whatever the decode size, so decoding a 1 px image keeps it cheap.
        override suspend fun prefetch(urls: Set<String>): Boolean =
            urls
                .map { url ->
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(Size(1, 1))
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build()
                    imageLoader.execute(request) is SuccessResult
                }.all { it }
    }
