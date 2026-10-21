package com.lumiform.formviewer.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

internal val context: Context get() = ApplicationProvider.getApplicationContext()

internal fun initTestWorkManager(): WorkManager {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
    )
    return WorkManager.getInstance(context)
}

internal fun WorkManager.uniqueWork(name: String): List<WorkInfo> = getWorkInfosForUniqueWork(name).get()
