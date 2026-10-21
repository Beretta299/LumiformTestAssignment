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
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.usecase.PrefetchFormImagesUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ImagePrefetchWorkerTest {
    private val repository = FakeFormRepository().apply { form.value = listOf(ImageQuestion(4, URL, "Image")) }
    private val images = RecordingImageRepository()

    private suspend fun runWorker(runAttemptCount: Int = 0): Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    ImagePrefetchWorker(appContext, workerParameters, PrefetchFormImagesUseCase(repository, images))
            }
        return TestListenableWorkerBuilder<ImagePrefetchWorker>(context, runAttemptCount = runAttemptCount)
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    @Test
    fun prefetchesFormImages() =
        runTest {
            assertThat(runWorker()).isEqualTo(Result.success())
            assertThat(images.requested).containsExactly(setOf(URL))
        }

    @Test
    fun failedDownload_retriesUntilAttemptsRunOut() =
        runTest {
            images.succeeds = false

            assertThat(runWorker(runAttemptCount = 0)).isEqualTo(Result.retry())
            assertThat(runWorker(runAttemptCount = ImagePrefetchWorker.MAX_ATTEMPTS - 2)).isEqualTo(Result.retry())
            assertThat(runWorker(runAttemptCount = ImagePrefetchWorker.MAX_ATTEMPTS - 1)).isEqualTo(Result.failure())
        }

    private companion object {
        const val URL = "https://example.com/a.png"
    }
}
