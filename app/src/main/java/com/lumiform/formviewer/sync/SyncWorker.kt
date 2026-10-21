package com.lumiform.formviewer.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.usecase.SyncFormUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncForm: SyncFormUseCase,
        private val scheduler: SyncScheduler,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (val result = syncForm()) {
                SyncResult.Success -> {
                    scheduler.scheduleImagePrefetch()
                    Result.success()
                }

                // Retrying can't fix an invalid payload or a rejected certificate; the next periodic run tries again.
                is SyncResult.Failure -> {
                    when (result.error) {
                        SyncError.Offline, SyncError.Server -> Result.retry()
                        SyncError.InvalidData, SyncError.Insecure -> Result.failure()
                    }
                }
            }
    }
