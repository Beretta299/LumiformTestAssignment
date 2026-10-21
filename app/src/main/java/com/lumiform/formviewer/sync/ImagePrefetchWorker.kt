package com.lumiform.formviewer.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumiform.formviewer.core.domain.usecase.PrefetchFormImagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ImagePrefetchWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val prefetchFormImages: PrefetchFormImagesUseCase,
    ) : CoroutineWorker(context, params) {
        // Bounded, because an image that is gone from the server would otherwise be retried forever.
        override suspend fun doWork(): Result =
            when {
                prefetchFormImages() -> Result.success()
                runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
                else -> Result.failure()
            }

        companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
