package com.lumiform.formviewer.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SyncSchedulerTest {
    private val workManager = initTestWorkManager()
    private val scheduler = SyncScheduler(workManager)

    @Before
    fun schedule() {
        scheduler.scheduleSync()
    }

    @Test
    fun scheduleSync_enqueuesPeriodicSyncOnAnyNetwork_startingOneIntervalLater() {
        val work = workManager.uniqueWork(SyncScheduler.PERIODIC_SYNC).single()

        assertThat(work.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(work.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(work.initialDelayMillis).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun scheduleSync_updatesPeriodicSyncFromAnOlderVersion_inPlace() {
        val id = workManager.uniqueWork(SyncScheduler.PERIODIC_SYNC).single().id
        workManager.enqueueUniquePeriodicWork(
            SyncScheduler.PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS).build(),
        )

        scheduler.scheduleSync()

        val work = workManager.uniqueWork(SyncScheduler.PERIODIC_SYNC).single()
        assertThat(work.id).isEqualTo(id)
        assertThat(work.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun scheduleSync_enqueuesOneTimeSyncHeldUntilConnected() {
        val work = workManager.uniqueWork(SyncScheduler.SYNC_WHEN_CONNECTED).single()

        assertThat(work.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(work.periodicityInfo).isNull()
        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun scheduleSyncAgain_keepsExistingWork() {
        val periodic = workManager.uniqueWork(SyncScheduler.PERIODIC_SYNC).single().id
        val oneTime = workManager.uniqueWork(SyncScheduler.SYNC_WHEN_CONNECTED).single().id

        scheduler.scheduleSync()

        assertThat(workManager.uniqueWork(SyncScheduler.PERIODIC_SYNC).map { it.id }).containsExactly(periodic)
        assertThat(workManager.uniqueWork(SyncScheduler.SYNC_WHEN_CONNECTED).map { it.id }).containsExactly(oneTime)
    }

    @Test
    fun scheduleImagePrefetch_waitsForUnmeteredNetwork_andKeepsPendingWork() {
        scheduler.scheduleImagePrefetch()
        val work = workManager.uniqueWork(SyncScheduler.IMAGE_PREFETCH).single()

        scheduler.scheduleImagePrefetch()

        assertThat(work.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(workManager.uniqueWork(SyncScheduler.IMAGE_PREFETCH).map { it.id }).containsExactly(work.id)
    }
}
