package com.lumiform.formviewer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lumiform.formviewer.sync.SyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FormViewerApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Lazy: creating it creates WorkManager, which reads workerFactory, so it must not depend on injection order.
    @Inject lateinit var syncScheduler: Lazy<SyncScheduler>

    // Coil's composables use this loader, so thumbnails share the pinned client and the filesDir cache with prefetch.
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    // WorkManager's default initializer is removed in the manifest, so it is created on demand with this factory.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.get().scheduleSync()
    }
}
