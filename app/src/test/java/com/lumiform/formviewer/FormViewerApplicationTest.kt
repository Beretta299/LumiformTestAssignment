package com.lumiform.formviewer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.sync.ImagePrefetchWorker
import com.lumiform.formviewer.sync.SyncScheduler
import com.lumiform.formviewer.sync.SyncWorker
import org.junit.Test
import org.junit.runner.RunWith

/** Boots the real Hilt application: proves the WorkManager setup and that Hilt can build every worker. */
@RunWith(AndroidJUnit4::class)
class FormViewerApplicationTest {
    private val application = ApplicationProvider.getApplicationContext<FormViewerApplication>()

    @Test
    fun onCreate_schedulesBackgroundSync() {
        val workManager = WorkManager.getInstance(application)

        assertThat(workManager.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_SYNC).get()).hasSize(1)
        assertThat(workManager.getWorkInfosForUniqueWork(SyncScheduler.SYNC_WHEN_CONNECTED).get()).hasSize(1)
    }

    @Test
    fun hiltWorkerFactory_createsEveryWorker() {
        val factory = application.workManagerConfiguration.workerFactory

        assertThat(TestListenableWorkerBuilder<SyncWorker>(application).setWorkerFactory(factory).build())
            .isInstanceOf(SyncWorker::class.java)
        assertThat(TestListenableWorkerBuilder<ImagePrefetchWorker>(application).setWorkerFactory(factory).build())
            .isInstanceOf(ImagePrefetchWorker::class.java)
    }
}
