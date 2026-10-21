package com.lumiform.formviewer.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The background sync policy in one place: what runs, when, and on which network. Retries use WorkManager's default
 * backoff, exponential from 30 s.
 */
class SyncScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        /**
         * Called on every app start. The one-time sync is held by WorkManager until a network is available, so an app
         * started offline syncs as soon as the connection comes back without a connectivity callback of our own.
         * It also covers the launch, so the periodic sync starts one interval later instead of syncing twice.
         */
        fun scheduleSync() {
            val connected = Constraints(requiredNetworkType = NetworkType.CONNECTED)
            // UPDATE, not KEEP, so a new app version can change the interval or constraints of installed work; it
            // keeps the schedule and doesn't cancel a running sync.
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(connected)
                    .setInitialDelay(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .build(),
            )
            workManager.enqueueUniqueWork(
                SYNC_WHEN_CONNECTED,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connected).build(),
            )
        }

        /** KEEP is enough: a pending prefetch reads the form when it runs, so it always fetches the latest images. */
        fun scheduleImagePrefetch() {
            workManager.enqueueUniqueWork(
                IMAGE_PREFETCH,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ImagePrefetchWorker>()
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.UNMETERED))
                    .build(),
            )
        }

        companion object {
            const val PERIODIC_SYNC = "periodic_sync"
            const val SYNC_WHEN_CONNECTED = "sync_when_connected"
            const val IMAGE_PREFETCH = "image_prefetch"
            const val SYNC_INTERVAL_MINUTES = 30L
        }
    }
