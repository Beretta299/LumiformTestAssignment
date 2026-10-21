package com.lumiform.formviewer.sync

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.usecase.SyncFormUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncWorkerTest {
    private val workManager = initTestWorkManager()
    private val repository = FakeFormRepository()

    private suspend fun runWorker(syncResult: SyncResult): Result {
        repository.syncResult = syncResult
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    SyncWorker(appContext, workerParameters, SyncFormUseCase(repository), SyncScheduler(workManager))
            }
        return TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()
    }

    private fun prefetchScheduled() = workManager.uniqueWork(SyncScheduler.IMAGE_PREFETCH).isNotEmpty()

    @Test
    fun success_succeedsAndSchedulesImagePrefetch() =
        runTest {
            assertThat(runWorker(SyncResult.Success)).isEqualTo(Result.success())
            assertThat(prefetchScheduled()).isTrue()
        }

    @Test
    fun transientFailures_retry() =
        runTest {
            assertThat(runWorker(SyncResult.Failure(SyncError.Offline))).isEqualTo(Result.retry())
            assertThat(runWorker(SyncResult.Failure(SyncError.Server))).isEqualTo(Result.retry())
            assertThat(prefetchScheduled()).isFalse()
        }

    @Test
    fun permanentFailures_fail() =
        runTest {
            assertThat(runWorker(SyncResult.Failure(SyncError.InvalidData))).isEqualTo(Result.failure())
            assertThat(runWorker(SyncResult.Failure(SyncError.Insecure))).isEqualTo(Result.failure())
            assertThat(prefetchScheduled()).isFalse()
        }
}
